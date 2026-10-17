package Controller.InflationRatesData;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet({"/getInflationRatesData", "/api/v1/inflation-rates"})
public class GetInflationRatesData extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, false, (user, input) -> Model.Controller.ReadData.inflationPage(user, input).put("canEdit", ApiSupport.isAdmin(user.getId())));
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doGet(request, response);
    }
}
