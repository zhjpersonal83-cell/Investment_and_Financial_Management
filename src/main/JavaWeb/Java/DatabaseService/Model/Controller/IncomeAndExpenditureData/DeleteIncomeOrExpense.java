package DatabaseService.Model.Controller.IncomeAndExpenditureData;

import DatabaseService.DatabaseInit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeleteIncomeOrExpense extends DatabaseInit {
    /** Client-supplied type/account/amount are deliberately ignored; the owned stored row is authoritative. */
    public static boolean deleteIncomeOrExpense(int userID, int id, String ignoredType, String ignoredPlatform, String ignoredAmount) {
        try (Connection connection = openConnection()) {
            try {
                lockUser(connection, userID);
                String type, platform, amount, currency;
                try (PreparedStatement statement = connection.prepareStatement("SELECT TYPE, PLATFORM, AMOUNT, CURRENCY FROM "
                        + "INCOME_AND_EXPENDITURE_DATA WHERE ID = ? AND USER_ID = ? FOR UPDATE")) {
                    statement.setInt(1, id); statement.setInt(2, userID);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) { rollback(connection); return false; }
                        type = result.getString("TYPE"); platform = result.getString("PLATFORM");
                        amount = result.getString("AMOUNT"); currency = result.getString("CURRENCY");
                    }
                }
                if (type.startsWith("transfer_")) {
                    Ledger.deleteTransfer(connection, userID, id);
                    connection.commit(); return true;
                }
                Ledger.removeExternal(connection, userID, id);
                // Old imports may contain a zero-value statement row; deleting it has no balance effect.
                if (Model.Tools.Computing.toMinorUnits(amount) != 0) {
                    CommonFunctionsNonDatabaseAbstraction.Account account = CommonFunctionsNonDatabaseAbstraction.find(connection, userID, platform);
                    if (!account.currency.equals(currency)) throw new IllegalArgumentException("The historical transaction currency differs from the account currency.");
                    CommonFunctionsNonDatabaseAbstraction.change(account, type, amount, true);
                    CommonFunctionsNonDatabaseAbstraction.save(connection, account);
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM INCOME_AND_EXPENDITURE_DATA WHERE ID = ? AND USER_ID = ?")) {
                    statement.setInt(1, id); statement.setInt(2, userID);
                    if (statement.executeUpdate() != 1) throw new SQLException("Transaction delete did not affect one owned row.");
                }
                connection.commit(); return true;
            } catch (SQLException | IllegalArgumentException | ArithmeticException exception) { rollback(connection); return false; }
        } catch (SQLException exception) { return false; }
    }
}
