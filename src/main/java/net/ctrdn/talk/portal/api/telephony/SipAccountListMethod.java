package net.ctrdn.talk.portal.api.telephony;

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
import net.ctrdn.talk.sip.SipRegistration;
import net.ctrdn.talk.dao.SipAccountDao;

public class SipAccountListMethod extends DefaultApiMethod {

    public SipAccountListMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-account.list");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonArrayBuilder accountListJab = Json.createArrayBuilder();

        for (SipAccountDao accountDao : DatabaseObjectFactory.getInstance().list(SipAccountDao.class, null, "Username", DatabaseSort.SORT_ASCENDING)) {
            JsonObjectBuilder accountJob = Json.createObjectBuilder();
            accountJob.add("ObjectId", accountDao.getObjectId().toHexString());
            accountJob.add("SystemUserObjectId", accountDao.getSystemUserDao().getObjectId().toHexString());
            accountJob.add("SystemUserDisplayName", accountDao.getSystemUserDao().getDisplayName());
            accountJob.add("Username", accountDao.getUsername());
            accountJob.add("Password", accountDao.getPlaintextPassword());
            accountJob.add("LastLoginTimestamp", this.processOutputTimestamp(accountDao.getLastLoginDate()));
            accountJob.add("CreateTimestamp", this.processOutputTimestamp(accountDao.getCreateDate()));
            accountJob.add("RecordIncomingCalls", accountDao.getRecordIncomingCalls());
            accountJob.add("RecordOutgoingCalls", accountDao.getRecordOutgoingCalls());
            accountJob.add("Enabled", accountDao.getEnabled());
            boolean online = false;
            for (SipRegistration reg : this.getProxyController().getSipServer().getSipRegistrationList()) {
                if (reg.getSipAccountDao().getObjectId().equals(accountDao.getObjectId())) {
                    online = true;
                    break;
                }
            }
            accountJob.add("IsOnline", online);

            accountListJab.add(accountJob);
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("SipAccountList", accountListJab);
        return responseJob;
    }
}
