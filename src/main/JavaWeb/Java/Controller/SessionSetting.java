package Controller;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@WebListener
public class SessionSetting implements HttpSessionListener {

    @Override
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        HttpSession httpSession = httpSessionEvent.getSession();
        httpSession.setMaxInactiveInterval(10 * 60);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        HttpSession httpSession = httpSessionEvent.getSession();
        Model.Tools.Locks.removeConcurrentHashMapLock(httpSession.getId());
    }

}
