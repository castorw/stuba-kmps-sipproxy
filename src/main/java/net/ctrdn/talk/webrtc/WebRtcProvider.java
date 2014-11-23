package net.ctrdn.talk.webrtc;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.dao.SystemUserDao;
import net.ctrdn.talk.exception.ConfigurationException;
import net.ctrdn.talk.exception.InitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebRtcProvider {

    public List<WebRtcSession> getSessionList() {
        return sessionList;
    }

    private class CleanupWorker implements Runnable {

        private boolean running = true;
        private final WebRtcProvider provider = WebRtcProvider.this;

        @Override
        public void run() {
            try {
                while (this.running) {
                    Date currentDate = new Date();
                    for (WebRtcPresence presence : provider.getPresenceList()) {
                        if (currentDate.getTime() - presence.getLastPresentMessage().getTime() > provider.getPresenceAcknowledgeTimeout()) {
                            provider.getPresenceList().remove(presence);
                            provider.logger.info("WebRTC presence for {} has timed out", presence.getSystemUserDao().getDisplayName());
                        }
                    }
                    for (WebRtcSession session : provider.getSessionList()) {
                        if (currentDate.getTime() - session.getLastUpdateDate().getTime() > provider.getSessionTimeout() && session.getState() != WebRtcSessionState.ESTABLISHED) {
                            provider.getSessionList().remove(session);
                            provider.logger.info("WebRTC session between {} and {} has timed out in state {}", session.getCallerDao().getDisplayName(), session.getCalleeDao().getDisplayName(), session.getState());
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                provider.logger.error("Presence cleanup has been interrupted", ex);
            }
        }
    }

    private final Logger logger = LoggerFactory.getLogger(WebRtcProvider.class);
    private final ProxyController proxyController;
    private final List<WebRtcPresence> presenceList = new CopyOnWriteArrayList<>();
    private final List<WebRtcSession> sessionList = new CopyOnWriteArrayList<>();
    private int presenceAcknowledgeTimeout;
    private int sessionTimeout;
    private CleanupWorker cleanupWorker;
    private Thread cleanupWorkerThread;

    public WebRtcProvider(ProxyController proxyController) throws InitializationException {
        try {
            this.proxyController = proxyController;
            this.proxyController.configurationPropertyExists("talk.webrtc.presence.ack-timeout");
            this.proxyController.configurationPropertyExists("talk.webrtc.session.timeout");
            this.presenceAcknowledgeTimeout = Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.webrtc.presence.ack-timeout"));
            this.sessionTimeout = Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.webrtc.session.timeout"));
        } catch (ConfigurationException ex) {
            throw new InitializationException("Failed to initialize WebRTC provider", ex);
        }
    }

    public void start() {
        this.cleanupWorker = new CleanupWorker();
        this.cleanupWorkerThread = new Thread(this.cleanupWorker);
        this.cleanupWorkerThread.start();
        this.logger.info("WebRTC provider is now available");
    }

    public WebRtcSession createSession(SystemUserDao callerDao, SystemUserDao calleeDao) {
        WebRtcSession session = new WebRtcSession(callerDao, calleeDao);
        this.sessionList.add(session);
        return session;
    }

    public void presentMessageReceived(SystemUserDao systemUserDao) {
        for (WebRtcPresence presence : this.getPresenceList()) {
            if (presence.getSystemUserDao().getObjectId().equals(systemUserDao.getObjectId())) {
                presence.presentMessageReceived();
                return;
            }
        }
        this.presenceList.add(new WebRtcPresence(this, systemUserDao));
    }

    public int getPresenceAcknowledgeTimeout() {
        return presenceAcknowledgeTimeout;
    }

    public List<WebRtcPresence> getPresenceList() {
        return presenceList;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

}
