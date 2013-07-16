package org.intermine.api.bag;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.types.ToMap;

public class Group implements ToMap {

	public enum Field { name, description };
	
    private final int groupId;
    private String name;
    private String description;
    private final int ownerId;
    private final String uuid;

    private transient ProfileManager pm = null;
 
    public Group(int id, String groupName, String description, int ownerId, String uuid) {
        this.groupId = id;
        this.name = groupName;
        this.description = description;
        this.ownerId = ownerId;
        this.uuid = uuid;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setProfileManager(ProfileManager pm) {
        this.pm = pm;
    }

    public Profile getOwner() {
        return pm.getProfile(ownerId);
    }

    public String getUUID() {
        return uuid;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> retval = new HashMap<String, Object>();
        retval.put("name", name);
        retval.put("description", description);
        retval.put("uuid", uuid);
        if (pm != null) {
            Map<String, Object> ownerData = new HashMap<String, Object>();
            Profile owner = getOwner();
            ownerData.put("name", owner.getName());
            ownerData.put("username", owner.getUsername());
            ownerData.put("email", owner.getEmailAddress());
            retval.put("owner", ownerData);
        }
        return retval;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getName())
                    .append("( ")
                    .append(" groupId = ").append(groupId)
                    .append(" name = ").append(name)
                    .append(" description = ").append(description)
                    .append(" ownerId = ").append(ownerId)
                    .append(" uuid = ").append(uuid)
                    .append(" )").toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(15, 51)
            .append(groupId)
            .append(name)
            .append(description)
            .append(ownerId)
            .append(uuid).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (other instanceof Group) {
            Group rhs = (Group) other;
            return new EqualsBuilder()
                .append(groupId, rhs.groupId)
                .append(name, rhs.name)
                .append(description, rhs.description)
                .append(ownerId, rhs.ownerId)
                .append(uuid, rhs.uuid)
                .isEquals();
        }
        return false;
    }

}
