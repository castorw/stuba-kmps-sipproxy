package net.ctrdn.talk.exception;

abstract public class TalkException extends Exception {

    public TalkException(String message) {
        super(message);
    }

    public TalkException(String message, Throwable suppressed) {
        this(message);
        this.addSuppressed(suppressed);
    }
}
