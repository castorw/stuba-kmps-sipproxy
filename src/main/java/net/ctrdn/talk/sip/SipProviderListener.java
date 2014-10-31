package net.ctrdn.talk.sip;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.parser.ParserFactory;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.TalkSipException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipProviderListener implements SipListener {

    private final Logger logger = LoggerFactory.getLogger(SipProviderListener.class);
    private final SipServer sipServer;
    private final SipProvider sipProvider;
    private final MessageFactory sipMessageFactory;
    private final HeaderFactory sipHeaderFactory;
    private final AddressFactory sipAddressFactory;
    private final ProxyController proxyController;

    public SipProviderListener(ProxyController proxyController, SipServer sipServer) {
        this.proxyController = proxyController;
        this.sipServer = sipServer;
        this.sipProvider = this.sipServer.getSipProvider();
        this.sipAddressFactory = this.sipServer.getSipAddressFactory();
        this.sipHeaderFactory = this.sipServer.getSipHeaderFactory();
        this.sipMessageFactory = this.sipServer.getSipMessageFactory();
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
            }
        } catch (TalkSipException ex) {
            this.logger.warn("Error processing SIP request", ex);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
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

    private void processRegister(RequestEvent requestEvent) throws TalkSipSessionException {
        SipRegistration registration = null;
        try {
            ViaHeader viaHeader = (ViaHeader) requestEvent.getRequest().getHeader("Via");
            this.logger.trace("Received SIP REGISTER from " + viaHeader.getHost() + ":" + viaHeader.getPort());
            registration = this.sipServer.getSipRegistration(viaHeader.getHost(), viaHeader.getPort());
            if (registration == null) {
                this.logger.debug("Creating new SIP registration for {}:{}", viaHeader.getHost(), viaHeader.getPort());
                registration = new SipRegistration(sipServer, viaHeader.getHost(), viaHeader.getPort());
                this.sipServer.addSipRegistration(registration);
            }
            registration.registerReceived(requestEvent.getRequest());

        } catch (TalkSipServerException ex) {
            this.logger.warn("Failed to parse incoming header", ex);
        } catch (TalkSipSessionException ex) {
            if (registration != null) {
                this.sipServer.removeSipRegistration(registration);
            }
            this.logger.warn("Error processing REGISTER: " + ex.getMessage());
        }
    }
}
