package Controller.RegisterAndLogin;

import Controller.ApiSupport;
import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/login")
public class Login extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.prepare(response);
        try {
            request.setCharacterEncoding("UTF-8");
            ApiSupport.checkOrigin(request);
            JSONObject input = ApiSupport.input(request);
            String email = ApiSupport.text(input, "emailAddress");
            String password = input.getString("password");
            if (email.length() > 50 || password.isEmpty() || password.length() > 1024)
                throw new IllegalArgumentException("Invalid credentials");
            if (!LoginThrottle.allow(LoginThrottle.client(request), System.currentTimeMillis())) {
                response.setStatus(429);
                response.setHeader("Retry-After", "300");
                ApiSupport.write(response, new JSONObject().put("status", "failure")
                        .put("message", "Too many sign-in attempts. Try again in five minutes."));
                return;
            }
            Model.User user = new Model.User();
            if (!Model.Controller.RegisterAndLogin.Login.userLogin(user, email, password)) {
                response.setStatus(401);
                ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Invalid email or password."));
                return;
            }
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute("User", user);
            ApiSupport.write(response, new JSONObject().put("status", "success")
                    .put("userName", user.getForename() + " " + user.getSurname()).put("userAccount", user.getEmailAddress()));
        } catch (ApiSupport.Failure failure) {
            response.setStatus(failure.httpStatus());
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Request denied."));
        } catch (IllegalArgumentException | org.json.JSONException exception) {
            response.setStatus(400);
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Enter an email and password."));
        } catch (Exception exception) {
            response.setStatus(500);
            ApiSupport.write(response, new JSONObject().put("status", "failure").put("message", "Sign-in is temporarily unavailable."));
        }
    }
}
