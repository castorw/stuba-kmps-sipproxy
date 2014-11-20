package net.ctrdn.talk.portal.api.telephony;

import com.mongodb.BasicDBObject;
import java.util.Date;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.core.common.DatabaseSort;
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SipExtensionDao;
import net.ctrdn.talk.dao.SipSessionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.sip.SipSession;

public class CallListMethod extends DefaultApiMethod {

    public CallListMethod(ProxyController proxyController) {
        super(proxyController, "telephony.call.list");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        String mode = request.getParameter("mode");
        if (mode == null || (!mode.equals("history") && !mode.equals("active"))) {
            mode = "active";
        }

        JsonArrayBuilder callJab = Json.createArrayBuilder();

        if ("history".equals(mode)) {
            for (SipSessionDao sipSessionRecord : DatabaseObjectFactory.getInstance().list(SipSessionDao.class, null, "InviteDate", DatabaseSort.SORT_DESCENDING)) {
                if (sipSessionRecord.getEndDate() == null) {
                    continue;
                }
                JsonObjectBuilder sessionJob = this.createSipSessionJob(sipSessionRecord);
                callJab.add(sessionJob);
            }
        } else {
            for (SipSession sipSession : this.getProxyController().getSipServer().getSipSessionList()) {
                JsonObjectBuilder sessionJob = this.createSipSessionJob(sipSession.getSipSessionDao());
                callJab.add(sessionJob);
            }
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("CallList", callJab);
        return responseJob;
    }

    private JsonObjectBuilder createSipSessionJob(SipSessionDao sipSessionRecord) {
        JsonObjectBuilder sessionJob = Json.createObjectBuilder();

        SipAccountDao callerDao = sipSessionRecord.getCallerSipAccountDao();
        SipAccountDao calleeDao = sipSessionRecord.getCalleeSipAccountDao();
        SipExtensionDao callerPrimaryExtensionDao = this.resolvePrimaryExtension(callerDao);

        sessionJob.add("CallerExtension", callerPrimaryExtensionDao.getExtension());
        sessionJob.add("CallerSipAccountUsername", callerDao.getUsername());
        sessionJob.add("CallerUsername", callerDao.getSystemUserDao().getUsername());
        sessionJob.add("CallerDisplayName", callerDao.getSystemUserDao().getDisplayName());
        sessionJob.add("CalleeSipAccountUsername", calleeDao.getUsername());
        sessionJob.add("CalleeUsername", calleeDao.getSystemUserDao().getUsername());
        sessionJob.add("CalleeDisplayName", calleeDao.getSystemUserDao().getDisplayName());

        sessionJob.add("StartDate", sipSessionRecord.getInviteDate() == null ? null : sipSessionRecord.getInviteDate().getTime());
        if (sipSessionRecord.getOkResponseDate() != null) {
            sessionJob.add("AnswerDate", sipSessionRecord.getOkResponseDate().getTime());
        } else {
            sessionJob.addNull("AnswerDate");
        }
        if (sipSessionRecord.getEndDate() != null) {
            sessionJob.add("EndDate", sipSessionRecord.getEndDate().getTime());
        } else {
            sessionJob.addNull("EndDate");
        }

        if (sipSessionRecord.getEndCause() != null) {
            sessionJob.add("EndCause", sipSessionRecord.getEndCause());
        } else {
            sessionJob.addNull("EndCause");
        }

        CallStatus status;
        if (sipSessionRecord.getEndDate() != null) {
            status = CallStatus.ENDED;
        } else if (sipSessionRecord.getOkResponseDate() != null) {
            status = CallStatus.ANSWERED;
        } else if (sipSessionRecord.getRingingResponseDate() != null) {
            status = CallStatus.RINGING;
        } else if (sipSessionRecord.getInviteDate() != null) {
            status = CallStatus.INVITED;
        } else {
            status = CallStatus.UNKNOWN;
        }
        sessionJob.add("Status", status.toString());

        sessionJob.add("AlgEnabled", sipSessionRecord.getRtpAlgEnabled());
        sessionJob.add("AlgRecordingEnabled", sipSessionRecord.getRtpAlgRecordingEnabled());

        if (sipSessionRecord.getOkResponseDate() != null) {
            long duration;
            if (sipSessionRecord.getEndDate() != null) {
                duration = sipSessionRecord.getEndDate().getTime() - sipSessionRecord.getOkResponseDate().getTime();
            } else {
                duration = new Date().getTime() - sipSessionRecord.getOkResponseDate().getTime();
            }
            sessionJob.add("Duration", duration);
        } else {
            sessionJob.addNull("Duration");
        }
        return sessionJob;
    }

    private SipExtensionDao resolvePrimaryExtension(SipAccountDao accountDao) {
        BasicDBObject lookupReference = new BasicDBObject("TargetType", "SipAccount");
        lookupReference.append("SipAccountObjectId", accountDao.getObjectId());
        lookupReference.append("Primary", true);
        SipExtensionDao sipExtensionDao = DatabaseObjectFactory.getInstance().find(SipExtensionDao.class, lookupReference);
        return sipExtensionDao;
    }
}
