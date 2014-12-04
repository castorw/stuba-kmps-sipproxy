package net.ctrdn.talk.portal.api.telephony;

import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;
import net.ctrdn.talk.sip.SipSession;

public class CallTerminateMethod extends DefaultApiMethod {

    public CallTerminateMethod(ProxyController proxyController) {
        super(proxyController, "telephony.call.terminate");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        UUID internalUuid = UUID.fromString(request.getParameter("uuid"));
        SipSession session = null;
        for (SipSession testSession : this.getProxyController().getSipServer().getSipSessionList()) {
            if (testSession.getInternalUuid().equals(internalUuid)) {
                session = testSession;
                break;
            }
        }
        if (session == null) {
            throw new ApiMethodUserException("Session not found (" + internalUuid.toString() + ")");
        }
        try {
            session.terminate();
            JsonObjectBuilder responseJob = Json.createObjectBuilder();
            responseJob.add("Successful", true);
            return responseJob;
        } catch (TalkSipSessionException ex) {
            this.getLogger().warn("Failed to terminate session", ex);
            throw new ApiMethodUserException("Failed to terminate session");
        }
    }

    @Override
    public boolean isAdminOnly() {
        return true;
    }
}
