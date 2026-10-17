package Model;

import Model.TheFinancialSituationOfTheUser.*;
import Model.UserAssets.*;

public class User {

    private int id = 0;
    private java.lang.String forename = null, surname = null, emailAddress = null;
    private volatile java.lang.String currencyCurrentChosen = "GBP";
    public int getId() {
        return id;
    }
    public java.lang.String getForename() {
        return forename;
    }
    public java.lang.String getSurname() {
        return surname;
    }
    public java.lang.String getEmailAddress() {
        return emailAddress;
    }
    public java.lang.String getCurrencyCurrentChosen() {
        return currencyCurrentChosen;
    }
    public void setId(int id) {
        this.id = id;
    }
    public void setForename(java.lang.String forename) {
        this.forename = forename;
    }
    public void setSurname(java.lang.String surname) {
        this.surname = surname;
    }
    public void setEmailAddress(java.lang.String emailAddress) {
        this.emailAddress = emailAddress;
    }
    public void setCurrencyCurrentChosen(java.lang.String currencyCurrentChosen) {
        this.currencyCurrentChosen = currencyCurrentChosen;
    }

    private java.lang.String[] currencies = null, platforms = null;
    public java.lang.String[] getCurrencies() {
        return currencies;
    }
    public java.lang.String[] getPlatforms() {
        return platforms;
    }
    public void setCurrencies(java.lang.String[] currencies) {
        this.currencies = currencies;
    }
    public void setPlatforms(java.lang.String[] platforms) {
        this.platforms = platforms;
    }

    private final TotalCurrentNetAssets totalCurrentNetAssets = new TotalCurrentNetAssets();
    private final CurrencyDistributionOfCurrentNetAssets currencyDistributionOfCurrentNetAssets =
            new CurrencyDistributionOfCurrentNetAssets();
    private final RiskDistributionOfCurrentNetAssets riskDistributionOfCurrentNetAssets =
            new RiskDistributionOfCurrentNetAssets();
    private final CurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets
            currencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets =
            new CurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets();
    private final RangeOfRiskDistributionOfCurrentNetAssets rangeOfRiskDistributionOfCurrentNetAssets =
            new RangeOfRiskDistributionOfCurrentNetAssets();
    private final BasicVariablesAndData basicVariablesAndData = new BasicVariablesAndData();
    public TotalCurrentNetAssets getTotalCurrentNetAssets() {
        return totalCurrentNetAssets;
    }
    public CurrencyDistributionOfCurrentNetAssets getCurrencyDistributionOfCurrentNetAssets() {
        return currencyDistributionOfCurrentNetAssets;
    }
    public RiskDistributionOfCurrentNetAssets getRiskDistributionOfCurrentNetAssets() {
        return riskDistributionOfCurrentNetAssets;
    }
    public CurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets
    getCurrencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets() {
        return currencyDistributionOfVeryLowAndLowRiskInCurrentNetAssets;
    }
    public RangeOfRiskDistributionOfCurrentNetAssets getRangeOfRiskDistributionOfCurrentNetAssets() {
        return rangeOfRiskDistributionOfCurrentNetAssets;
    }
    public BasicVariablesAndData getBasicVariablesAndData() {
        return basicVariablesAndData;
    }

    private InvestmentAsset[] investmentAssets = null;
    private NonInvestmentAsset[] nonInvestmentAssets = null;
    private BalanceOfIncomeAndExpenditure[] balanceOfIncomesAndExpenditures = null;
    public InvestmentAsset[] getInvestmentAssets() {
        return investmentAssets;
    }
    public NonInvestmentAsset[] getNonInvestmentAssets() {
        return nonInvestmentAssets;
    }
    public BalanceOfIncomeAndExpenditure[] getBalanceOfIncomesAndExpenditures() {
        return balanceOfIncomesAndExpenditures;
    }
    public void setInvestmentAssets(InvestmentAsset[] investmentAssets) {
        this.investmentAssets = investmentAssets;
    }
    public void setNonInvestmentAssets(NonInvestmentAsset[] nonInvestmentAssets) {
        this.nonInvestmentAssets = nonInvestmentAssets;
    }
    public void setBalanceOfIncomesAndExpenditures(BalanceOfIncomeAndExpenditure[] balanceOfIncomesAndExpenditures) {
        this.balanceOfIncomesAndExpenditures = balanceOfIncomesAndExpenditures;
    }

    public static boolean getCurrenciesChosen(Model.User user) {
        return DatabaseService.Model.User.importData_currenciesChosen(user);
    }
    public static boolean getAllPlatforms(Model.User user) {
        return DatabaseService.Model.User.importData_allPlatforms(user);
    }

}
