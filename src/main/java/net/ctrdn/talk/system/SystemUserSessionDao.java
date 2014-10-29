package net.ctrdn.talk.system;

import com.mongodb.DBCollection;
import java.util.Date;
import net.ctrdn.talk.core.common.DatabaseObject;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;

public class SystemUserSessionDao extends DatabaseObject {

    public SystemUserSessionDao(DBCollection databaseCollection) {
        super(databaseCollection);
    }

    public SystemUserSessionDao(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        super(databaseCollection, objectId);
    }

    public SystemUserDao getUser() {
        return this.getDaoField(SystemUserDao.class, "UserObject");
    }

    public void setUser(SystemUserDao user) {
        this.setDaoField("UserObject", user);
    }

    public Date getStartDate() {
        return this.getDateField("StartDate");
    }

    public void setStartDate(Date date) {
        this.setDateField("StartDate", date);
    }

    public Date getLastActivityDate() {
        return this.getDateField("LastActivityDate");
    }

    public void setLastActivityDate(Date date) {
        this.setDateField("LastActivityDate", date);
    }

    public Date getEndDate() {
        return this.getDateField("EndDate");
    }

    public void setEndDate(Date date) {
        this.setDateField("EndDate", date);
    }

    public boolean isFromCookie() {
        return this.getBooleanField("FromCookie");
    }

    public void setFromCookie(boolean fromCookie) {
        this.setBooleanField("FromCookie", fromCookie);
    }

    public void endSession() {
        this.setEndDate(new Date());
        this.store();
    }
}
