package net.ctrdn.talk.sip;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
    private ServerTransaction lastInviteServerTransaction = null;
    private Response lastOkResponse;
    private ClientTransaction lastOkClientTransaction;
    private final List<ResponseEvent> callerResponseList = new ArrayList<>();
    private final List<ResponseEvent> calleeResponseList = new ArrayList<>();

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
                    this.lastInviteServerTransaction = this.sipServer.getServerTransaction(requestEvent);
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
                            this.sendOkToCaller(requestEvent);
                            this.sipServer.getSipProvider().getNewClientTransaction(this.calleeResponseList.get(0).getClientTransaction().createCancel()).sendRequest();
                            break;
                        case Request.ACK:
                            Request ackRequest = this.lastOkClientTransaction.getDialog().createAck(((CSeqHeader) this.lastOkResponse.getHeader(CSeqHeader.NAME)).getSeqNumber());
                            this.sipServer.fuckitup(this.callerRegistration, ackRequest);
                            this.lastOkClientTransaction.getDialog().sendAck(ackRequest);
                            break;
                    }
                    break;
                }
                case ESTABLISHED: {
                    break;
                }
            }
        } catch (TalkSipServerException | ParseException | SipException | InvalidArgumentException ex) {
            throw new TalkSipSessionException("Problem processing session request within session", ex);
        }
    }

    public void sessionResponseReceived(ResponseEvent responseEvent) throws TalkSipServerException {
        try {
            if (responseEvent.getClientTransaction().getRequest().getMethod().equals(Request.INVITE)) {
                this.calleeResponseList.add(responseEvent);
                if (responseEvent.getResponse().getStatusCode() == 180) {
                    this.logger.debug("Forwarding RINGING response to caller");
                    this.sipServer.sendResponse(this.lastInviteServerTransaction, this.sipServer.getSipMessageFactory().createResponse(Response.RINGING, this.lastInviteRequest));
                } else if (responseEvent.getResponse().getStatusCode() == 200) {
                    this.logger.debug("Session established successfully, forwarding OK to caller");
                    Response newResponse = (Response) responseEvent.getResponse().clone();
                    this.sipServer.removeTopmostViaHeader(newResponse);
                    this.sipServer.rewriteUserAgentHeader(newResponse);
                    this.sipServer.sendResponse(this.lastInviteServerTransaction, newResponse);
                    this.lastOkResponse = responseEvent.getResponse();
                    this.lastOkClientTransaction = responseEvent.getClientTransaction();
                } else if (responseEvent.getResponse().getStatusCode() >= 300) {
                    Response newResponse = (Response) responseEvent.getResponse().clone();
                    this.sipServer.removeTopmostViaHeader(newResponse);
                    this.sipServer.rewriteUserAgentHeader(newResponse);
                    this.sipServer.sendResponse(this.lastInviteServerTransaction, newResponse);
                }
                if (responseEvent.getResponse().getStatusCode() >= 400) {
                    this.tearDown();
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

    private void sendTryingToCaller(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.callerRegistration.getSipServer().getSipMessageFactory().createResponse(Response.TRYING, requestEvent.getRequest());
            this.sipServer.sendResponse(this.lastInviteServerTransaction, response);
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
