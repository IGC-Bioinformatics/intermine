package org.intermine.webservice.server.lists;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.log4j.Logger;
import org.intermine.api.InterMineAPI;
import org.intermine.api.bag.Group;
import org.intermine.api.bag.SharedBagManager;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.webservice.server.core.JSONService;
import org.intermine.webservice.server.exceptions.ResourceNotFoundException;
import org.intermine.webservice.server.exceptions.ServiceException;

public abstract class AbstractGroupService extends JSONService {

    private static final Logger LOG = Logger.getLogger(AbstractGroupService.class);

    public static enum Relationship { members, lists }

    protected abstract void serveRelationship(Relationship relationship) throws ServiceException;

    protected abstract void serveGroup() throws ServiceException;

    protected abstract void serveGroups() throws ServiceException;

    private String uuid = null;
    private Relationship relationship = null;
    private SharedBagManager sbm;

    public String getUuid() {
        return uuid;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public SharedBagManager getSharedBagManager() {
        return sbm;
    }

    public AbstractGroupService(InterMineAPI im) {
        super(im);
    }

    @Override
    protected void execute() throws ServiceException {
        if (uuid == null) {
            serveGroups();
        } else if (relationship == null) {
            serveGroup();
        } else {
            serveRelationship(relationship);
        }
    }

    @Override
    protected void initState() {
        sbm = SharedBagManager.getInstance(im.getProfileManager());
        String pathInfo = request.getPathInfo();
        if (StringUtils.isBlank(pathInfo)) {
            return;
        }
        String[] parts = pathInfo.substring(1).split("/");
        if (parts.length < 1) return;
        this.uuid = parts[0];
        if (parts.length < 2) return;
        try {
            this.relationship = Relationship.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException(pathInfo);
        }
        if (parts.length > 3) throw new ResourceNotFoundException(pathInfo);
    }

    protected Group getGroup() {
        Profile user = getPermission().getProfile();
        Group g = sbm.getGroup(uuid);
        if (g == null || !sbm.isUserInGroup(user, g)) {
            throw new ResourceNotFoundException("Group not found: " + uuid);
        }
        return g;
    }

    @Override
    protected String getResultsKey() {
        if (uuid == null) {
            return getGroupsKey(); //"groups";
        } else if (relationship == null) {
            return getGroupKey(); //"group";
        } else {
            return getRelationshipKey(relationship);
        }
    }

    protected abstract String getGroupKey();

    protected abstract String getGroupsKey();

    protected abstract String getRelationshipKey(Relationship relationship);

    @Override
    protected boolean returnsArray() {
        return uuid == null || relationship != null;
    }

    protected Map<String, Object> getBagInfo(InterMineBag bag) {
        Map<String, Object> bagMap = new HashMap<String, Object>();
        bagMap.put("name", bag.getName());
        bagMap.put("type", bag.getType());
        Date bagDate = bag.getDateCreated();
        bagMap.put("dateCreated",
            bagDate == null ? null : DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(bagDate));
        try {
            bagMap.put("size", bag.getSize());
        } catch (ObjectStoreException e) {
            bagMap.put("size", null);
        }
        return bagMap;
    }

    protected Map<String, Object> getMemberInfo(Profile member) {
        Map<String, Object> memberMap = new HashMap<String, Object>();
        memberMap.put("name", member.getName());
        memberMap.put("username", member.getUsername());
        memberMap.put("email", member.getEmailAddress());
        return memberMap;
    }

}
