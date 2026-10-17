package Controller.AssetsData.InvestmentAsset;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/editInvestmentAsset")
public class EditAsset extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            return ApiSupport.result(Model.Controller.AssetsData.InvestmentAsset.EditAsset.editInvestmentAsset(
                    user.getId(), ApiSupport.positiveInt(input, "id", -1),
                    ApiSupport.text(input, "investmentPlatform"), ApiSupport.text(input, "investmentType"),
                    ApiSupport.text(input, "riskType"), ApiSupport.text(input, "investmentAmount"),
                    ApiSupport.text(input, "cashWithdrawal"), ApiSupport.text(input, "buyInAmount"),
                    ApiSupport.text(input, "amountSold"), ApiSupport.text(input, "managementFee"),
                    ApiSupport.text(input, "handlingFee"), ApiSupport.text(input, "realTimeAmount"),
                    ApiSupport.text(input, "initialEarnings"), ApiSupport.text(input, "currency")));
        });
    }
}
