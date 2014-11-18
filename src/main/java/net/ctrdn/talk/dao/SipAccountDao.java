package net.ctrdn.talk.dao;

import com.mongodb.DBCollection;
import java.util.Date;
import net.ctrdn.talk.core.common.DatabaseObject;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;

public class SipAccountDao extends DatabaseObject {

    public SipAccountDao(DBCollection databaseCollection) {
        super(databaseCollection);
    }

    public SipAccountDao(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        super(databaseCollection, objectId);
    }

    public String getUsername() {
        return this.getStringField("Username");
    }

    public void setUsername(String username) {
        this.setStringField("Username", username);
    }

    public String getPlaintextPassword() {
        return this.getStringField("PlaintextPassword");
    }

    public void setPlaintextPassword(String plaintextPassword) {
        this.setStringField("PlaintextPassword", plaintextPassword);
    }

    public boolean getEnabled() {
        return this.getBooleanField("Enabled");
    }

    public void setEnabled(boolean enabled) {
        this.setBooleanField("Enabled", enabled);
    }

    public SystemUserDao getSystemUserDao() {
        return this.getDaoField(SystemUserDao.class, "SystemUserObjectId");
    }

    public void setSystemUserDao(SystemUserDao systemUserDao) {
        this.setDaoField("SystemUserObjectId", systemUserDao);
    }

    public Date getCreateDate() {
        return this.getDateField("CreateDate");
    }

    public void setCreateDate(Date createDate) {
        this.setDateField("CreateDate", createDate);
    }

    public Date getLastLoginDate() {
        return this.getDateField("LastLoginDate");
    }

    public void setLastLoginDate(Date lastLoginDate) {
        this.setDateField("LastLoginDate", lastLoginDate);
    }

    public boolean getRecordIncomingCalls() {
        return this.getBooleanField("RecordIncomingCalls");
    }

    public void setRecordIncomingCalls(boolean recordIncomingCalls) {
        this.setBooleanField("RecordIncomingCalls", recordIncomingCalls);
    }

    public boolean getRecordOutgoingCalls() {
        return this.getBooleanField("RecordOutgoingCalls");
    }

    public void setRecordOutgoingCalls(boolean recordOutgoingCalls) {
        this.setBooleanField("RecordOutgoingCalls", recordOutgoingCalls);
    }
}
