package net.ctrdn.talk.portal.api.system;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.portal.api.DefaultApiMethod;

public class GetConfigurationMethod extends DefaultApiMethod {

    public GetConfigurationMethod(ProxyController proxyController) {
        super(proxyController, "system.configuration.get");
    }

    @Override
    public JsonObjectBuilder execute(ProxyController proxyController, HttpServletRequest request, HttpServletResponse response) throws ApiMethodException {
        JsonObjectBuilder configDao = Json.createObjectBuilder();
        this.addEntry(configDao, "talk.sip.server.enabled", "false");
        this.addEntry(configDao, "talk.sip.server.domain", "");
        this.addEntry(configDao, "talk.sip.server.listen.host", "0.0.0.0");
        this.addEntry(configDao, "talk.sip.server.listen.port", "5060");
        this.addEntry(configDao, "talk.sip.server.listen.transport", "udp");
        this.addEntry(configDao, "talk.sip.register.force-timeout", "false");
        this.addEntry(configDao, "talk.sip.register.force-timeout.time", "");
        this.addEntry(configDao, "talk.rtp.alg.enabled", "false");
        this.addEntry(configDao, "talk.rtp.alg.listen.host", "0.0.0.0");
        this.addEntry(configDao, "talk.rtp.alg.channel.timeout", "30");
        this.addEntry(configDao, "talk.rtp.alg.recording.path", "/tmp");

        JsonObjectBuilder responseJob = Json.createObjectBuilder();
        responseJob.add("SystemConfiguration", configDao);
        return responseJob;
    }

    private void addEntry(JsonObjectBuilder job, String attrName, String defaultValue) {
        String value = this.getProxyController().getConfiguration().getProperty(attrName, defaultValue);
        switch (value) {
            case "true":
                job.add(attrName, true);
                break;
            case "false":
                job.add(attrName, false);
                break;
            default:
                if (value.equals("")) {
                    job.addNull(attrName);
                } else {
                    job.add(attrName, value);
                }
                break;
        }
    }
}
