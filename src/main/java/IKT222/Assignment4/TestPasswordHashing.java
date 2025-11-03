package IKT222.Assignment4;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestPasswordHashing {
    private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
    
    public static void main(String[] args) throws Exception {
        // Step 1: Run the password migration
        HashPasswords.main(args);
        
        // Step 2: Test the authentication with known credentials
        try (Connection db = DriverManager.getConnection(CONNECTION_URL)) {
            // Get a test user from the database
            try (PreparedStatement select = db.prepareStatement("SELECT username, password FROM user LIMIT 1")) {
                ResultSet user = select.executeQuery();
                if (user.next()) {
                    String username = user.getString("username");
                    String hashedPassword = user.getString("password");
                    
                    // Test 1: Check if the password is actually hashed
                    System.out.println("\nChecking if password is hashed");
                    if (hashedPassword.startsWith("$2a$") || hashedPassword.startsWith("$2b$")) {
                        System.out.println("Password is properly hashed with BCrypt");
                    } else {
                        System.out.println("Password is not hashed with BCrypt");
                    }
                    
                    // Test 2: Verify that original password from backup still works
                    System.out.println("\nTest 2: Verifying original password");
                    try (Connection backupDb = DriverManager.getConnection("jdbc:sqlite:db.sqlite3.backup")) {
                        try (PreparedStatement backupSelect = backupDb.prepareStatement("SELECT password FROM user WHERE username = ?")) {
                            backupSelect.setString(1, username);
                            ResultSet backupUser = backupSelect.executeQuery();
                            if (backupUser.next()) {
                                String originalPassword = backupUser.getString("password");
                                boolean matches = BCrypt.checkpw(originalPassword, hashedPassword);
                                if (matches) {
                                    System.out.println("Original password validates against hash");
                                } else {
                                    System.out.println("Original password does not validate");
                                }
                            }
                        }
                    }
                    
                    // Test 3: Verify that wrong password fails
                    System.out.println("\nTest 3: Verifying wrong password fails");
                    boolean shouldFail = BCrypt.checkpw("wrong_password", hashedPassword);
                    if (!shouldFail) {
                        System.out.println("Wrong password correctly fails validation");
                    } else {
                        System.out.println("Wrong password incorrectly passes validation");
                    }
                }
            }
        }
    }
}