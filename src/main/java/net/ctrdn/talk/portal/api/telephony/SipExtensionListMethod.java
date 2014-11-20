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
import net.ctrdn.talk.dao.SipExtensionDao;

public class SipExtensionListMethod extends DefaultApiMethod {

    public SipExtensionListMethod(ProxyController proxyController) {
        super(proxyController, "telephony.sip-extension.list");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonArrayBuilder extensionListJab = Json.createArrayBuilder();

        for (SipExtensionDao extensionDao : DatabaseObjectFactory.getInstance().list(SipExtensionDao.class, null, "Extension", DatabaseSort.SORT_ASCENDING)) {
            JsonObjectBuilder extensionJob = Json.createObjectBuilder();
            extensionJob.add("ObjectId", extensionDao.getObjectId().toHexString());
            extensionJob.add("Extension", extensionDao.getExtension());
            extensionJob.add("TargetType", extensionDao.getTargetType());
            extensionJob.add("Enabled", extensionDao.isEnabled());
            extensionJob.add("Primary", extensionDao.isPrimary());
            if (extensionDao.getTargetType().equals("SipAccount") && extensionDao.getSipAccountDao() != null) {
                extensionJob.add("SipAccountObjectId", extensionDao.getSipAccountDao().getObjectId().toHexString());
                extensionJob.add("SipAccountUsername", extensionDao.getSipAccountDao().getUsername());
                extensionJob.add("SystemUserUsername", extensionDao.getSipAccountDao().getSystemUserDao().getUsername());
                extensionJob.add("SystemUserDisplayName", extensionDao.getSipAccountDao().getSystemUserDao().getDisplayName());
            }
            boolean online = false;
            for (SipRegistration reg : this.getProxyController().getSipServer().getSipRegistrationList()) {
                for (SipExtensionDao probeExtensionDao : reg.getRegisteredExtensionDaoList()) {
                    if (probeExtensionDao.getExtension().equals(extensionDao.getExtension())) {
                        online = true;
                        break;
                    }
                }
                if (online) {
                    break;
                }
            }
            extensionJob.add("IsOnline", online);

            extensionListJab.add(extensionJob);
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("SipExtensionList", extensionListJab);
        return responseJob;
    }
}
