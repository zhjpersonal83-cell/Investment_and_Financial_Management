package Controller;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

@WebServlet("/currencySwitch")
public class CurrencySwitch extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            String currency = Model.FixedTypes.Currencies.valueOf(ApiSupport.text(input, "currency")).name();
            Model.User sessionUser = (Model.User) request.getSession(false).getAttribute("User");
            sessionUser.setCurrencyCurrentChosen(currency);
            return new JSONObject().put("currencyCurrentChosen", currency);
        });
    }
}
