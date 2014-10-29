package net.ctrdn.talk.core;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Properties;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.ConfigurationException;
import net.ctrdn.talk.exception.InitializationException;
import net.ctrdn.talk.exception.PortalAuthenticationException;
import net.ctrdn.talk.exception.TalkException;
import net.ctrdn.talk.portal.ApiServlet;
import net.ctrdn.talk.portal.ResourceServlet;
import net.ctrdn.talk.system.SystemUserDao;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.ctrdn.talk.portal.api.ApiMethodRegistry;
import net.ctrdn.talk.sip.SipServer;
import net.ctrdn.talk.system.SipAccountDao;
import net.ctrdn.talk.system.SystemUserSessionDao;

public class ProxyController {

    private final File configurationFile = new File("talkd.properties");
    private final Logger logger = LoggerFactory.getLogger(ProxyController.class);
    private final List<SystemUserSessionDao> activePortalSessionList = new ArrayList<>();
    private MongoClient databaseClient;
    private DB database;
    private Properties configuration;
    private SipServer sipServer;

    public void initialize() {
        try {
            this.loadConfiguration();
            this.connectDatabase();
            this.mapDatabase();
            this.initializePortalApi();
            this.startSipServer();
            this.startWebserver();

            this.logger.info("Successfully initialized proxy controller");
        } catch (TalkException ex) {
            this.logger.error("Proxy controller initialization failed", ex);
        }
    }

    private void startSipServer() throws ConfigurationException, InitializationException {
        this.sipServer = new SipServer(this);
        this.sipServer.start();
    }

    private void initializePortalApi() throws InitializationException {
        ApiMethodRegistry.initalize();
    }

    private void loadConfiguration() throws ConfigurationException {
        if (!this.configurationFile.exists()) {
            throw new ConfigurationException("Failed to locate configuration file talkd.properties");
        }
        try {
            this.configuration = new Properties();
            this.getConfiguration().load(new FileInputStream(this.configurationFile));
        } catch (IOException ex) {
            throw new ConfigurationException("Failed to load configuration file", ex);
        }
    }

    public boolean configurationPropertyExists(String propertyName) throws ConfigurationException {
        return this.configurationPropertyExists(propertyName, true);
    }

    private boolean configurationPropertyExists(String propertyName, boolean mandatory) throws ConfigurationException {
        boolean e = this.getConfiguration().getProperty(propertyName) != null && !this.configuration.getProperty(propertyName).trim().isEmpty();
        if (!e && mandatory) {
            throw new ConfigurationException("Mandatory configuration parameter " + propertyName + " is not set");
        }
        return e;
    }

    private void connectDatabase() throws ConfigurationException, InitializationException {
        this.configurationPropertyExists("talk.db.host");
        this.configurationPropertyExists("talk.db.port");
        this.configurationPropertyExists("talk.db.database");
        try {
            this.databaseClient = new MongoClient(this.getConfiguration().getProperty("talk.db.host"), Integer.parseInt(this.getConfiguration().getProperty("talk.db.port")));
            this.database = this.databaseClient.getDB(this.getConfiguration().getProperty("talk.db.database"));
            this.logger.debug("Database connection has been successfully established with {} on port TCP/{}, using database {}", this.getConfiguration().getProperty("talk.db.host"), this.getConfiguration().getProperty("talk.db.port"), this.getConfiguration().getProperty("talk.db.database"));
        } catch (NumberFormatException | UnknownHostException ex) {
            throw new InitializationException("Failed to establish database connection", ex);
        }
    }

    private void mapDatabase() {
        DatabaseObjectFactory.addCollectionMapping(SystemUserDao.class, this.database.getCollection("core.user"));
        DatabaseObjectFactory.addCollectionMapping(SystemUserSessionDao.class, this.database.getCollection("core.user.session"));
        DatabaseObjectFactory.addCollectionMapping(SipAccountDao.class, this.database.getCollection("telephony.sip.account"));
    }

    private void startWebserver() throws ConfigurationException, InitializationException {
        this.configurationPropertyExists("talk.portal.webserver.port");
        try {
            ServletContextHandler sch = new ServletContextHandler(ServletContextHandler.SESSIONS);
            sch.setContextPath("/");
            sch.addServlet(new ServletHolder(new ApiServlet(this)), "/api/*");
            sch.addServlet(new ServletHolder(new ResourceServlet(this)), "/*");

            Server server = new Server(Integer.parseInt(this.getConfiguration().getProperty("talk.portal.webserver.port")));
            server.setHandler(sch);
            server.start();
            this.logger.debug("Portal webserver has been successfully started on port TCP/{}", this.getConfiguration().getProperty("talk.portal.webserver.port"));
        } catch (Exception ex) {
            throw new InitializationException("Failed to start portal webserver", ex);
        }
    }

    public List<SystemUserSessionDao> getActivePortalSessionList() {
        return this.activePortalSessionList;
    }

    public void checkPortalSession(SystemUserSessionDao sessionDao) throws PortalAuthenticationException {
        if (new Date().getTime() - sessionDao.getLastActivityDate().getTime() > 1800000) {
            sessionDao.endSession();
            throw new PortalAuthenticationException("Session has timed out.");
        }
        sessionDao.setLastActivityDate(new Date());
        sessionDao.store();
    }

    public SystemUserSessionDao getPortalSessionDao(HttpSession session) {
        SystemUserSessionDao sessionDao = (SystemUserSessionDao) session.getAttribute("UserSession");
        return sessionDao;
    }

    public void writeConfiguration() {
        try {
            try (FileOutputStream fos = new FileOutputStream(this.configurationFile)) {
                this.getConfiguration().store(fos, "Talk Daemon Configuration");
            }
        } catch (IOException ex) {
            this.logger.error("Failed to write configuration", ex);
        }
    }

    public Properties getConfiguration() {
        return configuration;
    }

    public SipServer getSipServer() {
        return sipServer;
    }
}
