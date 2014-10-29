package net.ctrdn.talk.system;

import com.mongodb.DBCollection;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import net.ctrdn.talk.core.common.DatabaseObject;
import net.ctrdn.talk.exception.DatabaseObjectException;
import org.bson.types.ObjectId;

public class SystemUserDao extends DatabaseObject {

    public SystemUserDao(DBCollection databaseCollection) {
        super(databaseCollection);
    }

    public SystemUserDao(DBCollection databaseCollection, ObjectId objectId) throws DatabaseObjectException {
        super(databaseCollection, objectId);
    }

    public String getUsername() {
        return this.getStringField("Username");
    }

    public void setUsername(String username) {
        this.setStringField("Username", username);
    }

    public String getPasswordHash() {
        return this.getStringField("Password");
    }

    public void setPasswordHash(String hash) {
        this.setStringField("Password", hash);
    }

    public void setPassword(String plaintext) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        this.setPasswordHash(this.generatePasswordHash(plaintext));
    }

    public String getDisplayName() {
        return this.getStringField("DisplayName");
    }

    public void setDisplayName(String displayName) {
        this.setStringField("DisplayName", displayName);
    }

    public boolean isAdministratorAccess() {
        return this.getBooleanField("AdministrativeAccess");
    }

    public void setAdministratorAccess(boolean access) {
        this.setBooleanField("AdministrativeAccess", access);
    }

    public boolean isEnabled() {
        return this.getBooleanField("Enabled");
    }

    public void setEnabled(boolean enabled) {
        this.setBooleanField("Enabled", enabled);
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

    public boolean matchPassword(String plaintext) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return this.generatePasswordHash(plaintext).equals(this.getPasswordHash());
    }

    private String generatePasswordHash(String plaintext) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(plaintext.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < hashBytes.length; i++) {
            if ((0xff & hashBytes[i]) < 0x10) {
                hexString.append("0").append(Integer.toHexString((0xFF & hashBytes[i])));
            } else {
                hexString.append(Integer.toHexString(0xFF & hashBytes[i]));
            }
        }
        return hexString.toString();
    }
}
