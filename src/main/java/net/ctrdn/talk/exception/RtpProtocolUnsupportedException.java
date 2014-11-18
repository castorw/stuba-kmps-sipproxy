package net.ctrdn.talk.exception;

public class RtpProtocolUnsupportedException extends TalkException {

    public RtpProtocolUnsupportedException(String message) {
        super(message);
    }

    public RtpProtocolUnsupportedException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
