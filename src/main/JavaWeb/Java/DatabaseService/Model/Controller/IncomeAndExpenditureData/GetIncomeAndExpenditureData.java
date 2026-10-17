package DatabaseService.Model.Controller.IncomeAndExpenditureData;

import DatabaseService.Model.Controller.ReadData;
import Model.UserAssets.BalanceOfIncomeAndExpenditure;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.SQLException;

public class GetIncomeAndExpenditureData {
    public static boolean importData(Model.User user, String[] types, String[] platforms, String[] months, String[] days, String[] years) {
        JSONObject filters = new JSONObject().put("filterType", types == null ? new String[0] : types)
                .put("filterPlatform", platforms == null ? new String[0] : platforms).put("filterMonth", months == null ? new String[0] : months)
                .put("filterDateOfMonth", days == null ? new String[0] : days).put("filterYear", years == null ? new String[0] : years)
                .put("page", 1).put("pageSize", 100);
        try {
            JSONArray items = ReadData.transactionsPage(user.getId(), filters).getJSONArray("items");
            BalanceOfIncomeAndExpenditure[] records = new BalanceOfIncomeAndExpenditure[items.length()];
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.getJSONObject(i);
                BalanceOfIncomeAndExpenditure record = new BalanceOfIncomeAndExpenditure();
                record.setID(row.getInt("id")); record.setType(row.getString("type")); record.setPlatform(row.getString("platform"));
                record.setName(row.getString("name")); record.setAmount(row.getString("amount")); record.setCurrency(row.getString("currency"));
                record.setDate(java.sql.Date.valueOf(row.getString("date"))); record.setYear(row.getInt("year"));
                record.setMonthOfYear(row.getInt("monthOfYear")); record.setDateOfMonth(row.getInt("dateOfMonth")); records[i] = record;
            }
            user.setBalanceOfIncomesAndExpenditures(records); return true;
        } catch (SQLException exception) { return false; }
    }
}
