package net.ctrdn.talk.sip;

import com.mongodb.BasicDBObject;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.address.Address;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipRegistrationException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SipExtensionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipRegistration {

    // internal
    private final Logger logger = LoggerFactory.getLogger(SipRegistration.class);
    private final SipServer sipServer;
    private final boolean forceRegistrationTimeout;
    private final int forcedRegistrationTimeout;

    // state description
    private final List<SipExtensionDao> registeredExtensionDaoList = new ArrayList<>();
    private final List<ContactHeader> registeredContactHeaderList = new ArrayList<>();
    private String remoteHost;
    private int remotePort;
    private SipRegistrationAuthenticationState state = SipRegistrationAuthenticationState.STARTED;
    private Date lastRegisterResponseSendDate;
    private SipAccountDao sipAccountDao = null;
    private Integer activeExpireTime = null;

    // helpers
    private final DigestServerAuthenticationHelper authenticationHelper;

    public SipRegistration(SipServer sipServer, String remoteHost, int remotePort) throws TalkSipRegistrationException {
        try {
            this.sipServer = sipServer;
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
            this.authenticationHelper = new DigestServerAuthenticationHelper();
            this.forceRegistrationTimeout = this.sipServer.getProxyController().getConfiguration().getProperty("talk.sip.register.force-timeout", "true").equals("true");
            this.forcedRegistrationTimeout = Integer.parseInt(this.sipServer.getProxyController().getConfiguration().getProperty("talk.sip.register.force-timeout.time", "60"));
            if (this.forceRegistrationTimeout) {
                this.logger.info("Registration timeout is forced to {} seconds", this.forcedRegistrationTimeout);
                if (this.forcedRegistrationTimeout < 60) {
                    throw new RuntimeException(new TalkSipServerException("Minimum for forced registration interval is 60 seconds"));
                }
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new TalkSipRegistrationException("Failed to create SIP session", ex);
        }
    }

    public void registerReceived(RequestEvent requestEvent) throws TalkSipRegistrationException, TalkSipServerException {
        try {
            if (requestEvent.getRequest().getExpires() != null && requestEvent.getRequest().getExpires().getExpires() == 0) {
                this.unregister(requestEvent);
            } else {
                ContactHeader contactHeader = (ContactHeader) requestEvent.getRequest().getHeader(ContactHeader.NAME);
                if (contactHeader.getExpires() == 0) {
                    this.unregister(requestEvent);
                }
            }
            switch (this.getState()) {
                case STARTED: {
                    Response challengeResponse = this.getSipServer().getSipMessageFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                    this.authenticationHelper.generateChallenge(this.getSipServer().getSipHeaderFactory(), challengeResponse, this.getSipServer().getSipDomain());
                    this.sipServer.sendResponse(requestEvent, challengeResponse);
                    this.logger.debug("Challenging remote " + this.getRemoteHost() + ":" + this.getRemotePort());
                    this.state = SipRegistrationAuthenticationState.CHALLENGED;
                    break;
                }
                case CHALLENGED: {
                    ProxyAuthorizationHeader proxyAuthHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);
                    String lookupUsername = proxyAuthHeader.getUsername();
                    String lookupRealm = proxyAuthHeader.getRealm();
                    if (!lookupRealm.equals(this.sipServer.getSipDomain())) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("Attempting to authenticate with unknown realm " + lookupRealm);
                    }
                    SipAccountDao lookupAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", lookupUsername));
                    if (lookupAccountDao == null) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("User not found in database: " + lookupUsername);
                    } else if (!lookupAccountDao.getEnabled()) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("Account is disabled: " + lookupUsername);
                    }
                    boolean authStatus = this.authenticationHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), this.generateDigestResponseMd5(lookupUsername, lookupRealm, lookupAccountDao.getPlaintextPassword()));
                    if (!authStatus) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("Invalid password for user " + lookupUsername);
                    }
                    this.sipAccountDao = lookupAccountDao;
                    this.state = SipRegistrationAuthenticationState.AUTHENTICATED;

                    this.registeredContactHeaderList.clear();
                    BasicDBObject extensionSearchCriteria = new BasicDBObject("TargetType", "SipAccount");
                    extensionSearchCriteria.put("SipAccountObjectId", this.getSipAccountDao().getObjectId());
                    extensionSearchCriteria.put("Enabled", true);

                    ContactHeader defaultContactHeader = this.sipServer.getSipHeaderFactory().createContactHeader(this.sipServer.getSipAddressFactory().createAddress(this.sipAccountDao.getSystemUserDao().getDisplayName(), this.sipServer.getSipAddressFactory().createSipURI(this.sipAccountDao.getUsername(), this.sipServer.getSipDomain())));
                    this.registeredContactHeaderList.add(defaultContactHeader);
                    for (SipExtensionDao sed : DatabaseObjectFactory.getInstance().list(SipExtensionDao.class, extensionSearchCriteria)) {
                        SipUri proxyUri = new SipUri();
                        proxyUri.setHost(this.getSipServer().getSipDomain());
                        proxyUri.setUser(sed.getExtension());
                        ContactHeader contactHeader = this.getSipServer().getSipHeaderFactory().createContactHeader(this.getSipServer().getSipAddressFactory().createAddress(this.getSipAccountDao().getSystemUserDao().getDisplayName(), proxyUri));
                        this.registeredContactHeaderList.add(contactHeader);
                        this.getRegisteredExtensionDaoList().add(sed);
                    }

                    this.sendResponseOk(requestEvent, true);
                    this.getSipAccountDao().setLastLoginDate(new Date());
                    this.getSipAccountDao().store();
                    this.logger.info("Remote " + this.getRemoteHost() + ":" + this.getRemotePort() + " has successfully authenticated as " + lookupUsername + "@" + lookupRealm);
                    break;
                }
                case AUTHENTICATED: {
                    int rxExpireTime = requestEvent.getRequest().getExpires().getExpires();
                    if (this.forceRegistrationTimeout) {
                        this.activeExpireTime = this.forcedRegistrationTimeout;
                    } else {
                        this.activeExpireTime = rxExpireTime;
                    }
                    this.sendResponseOk(requestEvent, true);
                    this.logger.debug("Updated contact list for  " + this.getSipAccountDao().getUsername() + "@" + this.getSipServer().getSipDomain() + " at " + this.getRemoteHost() + ":" + this.getRemotePort());
                    break;
                }
            }
        } catch (ParseException | TalkSipServerException | NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new TalkSipServerException("Failed processing REGISTER request", ex);
        }
    }

    public void sessionRequestReceived(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            if (this.getState() != SipRegistrationAuthenticationState.AUTHENTICATED) {
                this.logger.info("Cannot process session request on unauthenticated client");
            } else {
                ToHeader toHeader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                CallIdHeader callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                SipSession sipSession = null;
                for (SipSession session : this.sipServer.getSipSessionList()) {
                    if (session.getState() != SipSessionState.ENDED && ((session.getCallerRegistration() == this && session.getCalleeRegistration().isAvailableAtAddress(toHeader.getAddress()))
                            || (callIdHeader != null && session.getCallIdHeader() != null && session.getCallIdHeader().getCallId().equals(callIdHeader.getCallId())))) {
                        sipSession = session;
                        break;
                    }
                }
                if (sipSession == null) {
                    SipExtensionDao extensionLookup = DatabaseObjectFactory.getInstance().find(SipExtensionDao.class, new BasicDBObject("Extension", ((SipUri) toHeader.getAddress().getURI()).getUser()));
                    if (extensionLookup == null) {
                        this.sipServer.sendNotFound(requestEvent);
                        this.logger.info("Callee not found " + toHeader.getAddress().toString());
                    } else if (!extensionLookup.isEnabled()) {
                        this.sipServer.sendTemporarilyUnavailable(requestEvent);
                        this.logger.info("Callee extension is not enabled " + toHeader.getAddress().toString());
                    } else {
                        SipRegistration calleeRegistration = null;
                        for (SipRegistration reg : this.sipServer.getSipRegistrationList()) {
                            if (reg.isAvailableAtAddress(toHeader.getAddress())) {
                                calleeRegistration = reg;
                                break;
                            }
                        }
                        if (calleeRegistration == null) {
                            this.sipServer.sendTemporarilyUnavailable(requestEvent);
                            this.logger.info("Callee is not registered " + toHeader.getAddress().toString());
                        } else if (this == calleeRegistration) {
                            this.sipServer.sendServiceUnavailable(requestEvent);
                            this.logger.info("Caller attempted to call himself " + toHeader.getAddress().toString());
                        } else {
                            sipSession = new SipSession(this, calleeRegistration, extensionLookup);
                            this.sipServer.getSipSessionList().add(sipSession);
                        }
                    }
                }
                if (sipSession != null) {
                    sipSession.sessionRequestReceived(requestEvent);
                }
            }
        } catch (TalkSipServerException | TalkSipSessionException ex) {
            throw new TalkSipServerException("Failed processing session request", ex);
        }
    }

    private void unregister(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            Response okResponse = this.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            this.sipServer.sendResponse(requestEvent, okResponse);
            this.getSipServer().removeSipRegistration(this);
            this.logger.info("User " + this.getSipAccountDao().getUsername() + "@" + this.getSipServer().getSipDomain() + " unregistered from " + this.getRemoteHost() + ":" + this.getRemotePort());
            this.state = SipRegistrationAuthenticationState.UNREGISTERED;
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error unregistering", ex);
        }
    }

    public void timedOut() {
        this.logger.info("Registration for {} timed out", this.sipAccountDao.getUsername());
        this.state = SipRegistrationAuthenticationState.UNREGISTERED;
        this.getSipServer().removeSipRegistration(this);
    }

    private void sendResponseOk(RequestEvent requestEvent, boolean updateContactList) throws TalkSipServerException {
        try {
            Response okResponse = this.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            if (this.forceRegistrationTimeout) {
                okResponse.setExpires(this.sipServer.getSipHeaderFactory().createExpiresHeader(this.forcedRegistrationTimeout));
            }
            if (updateContactList) {
                for (ContactHeader ch : this.registeredContactHeaderList) {
                    okResponse.addHeader(ch);
                }
            }
            this.sipServer.sendResponse(requestEvent, okResponse);
            this.lastRegisterResponseSendDate = new Date();
        } catch (ParseException | InvalidArgumentException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void sendResponseUnauthorized(RequestEvent requestEvent) throws TalkSipServerException, ParseException {
        Response response = this.sipServer.getSipMessageFactory().createResponse(Response.UNAUTHORIZED, requestEvent.getRequest());
        this.sipServer.sendResponse(requestEvent, response);

    }

    private String generateDigestResponseMd5(String username, String realm, String plaintextPassword) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest((username + ":" + realm + ":" + plaintextPassword).getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < hashBytes.length; i++) {
            if ((0xff & hashBytes[i]) < 0x10) {
                hexString.append("0").append(Integer.toHexString((0xFF & hashBytes[i])));
            } else {
                hexString.append(Integer.toHexString(0xFF & hashBytes[i]));
            }
        }
        return hexString.toString();
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public SipRegistrationAuthenticationState getState() {
        return state;
    }

    public SipServer getSipServer() {
        return sipServer;
    }

    public boolean isAvailableAtAddress(Address address) {
        for (ContactHeader ch : this.registeredContactHeaderList) {
            SipUri su = (SipUri) ch.getAddress().getURI();
            SipUri su2 = (SipUri) address.getURI();
            if (su.getHost().equals(su2.getHost()) && su.getUser().equals(su2.getUser())) {
                return true;
            }
        }
        return false;
    }

    public SipAccountDao getSipAccountDao() {
        return sipAccountDao;
    }

    public Date getLastRegisterResponseSendDate() {
        return lastRegisterResponseSendDate;
    }

    public Integer getActiveExpireTime() {
        return activeExpireTime;
    }

    public List<SipExtensionDao> getRegisteredExtensionDaoList() {
        return registeredExtensionDaoList;
    }
}
