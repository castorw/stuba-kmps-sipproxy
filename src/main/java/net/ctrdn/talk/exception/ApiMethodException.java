package net.ctrdn.talk.exception;

public class ApiMethodException extends TalkException {

    public ApiMethodException(String message) {
        super(message);
    }

    public ApiMethodException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
