package DatabaseService.Model.Controller.AssetsData;

import DatabaseService.Model.Controller.ReadData;
import Model.UserAssets.InvestmentAsset;
import Model.UserAssets.NonInvestmentAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.SQLException;

/** Compatibility entry point; new controllers read one explicitly paginated kind. */
public class ViewAllCurrentAssets {
    public static boolean viewAllCurrentAssets(Model.User user) {
        try {
            JSONArray cash = ReadData.assetsPage(user.getId(), "nonInvestment", 1, 100, "", "").getJSONArray("items");
            NonInvestmentAsset[] cashAssets = new NonInvestmentAsset[cash.length()];
            for (int i = 0; i < cash.length(); i++) {
                JSONObject row = cash.getJSONObject(i);
                NonInvestmentAsset asset = new NonInvestmentAsset();
                asset.setId(row.getInt("id")); asset.setAssetName(row.getString("assetName")); asset.setAssetType(row.getString("assetType"));
                asset.setRiskType(row.getString("riskType")); asset.setCurrency(row.getString("currency")); asset.setAmount(row.getString("amount"));
                asset.setDate(java.sql.Date.valueOf(row.getString("date"))); asset.setDateOfMonth(row.getInt("dateOfMonth"));
                asset.setMonthOfYear(row.getInt("monthOfYear")); asset.setYear(row.getInt("year")); cashAssets[i] = asset;
            }
            JSONArray investment = ReadData.assetsPage(user.getId(), "investment", 1, 100, "", "").getJSONArray("items");
            InvestmentAsset[] investmentAssets = new InvestmentAsset[investment.length()];
            for (int i = 0; i < investment.length(); i++) {
                JSONObject row = investment.getJSONObject(i);
                InvestmentAsset asset = new InvestmentAsset();
                asset.setId(row.getInt("id")); asset.setInvestmentPlatform(row.getString("investmentPlatform"));
                asset.setInvestmentType(row.getString("investmentType")); asset.setRiskType(row.getString("riskType"));
                asset.setCurrency(row.getString("currency")); asset.setInvestmentAmount(row.getString("investmentAmount"));
                asset.setCashWithdrawal(row.getString("cashWithdrawal")); asset.setManagementFee(row.getString("managementFee"));
                asset.setHandlingFee(row.getString("handlingFee")); asset.setBuyInAmount(row.getString("buyInAmount"));
                asset.setAmountSold(row.getString("amountSold")); asset.setRealTimeAmount(row.getString("realTimeAmount"));
                asset.setInitialEarnings(row.getString("initialEarnings")); asset.setDate(java.sql.Date.valueOf(row.getString("date")));
                asset.setDateOfMonth(row.getInt("dateOfMonth")); asset.setMonthOfYear(row.getInt("monthOfYear")); asset.setYear(row.getInt("year"));
                investmentAssets[i] = asset;
            }
            user.setNonInvestmentAssets(cashAssets); user.setInvestmentAssets(investmentAssets); return true;
        } catch (SQLException exception) { return false; }
    }
}
