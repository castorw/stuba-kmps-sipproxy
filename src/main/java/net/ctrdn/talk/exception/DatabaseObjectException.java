package net.ctrdn.talk.exception;

public class DatabaseObjectException extends TalkException {

    public DatabaseObjectException(String message) {
        super(message);
    }

    public DatabaseObjectException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
