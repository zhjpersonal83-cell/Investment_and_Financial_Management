package Controller.IncomeAndExpenditureData;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/addIncomeOrExpense")
public class AddIncomeOrExpense extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            return ApiSupport.result(Model.Controller.IncomeAndExpenditureData.AddIncomeOrExpense.addIncomeOrExpense(
                    user.getId(), ApiSupport.text(input, "type"), ApiSupport.text(input, "platform"),
                    ApiSupport.text(input, "name"), ApiSupport.text(input, "amount"), ApiSupport.text(input, "date")));
        });
    }
}
