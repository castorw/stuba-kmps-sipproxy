package net.ctrdn.talk.portal.api.system;

import com.mongodb.BasicDBObject;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.dao.SystemUserDao;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import org.bson.types.ObjectId;

public class UserLoginMethod extends DefaultApiMethod {

    public UserLoginMethod(ProxyController proxyController) {
        super(proxyController, "system.user.login");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        try {
            JsonObjectBuilder responseJob = Json.createObjectBuilder();
            boolean inRecoverCookie = this.processInputBoolean(request.getParameter("recover-cookie"));
            boolean inRemember = this.processInputBoolean(request.getParameter("remember-login"));
            SystemUserDao userDao = null;
            if (!inRecoverCookie) {
                String inUsername = request.getParameter("username");
                String inPassword = request.getParameter("password");
                userDao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, new BasicDBObject("Username", inUsername));
                if (userDao == null || !userDao.matchPassword(inPassword) || !userDao.isEnabled()) {
                    throw new ApiMethodUserException("Username or password is incorrect.");
                }
                if (inRemember) {
                    Cookie cookie = new Cookie("UserObjectId", userDao.getObjectId().toHexString());
                    cookie.setPath("/");
                    cookie.setMaxAge(30 * 24 * 3600);
                    response.addCookie(cookie);
                }
            } else {
                Cookie[] cookies = request.getCookies();
                for (Cookie c : cookies) {
                    if (c.getName().equals("UserObjectId")) {
                        userDao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, new ObjectId(c.getValue()));
                        break;
                    }
                }
            }
            if (userDao == null && inRecoverCookie) {
                responseJob.add("Successful", false);
                return responseJob;
            } else {
                if (userDao == null) {
                    throw new ApiMethodUserException("Username or password is incorrect.");
                }
                SystemUserSessionDao sessionDao = DatabaseObjectFactory.getInstance().create(SystemUserSessionDao.class);
                sessionDao.setUser(userDao);
                sessionDao.setStartDate(new Date());
                sessionDao.setLastActivityDate(new Date());
                sessionDao.setEndDate(null);
                sessionDao.setFromCookie(false);
                sessionDao.store();
                userDao.setLastLoginDate(new Date());
                userDao.store();

                request.getSession().setAttribute("UserSession", sessionDao);

                responseJob.add("Remembered", inRemember);
                responseJob.add("Successful", true);

                String loginTargetUri = (String) request.getSession().getAttribute("LoginTargetUri");

                if (loginTargetUri != null) {
                    responseJob.add("TargetUrl", loginTargetUri);
                    request.getSession().removeAttribute("LoginTargetUri");
                } else {
                    responseJob.add("TargetUrl", "/");
                }
                if (inRecoverCookie) {
                    this.getLogger().info("{} User " + userDao.getUsername() + " successfully logged in from cookie", this.getLogId(request));
                } else {
                    this.getLogger().info("{} User " + userDao.getUsername() + " successfully logged in", this.getLogId(request));
                }
                return responseJob;
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new ApiMethodException("Failed to authenticate user", ex);
        }
    }

    @Override
    public boolean isAdminOnly() {
        return false;
    }
}
