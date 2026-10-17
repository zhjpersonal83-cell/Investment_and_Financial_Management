package Model.Controller.IncomeAndExpenditureData;

public class GetIncomeAndExpenditureData {

    public static boolean getIncomeAndExpenditureData(Model.User user, String[] filterTypes, String[] filterPlatforms, String[] filterMonths, String[] filterDatesOfMonth, String[] filterYears) {
        return DatabaseService.Model.Controller.IncomeAndExpenditureData.GetIncomeAndExpenditureData.importData(
                user, filterTypes, filterPlatforms, filterMonths, filterDatesOfMonth, filterYears);
    }

    public static void removePointers(Model.User user) {
        user.setBalanceOfIncomesAndExpenditures(null);
    }

}
