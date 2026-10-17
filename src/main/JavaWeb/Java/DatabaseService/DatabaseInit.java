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
                java.sql.DatabaseMetaData metadata = connection.getMetaData();
                event.getServletContext().log("IAFM database driver: " + metadata.getDriverName() + " " + metadata.getDriverVersion());
            } catch (SQLException unavailable) {
                throw new IllegalStateException("Database migrations must be applied before startup.", unavailable);
            }
        }
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
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                return connection;
            } catch (SQLException exception) {
                connection.close();
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

    /**
     * Existing recorder tables hold a fixed lock marker and one non-positive sequence row. Positive legacy recycled
     * IDs are preserved, but are no longer reused. The small allocation transaction commits
     * before business writes, so an account transaction never holds the sequence lock.
     */
    protected static int nextId(String table) throws SQLException {
        if (!java.util.Set.of("USERS", "ALL_NON_INVESTMENT_ASSETS", "ALL_INVESTMENT_ASSETS",
                "BASIC_VARIABLES_AND_DATA", "INCOME_AND_EXPENDITURE_DATA",
                "ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES").contains(table)) {
            throw new IllegalArgumentException("Unsupported ID sequence.");
        }
        String recorder = "ID_RECORDER_" + table;
        SQLException last = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            try (Connection connection = openConnection()) {
                try {
                    // A fixed marker is essential: locking a sequence row whose primary key
                    // changes can otherwise miss the moved row under concurrent initialization.
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + recorder
                            + " (TABLE_ID) VALUES (?) ON DUPLICATE KEY UPDATE TABLE_ID = TABLE_ID")) {
                        statement.setInt(1, Integer.MIN_VALUE);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement("SELECT TABLE_ID FROM " + recorder + " WHERE TABLE_ID = ? FOR UPDATE")) {
                        statement.setInt(1, Integer.MIN_VALUE);
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) throw new SQLException("ID lock marker is unavailable.", "40001");
                        }
                    }
                    Integer previous = null;
                    try (PreparedStatement statement = connection.prepareStatement("SELECT TABLE_ID FROM " + recorder
                            + " WHERE TABLE_ID <= 0 AND TABLE_ID > ? ORDER BY TABLE_ID LIMIT 1 FOR UPDATE")) {
                        statement.setInt(1, Integer.MIN_VALUE);
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) previous = result.getInt(1);
                        }
                    }
                    long maximum = 0;
                    if (previous == null) {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(ID) FROM " + table);
                             ResultSet result = statement.executeQuery()) {
                            if (result.next()) maximum = result.getLong(1);
                        }
                        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(TABLE_ID) FROM " + recorder);
                             ResultSet result = statement.executeQuery()) {
                            if (result.next()) maximum = Math.max(maximum, result.getLong(1));
                        }
                    } else maximum = -(long) previous;
                    if (maximum >= Integer.MAX_VALUE) throw new SQLException("The integer ID range is exhausted.");
                    int next = (int) maximum + 1;
                    String sql = previous == null ? "INSERT INTO " + recorder + " (TABLE_ID) VALUES (?)"
                            : "UPDATE " + recorder + " SET TABLE_ID = ? WHERE TABLE_ID = ?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, -next);
                        if (previous != null) statement.setInt(2, previous);
                        if (statement.executeUpdate() != 1) throw new SQLException("ID allocation conflict.", "40001");
                    }
                    connection.commit();
                    return next;
                } catch (SQLException exception) {
                    rollback(connection);
                    String state = exception.getSQLState();
                    if (state == null || !(state.startsWith("23") || state.startsWith("40"))) throw exception;
                    last = exception;
                }
            }
        }
        throw new SQLException("ID allocation is busy; retry the request.", last);
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
