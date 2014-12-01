package net.ctrdn.talk.portal.api.webrtc;

import java.util.UUID;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.dao.SystemUserDao;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.webrtc.WebRtcPresence;
import net.ctrdn.talk.webrtc.WebRtcSession;
import net.ctrdn.talk.webrtc.WebRtcSessionState;

public class PresentMethod extends DefaultApiMethod {

    public PresentMethod(ProxyController proxyController) {
        super(proxyController, "webrtc.present");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        HttpSession httpSession = request.getSession();
        if (httpSession == null) {
            throw new ApiMethodException("No HTTP session is established");
        }
        SystemUserSessionDao sessionDao = (SystemUserSessionDao) httpSession.getAttribute("UserSession");
        if (sessionDao == null) {
            throw new ApiMethodException("Not logged in");
        }
        SystemUserDao userDao = sessionDao.getUser();

        String ackSessionString = request.getParameter("ack-session-uuid");
        UUID ackSessionUuid = null;
        if (ackSessionString != null) {
            try {
                ackSessionUuid = UUID.fromString(ackSessionString);
                for (WebRtcSession session : this.getProxyController().getWebRtcProvider().getSessionList()) {
                    if (session.getSessionUuid().equals(ackSessionUuid) && session.getState() == WebRtcSessionState.ESTABLISHED) {
                        if (session.getCallerDao().getObjectId().equals(userDao.getObjectId())) {
                            session.callerAcknowledge();
                        } else if (session.getCalleeDao().getObjectId().equals(userDao.getObjectId())) {
                            session.calleeAcknowledge();
                        } else {
                            throw new ApiMethodException("Invalid session ack source");
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
            }
        }

        this.getProxyController().getWebRtcProvider().presentMessageReceived(userDao);
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Timeout", this.getProxyController().getWebRtcProvider().getPresenceAcknowledgeTimeout());

        JsonArrayBuilder userJab = Json.createArrayBuilder();
        for (WebRtcPresence presence : this.getProxyController().getWebRtcProvider().getPresenceList()) {
            JsonObjectBuilder userJob = Json.createObjectBuilder();
            userJob.add("ObjectId", presence.getSystemUserDao().getObjectId().toHexString());
            userJob.add("DisplayName", presence.getSystemUserDao().getDisplayName());
            userJob.add("IsSelf", presence.getSystemUserDao().getObjectId().equals(userDao.getObjectId()));
            userJab.add(userJob);
        }

        JsonObjectBuilder sessionInfoJob = null;
        for (WebRtcSession session : this.getProxyController().getWebRtcProvider().getSessionList()) {
            if (session.getCallerDao().getObjectId().equals(userDao.getObjectId())) {
                sessionInfoJob = Json.createObjectBuilder();
                sessionInfoJob.add("Direction", "OUTGOING");
                if (session.getState() == WebRtcSessionState.ANSWERED) {
                    session.answerDelivered();
                    sessionInfoJob.add("AnswerBase64", session.getCalleeAnswerBase64());
                } else if (session.getState() == WebRtcSessionState.CALLEE_CANDIDATE) {
                    session.calleeCandidateDelivered();
                    sessionInfoJob.add("CandidateBase64", session.getCalleeCandidateBase64());
                } else if (session.getState() == WebRtcSessionState.TERMINATED) {
                    session.calleeTerminatedDelivered();
                }
            } else if (session.getCalleeDao().getObjectId().equals(userDao.getObjectId())) {
                sessionInfoJob = Json.createObjectBuilder();
                sessionInfoJob.add("Direction", "INCOMING");
                if (session.getState() == WebRtcSessionState.OFFERED) {
                    session.offerDelivered();
                    sessionInfoJob.add("OfferBase64", session.getCallerOfferBase64());
                } else if (session.getState() == WebRtcSessionState.CALLER_CANDIDATE) {
                    session.callerCandidateDelivered();
                    sessionInfoJob.add("CandidateBase64", session.getCallerCandidateBase64());
                } else if (session.getState() == WebRtcSessionState.TERMINATED) {
                    session.callerTerminatedDelivered();
                }
            }
            if (sessionInfoJob != null) {
                sessionInfoJob.add("SessionUuid", session.getSessionUuid().toString());
                sessionInfoJob.add("State", session.getState().toString());
                sessionInfoJob.add("CallerObjectId", session.getCallerDao().getObjectId().toHexString());
                sessionInfoJob.add("CallerDisplayName", session.getCallerDao().getDisplayName());
                sessionInfoJob.add("CalleeObjectId", session.getCalleeDao().getObjectId().toHexString());
                sessionInfoJob.add("CalleeDisplayName", session.getCalleeDao().getDisplayName());
                break;
            }
        }

        if (sessionInfoJob != null) {
            responseJob.add("SessionInfo", sessionInfoJob);
        }
        responseJob.add("UserList", userJab);
        responseJob.add("Successful", true);
        return responseJob;
    }

}
