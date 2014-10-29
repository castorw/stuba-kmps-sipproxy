package net.ctrdn.talk.portal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiMethodUserException;
import net.ctrdn.talk.exception.ApiRegistryException;
import net.ctrdn.talk.exception.PortalAuthenticationException;
import net.ctrdn.talk.portal.api.ApiMethod;
import net.ctrdn.talk.portal.api.ApiMethodRegistry;
import net.ctrdn.talk.system.SystemUserSessionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(ApiServlet.class);
    private final ProxyController proxyController;

    public ApiServlet(ProxyController proxyController) {
        this.proxyController = proxyController;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        int outputStatus = HttpServletResponse.SC_OK;

        response.setContentType("text/json");
        String apiCallName = request.getRequestURI().replace(request.getServletPath() + "/", "");
        JsonObjectBuilder responseJob = Json.createObjectBuilder();

        try {
            if (!apiCallName.equals("system.user.login")) {
                if (session.getAttribute("UserSession") == null) {
                    throw new PortalAuthenticationException("Not logged in");
                }
                SystemUserSessionDao sessionDao = (SystemUserSessionDao) session.getAttribute("UserSession");
                this.proxyController.checkPortalSession(sessionDao);
            }

            try {
                ApiMethod method = ApiMethodRegistry.getInstance().getMethod(apiCallName);
                if (method != null) {
                    try {
                        JsonObjectBuilder methodJob = method.execute(this.proxyController, request, response);
                        responseJob.add("Response", methodJob);
                        responseJob.add("Status", true);
                    } catch (ApiMethodUserException ex) {
                        responseJob.add("Status", true);
                        responseJob.add("UserError", ex.getMessage());
                        this.logger.trace("[" + request.getRemoteAddr() + "] API method produced user error: " + ex.getMessage());
                    } catch (ApiMethodException ex) {
                        responseJob.add("Status", false);
                        responseJob.add("Error", "ApiMethodException: " + ex.getMessage());
                        this.logger.info("[" + request.getRemoteAddr() + "] API method invocation failed", ex);
                    }
                } else {
                    responseJob.add("Status", false);
                    responseJob.add("Error", "Unknown method " + apiCallName);
                    this.logger.info("[" + request.getRemoteAddr() + "] Unknown API method requested " + apiCallName);
                }
            } catch (ApiRegistryException ex) {
                responseJob.add("Status", false);
                responseJob.add("Error", "Internal error while processing request");
                outputStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                this.logger.warn("Failed to resolve API method", ex);
            }
        } catch (PortalAuthenticationException aex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            outputStatus = HttpServletResponse.SC_UNAUTHORIZED;
            responseJob.add("Status", false);
            responseJob.add("Reason", aex.getMessage());
        }

        response.setStatus(outputStatus);
        Map<String, Object> jwConfig = new HashMap<>();
        jwConfig.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriter jw = Json.createWriterFactory(jwConfig).createWriter(response.getOutputStream());
        jw.writeObject(responseJob.build());
    }
}
