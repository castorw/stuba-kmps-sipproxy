package net.ctrdn.talk.portal.api.system;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.dao.SystemUserSessionDao;

public class UserLogoutMethod extends DefaultApiMethod {

    public UserLogoutMethod(ProxyController proxyController) {
        super(proxyController, "system.user.logout");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        SystemUserSessionDao sesionDao = proxyController.getPortalSessionDao(request.getSession());
        sesionDao.endSession();
        Cookie cookie = new Cookie("UserObjectId", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        request.getSession().removeAttribute("UserSession");
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Successful", true);
        responseJob.add("TargetUri", "/");
        this.getLogger().info("{} User " + sesionDao.getUser().getUsername() + " logged out", this.getLogId(request));
        return responseJob;
    }

    @Override
    public boolean isAdminOnly() {
        return false;
    }
}
