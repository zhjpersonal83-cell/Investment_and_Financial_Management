package Model.InvestmentDecision;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class StockDataAPI {

    private static final String API_TOKEN = System.getenv("IAFM_TUSHARE_API_TOKEN"),
            BASE_URL = "https://api.tushare.pro/";

    public static void fetchStockData(String stockCode) {
        if (API_TOKEN == null || API_TOKEN.isBlank()) {
            System.err.println("IAFM_TUSHARE_API_TOKEN is not configured");
            return;
        }
        HttpURLConnection httpURLConnection = null;
        try {
            URI uri = new URI(BASE_URL);
            URL url = uri.toURL();
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Content-Type", "application/json");

            JSONObject jsonObject1 = new JSONObject();
            jsonObject1.put("api_name", "daily");
            jsonObject1.put("token", API_TOKEN);

            JSONObject jsonObject2 = new JSONObject();
            jsonObject2.put("ts_code", stockCode);
            jsonObject2.put("start_date", "20240701");
            jsonObject2.put("end_date", "20240715");
            jsonObject1.put("params", jsonObject2);
            jsonObject1.put("fields", "trade_date, open, high, low, close, change");

            httpURLConnection.setDoOutput(true);
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(jsonObject1.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            String inputLine;
            StringBuilder stringBuilder = new StringBuilder();
            while ((inputLine = bufferedReader.readLine()) != null) {
                stringBuilder.append(inputLine);
            }

            bufferedReader.close();
            httpURLConnection.disconnect();

            JSONObject jsonObject3 = new JSONObject(stringBuilder.toString());
            JSONObject jsonObject4 = jsonObject3.getJSONObject("data");
            JSONArray jsonArray1 = jsonObject4.getJSONArray("items");
            System.out.println("trade_date, open_price, highest_price, lowest_price, close_price, change");
            int numberOfTradeDays = jsonArray1.length() - 1;
            for (int i = numberOfTradeDays; i >= 0; i--) {
                JSONArray jsonArray2 = jsonArray1.getJSONArray(i);
                System.out.println(jsonArray2);
            }
        } catch (IOException | URISyntaxException | RuntimeException exception) {
            System.err.println("Stock-data request failed; the application remains available");
        } finally {
            if (httpURLConnection != null) httpURLConnection.disconnect();
        }
    }

    public static void main(String[] args) {
        fetchStockData("002115.SZ");
    }

}
