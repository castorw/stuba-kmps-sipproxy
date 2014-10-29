package net.ctrdn.talk.exception;

public class InitializationException extends TalkException {

    public InitializationException(String message) {
        super(message);
    }

    public InitializationException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
