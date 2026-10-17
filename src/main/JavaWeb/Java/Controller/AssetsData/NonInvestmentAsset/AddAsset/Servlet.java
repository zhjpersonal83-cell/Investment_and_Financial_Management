package Controller.AssetsData.NonInvestmentAsset.AddAsset;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/addNonInvestmentAsset")
public class Servlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            Command command = new Command();
            command.setAssetName(ApiSupport.text(input, "assetName"));
            command.setAssetType(ApiSupport.text(input, "assetType"));
            command.setRiskType(ApiSupport.text(input, "riskType"));
            command.setAmount(ApiSupport.text(input, "amount"));
            command.setCurrency(ApiSupport.text(input, "currency"));
            return ApiSupport.result(command.runCommand(user.getId(), command));
        });
    }
}
