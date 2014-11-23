package net.ctrdn.talk.webrtc;

import java.util.Date;
import net.ctrdn.talk.dao.SystemUserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebRtcPresence {

    private final Logger logger = LoggerFactory.getLogger(WebRtcPresence.class);
    private final SystemUserDao systemUserDao;
    private final WebRtcProvider webRtcProvider;
    private Date lastPresentMessage = new Date();

    public WebRtcPresence(WebRtcProvider provider, SystemUserDao systemUserDao) {
        this.webRtcProvider = provider;
        this.systemUserDao = systemUserDao;
        this.logger.info("User {} is now present for WebRTC communication", this.systemUserDao.getDisplayName());
    }

    public void presentMessageReceived() {
        this.logger.trace("User {} acknowledged WebRTC presence", this.systemUserDao.getDisplayName());
        this.lastPresentMessage = new Date();
    }

    public Date getLastPresentMessage() {
        return lastPresentMessage;
    }

    public SystemUserDao getSystemUserDao() {
        return systemUserDao;
    }
}
