package net.ctrdn.talk.dao;

import com.mongodb.DBCollection;
import java.util.Date;
import java.util.UUID;
import net.ctrdn.talk.core.common.DatabaseObject;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;

public class SipSessionDao extends DatabaseObject {

    public SipSessionDao(DBCollection databaseCollection) {
        super(databaseCollection);
    }

    public SipSessionDao(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        super(databaseCollection, objectId);
    }

    public SipAccountDao getCallerSipAccountDao() {
        return this.getDaoField(SipAccountDao.class, "CallerSipAccountObjectId");
    }

    public void setCallerSipAccountDao(SipAccountDao sipAccountDao) {
        this.setDaoField("CallerSipAccountObjectId", sipAccountDao);
    }

    public SipAccountDao getCalleeSipAccountDao() {
        return this.getDaoField(SipAccountDao.class, "CalleeSipAccountObjectId");
    }

    public void setCalleeSipAccountDao(SipAccountDao sipAccountDao) {
        this.setDaoField("CalleeSipAccountObjectId", sipAccountDao);
    }

    public Date getInviteDate() {
        return this.getDateField("InviteDate");
    }

    public void setInviteDate(Date date) {
        this.setDateField("InviteDate", date);
    }

    public Date getRingingResponseDate() {
        return this.getDateField("RingingResponseDate");
    }

    public void setRingingResponseDate(Date date) {
        this.setDateField("RingingResponseDate", date);
    }

    public Date getOkResponseDate() {
        return this.getDateField("OkResponseDate");
    }

    public void setOkResponseDate(Date date) {
        this.setDateField("OkResponseDate", date);
    }

    public Date getAckDate() {
        return this.getDateField("AckDate");
    }

    public void setAckDate(Date date) {
        this.setDateField("AckDate", date);
    }

    public Date getByeDate() {
        return this.getDateField("ByeDate");
    }

    public void setByeDate(Date date) {
        this.setDateField("ByeDate", date);
    }

    public Date getEndDate() {
        return this.getDateField("EndDate");
    }

    public void setEndDate(Date date) {
        this.setDateField("EndDate", date);
    }

    public String getEndCause() {
        return this.getStringField("EndCause");
    }

    public void setEndCause(String endCause) {
        this.setStringField("EndCause", endCause);
    }

    public Integer getCallerRtpPort() {
        return this.getIntegerField("CallerRtpPort");
    }

    public void setCallerRtpPort(Integer port) {
        this.setIntegerField("CallerRtpPort", port);
    }

    public Integer getCallerRtcpPort() {
        return this.getIntegerField("CallerRtcpPort");
    }

    public void setCallerRtcpPort(Integer port) {
        this.setIntegerField("CallerRtcpPort", port);
    }

    public Integer getCalleeRtpPort() {
        return this.getIntegerField("CalleeRtpPort");
    }

    public void setCalleeRtpPort(Integer port) {
        this.setIntegerField("CalleeRtpPort", port);
    }

    public Integer getCalleeRtcpPort() {
        return this.getIntegerField("CalleeRtcpPort");
    }

    public void setCalleeRtcpPort(Integer port) {
        this.setIntegerField("CalleeRtcpPort", port);
    }

    public Boolean getRtpAlgEnabled() {
        return this.getBooleanField("RtpAlgEnabled");
    }

    public void setRtpAlgEnabled(Boolean enabled) {
        this.setBooleanField("RtpAlgEnabled", enabled);
    }

    public Boolean getRtpAlgRecordingEnabled() {
        return this.getBooleanField("RtpAlgRecordingEnabled");
    }

    public void setRtpAlgRecordingEnabled(Boolean enabled) {
        this.setBooleanField("RtpAlgRecordingEnabled", enabled);
    }

    public UUID getRtpAlgChannelUuid() {
        String uuidString = this.getStringField("RtpAlgChannelUuid");
        return uuidString == null ? null : UUID.fromString(uuidString);
    }

    public void setRtpAlgChannelUuid(UUID channelUuid) {
        this.setStringField("RtpAlgChannelUuid", channelUuid == null ? null : channelUuid.toString());
    }

    public Integer getRtpAlgChannelRtpPort() {
        return this.getIntegerField("RtpAlgChannelRtpPort");
    }

    public void setRtpAlgChannelRtpPort(Integer port) {
        this.setIntegerField("RtpAlgChannelRtpPort", port);
    }

    public Integer getRtpAlgChannelRtcpPort() {
        return this.getIntegerField("RtpAlgChannelRtcpPort");
    }

    public void setRtpAlgChannelRtcpPort(Integer port) {
        this.setIntegerField("RtpAlgChannelRtcpPort", port);
    }

}
