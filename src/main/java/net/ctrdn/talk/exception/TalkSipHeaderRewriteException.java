package net.ctrdn.talk.exception;

public class TalkSipHeaderRewriteException extends TalkSipServerException {

    public TalkSipHeaderRewriteException(String message) {
        super(message);
    }

    public TalkSipHeaderRewriteException(String message, Throwable suppressed) {
        super(message, suppressed);
    }

}
