package net.ctrdn.talk.sip;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import net.ctrdn.talk.core.ProxyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipProviderListener implements SipListener {

    private final Logger logger = LoggerFactory.getLogger(SipProviderListener.class);
    private final MessageFactory sipMessageFactory;
    private final HeaderFactory sipHeaderFactory;
    private final AddressFactory sipAddressFactory;
    private final ProxyController proxyController;

    public SipProviderListener(ProxyController proxyController, MessageFactory sipMessageFactory, HeaderFactory sipHeaderFactory, AddressFactory sipAddressFactory) {
        this.proxyController = proxyController;
        this.sipAddressFactory = sipAddressFactory;
        this.sipHeaderFactory = sipHeaderFactory;
        this.sipMessageFactory = sipMessageFactory;
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        this.logger.trace("Received SIP message\n" + requestEvent.getRequest().toString());
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

}
