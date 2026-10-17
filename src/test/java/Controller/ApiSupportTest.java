package Controller;

import Controller.RegisterAndLogin.GetExchangeRate;
import Model.Tools.ExchangeRateForVariousCurrencies;
import Model.User;
import org.json.JSONObject;
import org.junit.Test;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ApiSupportTest {
    private static final String TOKEN_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOKEN_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static String config(int userId, String token) throws Exception {
        return userId + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void eachBearerKeyIdentifiesOnlyItsConfiguredUser() throws Exception {
        String config = config(7, TOKEN_A) + ";" + config(9, TOKEN_B);
        assertEquals(7, ApiSupport.apiUserId("Bearer " + TOKEN_A, config));
        assertEquals(9, ApiSupport.apiUserId("Bearer " + TOKEN_B, config));
        assertFailure(401, () -> ApiSupport.apiUserId("Bearer ccccccccccccccccccccccccccccccccccccccccccc", config));
        assertFailure(401, () -> ApiSupport.apiUserId(null, config));
        assertFailure(401, () -> ApiSupport.apiUserId("Bearer short", config));
    }

    @Test public void missingOrAmbiguousKeyConfigurationFailsClosed() throws Exception {
        assertFailure(503, () -> ApiSupport.apiUserId("Bearer " + TOKEN_A, null));
        String duplicate = config(7, TOKEN_A) + ";" + config(9, TOKEN_A);
        assertFailure(503, () -> ApiSupport.apiUserId("Bearer " + TOKEN_A, duplicate));
        assertFailure(503, () -> ApiSupport.apiUserId("Bearer " + TOKEN_A, "0:bad"));
    }

    @Test public void unauthenticatedRequestsNeverExecuteDataAccess() {
        Fixture f = new Fixture(null);
        AtomicBoolean invoked = new AtomicBoolean();
        ApiSupport.serve(f.request(), f.response(), false, (user, input) -> {
            invoked.set(true); return new JSONObject();
        });
        assertFalse(invoked.get());
        assertEquals(401, f.status);
        assertEquals("no-store", f.responseHeaders.get("Cache-Control"));
        assertEquals("failure", f.json().getString("status"));
    }

    @Test public void loginPageCanReadOnlyPublicReferenceRatesWithoutASession() throws Exception {
        withRate(9.0, () -> {
            for (String method : new String[]{"GET", "POST"}) {
                Fixture f = new Fixture(null); f.servletPath = "/getExchangeRate"; f.method = method;
                new GetExchangeRate().service(f.request(), f.response());
                assertEquals(200, f.status);
                assertEquals("success", f.json().getString("status"));
                assertEquals(9.0, f.json().getDouble("rate"), 0.0);
                assertFalse(f.json().has("userId"));
                assertEquals("no-store", f.responseHeaders.get("Cache-Control"));
            }
        });
    }

    @Test public void publicUnavailableRateReturns503WithoutRequiringLogin() throws Exception {
        withRate(0.0, () -> {
            Fixture f = new Fixture(null); f.servletPath = "/getExchangeRate";
            new GetExchangeRate().service(f.request(), f.response());
            assertEquals(503, f.status);
            assertEquals("failure", f.json().getString("status"));
            assertEquals("Exchange rate is unavailable.", f.json().getString("message"));
        });
    }

    @Test public void apiReferenceRateStillRequiresBearerEvenWithAUserSession() throws Exception {
        withRate(9.0, () -> {
            Fixture f = authenticated(); f.servletPath = "/api/v1/exchange-rate";
            new GetExchangeRate().service(f.request(), f.response());
            String keys = System.getenv("IAFM_API_KEYS");
            assertEquals(keys == null || keys.isBlank() ? 503 : 401, f.status);
            assertEquals("failure", f.json().getString("status"));
            assertFalse(f.json().has("rate"));
        });
    }

    @Test public void readRequestsUseTheSessionOwnerAndAnIsolatedSnapshot() {
        User owner = new User(); owner.setId(7); owner.setCurrencyCurrentChosen("GBP");
        Fixture f = new Fixture(owner);
        f.body = "{\"userId\":9,\"currency\":\"CNY\"}";
        ApiSupport.serve(f.request(), f.response(), false, (user, input) -> {
            assertEquals(7, user.getId());
            assertEquals(9, input.getInt("userId"));
            user.setId(99); user.setCurrencyCurrentChosen("CNY");
            return new JSONObject().put("value", "0.30");
        });
        assertEquals(200, f.status);
        assertEquals(7, owner.getId());
        assertEquals("GBP", owner.getCurrencyCurrentChosen());
        assertEquals("0.30", f.json().getString("value"));
    }

    @Test public void crossSiteMutationsAreRejectedBeforeWrites() {
        Fixture f = authenticated();
        f.requestHeaders.put("Origin", "https://another.example");
        AtomicBoolean invoked = new AtomicBoolean();
        ApiSupport.serve(f.request(), f.response(), true, (user, input) -> {
            invoked.set(true); return ApiSupport.result(true);
        });
        assertEquals(403, f.status); assertFalse(invoked.get());
    }

    @Test public void sameOriginMutationReturnsExplicitSuccess() {
        Fixture f = authenticated();
        f.requestHeaders.put("Origin", "https://finance.example");
        ApiSupport.serve(f.request(), f.response(), true, (user, input) -> ApiSupport.result(true));
        assertEquals(200, f.status);
        assertEquals("success", f.json().getString("status"));
    }

    @Test public void missingAndMalformedOriginsCannotMutate() {
        for (String value : new String[]{"", "null", "https://finance.example/path", "https://user@finance.example", "https://finance.example#x"}) {
            Fixture f = authenticated(); f.requestHeaders.put("Origin", value);
            assertFailure(403, () -> ApiSupport.checkOrigin(f.request()));
        }
        Fixture f = authenticated(); f.requestHeaders.remove("Origin");
        assertFailure(403, () -> ApiSupport.checkOrigin(f.request()));
    }

    @Test public void adminRequiresAnExplicitPositiveConfiguredIdentity() {
        assertTrue(ApiSupport.configuredAdmin(2, "2,7"));
        assertFalse(ApiSupport.configuredAdmin(3, "2,7"));
        assertFalse(ApiSupport.configuredAdmin(0, "0,2"));
        assertFalse(ApiSupport.configuredAdmin(2, null));
        assertFalse(ApiSupport.configuredAdmin(2, "12"));
    }

    @Test public void loopbackHttpsProxyAcceptsMatchingPublicOrigin() {
        Fixture f = authenticated();
        f.requestBase = "http://finance.example:8080"; f.remoteAddr = "127.0.0.1";
        f.requestHeaders.put("Origin", "https://finance.example");
        f.requestHeaders.put("Host", "finance.example");
        f.requestHeaders.put("X-Forwarded-Proto", "https");
        ApiSupport.checkOrigin(f.request());
        f.requestHeaders.put("Origin", "https://another.example");
        assertFailure(403, () -> ApiSupport.checkOrigin(f.request()));
    }

    @Test public void directClientsCannotSpoofProxySchemeOrCrossSiteMetadata() {
        Fixture f = authenticated(); f.requestBase = "http://finance.example";
        f.requestHeaders.put("Origin", "https://finance.example");
        f.requestHeaders.put("Host", "finance.example");
        f.requestHeaders.put("X-Forwarded-Proto", "https");
        assertFailure(403, () -> ApiSupport.checkOrigin(f.request()));
        f.remoteAddr = "127.0.0.1";
        f.requestHeaders.put("Sec-Fetch-Site", "cross-site");
        assertFailure(403, () -> ApiSupport.checkOrigin(f.request()));
    }

    @Test public void failedWritesCannotAppearAsAnEmptySuccessfulResponse() {
        Fixture f = authenticated();
        ApiSupport.serve(f.request(), f.response(), true, (user, input) -> ApiSupport.result(false));
        assertEquals(409, f.status);
        assertEquals("failure", f.json().getString("status"));
    }

    @Test public void numericMoneyAndOversizedJsonAreRejected() {
        Fixture f = authenticated(); f.body = "{\"amount\":0.1}";
        ApiSupport.serve(f.request(), f.response(), true, (user, input) ->
                new JSONObject().put("amount", ApiSupport.text(input, "amount")));
        assertEquals(400, f.status);
        Fixture large = authenticated(); large.body = " ".repeat(65537);
        ApiSupport.serve(large.request(), large.response(), false, (user, input) -> new JSONObject());
        assertEquals(413, large.status);
    }

    @Test public void integerPaginationDoesNotSilentlyTruncateOrOverflow() {
        assertEquals(100, ApiSupport.positiveInt(new JSONObject(), "pageSize", 100));
        for (String value : new String[]{"0", "-1", "1.5", "2147483648", "1e2"}) {
            try { ApiSupport.positiveInt(new JSONObject().put("page", value), "page", 1); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
    }

    private static Fixture authenticated() {
        User user = new User(); user.setId(7); return new Fixture(user);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static void withRate(double rate, CheckedAction assertions) throws Exception {
        java.lang.reflect.Field field = ExchangeRateForVariousCurrencies.class.getDeclaredField("rates");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, new ExchangeRateForVariousCurrencies.RateSnapshot(rate, "manual", "2026-10-16T00:00:00Z"));
            assertions.run();
        } finally { field.set(null, previous); }
    }
    private static void assertFailure(int status, Runnable task) {
        try { task.run(); fail("Expected failure"); }
        catch (ApiSupport.Failure expected) { assertEquals(status, expected.status); }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
    private static class Fixture {
        final User user;
        final Map<String,String> requestHeaders = new HashMap<>(), responseHeaders = new HashMap<>();
        final StringWriter output = new StringWriter();
        String body = "{}", servletPath = "/getAssetSummary", method = "GET"; int status = 200;
        String requestBase = "https://finance.example", remoteAddr = "203.0.113.2";
        Fixture(User user) { this.user = user; requestHeaders.put("Origin", "https://finance.example"); }
        JSONObject json() { return new JSONObject(output.toString()); }
        HttpServletRequest request() {
            HttpSession session = user == null ? null : proxy(HttpSession.class, (p, m, a) ->
                    m.getName().equals("getAttribute") ? user : defaultValue(m.getReturnType()));
            ServletContext context = proxy(ServletContext.class, (p,m,a) -> defaultValue(m.getReturnType()));
            return proxy(HttpServletRequest.class, (p,m,a) -> switch(m.getName()) {
                case "getSession" -> session;
                case "getServletPath" -> servletPath;
                case "getMethod" -> method;
                case "getContentType" -> "application/json";
                case "getContentLengthLong" -> (long)body.length();
                case "getReader" -> new BufferedReader(new StringReader(body));
                case "getParameterMap" -> Map.of();
                case "getHeader" -> requestHeaders.get(a[0]);
                case "getRequestURL" -> new StringBuffer(requestBase + servletPath);
                case "getRemoteAddr" -> remoteAddr;
                case "getServletContext" -> context;
                default -> defaultValue(m.getReturnType());
            });
        }
        HttpServletResponse response() {
            return proxy(HttpServletResponse.class, (p,m,a) -> {
                if (m.getName().equals("getWriter")) return new PrintWriter(output);
                if (m.getName().equals("setStatus")) status = (int)a[0];
                if (m.getName().equals("setHeader")) responseHeaders.put((String)a[0], (String)a[1]);
                return defaultValue(m.getReturnType());
            });
        }
    }
}
