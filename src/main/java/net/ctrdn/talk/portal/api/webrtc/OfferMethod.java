package net.ctrdn.talk.portal.api.webrtc;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.dao.SystemUserDao;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.webrtc.WebRtcPresence;
import net.ctrdn.talk.webrtc.WebRtcSession;
import org.bson.types.ObjectId;

public class OfferMethod extends DefaultApiMethod {

    public OfferMethod(ProxyController proxyController) {
        super(proxyController, "webrtc.offer");
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

        ObjectId calleeObjectId = new ObjectId(request.getParameter("callee-object-id"));
        String offerBase64 = request.getParameter("offer");

        SystemUserDao calleeDao = DatabaseObjectFactory.getInstance().find(SystemUserDao.class, calleeObjectId);
        if (calleeDao == null) {
            throw new ApiMethodException("Unable to resolve callee");
        }
        boolean calleeOnline = false;
        for (WebRtcPresence presence : this.getProxyController().getWebRtcProvider().getPresenceList()) {
            if (presence.getSystemUserDao().getObjectId().equals(calleeDao.getObjectId())) {
                calleeOnline = true;
                break;
            }
        }
        if (!calleeOnline) {
            throw new ApiMethodUserException("Callee is not available at the moment");
        }

        for (WebRtcSession session : this.getProxyController().getWebRtcProvider().getSessionList()) {
            if (session.getCalleeDao().getObjectId().equals(calleeDao.getObjectId()) || session.getCallerDao().getObjectId().equals(calleeDao.getObjectId())) {
                throw new ApiMethodUserException("User busy");
            }
        }

        WebRtcSession session = this.getProxyController().getWebRtcProvider().createSession(userDao, calleeDao);
        session.offerReceived(offerBase64);

        JsonObjectBuilder responseJob = Json.createObjectBuilder();

        JsonObjectBuilder sessionInfoJob = Json.createObjectBuilder();
        sessionInfoJob.add("Direction", "OUTGOING");
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

}
