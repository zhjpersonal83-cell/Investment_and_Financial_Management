package DatabaseService;

import Model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** Pins HTTP identities to immutable generations while the business transaction holds its locks. */
public final class RequestIdentity extends DatabaseInit implements AutoCloseable {
    private static final ThreadLocal<RequestIdentity> CURRENT = new ThreadLocal<>();
    private static final Set<String> OWNED = Set.of("ALL_INVESTMENT_ASSETS", "ALL_NON_INVESTMENT_ASSETS",
            "INCOME_AND_EXPENDITURE_DATA", "BASIC_VARIABLES_AND_DATA");
    private static final String SHARED = "ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES";
    public record Record(String table, int id, String generation) {
        public Record {
            if ((!OWNED.contains(table) && !SHARED.equals(table)) || id <= 0 || !validGeneration(generation))
                throw new StaleIdentity(false);
        }
    }
    public record Account(String name, String generation) {
        public Account {
            if (name == null || name.isBlank() || !validGeneration(generation)) throw new StaleIdentity(false);
        }
    }
    private final int userId;
    private final String userGeneration;
    private final boolean mutation;
    private final List<Record> records;
    private final List<Account> accounts;

    private RequestIdentity(User user, boolean mutation, List<Record> records, List<Account> accounts) {
        if (user == null || user.getId() <= 0 || !validGeneration(user.getGeneration())) throw new StaleIdentity(true);
        this.userId = user.getId(); this.userGeneration = user.getGeneration(); this.mutation = mutation;
        this.records = List.copyOf(records); this.accounts = List.copyOf(accounts);
    }

    public static RequestIdentity begin(User user, boolean mutation, List<Record> records, List<Account> accounts) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested request identity.");
        RequestIdentity identity = new RequestIdentity(user, mutation, records, accounts);
        CURRENT.set(identity);
        return identity;
    }

    public static boolean validGeneration(String value) {
        return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static boolean isActive() { return CURRENT.get() != null; }

    public static void verifyCurrentUser() throws SQLException {
        try (Connection connection = openConnection()) { connection.commit(); }
    }

    public static boolean isReadRequest() {
        RequestIdentity identity = CURRENT.get();
        return identity != null && !identity.mutation;
    }

    /** Every protected connection pins the user until commit, including read-only API connections. */
    public static void validateUser(Connection connection) throws SQLException {
        RequestIdentity identity = CURRENT.get();
        if (identity == null) return;
        try (PreparedStatement statement = connection.prepareStatement("SELECT GENERATION FROM USERS WHERE ID = ? "
                + (identity.mutation || !connection.getMetaData().getDatabaseProductName().contains("MySQL") ? "FOR UPDATE" : "LOCK IN SHARE MODE"))) {
            statement.setInt(1, identity.userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !identity.userGeneration.equalsIgnoreCase(result.getString(1)))
                    throw new StaleIdentity(true);
            }
        }
        if (identity.mutation) validateOwnedRecords(connection, identity.userId);
    }

    /** The owner row serializes all account and transaction changes, so these reads cannot race a recycle. */
    public static void validateOwnedRecords(Connection connection, int userId) throws SQLException {
        RequestIdentity identity = CURRENT.get();
        if (identity == null) return;
        if (identity.userId != userId) throw new StaleIdentity(true);
        for (Record record : identity.records) {
            if (!OWNED.contains(record.table())) continue;
            try (PreparedStatement statement = connection.prepareStatement("SELECT GENERATION FROM " + record.table()
                    + " WHERE ID = ? AND USER_ID = ?")) {
                statement.setInt(1, record.id()); statement.setInt(2, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !record.generation().equalsIgnoreCase(result.getString(1)))
                        throw new StaleIdentity(false);
                }
            }
        }
        for (Account account : identity.accounts) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT GENERATION FROM ALL_NON_INVESTMENT_ASSETS WHERE USER_ID = ? AND NAME = ? UNION ALL "
                            + "SELECT GENERATION FROM ALL_INVESTMENT_ASSETS WHERE USER_ID = ? AND INVESTMENT_PLATFORM = ?")) {
                statement.setInt(1, userId); statement.setString(2, account.name());
                statement.setInt(3, userId); statement.setString(4, account.name());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !account.generation().equalsIgnoreCase(result.getString(1)) || result.next())
                        throw new StaleIdentity(false);
                }
            }
        }
    }

    /** Call only after acquiring the shared inflation-series lock in the mutation transaction. */
    public static void validateSharedRecords(Connection connection) throws SQLException {
        RequestIdentity identity = CURRENT.get();
        if (identity == null) return;
        for (Record record : identity.records) {
            if (!SHARED.equals(record.table())) continue;
            try (PreparedStatement statement = connection.prepareStatement("SELECT GENERATION FROM " + SHARED + " WHERE ID = ?")) {
                statement.setInt(1, record.id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !record.generation().equalsIgnoreCase(result.getString(1)))
                        throw new StaleIdentity(false);
                }
            }
        }
    }

    @Override public void close() { CURRENT.remove(); }

    public static final class StaleIdentity extends RuntimeException {
        private final boolean user;
        public StaleIdentity(boolean user) {
            super(user ? "Please sign in again." : "This record has changed identity. Refresh the page before trying again.");
            this.user = user;
        }
        public boolean isUser() { return user; }
    }
}
