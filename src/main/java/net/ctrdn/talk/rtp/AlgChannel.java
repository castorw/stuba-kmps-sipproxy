package net.ctrdn.talk.rtp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.ctrdn.talk.exception.RtpAlgException;
import net.ctrdn.talk.exception.RtpProtocolUnsupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgChannel {
    
    private final Logger logger = LoggerFactory.getLogger(AlgChannel.class);
    private final Map<Integer, String> rtpMap = new HashMap<>();
    private final UUID channelUuid;
    private final AlgProvider algProvider;
    private final RtpAudioRecorder callerRecorder;
    private final RtpAudioRecorder calleeRecorder;
    
    private DatagramSocket rtpSocket;
    private DatagramSocket rtcpSocket;
    
    private Date lastActivityDate;
    private Date firstCallerRtpReceiveDate = null;
    private Date firstCalleeRtpReceiveDate = null;
    
    private InetAddress callerRtpAddress;
    private int callerRtpPort;
    private InetAddress calleeRtpAddress;
    private int calleeRtpPort;
    private InetAddress callerRtcpAddress;
    private int callerRtcpPort;
    private InetAddress calleeRtcpAddress;
    private int calleeRtcpPort;
    
    private boolean active = false;
    
    public AlgChannel(AlgProvider provider, boolean recordingEnabled) {
        this.algProvider = provider;
        this.channelUuid = UUID.randomUUID();
        if (recordingEnabled) {
            this.callerRecorder = new RtpAudioRecorder(this.rtpMap);
            this.calleeRecorder = new RtpAudioRecorder(this.rtpMap);
        } else {
            this.callerRecorder = null;
            this.calleeRecorder = null;
        }
        this.logger.debug("Created RTP ALG channel {}", this.channelUuid.toString());
    }
    
    private void writeRecordingWaveFiles() throws IOException {
        if (this.callerRecorder != null && this.calleeRecorder != null && this.firstCallerRtpReceiveDate != null && this.firstCalleeRtpReceiveDate != null) {
            File callerRecordingFile = new File(this.algProvider.getRecordingDirectory(), this.channelUuid.toString() + ".caller.wav");
            File calleeRecordingFile = new File(this.algProvider.getRecordingDirectory(), this.channelUuid.toString() + ".callee.wav");
            File combinedRecordingFile = new File(this.algProvider.getRecordingDirectory(), this.channelUuid.toString() + ".combined.wav");
            
            byte[] callerLpcmMsbData = this.callerRecorder.getLpcmMsbData();
            byte[] calleeLpcmMsbData = this.calleeRecorder.getLpcmMsbData();
            byte[] combinedData = this.combineAudioTracks(callerLpcmMsbData, calleeLpcmMsbData);
            
            this.writePcmWavFile(callerRecordingFile, callerLpcmMsbData);
            this.writePcmWavFile(calleeRecordingFile, calleeLpcmMsbData);
            this.writePcmWavFile(combinedRecordingFile, combinedData);
        }
    }
    
    private byte[] combineAudioTracks(byte[] callerData, byte[] calleeData) {
        long delta = this.firstCalleeRtpReceiveDate.getTime() - this.firstCallerRtpReceiveDate.getTime();
        int deltaSamples = (int) (((float) Math.abs(delta) / 1000f) * 8000f);
        byte[] referenceData, shiftedData;
        
        if (delta > 0) {
            referenceData = callerData;
            shiftedData = calleeData;
        } else {
            referenceData = calleeData;
            shiftedData = callerData;
        }
        
        int referenceDataSamples = referenceData.length / 2;
        int shiftedDataSamples = shiftedData.length / 2;
        int shiftedDataPlusDeltaSamples = shiftedDataSamples + deltaSamples;
        int outSamples = referenceDataSamples > shiftedDataPlusDeltaSamples ? referenceDataSamples : shiftedDataPlusDeltaSamples;
        
        byte[] outArray = new byte[outSamples * 2];
        
        int shiftIndex = 0;
        for (int refIndex = 0; refIndex < outSamples; refIndex++) {
            int j = 2 * refIndex;
            if (refIndex < referenceDataSamples) {
                outArray[j] = referenceData[j];
                outArray[j + 1] = referenceData[j + 1];
            }
            if (refIndex >= deltaSamples && shiftIndex < shiftedDataSamples) {
                int k = 2 * shiftIndex;
                outArray[j] += shiftedData[k];
                outArray[j + 1] += shiftedData[k + 1];
                shiftIndex++;
            }
        }
        
        return outArray;
    }
    
    private void writePcmWavFile(File file, byte[] pcmMsbData) throws IOException {
        file.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            WaveHeader waveHeader = new WaveHeader(WaveHeader.FORMAT_PCM, (short) 1, 8000, (short) 16, pcmMsbData.length);
            waveHeader.write(fos);
            fos.write(pcmMsbData, 0, pcmMsbData.length);
        }
        this.logger.debug("Wrote audio file {}", file.getAbsoluteFile());
    }
    
    public void start() throws RtpAlgException {
        try {
            int testPort = 1025 + new Random().nextInt((65534 - 1024));
            boolean gotCombo = false;
            while (!gotCombo) {
                try {
                    this.rtpSocket = null;
                    this.rtcpSocket = null;
                    this.rtpSocket = new DatagramSocket(testPort, Inet4Address.getByName(this.algProvider.getListenHostAddress()));
                    this.rtcpSocket = new DatagramSocket(testPort + 1, Inet4Address.getByName(this.algProvider.getListenHostAddress()));
                    gotCombo = true;
                } catch (SocketException ex) {
                    if (this.rtpSocket != null) {
                        this.rtpSocket.close();
                    }
                    testPort++;
                }
            }
            this.rtcpSocket.setSoTimeout(1000);
            this.rtpSocket.setSoTimeout(1000);
            this.active = true;
            this.lastActivityDate = new Date();
            
            this.logger.info("Starting RTP/RTCP channel {} on ports {} for RTP and {} for RTCP on address {}", this.channelUuid.toString(), this.rtpSocket.getLocalPort(), this.rtcpSocket.getLocalPort(), this.algProvider.getListenHostAddress());
            this.logger.info("Recording of channel {} is {}", this.channelUuid.toString(), this.callerRecorder == null ? "disabled" : "enabled");
            new Thread(new Runnable() {
                private final AlgChannel channel = AlgChannel.this;
                
                @Override
                public void run() {
                    try {
                        byte[] buffer = new byte[16384];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        while (channel.active) {
                            try {
                                channel.getRtpSocket().receive(packet);
                                channel.lastActivityDate = new Date();
                                
                                if (packet.getAddress().equals(channel.callerRtpAddress) && packet.getPort() == channel.callerRtpPort) {
                                    if (channel.firstCallerRtpReceiveDate == null) {
                                        channel.firstCallerRtpReceiveDate = new Date();
                                    }
                                    packet.setAddress(channel.calleeRtpAddress);
                                    packet.setPort(channel.calleeRtpPort);
                                    int rtpPayloadType = packet.getData()[1] & 0xffff;
                                    channel.logger.trace("Fowrading {} byte RTP packet caller-callee (PT {}), {}:{} to {}:{}", packet.getLength(), rtpPayloadType, channel.callerRtpAddress, channel.callerRtpPort, channel.calleeRtpAddress, channel.calleeRtpPort);
                                    if (channel.callerRecorder != null) {
                                        channel.callerRecorder.process(packet);
                                    }
                                } else if (packet.getAddress().equals(channel.calleeRtpAddress) && packet.getPort() == channel.calleeRtpPort) {
                                    if (channel.firstCalleeRtpReceiveDate == null) {
                                        channel.firstCalleeRtpReceiveDate = new Date();
                                    }
                                    packet.setAddress(channel.callerRtpAddress);
                                    packet.setPort(channel.callerRtpPort);
                                    int rtpPayloadType = packet.getData()[1] & 0xffff;
                                    channel.logger.trace("Fowrading {} byte RTP packet callee-caller (PT {}), {}:{} to {}:{}", packet.getLength(), rtpPayloadType, channel.callerRtpAddress, channel.callerRtpPort, channel.calleeRtpAddress, channel.calleeRtpPort);
                                    if (channel.calleeRecorder != null) {
                                        channel.calleeRecorder.process(packet);
                                    }
                                }
                                channel.getRtpSocket().send(packet);
                            } catch (SocketTimeoutException ex) {
                                if (new Date().getTime() - channel.lastActivityDate.getTime() > channel.algProvider.getChannelTimeout()) {
                                    channel.logger.warn("Channel between {}:{} and {}:{} timed out", channel.callerRtpAddress, channel.callerRtpPort, channel.calleeRtpAddress, channel.calleeRtpPort);
                                    channel.stop();
                                }
                            }
                        }
                    } catch (IOException ex) {
                        channel.logger.error("Input/Output error in ALG/RTP processing", ex);
                    } finally {
                        channel.getRtpSocket().close();
                        try {
                            this.channel.writeRecordingWaveFiles();
                        } catch (IOException ex) {
                            channel.logger.error("Input/Output error writing call recording", ex);
                        }
                    }
                }
            }).start();
            
            new Thread(new Runnable() {
                private final AlgChannel channel = AlgChannel.this;
                
                @Override
                public void run() {
                    try {
                        byte[] buffer = new byte[16384];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        while (channel.active) {
                            try {
                                channel.getRtcpSocket().receive(packet);
                                channel.lastActivityDate = new Date();
                                
                                if (packet.getAddress().equals(channel.callerRtcpAddress) && packet.getPort() == channel.callerRtcpPort) {
                                    packet.setAddress(channel.calleeRtcpAddress);
                                    packet.setPort(channel.calleeRtcpPort);
                                    channel.logger.trace("Fowrading {} byte RTCP packet caller-callee, {}:{} to {}:{}", packet.getLength(), channel.callerRtcpAddress, channel.callerRtcpPort, channel.calleeRtcpAddress, channel.calleeRtcpPort);
                                } else if (packet.getAddress().equals(channel.calleeRtcpAddress) && packet.getPort() == channel.calleeRtcpPort) {
                                    packet.setAddress(channel.callerRtcpAddress);
                                    packet.setPort(channel.callerRtcpPort);
                                    channel.logger.trace("Fowrading {} byte RTCP packet callee-caller, {}:{} to {}:{}", packet.getLength(), channel.calleeRtcpAddress, channel.calleeRtcpPort, channel.callerRtcpAddress, channel.callerRtcpPort);
                                }
                                channel.getRtcpSocket().send(packet);
                            } catch (SocketTimeoutException ex) {
                            }
                        }
                    } catch (IOException ex) {
                        channel.logger.error("Input/Output error in ALG/RTCP processing", ex);
                    } finally {
                        channel.getRtcpSocket().close();
                    }
                }
            }).start();
        } catch (IOException ex) {
            throw new RtpAlgException("Failed to create sockets", ex);
        }
    }
    
    public void stop() {
        this.logger.info("RTP ALG channel {} is being stopped", this.channelUuid.toString());
        this.active = false;
        this.algProvider.removeChannel(this);
    }
    
    public void mapRtpProtocol(String mapString) throws RtpProtocolUnsupportedException {
        String[] split = mapString.split(" ");
        Integer mapId = Integer.parseInt(split[0].trim());
        String mapProto = split[1].trim().toLowerCase();
        if (!mapProto.equals("ilbc/8000") && !mapProto.equals("pcmu/8000") && !mapProto.equals("pcma/8000") && !mapProto.equals("telephone-event/8000")) {
            throw new RtpProtocolUnsupportedException("RTP Payload Type " + mapProto + " is not supported by RTP ALG channel.");
        }
        
        this.rtpMap.put(mapId, mapProto);
    }
    
    public InetAddress getCallerRtpAddress() {
        return callerRtpAddress;
    }
    
    public void setCallerRtpAddress(InetAddress callerRtpAddress) {
        this.callerRtpAddress = callerRtpAddress;
    }
    
    public int getCallerRtpPort() {
        return callerRtpPort;
    }
    
    public void setCallerRtpPort(int callerRtpPort) {
        this.callerRtpPort = callerRtpPort;
    }
    
    public InetAddress getCalleeRtpAddress() {
        return calleeRtpAddress;
    }
    
    public void setCalleeRtpAddress(InetAddress calleeRtpAddress) {
        this.calleeRtpAddress = calleeRtpAddress;
    }
    
    public int getCalleeRtpPort() {
        return calleeRtpPort;
    }
    
    public void setCalleeRtpPort(int calleeRtpPort) {
        this.calleeRtpPort = calleeRtpPort;
    }
    
    public InetAddress getCallerRtcpAddress() {
        return callerRtcpAddress;
    }
    
    public void setCallerRtcpAddress(InetAddress callerRtcpAddress) {
        this.callerRtcpAddress = callerRtcpAddress;
    }
    
    public int getCallerRtcpPort() {
        return callerRtcpPort;
    }
    
    public void setCallerRtcpPort(int callerRtcpPort) {
        this.callerRtcpPort = callerRtcpPort;
    }
    
    public InetAddress getCalleeRtcpAddress() {
        return calleeRtcpAddress;
    }
    
    public void setCalleeRtcpAddress(InetAddress calleeRtcpAddress) {
        this.calleeRtcpAddress = calleeRtcpAddress;
    }
    
    public int getCalleeRtcpPort() {
        return calleeRtcpPort;
    }
    
    public void setCalleeRtcpPort(int calleeRtcpPort) {
        this.calleeRtcpPort = calleeRtcpPort;
    }
    
    public DatagramSocket getRtpSocket() {
        return rtpSocket;
    }
    
    public DatagramSocket getRtcpSocket() {
        return rtcpSocket;
    }
    
    public UUID getChannelUuid() {
        return channelUuid;
    }
    
    public boolean isRecordingEnabled() {
        return this.callerRecorder != null && this.calleeRecorder != null;
    }
}
