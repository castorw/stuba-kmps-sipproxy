package net.ctrdn.talk.portal.api.telephony;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.core.common.DatabaseSort;
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SipSessionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.sip.SipSession;
import org.bson.types.ObjectId;

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
                sessionJob.add("SipSessionInternalUuid", sipSession.getInternalUuid().toString());
                callJab.add(sessionJob);
            }
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("CallList", callJab);
        return responseJob;
    }

    private JsonObjectBuilder createSipSessionJob(SipSessionDao sipSessionRecord) {
        try {
            JsonObjectBuilder sessionJob = Json.createObjectBuilder();

            SipAccountDao callerDao = sipSessionRecord.getCallerSipAccountDao();
            SipAccountDao calleeDao = sipSessionRecord.getCalleeSipAccountDao();

            this.addJsonObjectOrNull(sessionJob, "CallObjectId", sipSessionRecord.getObjectId());
            sessionJob.add("CallerExtension", sipSessionRecord.getCallerExtensionDao().getExtension());
            sessionJob.add("CallerSipAccountUsername", callerDao.getUsername());
            sessionJob.add("CallerUsername", callerDao.getSystemUserDao().getUsername());
            sessionJob.add("CallerDisplayName", callerDao.getSystemUserDao().getDisplayName());
            sessionJob.add("CalleeExtension", sipSessionRecord.getCalleeExtensionDao().getExtension());
            sessionJob.add("CalleeSipAccountUsername", calleeDao.getUsername());
            sessionJob.add("CalleeUsername", calleeDao.getSystemUserDao().getUsername());
            sessionJob.add("CalleeDisplayName", calleeDao.getSystemUserDao().getDisplayName());

            this.addJsonObjectOrNull(sessionJob, "InviteTimestamp", sipSessionRecord.getInviteDate());
            this.addJsonObjectOrNull(sessionJob, "RingingTimestamp", sipSessionRecord.getRingingResponseDate());
            this.addJsonObjectOrNull(sessionJob, "AnswerTimestamp", sipSessionRecord.getOkResponseDate());
            this.addJsonObjectOrNull(sessionJob, "AnswerAckTimestamp", sipSessionRecord.getAckDate());
            this.addJsonObjectOrNull(sessionJob, "CancelTimestamp", sipSessionRecord.getCancelDate());
            this.addJsonObjectOrNull(sessionJob, "ByeTimestamp", sipSessionRecord.getByeDate());
            this.addJsonObjectOrNull(sessionJob, "EndTimestamp", sipSessionRecord.getEndDate());
            this.addJsonObjectOrNull(sessionJob, "EndCause", sipSessionRecord.getEndCause());

            this.addJsonObjectOrNull(sessionJob, "CalleeRtpPort", sipSessionRecord.getCalleeRtpPort());
            this.addJsonObjectOrNull(sessionJob, "CalleeRtcpPort", sipSessionRecord.getCalleeRtcpPort());
            this.addJsonObjectOrNull(sessionJob, "CallerRtpPort", sipSessionRecord.getCallerRtpPort());
            this.addJsonObjectOrNull(sessionJob, "CallerRtcpPort", sipSessionRecord.getCallerRtcpPort());

            sessionJob.add("AlgEnabled", sipSessionRecord.getRtpAlgEnabled());
            sessionJob.add("AlgRecordingEnabled", sipSessionRecord.getRtpAlgRecordingEnabled());
            this.addJsonObjectOrNull(sessionJob, "AlgChannelUuid", sipSessionRecord.getRtpAlgChannelUuid());
            this.addJsonObjectOrNull(sessionJob, "AlgChannelRtpPort", sipSessionRecord.getRtpAlgChannelRtpPort());
            this.addJsonObjectOrNull(sessionJob, "AlgChannelRtcpPort", sipSessionRecord.getRtpAlgChannelRtcpPort());

            CallStatus status;
            if (sipSessionRecord.getCancelDate() != null || (sipSessionRecord.getInviteDate() != null && sipSessionRecord.getOkResponseDate() == null && sipSessionRecord.getEndDate() != null)) {
                status = CallStatus.CANCELED;
            } else if (sipSessionRecord.getEndDate() != null) {
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

            if (sipSessionRecord.getOkResponseDate() != null) {
                long duration;
                if (sipSessionRecord.getEndDate() != null) {
                    duration = sipSessionRecord.getEndDate().getTime() - sipSessionRecord.getOkResponseDate().getTime();
                } else {
                    duration = new Date().getTime() - sipSessionRecord.getOkResponseDate().getTime();
                }
                sessionJob.add("Duration", duration);
            } else {
                sessionJob.add("Duration", 0);
            }
            return sessionJob;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
            throw new RuntimeException("Failed to write JSON field", ex);
        }
    }

    private void addJsonObjectOrNull(JsonObjectBuilder job, String name, Object data) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (data == null) {
            job.addNull(name);
        } else {
            if (data instanceof Date) {
                data = ((Date) data).getTime();
            } else if (data instanceof ObjectId) {
                data = ((ObjectId) data).toHexString();
            } else if (data instanceof UUID) {
                data = ((UUID) data).toString();
            }
            Method method;
            if (data instanceof Long) {
                method = job.getClass().getMethod("add", String.class, long.class);
            } else if (data instanceof Integer) {
                method = job.getClass().getMethod("add", String.class, int.class);
            } else if (data instanceof Double) {
                method = job.getClass().getMethod("add", String.class, double.class);
            } else if (data instanceof Boolean) {
                method = job.getClass().getMethod("add", String.class, boolean.class);
            } else {
                method = job.getClass().getMethod("add", String.class, data.getClass());
            }

            method.setAccessible(true);
            method.invoke(job, name, data);
        }
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }
}
