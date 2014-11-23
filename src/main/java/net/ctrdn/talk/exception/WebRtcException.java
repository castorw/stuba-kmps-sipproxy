package net.ctrdn.talk.exception;

public class WebRtcException extends TalkSipServerException {

    public WebRtcException(String message) {
        super(message);
    }

    public WebRtcException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
