package Controller.InflationRatesData;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/deleteInflationRate")
public class DeleteInflationRate extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            ApiSupport.requireAdmin(user.getId());
            return ApiSupport.result(Model.Controller.InflationRatesData.DeleteInflationRate.deleteInflationRate(ApiSupport.positiveInt(input, "id", -1)));
        });
    }
}
