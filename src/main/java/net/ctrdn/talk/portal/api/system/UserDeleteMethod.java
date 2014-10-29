package net.ctrdn.talk.portal.api.system;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.system.SystemUserDao;
import org.bson.types.ObjectId;

public class UserDeleteMethod extends DefaultApiMethod {

    public UserDeleteMethod() {
        super("system.user.delete");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Successful", false);
        ObjectId objectId = new ObjectId(request.getParameter("object-id"));
        if (!proxyController.getPortalSessionDao(request.getSession()).getObjectId().equals(objectId)) {
            SystemUserDao sudao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, objectId);
            if (sudao != null) {
                sudao.delete();
                responseJob.add("Successful", true);
            }
        }
        return responseJob;
    }

}
