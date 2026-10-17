package DatabaseService;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared connection utilities; each operation owns its connection and transaction. */
@WebListener
public class DatabaseInit implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        new RegisterDriver().registerDriver();
        if (Boolean.parseBoolean(setting("iafm.schema.autoInit", "IAFM_SCHEMA_AUTO_INIT", "true"))) {
            new CreateAllTables().createAllTables();
        } else {
            // Production migrations run separately with a maintenance identity. The runtime needs DML only.
            try (Connection connection = openConnection(); java.sql.Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT JOURNAL_ID FROM INCOME_AND_EXPENDITURE_DATA WHERE 1 = 0").close();
                statement.executeQuery("SELECT ID FROM LEDGER_JOURNALS WHERE 1 = 0").close();
                statement.executeQuery("SELECT JOURNAL_ID FROM LEDGER_ENTRIES WHERE 1 = 0").close();
                statement.executeQuery("SELECT OBSERVED_THROUGH FROM ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES WHERE 1 = 0").close();
                try (ResultSet inflationLock = statement.executeQuery("SELECT ID FROM INFLATION_SERIES_LOCK WHERE ID = 1")) {
                    if (!inflationLock.next()) throw new SQLException("Inflation schema migration is required.");
                }
                CreateAllTables.verifyIdSchema(connection);
                java.sql.DatabaseMetaData metadata = connection.getMetaData();
                event.getServletContext().log("IAFM database driver: " + metadata.getDriverName() + " " + metadata.getDriverVersion());
            } catch (SQLException unavailable) {
                throw new IllegalStateException("Database migrations must be applied before startup.", unavailable);
            }
        }
        new SetIds().setAllIds();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        new DeregisterDriver().deregisterDriver();
        AbandonedConnectionCleanupThread.checkedShutdown();
    }

    private static String setting(String property, String environment, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null) value = System.getenv(environment);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    protected static Connection openConnection() {
        try {
            String jndi = setting("iafm.db.jndi", "IAFM_DB_JNDI", null);
            Connection connection;
            if (jndi != null) {
                DataSource source = (DataSource) new InitialContext().lookup(jndi);
                connection = source.getConnection();
            } else {
                String url = setting("iafm.db.url", "IAFM_DB_URL", "jdbc:mysql://localhost:3306/IAFM");
                String username = setting("iafm.db.user", "IAFM_DB_USER", null);
                String password = setting("iafm.db.password", "IAFM_DB_PASSWORD", "");
                if (username == null) throw new IllegalStateException("Configure IAFM_DB_USER or IAFM_DB_JNDI.");
                connection = DriverManager.getConnection(url, username, password);
            }
            try {
                connection.setReadOnly(false);
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(RequestIdentity.isReadRequest()
                        ? Connection.TRANSACTION_REPEATABLE_READ : Connection.TRANSACTION_READ_COMMITTED);
                RequestIdentity.validateUser(connection);
                return connection;
            } catch (SQLException | RuntimeException exception) {
                // A failed identity check may already hold row locks; release them before returning a pooled connection.
                try { connection.rollback(); } catch (SQLException failure) { exception.addSuppressed(failure); }
                try { connection.close(); } catch (SQLException failure) { exception.addSuppressed(failure); }
                throw exception;
            }
        } catch (SQLException | NamingException exception) {
            throw new IllegalStateException("Database connection is unavailable.", exception);
        }
    }

    protected static PreparedStatement[] getPreparedStatements(int count) { return new PreparedStatement[count]; }
    protected static ResultSet[] getResultSets(int count) { return new ResultSet[count]; }

    protected static void releaseResources(Connection connection, PreparedStatement[] statements, ResultSet[] results) {
        if (results != null) for (ResultSet result : results) close(result);
        if (statements != null) for (PreparedStatement statement : statements) close(statement);
        close(connection);
    }

    private static void close(AutoCloseable resource) {
        if (resource == null) return;
        try { resource.close(); }
        catch (Exception exception) { System.err.println("A database resource could not be closed."); }
    }

    protected static void rollback(Connection connection) {
        if (connection == null) return;
        try { connection.rollback(); }
        catch (SQLException exception) { System.err.println("A database transaction could not be rolled back."); }
    }

    /** Serialize mutations only for the same owner, including across application instances. */
    protected static void lockUser(Connection connection, int userId) throws SQLException {
        if (userId <= 0) throw new IllegalArgumentException("A valid user is required.");
        try (PreparedStatement statement = connection.prepareStatement("SELECT ID FROM USERS WHERE ID = ? FOR UPDATE")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("User does not exist.");
            }
        }
    }

    static final java.util.List<String> ID_TABLES = java.util.List.of(
            "ALL_INVESTMENT_ASSETS", "ALL_NON_INVESTMENT_ASSETS",
            "ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES", "BASIC_VARIABLES_AND_DATA",
            "INCOME_AND_EXPENDITURE_DATA", "USERS");

    static void requireIdTable(String table) {
        if (!ID_TABLES.contains(table)) throw new IllegalArgumentException("Unsupported recyclable ID table.");
    }

    /** Lock owner first, then every required allocator in this canonical order before changing IDs. */
    public static void lockIdTables(Connection connection, String... tables) throws SQLException {
        if (connection.getAutoCommit()) throw new SQLException("ID allocation requires an explicit business transaction.");
        java.util.SortedSet<String> ordered = new java.util.TreeSet<>(java.util.Arrays.asList(tables));
        for (String table : ordered) {
            requireIdTable(table);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT HIGH_WATER FROM ID_ALLOCATION_STATE WHERE TABLE_NAME=? FOR UPDATE")) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("ID schema migration is required before writes.");
                }
            }
        }
    }

    /** Claim an ID in the insertion transaction. Insert it before allocating again for the same table. */
    protected static int nextId(Connection connection, String table) throws SQLException {
        lockIdTables(connection, table);
        String recorder = "ID_RECORDER_" + table;
        while (true) {
            Integer point = null, first = null, last = null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT MIN(TABLE_ID) FROM " + recorder + " WHERE TABLE_ID>0");
                 ResultSet result = statement.executeQuery()) {
                if (result.next()) { int value = result.getInt(1); if (!result.wasNull()) point = value; }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT FIRST_ID,LAST_ID FROM ID_RECOVERY_RANGES WHERE TABLE_NAME=? ORDER BY FIRST_ID LIMIT 1")) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) { first = result.getInt(1); last = result.getInt(2); }
                }
            }
            if (point == null && first == null) break;
            int candidate = point == null ? first : first == null ? point : Math.min(point, first);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + recorder + " WHERE TABLE_ID=?")) {
                statement.setInt(1, candidate); statement.executeUpdate();
            }
            if (first != null && candidate == first) {
                String sql = first.equals(last)
                        ? "DELETE FROM ID_RECOVERY_RANGES WHERE TABLE_NAME=? AND FIRST_ID=?"
                        : "UPDATE ID_RECOVERY_RANGES SET FIRST_ID=? WHERE TABLE_NAME=? AND FIRST_ID=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int parameter = 1;
                    if (!first.equals(last)) statement.setInt(parameter++, candidate + 1);
                    statement.setString(parameter++, table); statement.setInt(parameter, candidate); statement.executeUpdate();
                }
            }
            // Invalid legacy recycle rows are removed, never assigned to a live or referenced record.
            if (!SetIds.isUnavailable(connection, table, candidate)) {
                rememberHighWater(connection, table, candidate);
                return candidate;
            }
        }
        long maximum;
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(ID),0) FROM " + table);
             ResultSet result = statement.executeQuery()) { result.next(); maximum = result.getLong(1); }
        if (maximum < 0 || maximum >= Integer.MAX_VALUE) throw new SQLException("The integer ID range is exhausted.");
        int candidate = (int) maximum + 1;
        if (SetIds.isUnavailable(connection, table, candidate)) {
            throw new SQLException("The next ID has surviving references; repair those references before inserting.");
        }
        rememberHighWater(connection, table, candidate);
        return candidate;
    }

    static void rememberHighWater(Connection connection, String table, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ID_ALLOCATION_STATE SET HIGH_WATER=CASE WHEN HIGH_WATER<? THEN ? ELSE HIGH_WATER END WHERE TABLE_NAME=?")) {
            statement.setInt(1, id); statement.setInt(2, id); statement.setString(3, table);
            // MySQL may report zero changed rows when a recycled ID is below the high-water mark.
            statement.executeUpdate();
        }
    }

    /** Recycle only after the actual deletion and all dependent deletions, using the same transaction. */
    public static void recycleId(Connection connection, String table, int id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Only positive IDs can be recycled.");
        lockIdTables(connection, table);
        if (SetIds.isUnavailable(connection, table, id)) throw new SQLException("A live or referenced ID cannot be recycled.");
        rememberHighWater(connection, table, id);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT FIRST_ID FROM ID_RECOVERY_RANGES WHERE TABLE_NAME=? AND FIRST_ID<=? AND LAST_ID>=?")) {
            statement.setString(1, table); statement.setInt(2, id); statement.setInt(3, id);
            try (ResultSet result = statement.executeQuery()) { if (result.next()) return; }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO ID_RECORDER_" + table
                + " (TABLE_ID) VALUES (?) ON DUPLICATE KEY UPDATE TABLE_ID=TABLE_ID")) {
            statement.setInt(1, id); statement.executeUpdate();
        }
    }

    protected static java.sql.Date recordDate(String value) {
        try { return java.sql.Date.valueOf(java.time.LocalDate.parse(value)); }
        catch (java.time.DateTimeException | NullPointerException invalid) {
            throw new IllegalArgumentException("Use a valid date in YYYY-MM-DD format.");
        }
    }

    protected static String databaseAmount(String amount) {
        String normalized = Model.Tools.Computing.normalize(amount);
        if (normalized.length() > 15) throw new IllegalArgumentException("Amount exceeds the existing column capacity.");
        return normalized;
    }

    protected static String decimalAmount(String amount) {
        String normalized = Model.Tools.Computing.normalize(amount);
        long minor = Model.Tools.Computing.toMinorUnits(normalized);
        if (minor < -999999999999L || minor > 999999999999L) {
            throw new IllegalArgumentException("Amount exceeds the existing decimal column capacity.");
        }
        return normalized;
    }
}
