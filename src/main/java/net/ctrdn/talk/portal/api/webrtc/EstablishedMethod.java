package net.ctrdn.talk.portal.api.webrtc;

import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.dao.SystemUserDao;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.webrtc.WebRtcSession;
import net.ctrdn.talk.webrtc.WebRtcSessionState;

public class EstablishedMethod extends DefaultApiMethod {

    public EstablishedMethod(ProxyController proxyController) {
        super(proxyController, "webrtc.established");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        HttpSession httpSession = request.getSession();
        if (httpSession == null) {
            throw new ApiMethodException("No HTTP session is established");
        }
        SystemUserSessionDao userSessionDao = (SystemUserSessionDao) httpSession.getAttribute("UserSession");
        if (userSessionDao == null) {
            throw new ApiMethodException("Not logged in");
        }
        SystemUserDao userDao = userSessionDao.getUser();

        UUID sessionUuid;
        try {
            sessionUuid = UUID.fromString(request.getParameter("session-uuid"));
        } catch (IllegalArgumentException ex) {
            throw new ApiMethodException("Invalid request (failed to determine session id)");
        }

        WebRtcSession session = null;
        for (WebRtcSession lookupSession : this.getProxyController().getWebRtcProvider().getSessionList()) {
            if (lookupSession.getSessionUuid().equals(sessionUuid)) {
                session = lookupSession;
                break;
            }
        }
        if (session == null) {
            throw new ApiMethodException("Session does not exist");
        }

        if (session.getCallerDao().getObjectId().equals(userDao.getObjectId())) {
            session.callerEstablished();
        } else if (session.getCalleeDao().getObjectId().equals(userDao.getObjectId())) {
            session.calleeEstablished();
        } else {
            throw new ApiMethodException("Invalid session state for established");
        }

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        JsonObjectBuilder sessionInfoJob = Json.createObjectBuilder();
        sessionInfoJob.add("Direction", session.getCallerDao().getObjectId().equals(userDao.getObjectId()) ? "OUTGOING" : "INCOMING");
        sessionInfoJob.add("SessionUuid", session.getSessionUuid().toString());
        sessionInfoJob.add("State", session.getState().toString());
        sessionInfoJob.add("CallerObjectId  ", session.getCallerDao().getObjectId().toHexString());
        sessionInfoJob.add("CallerDisplayName", session.getCallerDao().getDisplayName());
        sessionInfoJob.add("CalleeObjectId", session.getCalleeDao().getObjectId().toHexString());
        sessionInfoJob.add("CalleeDisplayName", session.getCalleeDao().getDisplayName());

        responseJob.add("Successful", true);
        responseJob.add("SessionInfo", sessionInfoJob);
        return responseJob;
    }

    @Override
    public boolean isAdminOnly() {
        return false;
    }
}
