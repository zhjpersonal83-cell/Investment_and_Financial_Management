package Controller.RegisterAndLogin;

import Controller.ApiSupport;
import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/logOff")
public class LogOff extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            request.getSession(false).invalidate();
            return new JSONObject().put("status", "success");
        });
    }
}
