package net.ctrdn.talk.sip;

/*
import com.mongodb.BasicDBObject;
import gov.nist.javax.sip.address.SipUri;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ListIterator;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import net.ctrdn.talk.core.common.DatabaseObjectFactory;
import net.ctrdn.talk.exception.TalkSipException;
import net.ctrdn.talk.exception.TalkSipRegistrationException;
import net.ctrdn.talk.exception.TalkSipServerException;
import net.ctrdn.talk.system.SipAccountDao;
import net.ctrdn.talk.system.SipExtensionDao;

public class Pieces {

    public void registerReceived(RequestEvent requestEvent) throws TalkSipRegistrationException, TalkSipServerException {
        try {
            if (requestEvent.getRequest().getExpires() != null && requestEvent.getRequest().getExpires().getExpires() == 0) {
                this.unregister(requestEvent);
            } else {
                ContactHeader contactHeader = (ContactHeader) requestEvent.getRequest().getHeader(ContactHeader.NAME);
                if (contactHeader.getExpires() == 0) {
                    this.unregister(requestEvent);
                }
            }
            switch (this.getState()) {
                case STARTED: {
                    Response challengeResponse = this.getSipServer().getSipMessageFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                    this.authenticationHelper.generateChallenge(this.getSipServer().getSipHeaderFactory(), challengeResponse, this.getSipServer().getSipDomain());
                    this.sipServer.sendResponse(requestEvent, challengeResponse);
                    this.logger.debug("Challenging remote " + this.getRemoteHost() + ":" + this.getRemotePort());
                    this.state = SipRegistrationAuthenticationState.CHALLENGED;
                    break;
                }
                case CHALLENGED: {
                    ProxyAuthorizationHeader proxyAuthHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);
                    String lookupUsername = proxyAuthHeader.getUsername();
                    String lookupRealm = proxyAuthHeader.getRealm();
                    if (!lookupRealm.equals(this.sipServer.getSipDomain())) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("Attempting to authenticate with unknown realm " + lookupRealm);
                    }
                    SipAccountDao lookupAccountDao = DatabaseObjectFactory.getInstance().find(SipAccountDao.class, new BasicDBObject("Username", lookupUsername));
                    if (lookupAccountDao == null) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("User not found in database: " + lookupUsername);
                    }
                    boolean authStatus = this.authenticationHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), this.generateDigestResponseMd5(lookupUsername, lookupRealm, lookupAccountDao.getPlaintextPassword()));
                    if (!authStatus) {
                        this.sendResponseUnauthorized(requestEvent);
                        throw new TalkSipRegistrationException("Invalid password for user " + lookupUsername);
                    }
                    this.sipAccountDao = lookupAccountDao;
                    this.state = SipRegistrationAuthenticationState.AUTHENTICATED;

                    this.registeredContactHeaderList.clear();
                    BasicDBObject extensionSearchCriteria = new BasicDBObject("TargetType", "SipAccount");
                    extensionSearchCriteria.put("SipAccountObjectId", this.getSipAccountDao().getObjectId());
                    extensionSearchCriteria.put("Enabled", true);

                    ContactHeader defaultContactHeader = this.sipServer.getSipHeaderFactory().createContactHeader(this.sipServer.getSipAddressFactory().createAddress(this.sipAccountDao.getSystemUserDao().getDisplayName(), this.sipServer.getSipAddressFactory().createSipURI(this.sipAccountDao.getUsername(), this.sipServer.getSipDomain())));
                    this.registeredContactHeaderList.add(defaultContactHeader);
                    for (SipExtensionDao sed : DatabaseObjectFactory.getInstance().list(SipExtensionDao.class, extensionSearchCriteria)) {
                        SipUri proxyUri = new SipUri();
                        proxyUri.setHost(this.getSipServer().getSipDomain());
                        proxyUri.setUser(sed.getExtension());
                        ContactHeader contactHeader = this.getSipServer().getSipHeaderFactory().createContactHeader(this.getSipServer().getSipAddressFactory().createAddress(this.getSipAccountDao().getSystemUserDao().getDisplayName(), proxyUri));
                        this.registeredContactHeaderList.add(contactHeader);
                    }

                    this.sendResponseOk(requestEvent, true);
                    this.logger.info("Remote " + this.getRemoteHost() + ":" + this.getRemotePort() + " has successfully authenticated as " + lookupUsername + "@" + lookupRealm);
                    break;
                }
                case AUTHENTICATED: {
                    this.sendResponseOk(requestEvent, true);
                    this.logger.debug("Updated contact list for  " + this.getSipAccountDao().getUsername() + "@" + this.getSipServer().getSipDomain() + " at " + this.getRemoteHost() + ":" + this.getRemotePort());
                    break;
                }
            }
        } catch (ParseException | TalkSipServerException | NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new TalkSipServerException("Failed processing REGISTER request", ex);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            this.logger.trace("Received SIP message\n" + requestEvent.getRequest().toString());
            switch (requestEvent.getRequest().getMethod()) {
                case "REGISTER": {
                    this.processRegister(requestEvent);
                    break;
                }
                default: {
                    ViaHeader sourceViaHeader = (ViaHeader) requestEvent.getRequest().getHeader(ViaHeader.NAME);
                    SipRegistration sourceRegistration = null;
                    for (SipRegistration reg : this.sipServer.getSipRegistrationList()) {
                        if (reg.getRemoteHost().equals(sourceViaHeader.getHost()) && reg.getRemotePort() == sourceViaHeader.getPort()) {
                            sourceRegistration = reg;
                            break;
                        }
                    }
                    if (sourceRegistration == null) {
                        throw new TalkSipServerException("Unable to find originating registration for request");
                    }

                    ToHeader destinationToHeader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                    SipRegistration destinationRegistration = null;
                    for (SipRegistration reg : this.sipServer.getSipRegistrationList()) {
                        if (reg.isAvailableAtAddress(destinationToHeader.getAddress())) {
                            destinationRegistration = reg;
                            break;
                        }
                    }
                    if (destinationRegistration == null) {
                        throw new TalkSipServerException("Unable to find destination registration for request");
                    }

                    Request newRequest = (Request) requestEvent.getRequest().clone();

                    SipURI targetUri = this.sipServer.getSipAddressFactory().createSipURI(destinationRegistration.getSipAccountDao().getUsername(), destinationRegistration.getRemoteHost());
                    targetUri.setPort(destinationRegistration.getRemotePort());
                    targetUri.setLrParam();
                    Address targetAddress = this.sipServer.getSipAddressFactory().createAddress(destinationRegistration.getSipAccountDao().getSystemUserDao().getDisplayName(), targetUri);
                    RouteHeader targetRouteHeader = this.sipServer.getSipHeaderFactory().createRouteHeader(targetAddress);
                    newRequest.addFirst(targetRouteHeader);

                    ViaHeader viaHeader = this.sipServer.getSipHeaderFactory().createViaHeader(this.sipServer.getSipDomain(), this.sipServer.getSipPort(), "tcp", null);
                    newRequest.addFirst(viaHeader);

                    SipURI proxyUri = this.sipServer.getSipAddressFactory().createSipURI("proxy", this.sipServer.getSipDomain());
                    Address proxyAddress = this.sipServer.getSipAddressFactory().createAddress("proxy", proxyUri);
                    proxyUri.setPort(this.sipServer.getSipPort());
                    proxyUri.setLrParam();
                    RecordRouteHeader recordRouteHeader = this.sipServer.getSipHeaderFactory().createRecordRouteHeader(proxyAddress);

                    ToHeader toHeader = (ToHeader) newRequest.getHeader(ToHeader.NAME);
                    SipUri toHeaderSipUri = (SipUri) toHeader.getAddress().getURI();
                    toHeaderSipUri.setUser(destinationRegistration.getSipAccountDao().getUsername());
                    toHeaderSipUri.setPort(sipServer.getSipPort());
                    toHeader.getAddress().setDisplayName(destinationRegistration.getSipAccountDao().getUsername());

                    newRequest.addHeader(recordRouteHeader);
                    this.logger.trace("Sending SIP request\n{}", newRequest.toString());
                    this.sipServer.getSipProvider().sendRequest(newRequest);
                    break;
                }
            }
        } catch (TalkSipException | ParseException | SipException | InvalidArgumentException ex) {
            this.logger.warn("Error processing SIP request", ex);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            this.logger.trace("Received SIP response\n" + responseEvent.getResponse().toString());
            ContactHeader contactHeader = (ContactHeader) responseEvent.getResponse().getHeader(ContactHeader.NAME);
            SipUri contactUri = (SipUri) contactHeader.getAddress().getURI();
            SipRegistration sourceRegistration = null;
            for (SipRegistration reg : this.sipServer.getSipRegistrationList()) {
                if (reg.getRemoteHost().equals(contactUri.getHost()) && reg.getRemotePort() == contactUri.getPort()) {
                    sourceRegistration = reg;
                    break;
                }
            }
            if (sourceRegistration == null) {
                throw new TalkSipServerException("Unable to find originating registration for response");
            }
            ListIterator viaHeaderListIterator = responseEvent.getResponse().getHeaders(ViaHeader.NAME);
            ViaHeader lastViaHeader = null;
            while (viaHeaderListIterator.hasNext()) {
                lastViaHeader = (ViaHeader) viaHeaderListIterator.next();
            }

            if (lastViaHeader.getHost().equals(this.sipServer.getSipDomain())) {
                return;
            }

            SipRegistration destinationRegistration = null;
            for (SipRegistration reg : this.sipServer.getSipRegistrationList()) {
                if (reg.getRemoteHost().equals(lastViaHeader.getHost()) && reg.getRemotePort() == lastViaHeader.getPort()) {
                    destinationRegistration = reg;
                    break;
                }
            }
            if (destinationRegistration == null) {
                throw new TalkSipServerException("Unable to find destination registration for request");
            }

            Response newResponse = (Response) responseEvent.getResponse().clone();
            newResponse.removeLast(ViaHeader.NAME);
            lastViaHeader.setReceived(lastViaHeader.getHost());
            newResponse.addFirst(lastViaHeader);

            this.logger.trace("Sending SIP response\n{}", newResponse.toString());
            this.sipServer.getSipProvider().sendResponse(newResponse);
        } catch (SipException | TalkSipServerException | ParseException ex) {
            this.logger.warn("Something got fucked up", ex);
        }
    }
}
*/