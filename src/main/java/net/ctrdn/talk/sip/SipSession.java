package net.ctrdn.talk.sip;

import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.exception.TalkSipHeaderRewriteException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipSession {

    private final Logger logger = LoggerFactory.getLogger(SipSession.class);
    private final SipRegistration callerRegistration;
    private final SipRegistration calleeRegistration;
    private final SipServer sipServer;
    private CallIdHeader callIdHeader;
    private Request lastInviteRequest;
    private ServerTransaction lastInviteRequestServerTransaction = null;
    private Request lastCancelRequest;
    private ServerTransaction lastCancelRequestServerTransaction = null;
    private Response lastOkResponse;
    private ClientTransaction lastOkClientTransaction;
    private Response lastRingingResponse;
    private ClientTransaction lastRingingResponseClientTransaction;
    ;

    private SipSessionState state = SipSessionState.STARTED;

    public SipSession(SipRegistration callerRegistration, SipRegistration calleeRegistration) throws TalkSipServerException {
        this.callerRegistration = callerRegistration;
        this.calleeRegistration = calleeRegistration;
        if (this.calleeRegistration.getSipServer() != this.callerRegistration.getSipServer()) {
            throw new TalkSipServerException("Having single session between multiple SIP servers is not supported yet");
        }
        this.sipServer = this.callerRegistration.getSipServer();
        this.logger.info("Opened new SIP session from {}@{} to {}@{}", callerRegistration.getSipAccountDao().getUsername(), this.sipServer.getSipDomain(), calleeRegistration.getSipAccountDao().getUsername(), this.sipServer.getSipDomain());
    }

    public void sessionRequestReceived(RequestEvent requestEvent) throws TalkSipSessionException {
        try {
            switch (this.state) {
                case STARTED: {
                    this.lastInviteRequest = requestEvent.getRequest();
                    this.lastInviteRequestServerTransaction = this.sipServer.getServerTransaction(requestEvent);
                    this.callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    Request newRequest = (Request) requestEvent.getRequest().clone();
                    try {
                        this.sipServer.rewriteFromHeader(newRequest, this.callerRegistration);
                        this.sipServer.rewriteContactHeader(newRequest, this.callerRegistration);
                        this.sipServer.rewriteUserAgentHeader(newRequest);
                    } catch (TalkSipHeaderRewriteException ex) {

                    }
                    this.sipServer.forwardRequest(this.callerRegistration, this.calleeRegistration, newRequest);
                    this.sendTryingToCaller(requestEvent);
                    this.state = SipSessionState.INVITED;
                    break;
                }
                case INVITED: {
                    switch (requestEvent.getRequest().getMethod()) {
                        case Request.CANCEL:
                            this.lastCancelRequest = requestEvent.getRequest();
                            this.lastCancelRequestServerTransaction = this.sipServer.getServerTransaction(requestEvent);
                            this.sendOkToCaller(requestEvent);
                            Request cancelRequest = this.lastRingingResponseClientTransaction.createCancel();
                            this.sipServer.attachProxyViaHeader(cancelRequest);
                            ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(cancelRequest);
                            ct.sendRequest();
                            break;
                    }
                    break;
                }
                case ESTABLISHED: {
                    switch (requestEvent.getRequest().getMethod()) {
                        case Request.ACK: {
                            this.sipServer.forwardAck(this.lastOkResponse, this.lastOkClientTransaction);
                            break;
                        }
                        case Request.BYE: {
                            ViaHeader viaHeader = (ViaHeader) requestEvent.getRequest().getHeader(ViaHeader.NAME);
                            boolean byeByCaller = false;
                            if ((viaHeader.getHost().equals(this.callerRegistration.getRemoteHost()) && viaHeader.getPort() == this.callerRegistration.getRemotePort())
                                    || (viaHeader.getReceived().equals(this.callerRegistration.getRemoteHost()) && viaHeader.getPort() == this.callerRegistration.getRemotePort())) {
                                byeByCaller = true;
                            }
                            Request newRequest = (Request) requestEvent.getRequest().clone();
                            try {
                                this.sipServer.rewriteFromHeader(newRequest, this.callerRegistration);
                                this.sipServer.rewriteContactHeader(newRequest, this.callerRegistration);
                                this.sipServer.rewriteUserAgentHeader(newRequest);
                                this.sipServer.attachProxyViaHeader(newRequest);
                            } catch (TalkSipHeaderRewriteException ex) {
                            }
                            if (byeByCaller) {
                                this.sendOkToCaller(requestEvent);
                                SipUri requestUri = (SipUri) newRequest.getRequestURI();
                                requestUri.setHost(this.calleeRegistration.getRemoteHost());
                                requestUri.setPort(this.calleeRegistration.getRemotePort());
                                ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(newRequest);
                                this.lastOkClientTransaction.getDialog().sendRequest(ct);
                            } else {
                                this.sendOkToCallee(requestEvent);
                                SipUri requestUri = (SipUri) newRequest.getRequestURI();
                                requestUri.setHost(this.callerRegistration.getRemoteHost());
                                requestUri.setPort(this.callerRegistration.getRemotePort());
                                ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(newRequest);
                                ct.sendRequest();
                            }
                            this.state = SipSessionState.ENDED;
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (TalkSipServerException | ParseException | SipException ex) {
            throw new TalkSipSessionException("Problem processing session request within session", ex);
        }
    }

    public void sessionResponseReceived(ResponseEvent responseEvent) throws TalkSipServerException {
        try {
            switch (this.state) {
                case STARTED: {
                    break;
                }
                case INVITED: {
                    Response newResponse = (Response) responseEvent.getResponse().clone();
                    try {
                        this.sipServer.rewriteContactHeader(newResponse, this.callerRegistration);
                        this.sipServer.attachTargetViaHeader(newResponse, this.lastInviteRequest);
                        this.sipServer.attachUserAgentAndServerHeader(newResponse);
                    } catch (TalkSipHeaderRewriteException ex) {

                    }
                    if (responseEvent.getClientTransaction().getRequest().getMethod().equals(Request.INVITE)) {
                        if (responseEvent.getResponse().getStatusCode() == 180) {
                            this.logger.debug("Forwarding RINGING response to caller");
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, this.sipServer.getSipMessageFactory().createResponse(Response.RINGING, this.lastInviteRequest));
                            this.lastRingingResponse = responseEvent.getResponse();
                            this.lastRingingResponseClientTransaction = responseEvent.getClientTransaction();
                        } else if (responseEvent.getResponse().getStatusCode() == 200) {
                            this.logger.debug("Session answered by callee, forwarding OK to caller");
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, newResponse);
                            this.lastOkResponse = responseEvent.getResponse();
                            this.lastOkClientTransaction = responseEvent.getClientTransaction();
                            this.state = SipSessionState.ESTABLISHED;
                        } else if (responseEvent.getResponse().getStatusCode() >= 400) {
                            this.logger.info("Session has been terminated with message " + responseEvent.getResponse().getStatusCode() + " " + responseEvent.getResponse().getReasonPhrase());
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, newResponse);
                        }
                    }
                    if (responseEvent.getResponse().getStatusCode() >= 400) {
                        this.tearDown();
                    }
                    break;
                }
                case ESTABLISHED: {

                }
            }
        } catch (ParseException ex) {
            throw new TalkSipServerException("Failed processing response", ex);
        }
    }

    private void sendOkToCaller(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.callerRegistration.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            this.sipServer.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void sendOkToCallee(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.calleeRegistration.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            this.sipServer.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void sendTryingToCaller(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.callerRegistration.getSipServer().getSipMessageFactory().createResponse(Response.TRYING, requestEvent.getRequest());
            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void tearDown() {
        this.sipServer.getSipSessionList().remove(this);
    }

    public SipRegistration getCallerRegistration() {
        return callerRegistration;
    }

    public SipRegistration getCalleeRegistration() {
        return calleeRegistration;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }
}
