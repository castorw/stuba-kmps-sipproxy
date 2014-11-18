package net.ctrdn.talk.portal.api;

import java.util.Date;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class DefaultApiMethod implements ApiMethod {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProxyController proxyController;
    private final String path;

    protected DefaultApiMethod(ProxyController proxyController, String path) {
        this.path = path;
        this.proxyController = proxyController;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    protected boolean processInputBoolean(String boolString) {
        return (boolString == null) ? false : boolString.trim().toLowerCase().equals("true");
    }

    protected long processOutputTimestamp(Date date) {
        if (date != null) {
            return date.getTime();
        }
        return -1;
    }

    protected void insertToJsonObject(JsonObjectBuilder job, String n, Object o) {
        if (o == null) {
            job.addNull(n);
        } else if (o instanceof Integer) {
            job.add(n, (Integer) o);
        } else {
            job.add(n, o.toString());
        }
    }

    protected Logger getLogger() {
        return logger;
    }

    protected String getLogId(HttpServletRequest request) {
        String idString = "[" + request.getRemoteAddr();
        if (request.getSession().getAttribute("UserSession") != null) {
            idString += "/" + ((SystemUserSessionDao) request.getSession().getAttribute("UserSession")).getUser().getUsername();
        }
        return idString + "]";
    }

    protected ProxyController getProxyController() {
        return this.proxyController;
    }
}
