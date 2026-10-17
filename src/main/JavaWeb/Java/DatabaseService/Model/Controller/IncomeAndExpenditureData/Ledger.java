package DatabaseService.Model.Controller.IncomeAndExpenditureData;

import Model.Tools.Computing;
import java.sql.*;
import java.util.*;

/** Balanced journals accompany the existing MVC transaction records and account balances. */
public final class Ledger {
    private Ledger() { }

    public static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS LEDGER_JOURNALS (ID VARCHAR(36) PRIMARY KEY, USER_ID INT NOT NULL, KIND VARCHAR(20) NOT NULL, DATE DATE NOT NULL, BOOK_CURRENCY VARCHAR(50) NOT NULL, ORIGIN VARCHAR(30) NOT NULL, EVIDENCE VARCHAR(255) NOT NULL, FOREIGN KEY(USER_ID) REFERENCES USERS(ID))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS LEDGER_ENTRIES (JOURNAL_ID VARCHAR(36) NOT NULL, LINE_NUMBER INT NOT NULL, RECORD_ID INT, ACCOUNT_KIND VARCHAR(30) NOT NULL, ACCOUNT_NAME VARCHAR(150) NOT NULL, NATIVE_CURRENCY VARCHAR(50) NOT NULL, NATIVE_AMOUNT VARCHAR(15) NOT NULL, DEBIT_MINOR BIGINT NOT NULL, CREDIT_MINOR BIGINT NOT NULL, BALANCE_TYPE VARCHAR(50), PRIMARY KEY(JOURNAL_ID, LINE_NUMBER), FOREIGN KEY(JOURNAL_ID) REFERENCES LEDGER_JOURNALS(ID) ON DELETE CASCADE)");
        }
        boolean exists = false;
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "INCOME_AND_EXPENDITURE_DATA", "JOURNAL_ID")) { exists = columns.next(); }
        if (!exists) try (Statement statement = connection.createStatement()) { statement.executeUpdate("ALTER TABLE INCOME_AND_EXPENDITURE_DATA ADD COLUMN JOURNAL_ID VARCHAR(36)"); }
        boolean indexed = false;
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "INCOME_AND_EXPENDITURE_DATA", false, false)) {
            while (indexes.next()) if ("IDX_IAFM_TRANSACTION_JOURNAL".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) indexed = true;
        }
        if (!indexed) try (Statement statement = connection.createStatement()) { statement.executeUpdate("CREATE INDEX IDX_IAFM_TRANSACTION_JOURNAL ON INCOME_AND_EXPENDITURE_DATA (JOURNAL_ID, USER_ID)"); }
    }

    static void external(Connection connection, int userId, int id, String type, String platform,
                         String amount, java.sql.Date date, String currency) throws SQLException {
        if (!Set.of("income", "expense").contains(type)) throw new IllegalArgumentException("Use a paired transfer for internal movements.");
        String journal = create(connection, userId, type, date, currency, "application", "User entered external cash flow");
        long minor = Computing.toMinorUnits(amount);
        boolean income = "income".equals(type);
        entry(connection, journal, 1, id, "asset", platform, currency, amount, income ? minor : 0, income ? 0 : minor, type);
        entry(connection, journal, 2, null, income ? "external_income" : "external_expense", income ? "Income" : "Expense", currency, amount, income ? 0 : minor, income ? minor : 0, null);
        attach(connection, userId, id, journal);
        validate(connection, journal);
    }

    static void transfer(Connection connection, int userId, int outId, int inId,
                         CommonFunctionsNonDatabaseAbstraction.Account from, CommonFunctionsNonDatabaseAbstraction.Account to,
                         String outgoing, String incoming, java.sql.Date date) throws SQLException {
        String journal = create(connection, userId, "transfer", date, from.currency, "application", "Owned accounts; actual sent and received amounts; fees recorded separately");
        // The sent currency is the book currency. The receiving asset retains its actual native amount.
        // Its book cost is the actual amount surrendered, so FX does not manufacture income or expense.
        long bookMinor = Computing.toMinorUnits(outgoing);
        entry(connection, journal, 1, outId, "asset", from.name, from.currency, outgoing, 0, bookMinor, "transfer_out");
        entry(connection, journal, 2, inId, "asset", to.name, to.currency, incoming, bookMinor, 0, "transfer_in");
        attach(connection, userId, outId, journal); attach(connection, userId, inId, journal);
        validate(connection, journal);
    }

    private static String create(Connection connection, int userId, String kind, java.sql.Date date,
                                 String currency, String origin, String evidence) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO LEDGER_JOURNALS (ID, USER_ID, KIND, DATE, BOOK_CURRENCY, ORIGIN, EVIDENCE) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1,id); statement.setInt(2,userId); statement.setString(3,kind); statement.setDate(4,date);
            statement.setString(5,currency); statement.setString(6,origin); statement.setString(7,evidence); statement.executeUpdate();
        }
        return id;
    }

    private static void entry(Connection connection, String journal, int line, Integer recordId, String kind,
                              String account, String currency, String amount, long debit, long credit, String balanceType) throws SQLException {
        if (debit < 0 || credit < 0 || (debit == 0) == (credit == 0)) throw new IllegalArgumentException("Each entry must have exactly one positive side.");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO LEDGER_ENTRIES (JOURNAL_ID, LINE_NUMBER, RECORD_ID, ACCOUNT_KIND, ACCOUNT_NAME, NATIVE_CURRENCY, NATIVE_AMOUNT, DEBIT_MINOR, CREDIT_MINOR, BALANCE_TYPE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1,journal); statement.setInt(2,line); if(recordId == null) statement.setNull(3,Types.INTEGER); else statement.setInt(3,recordId);
            statement.setString(4,kind); statement.setString(5,account); statement.setString(6,currency); statement.setString(7,amount);
            statement.setLong(8,debit); statement.setLong(9,credit); statement.setString(10,balanceType); statement.executeUpdate();
        }
    }

    private static void attach(Connection connection, int userId, int id, String journal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE INCOME_AND_EXPENDITURE_DATA SET JOURNAL_ID = ? WHERE ID = ? AND USER_ID = ? AND JOURNAL_ID IS NULL")) {
            statement.setString(1,journal); statement.setInt(2,id); statement.setInt(3,userId);
            if(statement.executeUpdate()!=1) throw new SQLException("A record must belong to exactly one journal.");
        }
    }

    public static void validate(Connection connection, String journal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*), SUM(DEBIT_MINOR), SUM(CREDIT_MINOR) FROM LEDGER_ENTRIES WHERE JOURNAL_ID = ?")) {
            statement.setString(1,journal);
            try(ResultSet result=statement.executeQuery()) {
                if(!result.next() || result.getInt(1)<2 || result.getLong(2)<=0 || result.getLong(2)!=result.getLong(3)) throw new SQLException("Journal is not balanced.");
            }
        }
    }

    static void removeExternal(Connection connection, int userId, int recordId) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement("SELECT JOURNAL_ID, TYPE FROM INCOME_AND_EXPENDITURE_DATA WHERE ID = ? AND USER_ID = ? FOR UPDATE")) {
            statement.setInt(1,recordId); statement.setInt(2,userId);
            try(ResultSet result=statement.executeQuery()) {
                if(!result.next()) throw new SQLException("Missing transaction.");
                if(result.getString("TYPE").startsWith("transfer_")) throw new IllegalArgumentException("A transfer cannot be edited one leg at a time.");
                String journal=result.getString("JOURNAL_ID");
                if(journal!=null) remove(connection,userId,journal);
            }
        }
    }

    static void remove(Connection connection,int userId,String journal) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("UPDATE INCOME_AND_EXPENDITURE_DATA SET JOURNAL_ID = NULL WHERE JOURNAL_ID = ? AND USER_ID = ?")) {
            statement.setString(1,journal); statement.setInt(2,userId); statement.executeUpdate();
        }
        try(PreparedStatement statement=connection.prepareStatement("DELETE FROM LEDGER_JOURNALS WHERE ID = ? AND USER_ID = ?")) {
            statement.setString(1,journal); statement.setInt(2,userId);
            if(statement.executeUpdate()!=1) throw new SQLException("Journal ownership mismatch.");
        }
    }

    static boolean deleteTransfer(Connection connection,int userId,int recordId) throws SQLException {
        String journal;
        try(PreparedStatement statement=connection.prepareStatement("SELECT JOURNAL_ID FROM INCOME_AND_EXPENDITURE_DATA WHERE ID = ? AND USER_ID = ? FOR UPDATE")) {
            statement.setInt(1,recordId); statement.setInt(2,userId);
            try(ResultSet result=statement.executeQuery()) { if(!result.next() || (journal=result.getString(1))==null) throw new IllegalArgumentException("Legacy transfer requires reconciliation before deletion."); }
        }
        validate(connection,journal);
        List<Integer> ids=new ArrayList<>();
        List<CommonFunctionsNonDatabaseAbstraction.Account> accounts=new ArrayList<>();
        Set<String> directions=new HashSet<>();
        try(PreparedStatement statement=connection.prepareStatement("SELECT T.ID,T.TYPE,T.PLATFORM,T.AMOUNT,T.CURRENCY,E.BALANCE_TYPE FROM INCOME_AND_EXPENDITURE_DATA T JOIN LEDGER_JOURNALS J ON J.ID=T.JOURNAL_ID AND J.USER_ID=T.USER_ID JOIN LEDGER_ENTRIES E ON E.JOURNAL_ID=J.ID AND E.RECORD_ID=T.ID WHERE T.JOURNAL_ID=? AND T.USER_ID=? AND J.KIND='transfer' FOR UPDATE")) {
            statement.setString(1,journal); statement.setInt(2,userId);
            try(ResultSet result=statement.executeQuery()) {
                while(result.next()) {
                    String type=result.getString("TYPE");
                    if(!Set.of("transfer_in","transfer_out").contains(type)) throw new SQLException("Invalid transfer journal.");
                    directions.add(type); ids.add(result.getInt("ID"));
                    CommonFunctionsNonDatabaseAbstraction.Account account=CommonFunctionsNonDatabaseAbstraction.find(connection,userId,result.getString("PLATFORM"));
                    if(!account.currency.equals(result.getString("CURRENCY"))) throw new IllegalArgumentException("Historical account currency differs.");
                    // A reclassified historical row reverses its original balance effects, not newly invented investment contributions.
                    CommonFunctionsNonDatabaseAbstraction.change(account,result.getString("BALANCE_TYPE"),result.getString("AMOUNT"),true);
                    accounts.add(account);
                }
            }
        }
        if(ids.size()!=2 || directions.size()!=2 || (accounts.get(0).id==accounts.get(1).id && accounts.get(0).investment==accounts.get(1).investment)) throw new SQLException("Transfer requires two different owned accounts.");
        for(CommonFunctionsNonDatabaseAbstraction.Account account:accounts) CommonFunctionsNonDatabaseAbstraction.save(connection,account);
        remove(connection,userId,journal);
        try(PreparedStatement statement=connection.prepareStatement("DELETE FROM INCOME_AND_EXPENDITURE_DATA WHERE ID IN (?,?) AND USER_ID=?")) {
            statement.setInt(1,ids.get(0)); statement.setInt(2,ids.get(1)); statement.setInt(3,userId);
            if(statement.executeUpdate()!=2) throw new SQLException("Both transfer legs must be deleted together.");
        }
        return true;
    }
}
