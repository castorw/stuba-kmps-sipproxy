package net.ctrdn.talk.sip;

import com.mongodb.BasicDBObject;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.dao.SipAccountDao;
import net.ctrdn.talk.dao.SipExtensionDao;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipRegistrationException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipProviderListener implements SipListener {

    private final ExecutorService requestExecutorService;
    private final Logger logger = LoggerFactory.getLogger(SipProviderListener.class);
    private final SipServer sipServer;

    // helpers
    private final DigestServerAuthenticationHelper authenticationHelper;

    public SipProviderListener(ProxyController proxyController, SipServer sipServer) throws TalkSipServerException {
        try {
            this.authenticationHelper = new DigestServerAuthenticationHelper();
            this.sipServer = sipServer;
            ThreadFactory threadFactory = new ThreadFactory() {

                private int id = 1;

                @Override
                public Thread newThread(Runnable r) {
                    Thread nt = new Thread(r);
                    nt.setDaemon(true);
                    nt.setName("SipEventProcessor-" + this.id);
                    this.id++;
                    return nt;
                }
            };
            this.requestExecutorService = Executors.newFixedThreadPool(16, threadFactory);
        } catch (NoSuchAlgorithmException ex) {
            throw new TalkSipServerException("Failed to start provider listener", ex);
        }
    }

    @Override
    public void processRequest(final RequestEvent requestEvent) {
        this.requestExecutorService.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    SipProviderListener.this.logger.trace("Received SIP message\n" + requestEvent.getRequest().toString());
                    switch (requestEvent.getRequest().getMethod()) {
                        case "REGISTER": {
                            SipProviderListener.this.processRegister(requestEvent);
                            break;
                        }
                        case "BYE":
                        case "ACK":
                        case "CANCEL":
                        case "INVITE": {
                            SipProviderListener.this.processSessionRequest(requestEvent);
                            break;
                        }
                        default: {
                            SipProviderListener.this.sipServer.sendNotImplemented(requestEvent);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    SipProviderListener.this.logger.warn("Error processing SIP request", ex);
                }
            }
        });

    }

    @Override
    public void processResponse(final ResponseEvent responseEvent) {
        this.requestExecutorService.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    SipProviderListener.this.logger.trace("Received SIP response\n" + responseEvent.getResponse().toString());
                    CallIdHeader callIdHeader = (CallIdHeader) responseEvent.getResponse().getHeader(CallIdHeader.NAME);
                    for (SipSession session : SipProviderListener.this.sipServer.getSipSessionList()) {
                        if (session.getCallIdHeader().getCallId().equals(callIdHeader.getCallId())) {
                            session.sessionResponseReceived(responseEvent);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    SipProviderListener.this.logger.warn("Error processing SIP response", ex);
                }
            }
        });
    }

    @Override

    public void processTimeout(TimeoutEvent timeoutEvent) {
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
    }

    private SipRegistration lookupRegistration(Request request) throws TalkSipRegistrationException {
        return this.lookupRegistration(request, true);
    }

    private SipRegistration lookupRegistration(Request request, boolean createNew) throws TalkSipRegistrationException {
        SipRegistration registration;
        FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
        ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
        registration = this.sipServer.getSipRegistration(((SipURI) fromHeader.getAddress().getURI()).getUser());
        if (registration == null && createNew) {
            this.logger.debug("Creating new SIP registration for {}:{}", viaHeader.getHost(), (viaHeader.getPort() == -1) ? 5060 : viaHeader.getPort());
            String host = viaHeader.getHost();
            int port = (viaHeader.getPort() == -1) ? 5060 : viaHeader.getPort();
            if (viaHeader.getReceived() != null) {
                host = viaHeader.getReceived();
            }
            if (viaHeader.getRPort() != -1) {
                port = (viaHeader.getRPort() == 0) ? 5060 : viaHeader.getRPort();
            }

            registration = new SipRegistration(sipServer, host, port);
            this.sipServer.addSipRegistration(registration);
        }
        return registration;
    }

    private void processRegister(RequestEvent requestEvent) throws TalkSipRegistrationException {
        SipRegistration registration = null;
        try {
            registration = this.lookupRegistration(requestEvent.getRequest());
            registration.registerReceived(requestEvent);
        } catch (TalkSipServerException ex) {
            this.logger.warn("Failed to parse incoming header", ex);
        } catch (TalkSipRegistrationException ex) {
            if (registration != null) {
                this.sipServer.removeSipRegistration(registration);
            }
            this.logger.warn("Error processing REGISTER: " + ex.getMessage());
        }
    }

    private void processSessionRequest(RequestEvent requestEvent) {
        try {
            CallIdHeader callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
            SipSession sipSession = null;
            for (SipSession session : this.sipServer.getSipSessionList()) {
                if (session.getState() != SipSessionState.ENDED && (callIdHeader != null && session.getCallIdHeader() != null && session.getCallIdHeader().getCallId().equals(callIdHeader.getCallId()))) {
                    sipSession = session;
                    break;
                }
            }
            if (sipSession == null) {
                if (!requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
                    throw new TalkSipSessionException("Cannot process non-INVITE request on non-existent session");
                }
                ToHeader toHeader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
                ViaHeader viaHeader = (ViaHeader) requestEvent.getRequest().getHeader(ViaHeader.NAME);
                ProxyAuthorizationHeader proxyAuthHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);
                String callerHost = (viaHeader.getReceived() == null) ? viaHeader.getHost() : viaHeader.getReceived();
                int callerPort = (viaHeader.getRPort() == - 1) ? viaHeader.getPort() : viaHeader.getRPort();

                if (proxyAuthHeader == null) {
                    Response challengeResponse = this.sipServer.getSipMessageFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                    this.authenticationHelper.generateChallenge(this.sipServer.getSipHeaderFactory(), challengeResponse, this.sipServer.getSipDomain());
                    this.sipServer.sendResponse(requestEvent, challengeResponse);
                    this.logger.debug("Challenging remote " + callerHost + ":" + callerPort + " on INVITE");
                } else {
                    String lookupUsername = proxyAuthHeader.getUsername();
                    String lookupRealm = proxyAuthHeader.getRealm();
                    if (!lookupRealm.equals(this.sipServer.getSipDomain())) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipSessionException("Attempting to authenticate with unknown realm " + lookupRealm);
                    }
                    SipAccountDao callerSipAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", lookupUsername));

                    if (callerSipAccountDao == null || !callerSipAccountDao.getEnabled()) {
                        this.sipServer.sendNotFound(requestEvent);
                        this.logger.info("Caller not found " + fromHeader.getAddress().toString());
                    } else {
                        boolean authStatus = this.authenticationHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), this.generateDigestResponseMd5(lookupUsername, lookupRealm, callerSipAccountDao.getPlaintextPassword()));
                        if (!authStatus) {
                            this.sendResponseUnauthorized(requestEvent);
                            throw new TalkSipSessionException("Invalid password for user " + lookupUsername);
                        }

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
                            } else if (calleeRegistration.getSipAccountDao().getObjectId().equals(callerSipAccountDao.getObjectId())) {
                                this.sipServer.sendServiceUnavailable(requestEvent);
                                this.logger.info("Caller attempted to call himself " + toHeader.getAddress().toString());
                            } else {
                                sipSession = new SipSession(this.sipServer, callerSipAccountDao, callerHost, callerPort, calleeRegistration.getSipAccountDao(), calleeRegistration.getRemoteHost(), calleeRegistration.getRemotePort(), extensionLookup);
                                this.sipServer.getSipSessionList().add(sipSession);
                            }
                        }
                    }
                }
            }
            if (sipSession != null) {
                sipSession.sessionRequestReceived(requestEvent);
            }
        } catch (TalkSipServerException | TalkSipSessionException | ParseException | NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            this.logger.warn("Error processing session request", ex);
        }
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

    private void sendResponseUnauthorized(RequestEvent requestEvent) throws TalkSipServerException, ParseException {
        Response response = this.sipServer.getSipMessageFactory().createResponse(Response.UNAUTHORIZED, requestEvent.getRequest());
        this.sipServer.sendResponse(requestEvent, response);
    }

    public void stop() {
        this.requestExecutorService.shutdownNow();
    }
}
