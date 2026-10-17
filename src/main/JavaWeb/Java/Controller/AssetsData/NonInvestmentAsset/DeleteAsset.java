package Controller.AssetsData.NonInvestmentAsset;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/deleteNonInvestmentAsset")
public class DeleteAsset extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            return ApiSupport.result(Model.Controller.AssetsData.NonInvestmentAsset.DeleteAsset.deleteNonInvestmentAsset(
                    user.getId(), ApiSupport.positiveInt(input, "id", -1)));
        });
    }
}
