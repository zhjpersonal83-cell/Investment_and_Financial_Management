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
            DatabaseService.RequestIdentity.verifyCurrentUser();
            String currency = Model.FixedTypes.Currencies.valueOf(ApiSupport.text(input, "currency")).name();
            javax.servlet.http.HttpSession session = request.getSession(false);
            if (session == null) throw new ApiSupport.Failure(401, "Please sign in again.");
            synchronized (session) {
                Model.User sessionUser = (Model.User) session.getAttribute("User");
                if (sessionUser == null || !user.getGeneration().equals(sessionUser.getGeneration()))
                    throw new ApiSupport.Failure(401, "Please sign in again.");
                sessionUser.setCurrencyCurrentChosen(currency);
            }
            return new JSONObject().put("currencyCurrentChosen", currency);
        });
    }
}
