package net.ctrdn.talk.exception;

public class ConfigurationException extends TalkException {
    
    public ConfigurationException(String message) {
        super(message);
    }
    
    public ConfigurationException(String message, Throwable suppressed) {
        super(message, suppressed);
    }
}
