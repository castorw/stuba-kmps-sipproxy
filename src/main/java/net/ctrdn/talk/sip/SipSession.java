package net.ctrdn.talk.sip;

import gov.nist.javax.sdp.fields.AttributeField;
import gov.nist.javax.sip.address.SipUri;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import javax.sip.ClientTransaction;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.dao.SipSessionDao;
import net.ctrdn.talk.exception.RtpAlgException;
import net.ctrdn.talk.exception.RtpProtocolUnsupportedException;
import net.ctrdn.talk.exception.TalkSipHeaderRewriteException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.exception.TalkSipSessionException;
import net.ctrdn.talk.rtp.AlgChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipSession {

    private final Logger logger = LoggerFactory.getLogger(SipSession.class);
    private final SipSessionDao sipSessionDao;
    private final SipRegistration callerRegistration;
    private final SipRegistration calleeRegistration;
    private final SipServer sipServer;
    private CallIdHeader callIdHeader;
    private RequestEvent lastInviteRequestEvent;
    private ServerTransaction lastInviteRequestServerTransaction;
    private String lastInviteBranch;
    private Request lastCancelRequest;
    private ServerTransaction lastCancelRequestServerTransaction;
    private String lastCancelBranch;
    private String lastByeBranch;
    private Response lastOkResponse;
    private RequestEvent lastAckRequestEvent;
    private ClientTransaction lastOkClientTransaction;
    private Response lastRingingResponse;
    private ClientTransaction lastRingingResponseClientTransaction;
    private AlgChannel algChannel = null;

    private SipSessionState state = SipSessionState.STARTED;

    public SipSession(SipRegistration callerRegistration, SipRegistration calleeRegistration) throws TalkSipServerException {
        this.callerRegistration = callerRegistration;
        this.calleeRegistration = calleeRegistration;
        if (this.calleeRegistration.getSipServer() != this.callerRegistration.getSipServer()) {
            throw new TalkSipServerException("Having single session between multiple SIP servers is not supported yet");
        }
        this.sipServer = this.callerRegistration.getSipServer();
        this.sipSessionDao = DatabaseObjectFactory.getInstance().create(SipSessionDao.class);
        this.sipSessionDao.setCallerSipAccountDao(this.callerRegistration.getSipAccountDao());
        this.sipSessionDao.setCalleeSipAccountDao(this.calleeRegistration.getSipAccountDao());
        this.sipSessionDao.setAckDate(null);
        this.sipSessionDao.setRingingResponseDate(null);
        this.sipSessionDao.setByeDate(null);
        this.sipSessionDao.setCalleeRtcpPort(null);
        this.sipSessionDao.setCalleeRtpPort(null);
        this.sipSessionDao.setCallerRtcpPort(null);
        this.sipSessionDao.setCallerRtpPort(null);
        this.sipSessionDao.setEndCause(null);
        this.sipSessionDao.setEndDate(null);
        this.sipSessionDao.setInviteDate(null);
        this.sipSessionDao.setOkResponseDate(null);
        this.sipSessionDao.setRtpAlgChannelRtcpPort(null);
        this.sipSessionDao.setRtpAlgChannelRtpPort(null);
        this.sipSessionDao.setRtpAlgChannelUuid(null);
        this.sipSessionDao.setRtpAlgEnabled(false);
        this.sipSessionDao.setRtpAlgRecordingEnabled(false);
        this.logger.info("Opened new SIP session from {}@{} to {}@{}", callerRegistration.getSipAccountDao().getUsername(), this.sipServer.getSipDomain(), calleeRegistration.getSipAccountDao().getUsername(), this.sipServer.getSipDomain());
    }

    private void processInviteRequestSdp(Request request) throws TalkSipSessionException {
        try {
            if (this.sipServer.getRtpAlgProvider() != null) {
                this.algChannel = this.sipServer.getRtpAlgProvider().newChannel(this.getCallerRegistration().getSipAccountDao().getRecordOutgoingCalls() || this.getCalleeRegistration().getSipAccountDao().getRecordIncomingCalls());
                this.algChannel.start();
                this.getSipSessionDao().setRtpAlgEnabled(true);
                this.getSipSessionDao().setRtpAlgRecordingEnabled(this.algChannel.isRecordingEnabled());
                this.getSipSessionDao().setRtpAlgChannelUuid(this.algChannel.getChannelUuid());
                this.getSipSessionDao().setRtpAlgChannelRtpPort(this.algChannel.getRtpSocket().getLocalPort());
                this.getSipSessionDao().setRtpAlgChannelRtcpPort(this.algChannel.getRtcpSocket().getLocalPort());
            }

            ContentTypeHeader contentType = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
            ContentLengthHeader contentLen = (ContentLengthHeader) request.getHeader(ContentLengthHeader.NAME);

            if (contentLen.getContentLength() > 0 && contentType.getContentSubType().equals("sdp")) {
                String charset = null;

                if (charset == null) {
                    charset = "UTF-8";
                }
                byte[] rawContent = request.getRawContent();
                String sdpContent = new String(rawContent, charset);
                SdpFactory sdpFactory = SdpFactory.getInstance();
                SessionDescription sessionDescription = sdpFactory.createSessionDescription(sdpContent);
                MediaDescription md = (MediaDescription) sessionDescription.getMediaDescriptions(false).firstElement();

                this.getSipSessionDao().setCallerRtpPort(md.getMedia().getMediaPort());
                this.getSipSessionDao().setCallerRtcpPort(md.getMedia().getMediaPort() + 1);

                if (this.algChannel != null) {
                    this.algChannel.setCallerRtpAddress(InetAddress.getByName(sessionDescription.getConnection().getAddress()));
                    this.algChannel.setCallerRtcpAddress(InetAddress.getByName(sessionDescription.getConnection().getAddress()));
                    this.algChannel.setCallerRtpPort(md.getMedia().getMediaPort());
                    this.algChannel.setCallerRtcpPort(md.getMedia().getMediaPort() + 1);
                    sessionDescription.getConnection().setAddress(this.sipServer.getRtpAlgProvider().getListenHostAddress());
                    sessionDescription.getOrigin().setAddress(this.sipServer.getRtpAlgProvider().getListenHostAddress());
                    md.getMedia().setMediaPort(this.algChannel.getRtpSocket().getLocalPort());

                    Vector<String> mapIdVector = new Vector<>();
                    List<AttributeField> removeAttributeList = new ArrayList<>();
                    Vector<AttributeField> attributeVector = md.getAttributes(false);
                    Vector<String> originalMapIdVector = md.getMedia().getMediaFormats(false);
                    for (String protocolId : originalMapIdVector) {
                        AttributeField matchingAttributeField = null;
                        for (AttributeField attribute : attributeVector) {
                            if (attribute.getName().toLowerCase().equals("rtpmap")) {
                                String[] attrSplit = attribute.getValue().split(" ");
                                if (attrSplit[0].equals(protocolId)) {
                                    matchingAttributeField = attribute;
                                    break;
                                }
                            }
                        }
                        if (matchingAttributeField != null) {
                            try {
                                this.algChannel.mapRtpProtocol(matchingAttributeField.getValue());
                                mapIdVector.add(protocolId);
                            } catch (RtpProtocolUnsupportedException ex) {
                                this.logger.trace("Unsupported RTP protocol " + matchingAttributeField.getValue() + " is being removed from invite request");
                                removeAttributeList.add(matchingAttributeField);
                            }
                        } else if (protocolId.equals("0") || protocolId.equals("8")) {
                            mapIdVector.add(protocolId);
                        }
                    }
                    for (AttributeField af : removeAttributeList) {
                        attributeVector.remove(af);
                    }

                    md.getMedia().setMediaFormats(mapIdVector);

                    request.setContent(sessionDescription, contentType);
                }
            } else {
                throw new TalkSipSessionException("Not an SDP data");
            }
        } catch (UnsupportedEncodingException | SdpException | ParseException | UnknownHostException | RtpAlgException ex) {
            throw new TalkSipSessionException("Failed preprocessing SDP data", ex);
        }
    }

    private void processOkResponseSdp(Response response) throws TalkSipSessionException {
        try {
            ContentTypeHeader contentType = (ContentTypeHeader) response.getHeader(ContentTypeHeader.NAME);
            ContentLengthHeader contentLen = (ContentLengthHeader) response.getHeader(ContentLengthHeader.NAME);

            if (contentLen.getContentLength() > 0 && contentType.getContentSubType().equals("sdp")) {
                String charset = null;

                if (charset == null) {
                    charset = "UTF-8";
                }
                byte[] rawContent = response.getRawContent();
                String sdpContent = new String(rawContent, charset);
                SdpFactory sdpFactory = SdpFactory.getInstance();
                SessionDescription sessionDescription = sdpFactory.createSessionDescription(sdpContent);
                MediaDescription md = (MediaDescription) sessionDescription.getMediaDescriptions(false).firstElement();
                this.getSipSessionDao().setCalleeRtpPort(md.getMedia().getMediaPort());
                this.getSipSessionDao().setCalleeRtcpPort(md.getMedia().getMediaPort() + 1);

                if (this.algChannel != null) {
                    this.algChannel.setCalleeRtpAddress(InetAddress.getByName(sessionDescription.getConnection().getAddress()));
                    this.algChannel.setCalleeRtcpAddress(InetAddress.getByName(sessionDescription.getConnection().getAddress()));
                    this.algChannel.setCalleeRtpPort(md.getMedia().getMediaPort());
                    this.algChannel.setCalleeRtcpPort(md.getMedia().getMediaPort() + 1);
                    sessionDescription.getConnection().setAddress(this.sipServer.getRtpAlgProvider().getListenHostAddress());
                    sessionDescription.getOrigin().setAddress(this.sipServer.getRtpAlgProvider().getListenHostAddress());
                    md.getMedia().setMediaPort(this.algChannel.getRtpSocket().getLocalPort());

                    Vector<String> mapIdVector = new Vector<>();
                    List<AttributeField> removeAttributeList = new ArrayList<>();
                    Vector<AttributeField> attributeVector = md.getAttributes(false);
                    Vector<String> originalMapIdVector = md.getMedia().getMediaFormats(false);
                    for (String protocolId : originalMapIdVector) {
                        AttributeField matchingAttributeField = null;
                        for (AttributeField attribute : attributeVector) {
                            if (attribute.getName().toLowerCase().equals("rtpmap")) {
                                String[] attrSplit = attribute.getValue().split(" ");
                                if (attrSplit[0].equals(protocolId)) {
                                    matchingAttributeField = attribute;
                                    break;
                                }
                            }
                        }
                        if (matchingAttributeField != null) {
                            try {
                                this.algChannel.mapRtpProtocol(matchingAttributeField.getValue());
                                mapIdVector.add(protocolId);
                            } catch (RtpProtocolUnsupportedException ex) {
                                this.logger.trace("Unsupported RTP protocol " + matchingAttributeField.getValue() + " is being removed from ok response");
                                removeAttributeList.add(matchingAttributeField);
                            }
                        } else if (protocolId.equals("0") || protocolId.equals("8")) {
                            mapIdVector.add(protocolId);
                        }
                    }
                    for (AttributeField af : removeAttributeList) {
                        attributeVector.remove(af);
                    }

                    md.getMedia().setMediaFormats(mapIdVector);

                    response.setContent(sessionDescription, contentType);
                }
            } else {
                throw new TalkSipSessionException("Not an SDP data");
            }
        } catch (UnsupportedEncodingException | SdpException | ParseException | UnknownHostException ex) {
            throw new TalkSipSessionException("Failed preprocessing SDP data", ex);
        }
    }

    public void sessionRequestReceived(RequestEvent requestEvent) throws TalkSipSessionException {
        try {
            switch (this.getState()) {
                case STARTED: {
                    if (!requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
                        throw new TalkSipSessionException("First request needs to be INVITE, got" + requestEvent.getRequest().getMethod());
                    }
                    this.lastInviteRequestEvent = requestEvent;
                    this.lastInviteRequestServerTransaction = this.sipServer.getServerTransaction(requestEvent);
                    this.callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    this.getSipSessionDao().setInviteDate(new Date());

                    Request newRequest = (Request) requestEvent.getRequest().clone();
                    try {
                        this.sipServer.rewriteFromHeader(newRequest, this.callerRegistration);
                        this.sipServer.rewriteContactHeader(newRequest, this.callerRegistration);
                        this.sipServer.rewriteUserAgentHeader(newRequest);
                        this.processInviteRequestSdp(newRequest);
                    } catch (TalkSipHeaderRewriteException ex) {

                    }
                    this.lastInviteBranch = this.sipServer.generateBranch();
                    this.sipServer.forwardRequest(this.callerRegistration, this.calleeRegistration, newRequest, this.lastInviteBranch);
                    this.sendTryingToCaller(requestEvent);
                    this.state = SipSessionState.INVITED;
                    break;
                }
                case INVITED: {
                    switch (requestEvent.getRequest().getMethod()) {
                        case Request.CANCEL:
                            this.lastCancelRequest = requestEvent.getRequest();
                            this.lastCancelRequestServerTransaction = this.sipServer.getServerTransaction(requestEvent);
                            this.sendOkToCaller(requestEvent);
                            Request cancelRequest = this.lastRingingResponseClientTransaction.createCancel();
                            this.sipServer.attachProxyViaHeader(cancelRequest, this.lastInviteBranch);
                            this.logger.trace("Forwarding CANCEL request\n{}", cancelRequest.toString());
                            ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(cancelRequest);
                            ct.sendRequest();
                            this.getSipSessionDao().setEndCause("Canceled by caller");
                            this.state = SipSessionState.ENDED;
                            break;
                    }
                    break;
                }
                case ESTABLISHED: {
                    switch (requestEvent.getRequest().getMethod()) {
                        case Request.ACK: {
                            this.getSipSessionDao().setAckDate(new Date());
                            this.lastAckRequestEvent = requestEvent;
                            this.sipServer.forwardAck(this.lastOkResponse, this.lastOkClientTransaction);
                            break;
                        }
                        case Request.BYE: {
                            this.getSipSessionDao().setByeDate(new Date());
                            ViaHeader viaHeader = (ViaHeader) requestEvent.getRequest().getHeader(ViaHeader.NAME);
                            boolean byeByCaller = false;
                            if ((viaHeader.getHost().equals(this.callerRegistration.getRemoteHost()) && viaHeader.getPort() == this.callerRegistration.getRemotePort())
                                    || (viaHeader.getReceived() != null && viaHeader.getReceived().equals(this.callerRegistration.getRemoteHost()) && viaHeader.getRPort() == this.callerRegistration.getRemotePort())) {
                                byeByCaller = true;
                            }
                            Request newRequest = (Request) requestEvent.getRequest().clone();
                            this.lastByeBranch = this.sipServer.generateBranch();
                            this.sipServer.rewriteUserAgentHeader(newRequest);
                            this.sipServer.attachProxyViaHeader(newRequest, this.lastByeBranch);
                            if (byeByCaller) {
                                this.sendOkToCaller(requestEvent);
                                this.sipServer.rewriteFromHeader(newRequest, this.callerRegistration);
                                this.sipServer.rewriteContactHeader(newRequest, this.callerRegistration);
                                SipUri requestUri = (SipUri) newRequest.getRequestURI();
                                requestUri.setHost(this.calleeRegistration.getRemoteHost());
                                requestUri.setPort(this.calleeRegistration.getRemotePort());
                                this.logger.trace("Forwarding BYE to callee\n{}", newRequest.toString());
                                ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(newRequest);
                                this.lastOkClientTransaction.getDialog().sendRequest(ct);
                                this.getSipSessionDao().setEndCause("Graceful bye by caller");
                            } else {
                                this.sendOkToCallee(requestEvent);
                                this.sipServer.rewriteFromHeader(newRequest, this.calleeRegistration);
                                this.sipServer.rewriteContactHeader(newRequest, this.calleeRegistration);
                                SipUri requestUri = (SipUri) newRequest.getRequestURI();
                                requestUri.setHost(this.callerRegistration.getRemoteHost());
                                requestUri.setPort(this.callerRegistration.getRemotePort());
                                this.logger.trace("Forwarding BYE to caller\n{}", newRequest.toString());
                                ClientTransaction ct = this.sipServer.getSipProvider().getNewClientTransaction(newRequest);
                                this.lastAckRequestEvent.getDialog().sendRequest(ct);
                                this.getSipSessionDao().setEndCause("Graceful bye by callee");
                            }
                            this.state = SipSessionState.ENDED;
                            break;
                        }
                    }
                    break;
                }
            }
            this.onCommunicationProcessed();
        } catch (TalkSipServerException | ParseException | SipException ex) {
            throw new TalkSipSessionException("Problem processing session request within session", ex);
        }
    }

    public void sessionResponseReceived(ResponseEvent responseEvent) throws TalkSipServerException {
        try {
            switch (this.getState()) {
                case STARTED: {
                    break;
                }
                case INVITED: {
                    Response newResponse = (Response) responseEvent.getResponse().clone();
                    try {
                        this.sipServer.rewriteContactHeader(newResponse, this.callerRegistration);
                        this.sipServer.attachTargetViaHeader(newResponse, this.lastInviteRequestEvent.getRequest());
                        this.sipServer.attachUserAgentAndServerHeader(newResponse);
                    } catch (TalkSipHeaderRewriteException ex) {

                    }
                    if (responseEvent.getClientTransaction().getRequest().getMethod().equals(Request.INVITE)) {
                        if (responseEvent.getResponse().getStatusCode() == 180) {
                            this.getSipSessionDao().setRingingResponseDate(new Date());
                            this.logger.debug("Forwarding RINGING response to caller");
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, this.sipServer.getSipMessageFactory().createResponse(Response.RINGING, this.lastInviteRequestEvent.getRequest()));
                            this.lastRingingResponse = responseEvent.getResponse();
                            this.lastRingingResponseClientTransaction = responseEvent.getClientTransaction();
                        } else if (responseEvent.getResponse().getStatusCode() == 200) {
                            this.getSipSessionDao().setOkResponseDate(new Date());
                            this.logger.debug("Session answered by callee, forwarding OK to caller");
                            this.processOkResponseSdp(newResponse);
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, newResponse);
                            this.lastOkResponse = responseEvent.getResponse();
                            this.lastOkClientTransaction = responseEvent.getClientTransaction();
                            this.state = SipSessionState.ESTABLISHED;
                        } else if (responseEvent.getResponse().getStatusCode() >= 400) {
                            newResponse.removeHeader(ContactHeader.NAME);
                            this.logger.info("Session has been terminated with message " + responseEvent.getResponse().getStatusCode() + " " + responseEvent.getResponse().getReasonPhrase());
                            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, newResponse);
                        }
                    }
                    if (responseEvent.getResponse().getStatusCode() >= 400) {
                        this.getSipSessionDao().setEndCause(responseEvent.getResponse().getStatusCode() + " " + responseEvent.getResponse().getReasonPhrase());
                        this.state = SipSessionState.ENDED;
                    }
                    break;
                }
                case ESTABLISHED: {

                }
            }
            this.onCommunicationProcessed();
        } catch (ParseException | TalkSipSessionException ex) {
            throw new TalkSipServerException("Failed processing response", ex);
        }
    }

    private void sendOkToCaller(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.callerRegistration.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            this.sipServer.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void sendOkToCallee(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.calleeRegistration.getSipServer().getSipMessageFactory().createResponse(Response.OK, requestEvent.getRequest());
            this.sipServer.sendResponse(requestEvent, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void sendTryingToCaller(RequestEvent requestEvent) throws ParseException, TalkSipServerException {
        try {
            Response response = this.callerRegistration.getSipServer().getSipMessageFactory().createResponse(Response.TRYING, requestEvent.getRequest());
            this.sipServer.sendResponse(this.lastInviteRequestServerTransaction, response);
        } catch (ParseException ex) {
            throw new TalkSipServerException("Error responding", ex);
        }
    }

    private void onCommunicationProcessed() {
        if (this.state == SipSessionState.ENDED) {
            if (this.algChannel != null) {
                this.algChannel.stop();
            }
            this.getSipSessionDao().setEndDate(new Date());
            this.sipServer.getSipSessionList().remove(this);
        }
        if (this.getSipSessionDao().getInviteDate() != null) {
            this.getSipSessionDao().store();
        }
    }

    public SipRegistration getCallerRegistration() {
        return callerRegistration;
    }

    public SipRegistration getCalleeRegistration() {
        return calleeRegistration;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }

    public SipSessionState getState() {
        return state;
    }

    public SipSessionDao getSipSessionDao() {
        return sipSessionDao;
    }
}
