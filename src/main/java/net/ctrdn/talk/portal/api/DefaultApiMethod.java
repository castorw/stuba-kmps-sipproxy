package net.ctrdn.talk.portal.api;

import java.util.Date;
import javax.json.JsonObjectBuilder;

abstract public class DefaultApiMethod implements ApiMethod {

    private final String path;

    protected DefaultApiMethod(String path) {
        this.path = path;
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
}
