package IKT222.Assignment4;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;

@SuppressWarnings("serial")
public class AppServlet extends HttpServlet {

  private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
  private static final String AUTH_QUERY = "select * from user where username='%s' and password='%s'";
  private static final String SEARCH_QUERY = "select * from patient where surname like '%s'";

  private final String recaptchaSecret = "6LeOhAAsAAAAAN8EJncX5atw77itFQFf6EMh5w98";

  private final java.util.concurrent.ConcurrentHashMap<String, Integer> failures = new java.util.concurrent.ConcurrentHashMap<>();

  private final Configuration fm = new Configuration(Configuration.VERSION_2_3_28);
  private Connection database;

  @Override
  public void init() throws ServletException {
    configureTemplateEngine();
    connectToDatabase();
  }


  private boolean captchaRequired(String user, String ip) {
      int u = failures.getOrDefault("u:"+user, 0);
      int i = failures.getOrDefault("i:"+ip, 0);
      return (u >= 3) || (i >= 10);
  }

  private boolean verifyCaptcha(String token, String ip) throws Exception {
      if (token == null || token.isEmpty()) return false;

      String body = "secret=" + URLEncoder.encode(recaptchaSecret, String.valueOf(StandardCharsets.UTF_8))
              + "&response=" + URLEncoder.encode(token, String.valueOf(StandardCharsets.UTF_8))
              + "&remoteip=" + URLEncoder.encode(ip, String.valueOf(StandardCharsets.UTF_8));

      org.eclipse.jetty.util.ssl.SslContextFactory sslContextFactory = new SslContextFactory.Client();
      HttpClient client = new HttpClient(sslContextFactory);
      client.start();

      try {
          ContentResponse response = client.POST("https://www.google.com/recaptcha/api/siteverify")
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .content(new StringContentProvider(body))
                  .send();

          String responseBody = response.getContentAsString();
          return responseBody.contains("\"sucess\": true") || responseBody.contains("\"success\": true");
      } finally {
          client.stop();
      }
  }

  private void configureTemplateEngine() throws ServletException {
    try {
      fm.setDirectoryForTemplateLoading(new File("./templates"));
      fm.setDefaultEncoding("UTF-8");
      fm.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
      fm.setLogTemplateExceptions(false);
      fm.setWrapUncheckedExceptions(true);
    } catch (IOException error) {
      throw new ServletException(error.getMessage());
    }
  }

  private void connectToDatabase() throws ServletException {
    try {
      database = DriverManager.getConnection(CONNECTION_URL);
    } catch (SQLException error) {
      throw new ServletException(error.getMessage());
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
//        String username = request.getParameter("username");
//        String ip = request.getRemoteAddr();
//
//        Map<String, Object> model = new HashMap<>();
//        model.put("captchaRequired", username != null && captchaRequired(username, ip));
//        model.put("username", username);
      Template template = fm.getTemplate("login.html");
      template.process(null, response.getWriter());
      response.setContentType("text/html");
      response.setStatus(HttpServletResponse.SC_OK);
    } catch (TemplateException error) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Get form parameters
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    String surname = request.getParameter("surname");
    String captchaToken =  request.getParameter("g-recaptcha-response");
    String ip = request.getRemoteAddr();

    try {
        if (captchaRequired(username, ip)) {
            if (!verifyCaptcha(captchaToken, ip)) {
                Map<String, Object> model = new HashMap<>();
                model.put("captchaRequired", true);
                model.put("username", username);
                model.put("error", "Captcha verification failed. Please try again.");
                Template template = fm.getTemplate("login.html");
                template.process(model, response.getWriter());
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
        }

      if (authenticated(username, password)) {
          failures.remove("u:" + username);
          failures.remove("i:"+ip);
        // Get search results and merge with template
        Map<String, Object> model = new HashMap<>();
        model.put("records", searchResults(surname));
        Template template = fm.getTemplate("details.html");
        template.process(model, response.getWriter());
      } else {
          failures.merge("u:"+username, 1, Integer::sum);
          failures.merge("i:"+ip, 1, Integer::sum);
        Template template = fm.getTemplate("invalid.html");
        template.process(null, response.getWriter());
      }
      response.setContentType("text/html");
      response.setStatus(HttpServletResponse.SC_OK);
    } catch (Exception error) {
        error.printStackTrace();
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private boolean authenticated(String username, String password) throws SQLException {
    String query = String.format(AUTH_QUERY, username, password);
    try (Statement stmt = database.createStatement()) {
      ResultSet results = stmt.executeQuery(query);
      return results.next();
    }
  }

  private List<Record> searchResults(String surname) throws SQLException {
    List<Record> records = new ArrayList<>();
    String query = String.format(SEARCH_QUERY, surname);
    try (Statement stmt = database.createStatement()) {
      ResultSet results = stmt.executeQuery(query);
      while (results.next()) {
        Record rec = new Record();
        rec.setSurname(results.getString(2));
        rec.setForename(results.getString(3));
        rec.setAddress(results.getString(4));
        rec.setDateOfBirth(results.getString(5));
        rec.setDoctorId(results.getString(6));
        rec.setDiagnosis(results.getString(7));
        records.add(rec);
      }
    }
    return records;
  }
}
