package Controller;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet({"/getCashflowSummary", "/api/v1/summaries/cashflow"})
public class GetCashflowSummary extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, false, Model.Controller.ReadData::cashflowSummary);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doGet(request, response);
    }
}
