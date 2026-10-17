package Controller.RegisterAndLogin;

import Controller.ApiSupport;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Deletes only the currently authenticated account and its owned financial records. */
@WebServlet("/deleteUser")
public class DeleteUser extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ApiSupport.serve(request, response, true, (user, input) -> {
            org.json.JSONObject result = ApiSupport.result(Model.Controller.RegisterAndLogin.DeleteUser.deleteUser(user));
            ApiSupport.invalidateMatchingSession(request, user);
            return result;
        });
    }
}
