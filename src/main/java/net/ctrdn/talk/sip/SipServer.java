package net.ctrdn.talk.sip;

import java.util.Properties;
import java.util.TooManyListenersException;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.ConfigurationException;
import net.ctrdn.talk.exception.InitializationException;

public class SipServer {

    private final ProxyController proxyController;
    private final SipFactory sipFactory;
    private final SipStack sipStack;
    private final MessageFactory sipMessageFactory;
    private final HeaderFactory sipHeaderFactory;
    private final AddressFactory sipAddressFactory;
    private SipProvider sipProvider = null;
    private SipProviderListener sipProviderListener;

    public SipServer(ProxyController proxyController) throws ConfigurationException, InitializationException {
        try {
            this.proxyController = proxyController;
            this.proxyController.configurationPropertyExists("talk.sip.server.enabled");
            this.proxyController.configurationPropertyExists("talk.sip.server.domain");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.host");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.port");
            this.proxyController.configurationPropertyExists("talk.sip.server.listen.transport");

            this.sipFactory = SipFactory.getInstance();
            this.sipFactory.setPathName("gov.nist");
            Properties sipStackProperties = new Properties();
            sipStackProperties.setProperty("javax.sip.STACK_NAME", this.proxyController.getConfiguration().getProperty("talk.sip.server.domain"));
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
            boolean enabled = this.proxyController.getConfiguration().getProperty("talk.sip.server.enabled").equals("true");
            if (enabled) {
                this.sipProviderListener = new SipProviderListener(proxyController, sipMessageFactory, sipHeaderFactory, sipAddressFactory);
                ListeningPoint lp = this.sipStack.createListeningPoint(this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.host"), Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.port")), this.proxyController.getConfiguration().getProperty("talk.sip.server.listen.transport"));
                this.sipProvider = this.sipStack.createSipProvider(lp);
                this.sipProvider.addSipListener(this.sipProviderListener);
            }
        } catch (InvalidArgumentException | NumberFormatException | TransportNotSupportedException | ObjectInUseException | TooManyListenersException ex) {
            throw new InitializationException("Failed to start SIP server", ex);
        }
    }
}
