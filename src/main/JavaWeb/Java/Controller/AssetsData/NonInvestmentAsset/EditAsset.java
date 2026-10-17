package Controller.AssetsData.NonInvestmentAsset;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/editNonInvestmentAsset")
public class EditAsset extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            return ApiSupport.result(Model.Controller.AssetsData.NonInvestmentAsset.EditAsset.editNonInvestmentAsset(
                    user.getId(), ApiSupport.positiveInt(input, "id", -1), ApiSupport.text(input, "assetName"),
                    ApiSupport.text(input, "assetType"), ApiSupport.text(input, "riskType"),
                    ApiSupport.text(input, "amount"), ApiSupport.text(input, "currency")));
        });
    }
}
