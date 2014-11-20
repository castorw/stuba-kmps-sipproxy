package net.ctrdn.talk.portal.api.system;

import java.util.Enumeration;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.exception.TalkException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;

public class SetConfigurationMethod extends DefaultApiMethod {

    public SetConfigurationMethod(ProxyController proxyController) {
        super(proxyController, "system.configuration.set");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements();) {
            String parameterName = e.nextElement();
            this.getProxyController().getConfiguration().setProperty(parameterName, request.getParameter(parameterName));
        }
        this.getProxyController().writeConfiguration();
        try {
            this.getProxyController().restartSipServer();
        } catch (TalkException ex) {
            throw new ApiMethodUserException("Failed to reload server: (" + ex.getClass().getSimpleName() + ") " + ex.getMessage(), ex);
        }
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("Successful", true);
        return responseJob;
    }
}
