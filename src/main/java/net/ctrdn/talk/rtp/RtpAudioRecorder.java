package net.ctrdn.talk.rtp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.mobius.media.server.spi.memory.ByteFrame;
import ua.mobius.media.server.spi.memory.ByteMemory;
import ua.mobius.media.server.spi.memory.ShortFrame;

public class RtpAudioRecorder {

    private final Map<Integer, String> rtpMap;
    private final Logger logger = LoggerFactory.getLogger(RtpAudioRecorder.class);
    private final ByteArrayOutputStream linearBaos = new ByteArrayOutputStream();
    private final ua.mobius.media.server.impl.dsp.audio.ilbc.Decoder iLbcDecoder = new ua.mobius.media.server.impl.dsp.audio.ilbc.Decoder();
    private final ua.mobius.media.server.impl.dsp.audio.g711.ulaw.Decoder g711uLawDecoder = new ua.mobius.media.server.impl.dsp.audio.g711.ulaw.Decoder();
    private final ua.mobius.media.server.impl.dsp.audio.g711.alaw.Decoder g711aLawDecoder = new ua.mobius.media.server.impl.dsp.audio.g711.alaw.Decoder();

    public RtpAudioRecorder(Map<Integer, String> rtpMap) {
        this.rtpMap = rtpMap;
    }

    public void process(DatagramPacket packet) {
        if (packet.getLength() <= 12) {
            this.logger.debug("RTP packet is smaller than 13 bytes");
            return;
        }
        int rtpPayloadType = packet.getData()[1] & 0xffff;
        if (!this.rtpMap.containsKey(rtpPayloadType)) {
            this.logger.debug("Unknown RTP payload type {}", rtpPayloadType);
            return;
        }

        ByteFrame inFrame = ByteMemory.allocate(packet.getLength() - 12);
        inFrame.setLength(packet.getLength() - 12);
        byte[] inData = inFrame.getData();
        System.arraycopy(packet.getData(), 12, inData, 0, packet.getLength() - 12);
        ShortFrame outFrame = null;
        String ptString = null;

        switch (this.rtpMap.get(rtpPayloadType)) {
            case "ilbc/8000": {
                outFrame = this.iLbcDecoder.process(inFrame);
                ptString = "iLBC";
                break;
            }
            case "pcma/8000": {
                outFrame = this.g711aLawDecoder.process(inFrame);
                ptString = "G.711 aLaw";
                break;
            }
            case "pcmu/8000": {
                outFrame = this.g711uLawDecoder.process(inFrame);
                ptString = "G.711 uLaw";
                break;
            }
        }
        if (outFrame != null) {
            for (int i = 0; i < outFrame.getData().length; i++) {
                this.linearBaos.write(outFrame.getData()[i] & 0xff);
                this.linearBaos.write(outFrame.getData()[i] >> 8 & 0xff);
            }
            this.logger.trace("Processed {} bytes of {} audio data (inl={}, outl={})", packet.getLength(), ptString, inFrame.getLength(), outFrame.getLength());
        }
    }

    public void close() throws IOException {
        this.linearBaos.close();
    }

    public byte[] getLpcmMsbData() {
        return this.linearBaos.toByteArray();
    }
}
