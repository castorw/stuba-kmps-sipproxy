package net.ctrdn.talk.portal.api.telephony;

import com.mongodb.BasicDBObject;
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
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SystemUserDao;
import org.bson.types.ObjectId;

public class SipAccountCreateMethod extends DefaultApiMethod {

    public SipAccountCreateMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-account.create");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        String inUsername = request.getParameter("username");
        String inSystemAccountObjectId = request.getParameter("system-account-object-id");
        String inPassword = request.getParameter("password");
        boolean inEnabled = this.processInputBoolean(request.getParameter("enabled"));
        boolean inRecordIncomingCalls = this.processInputBoolean(request.getParameter("record-incoming-calls"));
        boolean inRecordOutgoingCalls = this.processInputBoolean(request.getParameter("record-outgoing-calls"));

        if (inSystemAccountObjectId.trim().isEmpty()) {
            throw new ApiMethodUserException("System account needs to be selected.");
        }
        if (inUsername.trim().length() < 5) {
            throw new ApiMethodUserException("Username must be at least 5 characters long.");
        }
        if (inPassword.length() < 10) {
            throw new ApiMethodUserException("Secret needs to be at least 10 characters long.");
        }
        SystemUserDao systemAccountDao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, new ObjectId(inSystemAccountObjectId));
        if (systemAccountDao == null) {
            throw new ApiMethodUserException("Error looking the system account up.");
        }
        SipAccountDao sipAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", inUsername));
        if (sipAccountDao != null) {
            throw new ApiMethodUserException("SIP Account with this username already exists.");
        }

        sipAccountDao = DatabaseObjectFactory.getInstance().create(SipAccountDao.class);
        sipAccountDao.setSystemUserDao(systemAccountDao);
        sipAccountDao.setUsername(inUsername);
        sipAccountDao.setPlaintextPassword(inPassword);
        sipAccountDao.setCreateDate(new Date());
        sipAccountDao.setLastLoginDate(null);
        sipAccountDao.setRecordIncomingCalls(inRecordIncomingCalls);
        sipAccountDao.setRecordOutgoingCalls(inRecordOutgoingCalls);
        sipAccountDao.setEnabled(inEnabled);
        sipAccountDao.store();

        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("ObjectId", sipAccountDao.getObjectId().toHexString());
        return job;
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }
}
