package Model.Controller;

import Model.TheFinancialSituationOfTheUser.*;
import Model.Tools.AnnualInflationRatesInVariousCurrencies;

public class GeneratingFinancialReport {

    public static boolean generateFinancialReport(Model.User user) {
        TotalCurrentNetAssets totalCurrentNetAssets = user.getTotalCurrentNetAssets();
        CurrencyDistributionOfCurrentNetAssets currencyDistributionOfCurrentNetAssets =
                user.getCurrencyDistributionOfCurrentNetAssets();
        RiskDistributionOfCurrentNetAssets riskDistributionOfCurrentNetAssets =
                user.getRiskDistributionOfCurrentNetAssets();
        CurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets
                currencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets =
                user.getCurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets();
        RangeOfRiskDistributionOfCurrentNetAssets rangeOfRiskDistributionOfCurrentNetAssets =
                user.getRangeOfRiskDistributionOfCurrentNetAssets();
        BasicVariablesAndData basicVariablesAndData = user.getBasicVariablesAndData();

        boolean isImportFinancialDataOfUserSuccessful =
                DatabaseService.Model.Controller.GeneratingFinancialReport.importData(user);
        if (!isImportFinancialDataOfUserSuccessful) return false;
        AnnualInflationRatesInVariousCurrencies.setLatestAnnualInflationRatesInVariousCurrencies();
        totalCurrentNetAssets.calculateTotalCurrentNetAssets(user);
        currencyDistributionOfCurrentNetAssets.calculateTheAmountOfCurrentNetAssetsHeldInVariousCurrencies(user);
        currencyDistributionOfCurrentNetAssets.
                calculateThePercentageOfTheAmountOfCurrentNetAssetsHeldInVariousCurrencies(totalCurrentNetAssets);
        riskDistributionOfCurrentNetAssets.calculateTheRiskDistributionOfTotalCurrentNetAssets(user);
        riskDistributionOfCurrentNetAssets.calculatePercentageOfTotalVeryLowAndLowRiskCurrentNetAssets(
                totalCurrentNetAssets);
        riskDistributionOfCurrentNetAssets.calculatePercentageOfTotalMediumLowAndMediumRiskCurrentNetAssets(
                totalCurrentNetAssets);
        riskDistributionOfCurrentNetAssets.calculatePercentageOfTotalMediumHighAndHighRiskCurrentNetAssets(
                totalCurrentNetAssets);
        currencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets.
                calculateTotalAmountOfVeryLowRiskCurrentNetAssetsHeldInVariousCurrencies(user);
        currencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets.
                calculatePercentageOfVeryLowAndLowRiskCurrentNetAssetsHeldInVariousCurrencies(
                        riskDistributionOfCurrentNetAssets);
        rangeOfRiskDistributionOfCurrentNetAssets.calculateOptimisedAnnualInflationRate(user);
        rangeOfRiskDistributionOfCurrentNetAssets.
                calculateRequiredDistributionOfLiquidAssetsAndAnnualisedRatesOfReturn(totalCurrentNetAssets);
        basicVariablesAndData.calculateIdealEarnings(user);
        basicVariablesAndData.calculateActualEarnings(user);
        basicVariablesAndData.calculateCycleEarnings();
        basicVariablesAndData.calculateDegreeOfCompletion();
        return isImportFinancialDataOfUserSuccessful;
    }

    public static void removePointers(Model.User user) {
        user.setNonInvestmentAssets(null);
        user.setInvestmentAssets(null);
        user.setBalanceOfIncomesAndExpenditures(null);
    }

}
