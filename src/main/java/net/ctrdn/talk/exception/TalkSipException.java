package net.ctrdn.talk.exception;

abstract public class TalkSipException extends TalkException {

    public TalkSipException(String message) {
        super(message);
    }

    public TalkSipException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
