package net.ctrdn.talk.portal.api.telephony;

import com.mongodb.BasicDBObject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.dao.SipAccountDao;

public class SipAccountModifyMethod extends DefaultApiMethod {

    public SipAccountModifyMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-account.modify");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        String inUsername = request.getParameter("username");
        String inPassword = request.getParameter("password");
        boolean inEnabled = this.processInputBoolean(request.getParameter("enabled"));
        boolean inRecordIncomingCalls = this.processInputBoolean(request.getParameter("record-incoming-calls"));
        boolean inRecordOutgoingCalls = this.processInputBoolean(request.getParameter("record-outgoing-calls"));

        SipAccountDao sipAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", inUsername));
        if (sipAccountDao == null) {
            throw new ApiMethodUserException("SIP Account with this username not found.");
        }

        if (inPassword.length() < 10) {
            throw new ApiMethodUserException("Secret needs to be at least 10 characters long.");
        }

        sipAccountDao.setPlaintextPassword(inPassword);
        sipAccountDao.setEnabled(inEnabled);
        sipAccountDao.setRecordIncomingCalls(inRecordIncomingCalls);
        sipAccountDao.setRecordOutgoingCalls(inRecordOutgoingCalls);
        sipAccountDao.store();

        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("ObjectId", sipAccountDao.getObjectId().toHexString());
        return job;
    }
}
