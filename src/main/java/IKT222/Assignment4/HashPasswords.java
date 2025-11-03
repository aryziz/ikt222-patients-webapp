package IKT222.Assignment4;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class HashPasswords {
    private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
    
    public static void main(String[] args) throws Exception {
        // First, get original passwords from backup
        try (Connection backupDb = DriverManager.getConnection("jdbc:sqlite:db.sqlite3.backup")) {
            try (Connection db = DriverManager.getConnection(CONNECTION_URL)) {
                // Get all users and their original passwords from backup
                try (PreparedStatement select = backupDb.prepareStatement("SELECT id, password FROM user")) {
                    ResultSet users = select.executeQuery();
                    
                    // For each user, hash their original password and update the main DB
                    try (PreparedStatement update = db.prepareStatement("UPDATE user SET password = ? WHERE id = ?")) {
                        while (users.next()) {
                            int id = users.getInt("id");
                            String plainPassword = users.getString("password");
                            String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
                            
                            update.setString(1, hashedPassword);
                            update.setInt(2, id);
                            update.executeUpdate();
                            
                            System.out.println("Updated password for user ID: " + id);
                        }
                    }
                }
            }
        }
        System.out.println("All passwords have been hashed successfully");
    }
}