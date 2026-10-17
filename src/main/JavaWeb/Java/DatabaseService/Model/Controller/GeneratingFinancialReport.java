package DatabaseService.Model.Controller;

/** The old all-data session import is retired. Use the four atomic ReadData summaries. */
@Deprecated
public class GeneratingFinancialReport {
    public static boolean importData(Model.User user) {
        // Never compute a complete financial report from a truncated page of transactions.
        return false;
    }
}
