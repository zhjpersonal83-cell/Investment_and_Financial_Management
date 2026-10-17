package Model.TheFinancialSituationOfTheUser;

import Model.FixedTypes.Currencies;
import Model.Tools.Computing;
import Model.UserAssets.InvestmentAsset;
import Model.UserAssets.NonInvestmentAsset;

/**
 * 职责:
 * 存储流动净资产的不同货币分布
 * 计算流动净资产的不同货币分布
 * (以某种货币)
 */
public class CurrencyDistributionOfCurrentNetAssets {

    private String amountOfCurrentNetAssetsHeldInGBP = "0.00", amountOfCurrentNetAssetsHeldInCNY = "0.00";
    public String getAmountOfCurrentNetAssetsHeldInGBP() {
        return amountOfCurrentNetAssetsHeldInGBP;
    }
    public String getAmountOfCurrentNetAssetsHeldInCNY() {
        return amountOfCurrentNetAssetsHeldInCNY;
    }

    private double percentageOfAmountOfCurrentNetAssetsHeldInGBP = 0.0,
            percentageOfAmountOfCurrentNetAssetsHeldInCNY = 0.0;
    public double getPercentageOfAmountOfCurrentNetAssetsHeldInGBP() {
        return percentageOfAmountOfCurrentNetAssetsHeldInGBP;
    }
    public double getPercentageOfAmountOfCurrentNetAssetsHeldInCNY() {
        return percentageOfAmountOfCurrentNetAssetsHeldInCNY;
    }

    public void calculateTheAmountOfCurrentNetAssetsHeldInVariousCurrencies(Model.User user) {
        long gbp = Math.addExact(TotalCurrentNetAssets.calculateTotalAmountForCurrency(user.getNonInvestmentAssets(), "GBP"),
                TotalCurrentNetAssets.calculateTotalAmountForCurrency(user.getInvestmentAssets(), "GBP"));
        long cny = Math.addExact(TotalCurrentNetAssets.calculateTotalAmountForCurrency(user.getNonInvestmentAssets(), "CNY"),
                TotalCurrentNetAssets.calculateTotalAmountForCurrency(user.getInvestmentAssets(), "CNY"));
        amountOfCurrentNetAssetsHeldInGBP = Computing.fromMinorUnits(
                Computing.convertMinorUnits(gbp, "GBP", user.getCurrencyCurrentChosen()));
        amountOfCurrentNetAssetsHeldInCNY = Computing.fromMinorUnits(
                Computing.convertMinorUnits(cny, "CNY", user.getCurrencyCurrentChosen()));
    }

    public void calculateThePercentageOfTheAmountOfCurrentNetAssetsHeldInVariousCurrencies(
            TotalCurrentNetAssets totalCurrentNetAssets) {
        percentageOfAmountOfCurrentNetAssetsHeldInGBP =
                Computing.percentage(amountOfCurrentNetAssetsHeldInGBP, totalCurrentNetAssets.getTotalCurrentNetAssets());
        percentageOfAmountOfCurrentNetAssetsHeldInCNY =
                Computing.percentage(amountOfCurrentNetAssetsHeldInCNY, totalCurrentNetAssets.getTotalCurrentNetAssets());
    }

}
