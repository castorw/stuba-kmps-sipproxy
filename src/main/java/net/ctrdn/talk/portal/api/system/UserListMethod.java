package net.ctrdn.talk.portal.api.system;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.core.common.DatabaseSort;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.system.SystemUserDao;

public class UserListMethod extends DefaultApiMethod {

    public UserListMethod() {
        super("system.user.list");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonArrayBuilder userListJab = Json.createArrayBuilder();

        for (SystemUserDao userDao : DatabaseObjectFactory.getInstance().list(SystemUserDao.class, null, "Username", DatabaseSort.SORT_ASCENDING)) {
            JsonObjectBuilder userJob = Json.createObjectBuilder();
            userJob.add("ObjectId", userDao.getObjectId().toHexString());
            userJob.add("Username", userDao.getUsername());
            userJob.add("DisplayName", userDao.getDisplayName());
            userJob.add("LastLoginTimestamp", this.processOutputTimestamp(userDao.getLastLoginDate()));
            userJob.add("CreateTimestamp", this.processOutputTimestamp(userDao.getCreateDate()));
            userJob.add("AdministrativeAccess", userDao.isAdministratorAccess());
            userJob.add("Enabled", userDao.isEnabled());
            userJob.add("IsCurrentUser", proxyController.getPortalSessionDao(request.getSession()).getUser().getObjectId().equals(userDao.getObjectId()));
            userListJab.add(userJob);
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("SystemUserList", userListJab);
        return responseJob;
    }

}
