package Model.Controller.AssetsData;

public class ViewAllCurrentAssets {

    public static boolean viewAllCurrentAssets(Model.User user) {
        return DatabaseService.Model.Controller.AssetsData.ViewAllCurrentAssets.viewAllCurrentAssets(user);
    }

    public static void removePointers(Model.User user) {
        user.setNonInvestmentAssets(null);
        user.setInvestmentAssets(null);
    }

}
