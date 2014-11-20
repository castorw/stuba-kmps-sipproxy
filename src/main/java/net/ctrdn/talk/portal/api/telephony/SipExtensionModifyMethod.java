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
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import org.bson.types.ObjectId;

public class SipExtensionModifyMethod extends DefaultApiMethod {

    public SipExtensionModifyMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-extension.modify");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        ObjectId objectId = new ObjectId(request.getParameter("object-id"));
        String inTarget = request.getParameter("target");
        boolean inPrimary = this.processInputBoolean(request.getParameter("primary"));
        boolean inEnabled = this.processInputBoolean(request.getParameter("enabled"));
        boolean hasPrimary = false;

        SipExtensionDao sipExtensionDao = DatabaseObjectFactory.getInstance().find(SipExtensionDao.class, objectId);
        if (sipExtensionDao == null) {
            throw new ApiMethodUserException("Internal error - extension not found.");
        }
        if (inTarget.trim().isEmpty()) {
            throw new ApiMethodUserException("Target needs to be set.");
        }
        String[] targetSplit = inTarget.split("/");

        sipExtensionDao.setTargetType(targetSplit[0]);
        if (targetSplit[0].equals("SipAccount")) {
            SipAccountDao sipAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new ObjectId(targetSplit[1]));
            if (sipAccountDao == null) {
                throw new ApiMethodUserException("Internal error - sip account not found.");
            }
            for (SipExtensionDao testDao : DatabaseObjectFactory.getInstance().list(SipExtensionDao.class, new BasicDBObject("SipAccountObjectId", sipAccountDao.getObjectId()))) {
                if (testDao.isPrimary()) {
                    if (inPrimary) {
                        testDao.setPrimary(false);
                        testDao.store();
                    }
                    hasPrimary = true;
                    break;
                }
            }
            sipExtensionDao.setSipAccountDao(sipAccountDao);
        }
        sipExtensionDao.setEnabled(inEnabled);
        sipExtensionDao.setPrimary(hasPrimary ? inPrimary : true);
        sipExtensionDao.store();

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("ObjectId", sipExtensionDao.getObjectId().toHexString());
        return responseJob;
    }
}
