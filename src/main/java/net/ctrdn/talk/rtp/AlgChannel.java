package net.ctrdn.talk.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Random;
import net.ctrdn.talk.exception.RtpAlgException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgChannel {

    private final Logger logger = LoggerFactory.getLogger(AlgChannel.class);
    private final String listenHostAddress;
    private final AlgProvider algProvider;
    private final int channelTimeout;

    private DatagramSocket rtpSocket;
    private DatagramSocket rtcpSocket;

    private Date lastActivityDate;

    private InetAddress callerRtpAddress;
    private int callerRtpPort;
    private InetAddress calleeRtpAddress;
    private int calleeRtpPort;
    private InetAddress callerRtcpAddress;
    private int callerRtcpPort;
    private InetAddress calleeRtcpAddress;
    private int calleeRtcpPort;

    private boolean active = false;

    public AlgChannel(AlgProvider provider) {
        this.algProvider = provider;
        this.listenHostAddress = this.algProvider.getListenHostAddress();
        this.channelTimeout = this.algProvider.getChannelTimeout();
    }

    public void start() throws RtpAlgException {
        try {
            int testPort = 1025 + new Random().nextInt((65534 - 1024));
            boolean gotCombo = false;
            while (!gotCombo) {
                try {
                    this.rtpSocket = null;
                    this.rtcpSocket = null;
                    this.rtpSocket = new DatagramSocket(testPort, Inet4Address.getByName(this.getListenHostAddress()));
                    this.rtcpSocket = new DatagramSocket(testPort + 1, Inet4Address.getByName(this.getListenHostAddress()));
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
            this.logger.info("Starting RTP/RTCP channel on ports {} for RTP and {} for RTCP on address {}", this.rtpSocket.getLocalPort(), this.rtcpSocket.getLocalPort(), this.getListenHostAddress());
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
                                    packet.setAddress(channel.calleeRtpAddress);
                                    packet.setPort(channel.calleeRtpPort);
                                    channel.logger.trace("Fowrading {} byte RTP packet caller-callee, {}:{} to {}:{}", packet.getLength(), channel.callerRtpAddress, channel.callerRtpPort, channel.calleeRtpAddress, channel.calleeRtpPort);
                                } else if (packet.getAddress().equals(channel.calleeRtpAddress) && packet.getPort() == channel.calleeRtpPort) {
                                    packet.setAddress(channel.callerRtpAddress);
                                    packet.setPort(channel.callerRtpPort);
                                    channel.logger.trace("Fowrading {} byte RTP packet callee-caller, {}:{} to {}:{}", packet.getLength(), channel.calleeRtpAddress, channel.calleeRtpPort, channel.callerRtpAddress, channel.callerRtpPort);
                                }
                                channel.getRtpSocket().send(packet);
                            } catch (SocketTimeoutException ex) {
                                if (new Date().getTime() - channel.lastActivityDate.getTime() > channel.channelTimeout) {
                                    channel.logger.warn("Channel between {}:{} and {}:{} timed out", channel.callerRtpAddress, channel.callerRtpPort, channel.calleeRtpAddress, channel.calleeRtpPort);
                                    channel.stop();
                                }
                            }
                        }
                    } catch (IOException ex) {
                        channel.logger.error("Input/Output error in ALG/RTP processing", ex);
                    } finally {
                        channel.getRtpSocket().close();
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
        } catch (UnknownHostException | SocketException ex) {
            throw new RtpAlgException("Failed to create sockets", ex);
        }
    }

    public void stop() {
        this.active = false;
    }

    public String getListenHostAddress() {
        return listenHostAddress;
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
}
