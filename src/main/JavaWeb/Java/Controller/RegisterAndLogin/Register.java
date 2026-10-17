package Controller.RegisterAndLogin;

import Controller.ApiSupport;
import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class Register extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.prepare(response);
        try {
            request.setCharacterEncoding("UTF-8");
            ApiSupport.checkOrigin(request);
            JSONObject input = ApiSupport.input(request);
            String email = ApiSupport.text(input, "regEmailAddress");
            String password = input.getString("regPassword");
            String surname = ApiSupport.text(input, "lastName"), forename = ApiSupport.text(input, "firstName");
            if (password.isEmpty() || password.length() > 1024 || surname.length() > 50 || forename.length() > 50
                    || !Model.Controller.RegisterAndLogin.ConditionalityCheck.isEmailAddressValid(email))
                throw new IllegalArgumentException("Invalid registration details.");
            if (!LoginThrottle.allow(LoginThrottle.client(request), System.currentTimeMillis()))
                throw new ApiSupport.Failure(429, "Too many requests. Try again in five minutes.");
            boolean success = Model.Controller.RegisterAndLogin.Register.userRegister(email, password, surname, forename);
            response.setStatus(success ? 200 : 409);
            ApiSupport.write(response, new JSONObject().put("status", success ? "success" : "failure")
                    .put("message", success ? "Account created." : "Registration could not be completed. Check your details or use another email."));
        } catch (ApiSupport.Failure failure) {
            response.setStatus(failure.httpStatus());
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", failure.getMessage()));
        } catch (IllegalArgumentException | org.json.JSONException invalid) {
            response.setStatus(400);
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Enter valid registration details."));
        } catch (Exception failure) {
            response.setStatus(503);
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Registration is temporarily unavailable."));
        }
    }
}
