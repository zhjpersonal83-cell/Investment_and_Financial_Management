package Controller.RegisterAndLogin;

import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class Register extends HttpServlet {

    private static int registerBeginCount = 1, registerFinishCount = 1;

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        synchronized (Model.Tools.Locks.getLock()) {
            System.out.println("Register begin id: " + registerBeginCount);
            registerBeginCount++;
        }

        java.lang.String emailAddress = httpServletRequest.getParameter("regEmailAddress").trim(),
                password = httpServletRequest.getParameter("regPassword").trim(),
                surname = httpServletRequest.getParameter("lastName").trim(),
                forename = httpServletRequest.getParameter("firstName").trim();

        boolean isEmailAddressValid =
                Model.Controller.RegisterAndLogin.ConditionalityCheck.isEmailAddressValid(emailAddress),
                registerSuccessful = false;

        if (isEmailAddressValid) {
            registerSuccessful =
                    Model.Controller.RegisterAndLogin.Register.userRegister(emailAddress, password, surname, forename);
            System.out.println("Register execution: " + registerSuccessful);
        }

        java.lang.String status;
        if (registerSuccessful) {
            status = "success";
        } else {
            status = "failure";
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", status);
        if ("success".equals(status)) {
            jsonObject.put("message", "注册成功");
        } else {
            jsonObject.put("message", "注册失败，请检查您的输入或尝试其他电子邮件地址(账号)");
        }

        Controller.CommonFunctions.setCharacterEncoding(httpServletResponse);
        Controller.CommonFunctions.printWriterProcess(httpServletResponse, jsonObject);

        synchronized (Model.Tools.Locks.getLock()) {
            System.out.println("Register finish id: " + registerFinishCount);
            registerFinishCount++;
        }
    }

}
