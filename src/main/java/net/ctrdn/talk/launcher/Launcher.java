package net.ctrdn.talk.launcher;

import net.ctrdn.talk.core.ProxyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private final Logger logger = LoggerFactory.getLogger(Launcher.class);

    public void start() {
        this.logger.debug("Starting proxy controller");
        new ProxyController().initialize();
    }

    public static void main(String[] argv) {
        new Launcher().start();
    }
}
