package net.ctrdn.talk.exception;

public class TalkSipSessionException extends TalkSipException {

    public TalkSipSessionException(String message) {
        super(message);
    }

    public TalkSipSessionException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
