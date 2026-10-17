package DatabaseService.Model.Controller.AssetsData.NonInvestmentAsset;

import DatabaseService.DatabaseInit;
import DatabaseService.Model.Controller.AssetsData.CommonFunctions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeleteAsset extends DatabaseInit {
    public static boolean deleteNonInvestmentAsset(int userId, int id) {
        try (Connection connection = openConnection()) {
            try {
                lockUser(connection, userId);
                String name;
                try (PreparedStatement statement = connection.prepareStatement("SELECT NAME FROM ALL_NON_INVESTMENT_ASSETS WHERE ID = ? AND USER_ID = ? FOR UPDATE")) {
                    statement.setInt(1, id); statement.setInt(2, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) { rollback(connection); return false; }
                        name = result.getString(1);
                    }
                }
                if (CommonFunctions.hasTransactions(connection, userId, name)) {
                    throw new IllegalArgumentException("Remove or move this account's transaction records before deleting it.");
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ALL_NON_INVESTMENT_ASSETS WHERE ID = ? AND USER_ID = ?")) {
                    statement.setInt(1, id); statement.setInt(2, userId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Account delete did not affect one owned row.");
                }
                connection.commit(); return true;
            } catch (SQLException | IllegalArgumentException exception) { rollback(connection); return false; }
        } catch (SQLException exception) { return false; }
    }
}
