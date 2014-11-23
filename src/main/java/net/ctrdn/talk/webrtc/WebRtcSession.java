package net.ctrdn.talk.webrtc;

import java.util.Date;
import java.util.UUID;
import net.ctrdn.talk.dao.SystemUserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebRtcSession {

    private final Logger logger = LoggerFactory.getLogger(WebRtcSession.class);
    private final UUID sessionUuid = UUID.randomUUID();
    private final SystemUserDao callerDao;
    private final SystemUserDao calleeDao;
    private WebRtcSessionState state = WebRtcSessionState.STARTED;
    private String callerOfferBase64;
    private String calleeAnswerBase64;
    private String callerCandidateBase64;
    private String calleeCandidateBase64;
    private Date lastUpdateDate = new Date();
    private Date lastCallerAcknowledge = new Date();
    private Date lastCalleeAcknowledge = new Date();
    private boolean callerEstablished = false;
    private boolean calleeEstablished = false;

    protected WebRtcSession(SystemUserDao callerDao, SystemUserDao calleeDao) {
        this.callerDao = callerDao;
        this.calleeDao = calleeDao;
        this.logger.info("Opened new WebRTC session for {} and {}", callerDao.getDisplayName(), calleeDao.getDisplayName());
    }

    public void offerReceived(String offerBase64) {
        this.callerOfferBase64 = offerBase64;
        this.setState(WebRtcSessionState.OFFERED);
        this.lastUpdateDate = new Date();
    }

    public void offerDelivered() {
        this.setState(WebRtcSessionState.OFFER_DELIVERED);
        this.lastUpdateDate = new Date();
    }

    public void answerReceived(String answerBase64) {
        this.calleeAnswerBase64 = answerBase64;
        this.setState(WebRtcSessionState.ANSWERED);
        this.lastUpdateDate = new Date();
    }

    public void answerDelivered() {
        this.setState(WebRtcSessionState.ANSWER_DELIVERED);
        this.lastUpdateDate = new Date();
    }

    public void callerCandidateReceived(String candidateBase64) {
        this.callerCandidateBase64 = candidateBase64;
        this.setState(WebRtcSessionState.CALLER_CANDIDATE);
        this.lastUpdateDate = new Date();
    }

    public void callerCandidateDelivered() {
        this.setState(WebRtcSessionState.CALLER_CANDIDATE_DELIVERED);
        this.lastUpdateDate = new Date();
    }

    public void calleeCandidateReceived(String candidateBase64) {
        this.calleeCandidateBase64 = candidateBase64;
        this.setState(WebRtcSessionState.CALLEE_CANDIDATE);
        this.lastUpdateDate = new Date();
    }

    public void calleeCandidateDelivered() {
        this.setState(WebRtcSessionState.CALLEE_CANDIDATE_DELIVERED);
        this.lastUpdateDate = new Date();
    }

    public void callerEstablished() {
        this.callerEstablished = true;
        if (this.calleeEstablished) {
            this.setState(WebRtcSessionState.ESTABLISHED);
        }
        this.lastUpdateDate = new Date();
    }

    public void calleeEstablished() {
        this.calleeEstablished = true;
        if (this.callerEstablished) {
            this.setState(WebRtcSessionState.ESTABLISHED);
        }
        this.lastUpdateDate = new Date();
    }

    public void callerAcknowledge() {
        this.lastUpdateDate = new Date();
    }

    public void calleeAcknowledge() {
        this.lastUpdateDate = new Date();
    }

    public WebRtcSessionState getState() {
        return state;
    }

    public String getCallerOfferBase64() {
        return callerOfferBase64;
    }

    public String getCalleeAnswerBase64() {
        return calleeAnswerBase64;
    }

    public UUID getSessionUuid() {
        return sessionUuid;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public SystemUserDao getCallerDao() {
        return callerDao;
    }

    public SystemUserDao getCalleeDao() {
        return calleeDao;
    }

    private void setState(WebRtcSessionState state) {
        this.logger.debug("WebRTC session between {} and {} changed state to {}", this.callerDao.getDisplayName(), this.calleeDao.getDisplayName(), state.toString());
        this.state = state;
    }

    public String getCallerCandidateBase64() {
        return callerCandidateBase64;
    }

    public String getCalleeCandidateBase64() {
        return calleeCandidateBase64;
    }
}
