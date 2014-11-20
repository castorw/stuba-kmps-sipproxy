package net.ctrdn.talk.rtp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.ctrdn.talk.core.ProxyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgProvider {
    
    private final Logger logger = LoggerFactory.getLogger(AlgProvider.class);
    private final ProxyController proxyController;
    private final File recordingDirectory;
    private final String listenHostAddress;
    private int channelTimeout = 30000;
    private final List<AlgChannel> channelList = new ArrayList<>();
    
    public AlgProvider(ProxyController proxyController) {
        this.proxyController = proxyController;
        this.channelTimeout = Integer.parseInt(this.proxyController.getConfiguration().getProperty("talk.rtp.alg.channel.timeout", "30")) * 1000;
        this.recordingDirectory = new File(this.proxyController.getConfiguration().getProperty("talk.rtp.alg.recording.path", "."));
        this.listenHostAddress = this.proxyController.getConfiguration().getProperty("talk.rtp.alg.listen.host", "0.0.0.0");
        this.logger.info("RTP Application Layer Gateway Provider is available");
    }
    
    public synchronized AlgChannel newChannel(boolean recordingEnabled) {
        AlgChannel channel = new AlgChannel(this, recordingEnabled);
        this.channelList.add(channel);
        return channel;
    }
    
    protected synchronized void removeChannel(AlgChannel channel) {
        this.channelList.remove(channel);
    }
    
    public synchronized void stop() {
        this.logger.info("Killing all ALG channels");
        for (AlgChannel channel : this.channelList) {
            channel.stop();
            this.logger.debug("Killed ALG channel " + channel.getChannelUuid().toString());
        }
    }
    
    public String getListenHostAddress() {
        return listenHostAddress;
    }
    
    public int getChannelTimeout() {
        return channelTimeout;
    }
    
    public File getRecordingDirectory() {
        return recordingDirectory;
    }
}
