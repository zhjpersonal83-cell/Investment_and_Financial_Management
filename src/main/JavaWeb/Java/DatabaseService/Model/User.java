package DatabaseService.Model;

import DatabaseService.DatabaseInit;
import DatabaseService.Model.Controller.ReadData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public class User extends DatabaseInit {
    public static boolean importData_currenciesChosen(Model.User user) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT CURRENCY FROM ALL_NON_INVESTMENT_ASSETS WHERE USER_ID = ? UNION "
                        + "SELECT DISTINCT CURRENCY FROM ALL_INVESTMENT_ASSETS WHERE USER_ID = ? ORDER BY CURRENCY")) {
            statement.setInt(1, user.getId()); statement.setInt(2, user.getId());
            List<String> currencies = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) { while (result.next()) currencies.add(result.getString(1)); }
            user.setCurrencies(currencies.toArray(new String[0])); return true;
        } catch (SQLException exception) { return false; }
    }

    /** Compatibility only: applications must request subsequent platform pages explicitly. */
    public static boolean importData_allPlatforms(Model.User user) {
        try {
            JSONArray items = ReadData.platformsPage(user.getId(), 1, 100, "").getJSONArray("items");
            String[] platforms = new String[items.length()];
            for (int i = 0; i < items.length(); i++) platforms[i] = items.getJSONObject(i).getString("name");
            user.setPlatforms(platforms); return true;
        } catch (SQLException exception) { return false; }
    }
}
