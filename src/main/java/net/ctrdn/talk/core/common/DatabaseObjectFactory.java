package net.ctrdn.talk.core.common;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseObjectFactory {

    private final Logger logger = LoggerFactory.getLogger(DatabaseObjectFactory.class);
    private static final DatabaseObjectFactory factory = new DatabaseObjectFactory();
    private final Map<Class<? extends DatabaseObject>, DBCollection> collectionMap = new HashMap<>();

    private DatabaseObjectFactory() {

    }

    public static DatabaseObjectFactory getInstance() {
        return DatabaseObjectFactory.factory;
    }

    public static void addCollectionMapping(Class<? extends DatabaseObject> type, DBCollection collection) {
        DatabaseObjectFactory.factory.collectionMap.put(type, collection);
    }

    public <T extends DatabaseObject> T create(Class<T> type) {
        return this.find(type, (ObjectId) null);
    }

    public <T extends DatabaseObject> T find(Class<T> type, ObjectId objectId) {
        if (!this.collectionMap.containsKey(type)) {
            this.logger.warn("No collection mapping found for " + type.getName());
            return null;
        }
        try {
            T dbo;
            if (objectId != null) {
                Constructor constructor = type.getDeclaredConstructor(DBCollection.class, ObjectId.class);
                dbo = (T) constructor.newInstance(this.collectionMap.get(type), objectId);
            } else {
                Constructor constructor = type.getDeclaredConstructor(DBCollection.class);
                dbo = (T) constructor.newInstance(this.collectionMap.get(type));
            }
            return dbo;
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalArgumentException | SecurityException ex) {
            this.logger.warn("Failed to instantinate database object", ex);
        }
        return null;
    }

    public <T extends DatabaseObject> T find(Class<T> type, DBObject ref) {
        if (!this.collectionMap.containsKey(type)) {
            this.logger.warn("No collection mapping found for " + type.getName());
            return null;
        }
        DBCollection collection = this.collectionMap.get(type);
        DBObject dbObject = collection.findOne(ref, new BasicDBObject());
        if (dbObject == null) {
            return null;
        }
        return this.find(type, (ObjectId) dbObject.get("_id"));
    }

    public <T extends DatabaseObject> List<T> list(Class<T> type, DBObject ref, String sortBy, DatabaseSort sortDir) {
        if (!this.collectionMap.containsKey(type)) {
            this.logger.warn("No collection mapping found for " + type.getName());
            return null;
        }
        DBCollection collection = this.collectionMap.get(type);
        DBCursor cursor;
        if (ref == null) {
            cursor = collection.find(new BasicDBObject(), new BasicDBObject());
        } else {
            cursor = collection.find(ref, new BasicDBObject());
        }
        if (sortBy != null && sortDir != null) {
            cursor = cursor.sort(new BasicDBObject(sortBy, (sortDir == DatabaseSort.SORT_ASCENDING) ? 1 : -1));
        }
        List<T> finalList = new ArrayList<>();
        while (cursor.hasNext()) {
            DBObject object = cursor.next();
            T dbObject = this.find(type, (ObjectId) object.get("_id"));
            finalList.add(dbObject);
        }
        return finalList;
    }

    public <T extends DatabaseObject> List<T> list(Class<T> type, DBObject ref) {
        return this.list(type, ref, null, null);
    }

    public <T extends DatabaseObject> List<T> list(Class<T> type) {
        return this.list(type, null);
    }
}
