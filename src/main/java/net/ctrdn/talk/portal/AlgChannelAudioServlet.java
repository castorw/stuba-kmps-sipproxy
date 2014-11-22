package net.ctrdn.talk.portal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.ctrdn.talk.core.ProxyController;
import net.ctrdn.talk.exception.AlgChannelAudioServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgChannelAudioServlet extends HttpServlet {
    
    private final Logger logger = LoggerFactory.getLogger(ResourceServlet.class);
    private final ProxyController proxyController;
    
    public AlgChannelAudioServlet(ProxyController proxyController) {
        this.proxyController = proxyController;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String requestUrl = request.getRequestURI().replace(request.getServletPath() + "/", "");
        try {
            String[] uriSplit = requestUrl.split("\\.");
            if (uriSplit.length != 3) {
                throw new AlgChannelAudioServletException("Invalid request format");
            }
            if (!uriSplit[2].equals("wav")) {
                throw new AlgChannelAudioServletException("Unsupported file format requested");
            }
            if (!uriSplit[1].equals("caller") && !uriSplit[1].equals("callee") && !uriSplit[1].equals("combined")) {
                throw new AlgChannelAudioServletException("Unsupported channel context");
            }
            UUID channelUuid;
            try {
                channelUuid = UUID.fromString(uriSplit[0]);
            } catch (IllegalArgumentException ex) {
                throw new AlgChannelAudioServletException("Invalid channel identifier requested");
            }
            File requestedFile = new File(this.proxyController.getSipServer().getRtpAlgProvider().getRecordingDirectory(), channelUuid.toString() + "." + uriSplit[1] + ".wav");
            if (!requestedFile.exists()) {
                throw new AlgChannelAudioServletException("Channel not found");
            }
            FileInputStream fis = new FileInputStream(requestedFile);
            this.logger.debug("Delivering recording file " + requestedFile.getAbsolutePath());
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("audio/wav");
            response.setContentLength((int) requestedFile.length());
            byte[] buffer = new byte[4096];
            while (fis.available() > 0) {
                int rd = fis.read(buffer, 0, buffer.length);
                response.getOutputStream().write(buffer, 0, rd);
            }
        } catch (AlgChannelAudioServletException ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().println("Internal Server Error (Reason: " + ex.getMessage() + ")");
            this.logger.info("RTP ALG channeld data request failed", ex);
        }
    }
}
