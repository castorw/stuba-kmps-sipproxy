package net.ctrdn.talk.portal.api.system;

import com.mongodb.BasicDBObject;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.dao.SystemUserDao;

public class UserCreateMethod extends DefaultApiMethod {

    public UserCreateMethod(ProxyController proxyController) {
        super(proxyController, "system.user.create");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        String inUsername = request.getParameter("username");
        String inDisplayName = request.getParameter("display-name");
        String inPassword = request.getParameter("password");
        String inPassword2 = request.getParameter("password2");
        boolean inAdminAccess = this.processInputBoolean(request.getParameter("administrative-access"));
        boolean inEnabled = this.processInputBoolean(request.getParameter("enabled"));

        if (inUsername.trim().length() < 5) {
            throw new ApiMethodUserException("Username must be at least 5 characters long.");
        }
        if (inDisplayName.trim().length() < 5) {
            throw new ApiMethodUserException("Display name must be at least 5 characters long.");
        }
        if (!inPassword.equals(inPassword2)) {
            throw new ApiMethodUserException("Entered passwords do not match.");
        }
        SystemUserDao lookupDao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, new BasicDBObject("Username", inUsername));
        if (lookupDao != null) {
            throw new ApiMethodUserException("This username is already in use by other user.");
        }
        try {
            SystemUserDao newUserDao = DatabaseObjectFactory.getInstance().create(SystemUserDao.class);
            newUserDao.setUsername(inUsername);
            newUserDao.setDisplayName(inDisplayName);
            newUserDao.setAdministratorAccess(inAdminAccess);
            newUserDao.setEnabled(inEnabled);
            newUserDao.setLastLoginDate(null);
            newUserDao.setCreateDate(new Date());
            newUserDao.setPassword(inPassword);
            newUserDao.store();

            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add("ObjectId", newUserDao.getObjectId().toHexString());
            return job;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new ApiMethodException("Internal Error - Unable to digest password.", ex);
        }
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }
}
