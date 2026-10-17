package Controller.AssetsData.InvestmentAsset;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/deleteInvestmentAsset")
public class DeleteAsset extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            return ApiSupport.result(Model.Controller.AssetsData.InvestmentAsset.DeleteAsset.deleteInvestmentAsset(
                    user.getId(), ApiSupport.positiveInt(input, "id", -1)));
        });
    }
}
