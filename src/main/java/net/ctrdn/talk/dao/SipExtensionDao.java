package net.ctrdn.talk.dao;

import com.mongodb.DBCollection;
import net.ctrdn.talk.core.common.DatabaseObject;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;

public class SipExtensionDao extends DatabaseObject {
    
    public SipExtensionDao(DBCollection databaseCollection) {
        super(databaseCollection);
    }
    
    public SipExtensionDao(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        super(databaseCollection, objectId);
    }
    
    public String getExtension() {
        return this.getStringField("Extension");
    }
    
    public void setExtension(String extension) {
        this.setStringField("Extension", extension);
    }
    
    public String getTargetType() {
        return this.getStringField("TargetType");
    }
    
    public void setTargetType(String targetType) {
        this.setStringField("TargetType", targetType);
    }
    
    public SipAccountDao getSipAccountDao() {
        return this.getDaoField(SipAccountDao.class, "SipAccountObjectId");
    }
    
    public void setSipAccountDao(SipAccountDao sipAccountDao) {
        this.setDaoField("SipAccountObjectDao", sipAccountDao);
    }
    
    public boolean isEnabled() {
        return this.getBooleanField("Enabled");
    }
    
    public void setEnabled(boolean enabled) {
        this.setBooleanField("Enabled", enabled);
    }
    
    public boolean isPrimary() {
        return this.getBooleanField("Primary");
    }
    
    public void setPrimary(boolean value) {
        this.setBooleanField("Primary", value);
    }
}
