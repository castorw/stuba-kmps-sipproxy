package net.ctrdn.talk.rtp;

import net.ctrdn.talk.core.ProxyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgProvider {

    private final Logger logger = LoggerFactory.getLogger(AlgProvider.class);
    private final ProxyController proxyController;
    private String listenHostAddress;
    private int channelTimeout = 30000;

    public AlgProvider(ProxyController proxyController) {
        this.proxyController = proxyController;
        this.channelTimeout = Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.rtp.alg.channel.timeout", "30")) * 1000;
        this.logger.info("RTP Application Layer Gateway Provider is available");
    }

    public AlgChannel newChannel() {
        return new AlgChannel(this);
    }

    public String getListenHostAddress() {
        return listenHostAddress;
    }

    public void setListenHostAddress(String listenHostAddress) {
        this.listenHostAddress = listenHostAddress;
    }

    public int getChannelTimeout() {
        return channelTimeout;
    }
}
