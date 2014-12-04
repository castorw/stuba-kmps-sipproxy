package net.ctrdn.talk.portal.api.telephony;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.dao.SipAccountDao;
import org.bson.types.ObjectId;

public class SipAccountDeleteMethod extends DefaultApiMethod {

    public SipAccountDeleteMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-account.delete");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Successful", false);
        ObjectId objectId = new ObjectId(request.getParameter("object-id"));
        SipAccountDao accountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, objectId);
        if (accountDao != null) {
            accountDao.delete();
            responseJob.add("Successful", true);
        }
        return responseJob;
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }
}
