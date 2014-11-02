package net.ctrdn.talk.sip;

import net.ctrdn.talk.exception.TalkSipHeaderRewriteException;
import com.mongodb.BasicDBObject;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TooManyListenersException;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionUnavailableException;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ServerHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ConfigurationException;
import net.ctrdn.talk.exception.InitializationException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.system.SipExtensionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipServer {

    private final static String USER_AGENT = "Talk Telephony Server/1.0.0";
    private final Logger logger = LoggerFactory.getLogger(SipServer.class);
    private final ProxyController proxyController;
    private final SipFactory sipFactory;
    private final SipStack sipStack;
    private final MessageFactory sipMessageFactory;
    private final HeaderFactory sipHeaderFactory;
    private final AddressFactory sipAddressFactory;
    private Date startDate;
    private SipProvider sipProvider = null;
    private SipProviderListener sipProviderListener;
    private final String sipDomain;
    private final int sipPort;
    private final String sipTransport;
    private final List<SipRegistration> sipRegistrationList = new ArrayList<>();
    private final List<SipSession> sipSessionList = new ArrayList<>();

    public SipServer(ProxyController proxyController) throws ConfigurationException, InitializationException {
        try {
            this.proxyController = proxyController;
            this.proxyController.configurationPropertyExists("talk.sip.server.enabled");
            this.proxyController.configurationPropertyExists("talk.sip.server.domain");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.host");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.port");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.transport");

            this.sipDomain = this.proxyController.getConfiguration().getProperty("talk.sip.server.domain");
            this.sipPort = Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.port"));
            this.sipTransport = this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.transport");

            this.sipFactory = SipFactory.getInstance();
            this.sipFactory.setPathName("gov.nist");
            Properties sipStackProperties = new Properties();
            sipStackProperties.setProperty("javax.sip.STACK_NAME", this.sipDomain);
            this.sipStack = this.sipFactory.createSipStack(sipStackProperties);
            this.sipMessageFactory = this.sipFactory.createMessageFactory();
            this.sipHeaderFactory = this.sipFactory.createHeaderFactory();
            this.sipAddressFactory = this.sipFactory.createAddressFactory();
        } catch (PeerUnavailableException ex) {
            throw new InitializationException("Failed to initialize SIP stack", ex);
        }
    }

    public void start() throws InitializationException {
        try {
            this.startDate = new Date();
            boolean enabled = this.proxyController.getConfiguration().getProperty("talk.sip.server.enabled").equals("true");
            if (enabled) {
                this.sipProviderListener = new SipProviderListener(this.proxyController, this);
                ListeningPoint lp = this.sipStack.createListeningPoint(this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.host"), Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.port")), this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.transport"));
                this.sipProvider = this.sipStack.createSipProvider(lp);
                this.getSipProvider().addSipListener(this.sipProviderListener);
            }
        } catch (InvalidArgumentException | NumberFormatException | TransportNotSupportedException | ObjectInUseException | TooManyListenersException ex) {
            throw new InitializationException("Failed to start SIP server", ex);
        }
    }

    public void sendResponse(ServerTransaction st, Response response) throws TalkSipServerException {
        try {
            this.logger.trace("Sending stateful SIP response using pre-set server transaction\n" + response.toString());
            st.sendResponse(response);
        } catch (SipException | InvalidArgumentException ex) {
            throw new TalkSipServerException("Failed to send response", ex);
        }
    }

    public void sendResponse(RequestEvent requestEvent, Response response) throws TalkSipServerException {
        try {
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = this.sipProvider.getNewServerTransaction(requestEvent.getRequest());
            }
            this.logger.trace("Sending stateful SIP response\n" + response.toString());
            st.sendResponse(response);
        } catch (SipException | InvalidArgumentException ex) {
            throw new TalkSipServerException("Failed to send response", ex);
        }
    }

    private SipExtensionDao resolvePrimaryExtensionDao(SipRegistration sipRegistration) {
        BasicDBObject extensionSearchCriteria = new BasicDBObject();
        extensionSearchCriteria.put("Enabled", true);
        extensionSearchCriteria.put("Primary", true);
        extensionSearchCriteria.put("TargetType", "SipAccount");
        extensionSearchCriteria.put("SipAccountObjectId", sipRegistration.getSipAccountDao().getObjectId());
        SipExtensionDao extensionDao = DatabaseObjectFactory.getInstance().find(SipExtensionDao.class, extensionSearchCriteria);
        return extensionDao;
    }

    public void rewriteUserAgentHeader(Request request) throws TalkSipServerException {
        try {
            List<String> userAgentList = new ArrayList<>();
            userAgentList.add(SipServer.USER_AGENT);
            UserAgentHeader contactHeader = this.getSipHeaderFactory().createUserAgentHeader(userAgentList);
            request.setHeader(contactHeader);
            this.logger.debug("Rewrote User-Agent header on {} request", request.getMethod());
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to rewrite User-Agent header", ex);
        }
    }

    public void attachUserAgentAndServerHeader(Response response) throws TalkSipServerException {
        try {
            List<String> userAgentList = new ArrayList<>();
            userAgentList.add(SipServer.USER_AGENT);
            UserAgentHeader contactHeader = this.getSipHeaderFactory().createUserAgentHeader(userAgentList);
            response.setHeader(contactHeader);
            ServerHeader serverHeader = this.getSipHeaderFactory().createServerHeader(userAgentList);
            response.setHeader(serverHeader);
            this.logger.debug("Rewrote User-Agent header on response {} {}", response.getStatusCode(), response.getReasonPhrase());
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to rewrite User-Agent header", ex);
        }
    }

    public void rewriteContactHeader(Request request, SipRegistration sipRegistration) throws TalkSipServerException {
        try {
            SipExtensionDao extensionDao = this.resolvePrimaryExtensionDao(sipRegistration);
            if (extensionDao != null) {
                SipURI sipUri = this.getSipAddressFactory().createSipURI(extensionDao.getExtension(), this.getSipDomain());
                sipUri.setPort(this.getSipPort());
                sipUri.setTransportParam(this.sipTransport);
                Address address = this.getSipAddressFactory().createAddress(sipRegistration.getSipAccountDao().getSystemUserDao().getDisplayName(), sipUri);
                ContactHeader contactHeader = this.getSipHeaderFactory().createContactHeader(address);
                request.setHeader(contactHeader);
                this.logger.debug("Rewrote Contact header on {} request", request.getMethod());
            } else {
                this.logger.debug("Failed to rewrite Contact header on {} request, no primary extension found", request.getMethod());
                throw new TalkSipHeaderRewriteException("No primary extension defined");
            }
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to rewrite Contact header", ex);
        }
    }

    public void rewriteContactHeader(Response response, SipRegistration sipRegistration) throws TalkSipServerException {
        try {
            SipExtensionDao extensionDao = this.resolvePrimaryExtensionDao(sipRegistration);
            if (extensionDao != null) {
                SipURI sipUri = this.getSipAddressFactory().createSipURI(extensionDao.getExtension(), this.getSipDomain());
                sipUri.setPort(this.getSipPort());
                sipUri.setTransportParam(this.sipTransport);
                Address address = this.getSipAddressFactory().createAddress(sipRegistration.getSipAccountDao().getSystemUserDao().getDisplayName(), sipUri);
                ContactHeader contactHeader = this.getSipHeaderFactory().createContactHeader(address);
                response.setHeader(contactHeader);
                this.logger.debug("Rewrote Contact header on response {} {}", response.getStatusCode(), response.getReasonPhrase());
            } else {
                this.logger.debug("Failed to rewrite Contact header on response {} {}, no primary extension found", response.getStatusCode(), response.getReasonPhrase());
                throw new TalkSipHeaderRewriteException("No primary extension defined");
            }
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to rewrite Contact header", ex);
        }
    }

    public void rewriteFromHeader(Request request, SipRegistration sipRegistration) throws TalkSipServerException {
        try {
            SipExtensionDao extensionDao = this.resolvePrimaryExtensionDao(sipRegistration);
            if (extensionDao != null) {
                FromHeader fh = (FromHeader) request.getHeader(FromHeader.NAME);
                SipUri fhSipUri = (SipUri) fh.getAddress().getURI();
                fhSipUri.setHost(this.getSipDomain());
                fhSipUri.setUser(extensionDao.getExtension());
                fh.getAddress().setDisplayName(sipRegistration.getSipAccountDao().getSystemUserDao().getDisplayName());
                this.logger.debug("Rewrote From header on {} request", request.getMethod());
            } else {
                this.logger.debug("Failed to rewrite From header on {} request, no primary extension found", request.getMethod());
                throw new TalkSipHeaderRewriteException("No primary extension defined");
            }
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to rewrite From header", ex);
        }
    }

    public void attachProxyViaHeader(Request request) throws ParseException, TalkSipServerException {
        try {
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = this.getSipHeaderFactory().createViaHeader(this.getSipDomain(), this.getSipPort(), this.getSipTransport(), "z9hG4bK-talkd-" + Long.toString(this.startDate.getTime()));
            request.addFirst(viaHeader);
        } catch (InvalidArgumentException | ParseException | SipException ex) {
            throw new TalkSipServerException("Failed to fix-up Via header on request", ex);
        }
    }

    public void attachProxyViaHeader(Response response) throws ParseException, TalkSipServerException {
        try {
            response.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = this.getSipHeaderFactory().createViaHeader(this.getSipDomain(), this.getSipPort(), this.getSipTransport(), "z9hG4bK-talkd-" + Long.toString(this.startDate.getTime()));
            response.addFirst(viaHeader);
        } catch (InvalidArgumentException | ParseException | SipException ex) {
            throw new TalkSipServerException("Failed to fix-up Via header on response", ex);
        }
    }

    public void attachTargetViaHeader(Response response, Request sourceRequest) throws TalkSipServerException {
        try {
            response.removeHeader(ViaHeader.NAME);
            response.addFirst((ViaHeader) sourceRequest.getHeader(ViaHeader.NAME));
        } catch (SipException ex) {
            throw new TalkSipServerException("Failed to fix-up Via header on response", ex);
        }
    }

    public ServerTransaction getServerTransaction(RequestEvent requestEvent) throws TransactionAlreadyExistsException, TransactionUnavailableException {
        return requestEvent.getServerTransaction() == null ? this.getSipProvider().getNewServerTransaction(requestEvent.getRequest()) : requestEvent.getServerTransaction();
    }

    public void forwardAck(Response response, ClientTransaction transaction) throws TalkSipServerException {
        try {
            Request ackRequest = transaction.getDialog().createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
            this.logger.trace("Forwarding ACK request\n{}", ackRequest.toString());
            transaction.getDialog().sendAck(ackRequest);
        } catch (InvalidArgumentException | SipException ex) {
            throw new TalkSipServerException("Failed to forward ACK", ex);
        }
    }

    public void forwardRequest(SipRegistration sourceRegistration, SipRegistration targetRegistration, Request request) throws TalkSipServerException {
        try {
            if (sourceRegistration == null || targetRegistration == null) {
                throw new TalkSipServerException("Cannot forward request with no source and target provided");
            }

            this.attachProxyViaHeader(request);

            SipUri requestUri = (SipUri) request.getRequestURI();
            requestUri.setHost(targetRegistration.getRemoteHost());
            requestUri.setPort(targetRegistration.getRemotePort());

            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
            SipUri toHeaderSipUri = (SipUri) toHeader.getAddress().getURI();
            toHeaderSipUri.setUser(targetRegistration.getSipAccountDao().getUsername());
            toHeaderSipUri.setPort(this.getSipPort());
            toHeader.getAddress().setDisplayName(targetRegistration.getSipAccountDao().getUsername());

            this.logger.trace("Statefully forwarding SIP request from {}@{} to {}@{}\n{}", sourceRegistration.getSipAccountDao().getUsername(), this.getSipDomain(), targetRegistration.getSipAccountDao().getUsername(), this.getSipDomain(), request.toString());

            ClientTransaction ct = this.sipProvider.getNewClientTransaction(request);
            ct.getDialog();
            ct.sendRequest();
        } catch (ParseException | SipException ex) {
            throw new TalkSipServerException("Failed to prepare request forward", ex);
        }
    }

    public void sendNotFound(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            Response response = this.getSipMessageFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest());
            this.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed creating response", ex);
        }
    }

    public void sendTemporarilyUnavailable(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            Response response = this.getSipMessageFactory().createResponse(Response.TEMPORARILY_UNAVAILABLE, requestEvent.getRequest());
            this.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed creating response", ex);
        }
    }

    public void sendServiceUnavailable(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            Response response = this.getSipMessageFactory().createResponse(Response.SERVICE_UNAVAILABLE, requestEvent.getRequest());
            this.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed creating response", ex);
        }
    }

    public void sendNotImplemented(RequestEvent requestEvent) throws TalkSipServerException {
        try {
            Response niResponse = this.sipMessageFactory.createResponse(Response.NOT_IMPLEMENTED, requestEvent.getRequest());
            this.sendResponse(requestEvent, niResponse);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed to send response", ex);
        }
    }

    public MessageFactory getSipMessageFactory() {
        return sipMessageFactory;
    }

    public HeaderFactory getSipHeaderFactory() {
        return sipHeaderFactory;
    }

    public AddressFactory getSipAddressFactory() {
        return sipAddressFactory;
    }

    public SipProvider getSipProvider() {
        return sipProvider;
    }

    public String getSipDomain() {
        return sipDomain;
    }

    public SipRegistration getSipRegistration(String host, int port) {
        for (SipRegistration session : this.getSipRegistrationList()) {
            if (session.getRemoteHost().equals(host) && session.getRemotePort() == port) {
                return session;
            }
        }
        return null;
    }

    public void removeSipRegistration(SipRegistration session) {
        this.getSipRegistrationList().remove(session);
    }

    public void addSipRegistration(SipRegistration session) {
        this.getSipRegistrationList().add(session);
    }

    public List<SipSession> getSipSessionList() {
        return this.sipSessionList;
    }

    public List<SipRegistration> getSipRegistrationList() {
        return sipRegistrationList;
    }

    public int getSipPort() {
        return sipPort;
    }

    public String getSipTransport() {
        return sipTransport;
    }
}
