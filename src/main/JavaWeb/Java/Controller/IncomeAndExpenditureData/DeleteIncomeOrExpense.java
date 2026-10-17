package Controller.IncomeAndExpenditureData;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/deleteIncomeOrExpense")
public class DeleteIncomeOrExpense extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            // Amount, platform and type must be read from the user's stored record, never trusted from the client.
            return ApiSupport.result(Model.Controller.IncomeAndExpenditureData.DeleteIncomeOrExpense.deleteIncomeOrExpense(
                    user.getId(), ApiSupport.positiveInt(input, "id", -1), null, null, null));
        });
    }
}
