package net.ctrdn.talk.portal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.PortalAuthenticationException;
import net.ctrdn.talk.portal.menu.MenuItem;
import net.ctrdn.talk.dao.SystemUserSessionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(ResourceServlet.class);
    private final ProxyController proxyController;

    public ResourceServlet(ProxyController proxyController) {
        this.proxyController = proxyController;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String requestUrl = request.getRequestURI();
        try {
            if (requestUrl.equals("/")) {
                requestUrl = "/dashboard.html";
            }

            String requestFileName = "/net/ctrdn/talk/portal/htdocs" + requestUrl;
            InputStream is = getClass().getResourceAsStream(requestFileName);
            if (is != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                String fileNameLc = requestFileName.toLowerCase();
                if (fileNameLc.endsWith(".html")) {
                    response.setContentType("text/html");
                } else if (fileNameLc.endsWith(".js")) {
                    response.setContentType("text/javascript");
                } else if (fileNameLc.endsWith(".css")) {
                    response.setContentType("text/css");
                } else if (fileNameLc.endsWith(".png")) {
                    response.setContentType("image/png");
                } else if (fileNameLc.endsWith(".svg")) {
                    response.setContentType("image/svg");
                } else if (fileNameLc.endsWith(".eot")) {
                    response.setContentType("font/opentype");
                } else if (fileNameLc.endsWith(".ttf")) {
                    response.setContentType("application/x-font-ttf");
                } else if (fileNameLc.endsWith(".woff")) {
                    response.setContentType("application/x-font-woff");
                } else if (fileNameLc.endsWith(".mp3")) {
                    response.setContentType("audio/mpeg");
                } else {
                    throw new ServletException("Unknown file type");
                }
                if (fileNameLc.endsWith(".tpl.html")) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().println("404 Not Found");
                } else if (fileNameLc.endsWith(".html")) {
                    SystemUserSessionDao sessionDao = null;
                    if (!requestUrl.equals("/login.html")) {
                        if (session.getAttribute("UserSession") == null) {
                            throw new PortalAuthenticationException("Not logged in");
                        }
                        sessionDao = (SystemUserSessionDao) session.getAttribute("UserSession");
                        if (!requestUrl.equals("/webrtc-client.html") && !sessionDao.getUser().isAdministratorAccess()) {
                            response.sendRedirect("/webrtc-client.html");
                        }
                        this.proxyController.checkPortalSession(sessionDao);
                    }
                    byte[] data = this.preprocess((sessionDao == null) ? false : sessionDao.getUser().isAdministratorAccess(), is, requestUrl);
                    response.getOutputStream().write(data, 0, data.length);
                } else {
                    byte[] buffer = new byte[1024];
                    while (is.available() > 0) {
                        int rd = is.read(buffer, 0, buffer.length);
                        response.getOutputStream().write(buffer, 0, rd);
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().println("404 Not Found");
            }
        } catch (PortalAuthenticationException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().println("Unauthorized (Reason: " + ex.getMessage() + ")");
            session.setAttribute("LoginTargetUri", requestUrl);
            response.sendRedirect("/login.html");
        }
    }

    private byte[] preprocess(boolean isAdmin, InputStream is, String requestUrl) throws IOException, ServletException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (is.available() > 0) {
            int rd = is.read(buffer, 0, buffer.length);
            baos.write(buffer, 0, rd);
        }
        String htmlString = baos.toString("UTF-8");
        htmlString = htmlString.replaceAll("%portal_header_html%", this.preprocessHeader(isAdmin, requestUrl));
        htmlString = htmlString.replaceAll("%portal_footer_html%", this.getTemplate("/footer.tpl.html"));
        return htmlString.getBytes("UTF-8");
    }

    private String preprocessHeader(boolean isAdmin, String requestUrl) throws IOException, ServletException {
        List<MenuItem> menuItemList = new ArrayList<>();

        // Dashboard Menu Item
        menuItemList.add(new MenuItem("Dashboard", "/dashboard.html", "fa-dashboard", true));
        menuItemList.add(new MenuItem("WebRTC Client", "/webrtc-client.html", "fa-comments-o", false, true));

        // Telephony Menu
        MenuItem telephonyMenuItem = new MenuItem("Telephony", null, "fa-phone", true);
        telephonyMenuItem.addSubItem(new MenuItem("SIP Accounts", "/sip-accounts.html", null, true));
        telephonyMenuItem.addSubItem(new MenuItem("SIP Extensions", "/sip-extensions.html", null, true));
        telephonyMenuItem.addSubItem(new MenuItem("SIP Calls", "/sip-calls.html", null, true));
        menuItemList.add(telephonyMenuItem);

        // System Menu
        MenuItem systemMenuItem = new MenuItem("System", null, "fa-gear", true);
        systemMenuItem.addSubItem(new MenuItem("Configuration", "/system-configuration.html", null, true));
        systemMenuItem.addSubItem(new MenuItem("User Accounts", "/system-accounts.html", null, true));
        menuItemList.add(systemMenuItem);

        // Logout Menu Item
        menuItemList.add(new MenuItem("Logout", "#logout", "fa-sign-out"));
        String mainMenuHtml = "";
        for (MenuItem mi : menuItemList) {
            mainMenuHtml += mi.toHtmlString(isAdmin, requestUrl);
        }

        String headerTemplate = this.getTemplate("/header.tpl.html");

        String title = "Talk";
        switch (requestUrl) {
            case "/dashboard.html": {
                title += " | Dashboard";
                break;
            }
            case "/sip-accounts.html": {
                title += " | SIP Accounts";
                break;
            }
            case "/sip-calls.html": {
                title += " | SIP Calls";
                break;
            }
            case "/rtp-alg.html": {
                title += " | RTP Application Layer Gateway";
                break;
            }
            case "/system-configuration.html": {
                title += " | System Configuration";
                break;
            }
            case "/system-accounts.html": {
                title += " | System User Accounts";
                break;
            }
            case "/webrtc-client.html": {
                title += " | WebRTC Client";
                break;
            }
            default: {
                title += "";
            }
        }

        headerTemplate = headerTemplate.replaceAll("%site_title%", title);
        headerTemplate = headerTemplate.replaceAll("%main_menu_html%", mainMenuHtml);
        return headerTemplate;
    }

    private String getTemplate(String path) throws IOException, ServletException {
        String requestFileName = "/net/ctrdn/talk/portal/htdocs" + path;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = getClass().getResourceAsStream(requestFileName);
        if (is == null) {
            throw new ServletException("Template not found " + requestFileName);
        }
        byte[] buffer = new byte[1024];
        while (is.available() > 0) {
            int rd = is.read(buffer, 0, buffer.length);
            baos.write(buffer, 0, rd);
        }
        return baos.toString("UTF-8").replaceAll("\\$", "\\\\\\$");
    }
}
