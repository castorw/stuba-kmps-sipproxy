package net.ctrdn.talk.core.common;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import java.util.Date;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class DatabaseObject {

    private final Logger logger = LoggerFactory.getLogger(DatabaseObject.class);
    private DBCollection databaseCollection;
    private DBObject databaseObject = null;

    public DatabaseObject(DBCollection datbaseCollection) {
        this.databaseCollection = datbaseCollection;
        this.databaseObject = new BasicDBObject();
    }

    public DatabaseObject(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        this(databaseCollection);
        if (objectId != null) {
            this.databaseObject.put("_id", objectId);
            this.load();
        }
    }

    private boolean isStored() {
        return this.databaseObject.containsField("_id");
    }

    private BasicDBObject getMatchObjectId() {
        return new BasicDBObject("_id", this.databaseObject.get("_id"));
    }

    private void load() throws DatabaseObjectException {
        if (!this.databaseObject.containsField("_id")) {
            throw new DatabaseObjectException("Cannot load object if no object id is available");
        }
        DBObject object = this.databaseCollection.findOne(this.getMatchObjectId());
        if (object == null) {
            throw new DatabaseObjectException("Object not found in database (" + ((ObjectId) this.databaseObject.get("_id")).toString() + ")");
        }
        this.databaseObject = object;
    }

    public void reload() {
        if (this.isStored()) {
            try {
                this.load();
            } catch (DatabaseObjectException ex) {
                this.logger.warn("Failed to reload stored database object", ex);
            }
        }
    }

    public void store() {
        if (this.isStored()) {
            this.databaseCollection.update(this.getMatchObjectId(), this.databaseObject);
        } else {
            this.databaseCollection.insert(this.databaseObject);
        }
    }

    public void delete() {
        if (this.isStored()) {
            this.databaseCollection.remove(this.getMatchObjectId());
        }
    }

    public ObjectId getObjectId() {
        if (this.isStored()) {
            return (ObjectId) this.databaseObject.get("_id");
        }
        return null;
    }

    private Object getField(String fieldName) {
        if (!this.databaseObject.containsField(fieldName)) {
            try {
                this.load();
            } catch (DatabaseObjectException ex) {
                this.logger.info("Failed to load object while resolving a field", ex);
                return null;
            }
        }
        return this.databaseObject.get(fieldName);
    }

    protected <T extends DatabaseObject> T getDaoField(Class<T> type, String fieldName) {
        if (!this.databaseObject.containsField(fieldName)) {
            try {
                this.load();
            } catch (DatabaseObjectException ex) {
                this.logger.info("Failed to load object while resolving a field", ex);
                return null;
            }
        }
        ObjectId targetObjectId = (ObjectId) this.databaseObject.get(fieldName);
        return DatabaseObjectFactory.getInstance().find(type, targetObjectId);
    }

    protected void setDaoField(String fieldName, DatabaseObject dao) {
        this.setField(fieldName, dao.getObjectId(), false);
    }

    private void setField(String fieldName, Object value, boolean imminentUpdate) {
        this.databaseObject.put(fieldName, value);
        if (imminentUpdate) {
            this.store();
        }
    }

    protected String getStringField(String fieldName) {
        return (String) this.getField(fieldName);
    }

    protected void setStringField(String fieldName, String fieldValue) {
        this.setField(fieldName, fieldValue, false);
    }

    protected Integer getIntegerField(String fieldName) {
        return (Integer) this.getField(fieldName);
    }

    protected void setIntegerField(String fieldName, Integer value) {
        this.setField(fieldName, value, false);
    }

    protected Long getLongField(String fieldName) {
        return (Long) this.getField(fieldName);
    }

    protected Boolean getBooleanField(String fieldName) {
        return (this.getField(fieldName) != null) ? (boolean) this.getField(fieldName) : null;
    }

    protected void setBooleanField(String fieldName, Boolean value) {
        this.setField(fieldName, value, false);
    }

    protected Date getDateField(String fieldName) {
        return (Date) this.getField(fieldName);
    }

    protected void setDateField(String fieldName, Date value) {
        this.setField(fieldName, value, false);
    }
}
