package net.ctrdn.talk.exception;

public class TalkSipServerException extends TalkSipException {

    public TalkSipServerException(String message) {
        super(message);
    }

    public TalkSipServerException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
