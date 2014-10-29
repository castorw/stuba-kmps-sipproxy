package net.ctrdn.talk.exception;

public class ApiMethodUserException extends ApiMethodException {

    public ApiMethodUserException(String message) {
        super(message);
    }

    public ApiMethodUserException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
