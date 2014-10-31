package net.ctrdn.talk.sip;

import com.mongodb.BasicDBObject;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import gov.nist.javax.sip.header.Expires;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import javax.sip.header.ContactHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import net.ctrdn.talk.system.SipAccountDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipRegistration {

    // internal
    private final Logger logger = LoggerFactory.getLogger(SipRegistration.class);
    private final SipServer sipServer;

    // state description
    private String remoteHost;
    private int remotePort;
    private SipRegistrationState state = SipRegistrationState.STARTED;
    private SipAccountDao sipAccountDao = null;

    // helpers
    private final DigestServerAuthenticationHelper authenticationHelper;

    public SipRegistration(SipServer sipServer, String remoteHost, int remotePort) throws TalkSipSessionException {
        try {
            this.sipServer = sipServer;
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
            this.authenticationHelper = new DigestServerAuthenticationHelper();
        } catch (NoSuchAlgorithmException ex) {
            throw new TalkSipSessionException("Failed to create SIP session", ex);
        }
    }

    public void registerReceived(Request request) throws TalkSipSessionException, TalkSipServerException {
        try {
            if (request.getExpires() != null && request.getExpires().getExpires() == 0) {
                this.unregister(request);
            } else {
                ContactHeader contactHeader = (ContactHeader) request.getHeader(ContactHeader.NAME);
                if (contactHeader.getExpires() == 0) {
                    this.unregister(request);
                }
            }
            switch (this.state) {
                case STARTED: {
                    Response challengeResponse = this.sipServer.getSipMessageFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, request);
                    this.authenticationHelper.generateChallenge(this.sipServer.getSipHeaderFactory(), challengeResponse, this.sipServer.getSipDomain());
                    this.sipServer.sendResponse(challengeResponse);
                    this.logger.debug("Challenging remote " + this.getRemoteHost() + ":" + this.getRemotePort());
                    this.state = SipRegistrationState.CHALLENGED;
                    break;
                }
                case CHALLENGED: {
                    ProxyAuthorizationHeader proxyAuthHeader = (ProxyAuthorizationHeader) request.getHeader(ProxyAuthorizationHeader.NAME);
                    String lookupUsername = proxyAuthHeader.getUsername();
                    String lookupRealm = proxyAuthHeader.getRealm();
                    if (!lookupRealm.equals(this.sipServer.getSipDomain())) {
                        this.sendResponseUnauthorized(request);
                        throw new TalkSipSessionException("Attempting to authenticate with unknown realm " + lookupRealm);
                    }
                    SipAccountDao lookupAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", lookupUsername));
                    if (lookupAccountDao == null) {
                        this.sendResponseUnauthorized(request);
                        throw new TalkSipSessionException("User not found in database: " + lookupUsername);
                    }
                    boolean authStatus = this.authenticationHelper.doAuthenticateHashedPassword(request, this.generateDigestResponseMd5(lookupUsername, lookupRealm, lookupAccountDao.getPlaintextPassword()));
                    if (!authStatus) {
                        this.sendResponseUnauthorized(request);
                        throw new TalkSipSessionException("Invalid password for user " + lookupUsername);
                    }
                    this.sipAccountDao = lookupAccountDao;
                    this.state = SipRegistrationState.AUTHENTICATED;
                    this.sendResponseOk(request, true);
                    this.logger.info("Remote " + this.getRemoteHost() + ":" + this.getRemotePort() + " has successfully authenticated as " + lookupUsername + "@" + lookupRealm);
                    break;
                }
                case AUTHENTICATED: {
                    this.sendResponseOk(request, true);
                    this.logger.info("Updated contact list for  " + this.sipAccountDao.getUsername() + "@" + this.sipServer.getSipDomain() + " at " + this.getRemoteHost() + ":" + this.getRemotePort());
                    break;
                }
            }
        } catch (ParseException | TalkSipServerException | NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new TalkSipServerException("Failed processing REGISTER request", ex);
        }
    }

    private void unregister(Request request) throws ParseException, TalkSipServerException {
        Response okResponse = this.sipServer.getSipMessageFactory().createResponse(Response.OK, request);
        this.sipServer.sendResponse(okResponse);
        this.sipServer.removeSipRegistration(this);
        this.logger.info("User " + this.sipAccountDao.getUsername() + "@" + this.sipServer.getSipDomain() + " unregistered from " + this.getRemoteHost() + ":" + this.getRemotePort());
        this.state = SipRegistrationState.UNREGISTERED;
    }

    private void sendResponseOk(Request request, boolean updateContactList) throws ParseException, TalkSipServerException {
        SipUri proxyUri = new SipUri();
        proxyUri.setHost(this.sipServer.getSipDomain());
        proxyUri.setUser(this.sipAccountDao.getUsername());
        Response okResponse = this.sipServer.getSipMessageFactory().createResponse(Response.OK, request);
        if (updateContactList) {
            ContactHeader contactHeader = this.sipServer.getSipHeaderFactory().createContactHeader(this.sipServer.getSipAddressFactory().createAddress(this.sipAccountDao.getSystemUserDao().getDisplayName(), proxyUri));
            okResponse.addHeader(contactHeader);
        }
        this.sipServer.sendResponse(okResponse);
    }

    private void sendResponseUnauthorized(Request request) throws TalkSipServerException, ParseException {
        Response unauthorizedResponse = this.sipServer.getSipMessageFactory().createResponse(Response.UNAUTHORIZED, request);
        this.sipServer.sendResponse(unauthorizedResponse);
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
}
