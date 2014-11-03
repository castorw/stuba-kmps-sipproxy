package net.ctrdn.talk.sip;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.TalkSipException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipProviderListener implements SipListener {

    private final Logger logger = LoggerFactory.getLogger(SipProviderListener.class);
    private final SipServer sipServer;

    public SipProviderListener(ProxyController proxyController, SipServer sipServer) {
        this.sipServer = sipServer;
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            this.logger.trace("Received SIP message\n" + requestEvent.getRequest().toString());
            switch (requestEvent.getRequest().getMethod()) {
                case "REGISTER": {
                    this.processRegister(requestEvent);
                    break;
                }
                case "BYE":
                case "ACK":
                case "CANCEL":
                case "INVITE": {
                    this.processSessionRequest(requestEvent);
                    break;
                }
                default: {
                    this.sipServer.sendNotImplemented(requestEvent);
                    break;
                }
            }
        } catch (TalkSipException ex) {
            this.logger.warn("Error processing SIP request", ex);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            this.logger.trace("Received SIP response\n" + responseEvent.getResponse().toString());
            CallIdHeader callIdHeader = (CallIdHeader) responseEvent.getResponse().getHeader(CallIdHeader.NAME);
            for (SipSession session : this.sipServer.getSipSessionList()) {
                if (session.getCallIdHeader().getCallId().equals(callIdHeader.getCallId())) {
                    session.sessionResponseReceived(responseEvent);
                    break;
                }
            }
        } catch (TalkSipServerException ex) {
            this.logger.warn("Error processing SIP response", ex);
        }
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
        ViaHeader viaHeader = (ViaHeader) request.getHeader("Via");
        registration = this.sipServer.getSipRegistration(viaHeader.getHost(), (viaHeader.getPort() == -1) ? 5060 : viaHeader.getPort());
        if (registration == null && createNew) {
            this.logger.debug("Creating new SIP registration for {}:{}", viaHeader.getHost(), (viaHeader.getPort() == -1) ? 5060 : viaHeader.getPort());
            registration = new SipRegistration(sipServer, viaHeader.getHost(), (viaHeader.getPort() == -1) ? 5060 : viaHeader.getPort());
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
        SipRegistration registration = null;
        try {
            registration = this.lookupRegistration(requestEvent.getRequest(), false);
            if (registration != null) {
                registration.sessionRequestReceived(requestEvent);
            } else {
                throw new TalkSipRegistrationException("Failed to lookup originating registration");
            }
        } catch (TalkSipRegistrationException ex) {
            if (registration != null) {
                this.sipServer.removeSipRegistration(registration);
            }
            this.logger.warn("Error processing session request: " + ex.getMessage());
        } catch (TalkSipServerException ex) {
            this.logger.warn("Failed to parse incoming header", ex);
        }
    }
}
