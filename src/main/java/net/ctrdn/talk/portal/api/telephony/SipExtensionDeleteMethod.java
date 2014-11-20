package net.ctrdn.talk.portal.api.telephony;

import com.mongodb.BasicDBObject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SipExtensionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import org.bson.types.ObjectId;

public class SipExtensionDeleteMethod extends DefaultApiMethod {

    public SipExtensionDeleteMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-extension.delete");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Successful", false);
        ObjectId objectId = new ObjectId(request.getParameter("object-id"));
        SipExtensionDao extensionDao = DatabaseObjectFactory.getInstance().find(SipExtensionDao.class, objectId);
        if (extensionDao != null) {
            SipAccountDao sipAccountDao = extensionDao.getSipAccountDao();
            extensionDao.delete();
            if (extensionDao.getTargetType().equals("SipAccount") && extensionDao.getSipAccountDao() != null) {
                BasicDBObject replacementSearchConditions = new BasicDBObject();
                replacementSearchConditions.put("TargetType", "SipAccount");
                replacementSearchConditions.put("SipAccountObjectId", sipAccountDao.getObjectId());
                for (SipExtensionDao replacementExtensionDao : DatabaseObjectFactory.getInstance().list(SipExtensionDao.class, replacementSearchConditions)) {
                    replacementExtensionDao.setPrimary(true);
                    break;
                }
            }
            responseJob.add("Successful", true);
        }
        return responseJob;
    }
}
