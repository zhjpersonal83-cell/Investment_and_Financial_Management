package DatabaseService.Model.Controller.RegisterAndLogin;

import DatabaseService.DatabaseInit;
import Model.Tools.Passwords;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class Login extends DatabaseInit {
    public static boolean userLogin(Model.User user, String emailAddress, String password) {
        if (emailAddress == null || password == null || password.length() > 1024) return false;
        String email = emailAddress.trim().toLowerCase(Locale.ROOT);
        try (Connection connection = openConnection()) {
            try {
                int id; String forename, surname, stored;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT ID, FORENAME, SURNAME, PASSWORD FROM USERS WHERE LOWER(EMAIL_ADDRESS) = ? LIMIT 2")) {
                    statement.setString(1, email);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return Passwords.verify(password, null);
                        id = result.getInt("ID"); forename = result.getString("FORENAME");
                        surname = result.getString("SURNAME"); stored = result.getString("PASSWORD");
                        if (result.next()) return Passwords.verify(password, null); // Ambiguous legacy accounts require correction.
                    }
                }
                if (!Passwords.verify(password, stored)) return false;
                if (!Passwords.isEncoded(stored)) {
                    String encoded = Passwords.encode(password);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE USERS SET PASSWORD = ? WHERE ID = ? AND PASSWORD = ?")) {
                        statement.setString(1, encoded); statement.setInt(2, id); statement.setString(3, stored);
                        statement.executeUpdate(); // A concurrent successful login may already have upgraded it.
                    }
                }
                connection.commit();
                user.setId(id); user.setForename(forename); user.setSurname(surname); user.setEmailAddress(email);
                return true;
            } catch (SQLException exception) { rollback(connection); return false; }
        } catch (SQLException exception) { return false; }
    }
}
