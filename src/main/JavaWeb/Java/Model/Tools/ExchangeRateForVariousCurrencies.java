package Model.Tools;

import Model.ExternalAPI.ExchangeRate;
import Model.FixedTypes.Currencies;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.TimerTask;

@WebListener
public class ExchangeRateForVariousCurrencies extends TimerTask implements ServletContextListener {
    public record RateSnapshot(double gbpToCny, String source, String updatedAt) {}
    private java.util.Timer timer;
    private static volatile RateSnapshot rates = initialRates();

    public static double getGbpToCny() { return rates.gbpToCny(); }
    public static String getRateSource() { return rates.source(); }
    public static String getLastUpdatedAt() { return rates.updatedAt(); }
    public static RateSnapshot getSnapshot() { return rates; }

    private static RateSnapshot initialRates() {
        String configured = System.getenv("IAFM_GBP_TO_CNY");
        if (configured != null && !configured.isBlank()) {
            try {
                double value = Double.parseDouble(configured.trim());
                if (!Double.isFinite(value) || value <= 0 || !Double.isFinite(1.0 / value)) {
                    throw new IllegalArgumentException("Invalid rate");
                }
                return new RateSnapshot(value, "manual", java.time.Instant.now().toString());
            } catch (IllegalArgumentException exception) {
                System.err.println("IAFM_GBP_TO_CNY must be a finite positive rate; rate remains unavailable");
            }
        }
        return new RateSnapshot(0.0, "unavailable", null);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        timer = new java.util.Timer("investment-exchange-rates", true);
        // An hourly due check uses the persisted 24-hour gate; it never fetches per visitor.
        timer.schedule(new ExchangeRateForVariousCurrencies(), 0, 3600000);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (timer != null) timer.cancel();
    }

    @Override
    public void run() { getExchangeRateForVariousCurrencies(); }

    private static void getExchangeRateForVariousCurrencies() {
        if ("manual".equals(rates.source())) return;
        ExchangeRate.getExchangeRate();
        ExchangeRate.UsdRates refreshed = ExchangeRate.getLatestRates();
        if (refreshed.gbp() > 0 && refreshed.cny() > 0) {
            double ratio = refreshed.cny() / refreshed.gbp();
            if (Double.isFinite(ratio) && ratio > 0 && Double.isFinite(1.0 / ratio)) {
                rates = new RateSnapshot(ratio, "exchangerate-api", refreshed.updatedAt());
            }
        }
    }

    public static double currencyConversion(String currencyCurrentChosen, double currencyAmount, String currencyType) {
        Currencies.valueOf(currencyCurrentChosen);
        Currencies.valueOf(currencyType);
        if (!Double.isFinite(currencyAmount)) throw new IllegalArgumentException("Amount must be finite");
        if (currencyType.equals(currencyCurrentChosen) || currencyAmount == 0) return currencyAmount;
        RateSnapshot snapshot = rates;
        boolean legacyPair = ("GBP".equals(currencyType) && "CNY".equals(currencyCurrentChosen))
                || ("CNY".equals(currencyType) && "GBP".equals(currencyCurrentChosen));
        double ratio;
        if (legacyPair && snapshot.gbpToCny() > 0) {
            ratio = "CNY".equals(currencyCurrentChosen) ? snapshot.gbpToCny() : 1.0 / snapshot.gbpToCny();
        } else {
            // Both sides come from the same immutable daily USD snapshot.
            java.util.Map<String, Double> all = ExchangeRate.getAllRates();
            double from = all.getOrDefault(currencyType, 0.0), to = all.getOrDefault(currencyCurrentChosen, 0.0);
            if (from <= 0 || to <= 0) throw new Computing.ExchangeRateUnavailableException();
            ratio = to / from;
        }
        if (!Double.isFinite(ratio) || ratio <= 0) throw new Computing.ExchangeRateUnavailableException();
        double converted = currencyAmount * ratio;
        if (!Double.isFinite(converted)) throw new ArithmeticException("Converted amount exceeds the supported range");
        return converted;
    }
}
