package net.ctrdn.talk.exception;

public class ApiRegistryException extends TalkException {

    public ApiRegistryException(String message) {
        super(message);
    }

    public ApiRegistryException(String message, Throwable suppressed) {
        super(message, suppressed);
    }
}
