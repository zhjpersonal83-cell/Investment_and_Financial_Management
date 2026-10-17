package DatabaseService;

import java.sql.PreparedStatement;
import java.sql.SQLException;

// 数据每月备份一次（OneDrive）（期望）
class CreateAllTables extends DatabaseInit {

    void createAllTables() {
        java.lang.String createUSERS = "CREATE TABLE IF NOT EXISTS USERS (" +
                "ID INT PRIMARY KEY, " +
                "FORENAME VARCHAR(50) NOT NULL, " +
                "SURNAME VARCHAR(50) NOT NULL, " +
                "EMAIL_ADDRESS VARCHAR(50) NOT NULL, " +
                "PASSWORD VARCHAR(50) NOT NULL) " +
                "ENGINE=INNODB;",
                createID_RECORDER_users = "CREATE TABLE IF NOT EXISTS ID_RECORDER_USERS (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;",
                // 该表格需要手动输入数据
                // 每月一次（期望）
                // 同一种货币若出现多个（两个或以上）相邻月份数据相等则只保留时间最早的一个
                // 数据的日期为十五号
                createANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES = "CREATE TABLE IF NOT EXISTS ANNUAL_INFLATION_RA" +
                        "TES_IN_VARIOUS_CURRENCIES (" +
                        "ID INT PRIMARY KEY, " +
                        "AIR_IN_VARIOUS_CURRENCIES DECIMAL(5,2) NOT NULL, " +
                        "VARIOUS_CURRENCIES VARCHAR(50) NOT NULL, " +
                        "DATE DATE NOT NULL) " +
                        "ENGINE=INNODB;",
                createID_RECORDER_annualInflationRatesInVariousCurrencies = "CREATE TABLE IF NOT EXISTS ID_RECORDER_" +
                        "ANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;",
                createALL_NON_INVESTMENT_ASSETS = "CREATE TABLE IF NOT EXISTS ALL_NON_INVESTMENT_ASSETS (" +
                        "ID INT PRIMARY KEY, " +
                        "USER_ID INT NOT NULL, " +
                        "NAME VARCHAR(50) NOT NULL, " +
                        "AMOUNT VARCHAR(15) NOT NULL, " +
                        "TYPE VARCHAR(50) NOT NULL, " +
                        "RISK_TYPE VARCHAR(50) NOT NULL, " +
                        "CURRENCY VARCHAR(50) NOT NULL, " +
                        "DATE DATE NOT NULL, " +
                        "FOREIGN KEY(USER_ID) REFERENCES USERS(ID)) " +
                        "ENGINE=INNODB;",
                createID_RECORDER_allNonInvestmentAssets = "CREATE TABLE IF NOT EXISTS ID_RECORDER_ALL_NON_INVESTMEN" +
                        "T_ASSETS (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;",
                createALL_INVESTMENT_ASSETS = "CREATE TABLE IF NOT EXISTS ALL_INVESTMENT_ASSETS (" +
                        "ID INT PRIMARY KEY, " +
                        "USER_ID INT NOT NULL, " +
                        "INVESTMENT_AMOUNT VARCHAR(15) NOT NULL, " +
                        "CASH_WITHDRAWAL VARCHAR(15) NOT NULL, " +
                        "MANAGEMENT_FEE VARCHAR(15) NOT NULL, " +
                        "HANDLING_FEE VARCHAR(15) NOT NULL, " +
                        "BUY_IN_AMOUNT VARCHAR(15) NOT NULL, " +
                        "AMOUNT_SOLD VARCHAR(15) NOT NULL, " +
                        "REAL_TIME_AMOUNT VARCHAR(15) NOT NULL, " +
                        "INITIAL_EARNINGS DECIMAL(12,2) NOT NULL, " +
                        "INVESTMENT_PLATFORM VARCHAR(50) NOT NULL, " +
                        "RISK_TYPE VARCHAR(50) NOT NULL, " +
                        "INVESTMENT_TYPE VARCHAR(50) NOT NULL, " +
                        "CURRENCY VARCHAR(50) NOT NULL, " +
                        "DATE DATE NOT NULL, " +
                        "FOREIGN KEY(USER_ID) REFERENCES USERS(ID)) " +
                        "ENGINE=INNODB;",
                createID_RECORDER_allInvestmentAssets = "CREATE TABLE IF NOT EXISTS ID_RECORDER_ALL_INVESTMENT_ASSET" +
                        "S (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;",
                createBASIC_VARIABLES_AND_DATA = "CREATE TABLE IF NOT EXISTS BASIC_VARIABLES_AND_DATA (" +
                        "ID INT PRIMARY KEY, " +
                        "USER_ID INT NOT NULL, " +
                        "ACCUMULATED_AMOUNT DECIMAL(12,2) NOT NULL DEFAULT 0.0, " +
                        "ACTUAL_EARNINGS_BEFORE DECIMAL(12,2) NOT NULL DEFAULT 0.0, " +
                        "OTHER_ACCUMULATED_PASSIVE_INCOME DECIMAL(12,2) NOT NULL DEFAULT 0.0, " +
                        "FOREIGN KEY(USER_ID) REFERENCES USERS(ID)) " +
                        "ENGINE=INNODB;",
                createID_RECORDER_basicVariablesAndData = "CREATE TABLE IF NOT EXISTS ID_RECORDER_BASIC_VARIABLES_AN" +
                        "D_DATA (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;",
                createINCOME_AND_EXPENDITURE_DATA = "CREATE TABLE IF NOT EXISTS INCOME_AND_EXPENDITURE_DATA (" +
                        "ID INT PRIMARY KEY, " +
                        "USER_ID INT NOT NULL, " +
                        "TYPE VARCHAR(50) NOT NULL, " +
                        "PLATFORM VARCHAR(50) NOT NULL, " +
                        "NAME VARCHAR(150) NOT NULL, " +
                        "AMOUNT VARCHAR(15) NOT NULL, " +
                        "DATE DATE NOT NULL, " +
                        "CURRENCY VARCHAR(50) NOT NULL, " +
                        "FOREIGN KEY(USER_ID) REFERENCES USERS(ID)) " +
                        "ENGINE=INNODB;",
                createID_RECORDER_incomeAndExpenditureData = "CREATE TABLE IF NOT EXISTS ID_RECORDER_INCOME_AND_EXPE" +
                        "NDITURE_DATA (" +
                        "TABLE_ID INT PRIMARY KEY NOT NULL) " +
                        "ENGINE=INNODB;";

        String[] commands = {createUSERS, createID_RECORDER_users,
                createANNUAL_INFLATION_RATES_IN_VARIOUS_CURRENCIES, createID_RECORDER_annualInflationRatesInVariousCurrencies,
                createALL_NON_INVESTMENT_ASSETS, createID_RECORDER_allNonInvestmentAssets,
                createALL_INVESTMENT_ASSETS, createID_RECORDER_allInvestmentAssets,
                createBASIC_VARIABLES_AND_DATA, createID_RECORDER_basicVariablesAndData,
                createINCOME_AND_EXPENDITURE_DATA, createID_RECORDER_incomeAndExpenditureData};
        try (java.sql.Connection connection = openConnection()) {
            for (String command : commands) {
                try (PreparedStatement statement = connection.prepareStatement(command)) { statement.executeUpdate(); }
            }
            DatabaseService.Model.Controller.IncomeAndExpenditureData.Ledger.ensureSchema(connection);
            DatabaseService.Model.Controller.InflationRatesData.InflationSeries.ensureSchema(connection);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Database initialization failed.", exception);
        }
    }
}
