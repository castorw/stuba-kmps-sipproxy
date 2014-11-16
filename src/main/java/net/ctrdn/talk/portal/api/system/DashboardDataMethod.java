package net.ctrdn.talk.portal.api.system;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;

public class DashboardDataMethod extends DefaultApiMethod {

    public DashboardDataMethod(ProxyController proxyController) {
        super(proxyController, "system.dashboard.get-data");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        JsonObjectBuilder dataJob = Json.createObjectBuilder();

        dataJob.add("SipRegistrationCount", this.getProxyController().getSipServer().getSipRegistrationList().size());
        dataJob.add("SipCallCount", this.getProxyController().getSipServer().getSipSessionList().size());

        responseJob.add("DashboardData", dataJob);
        return responseJob;
    }
}
