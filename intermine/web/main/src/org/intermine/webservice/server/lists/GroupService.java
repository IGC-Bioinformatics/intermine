package org.intermine.webservice.server.lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.intermine.api.InterMineAPI;
import org.intermine.api.bag.Group;
import org.intermine.api.bag.SharedBagManager;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.webservice.server.exceptions.BadRequestException;
import org.intermine.webservice.server.exceptions.InternalErrorException;
import org.intermine.webservice.server.exceptions.ServiceException;

public class GroupService extends AbstractGroupService {

    public static enum Type { ALL, OWN };

    public GroupService(InterMineAPI im) {
        super(im);
    }

    @Override
    protected void serveGroups() throws ServiceException {
        Type type = getType();
        SharedBagManager sbm = getSharedBagManager();
        Profile user = getPermission().getProfile();
        Set<Group> groups;
        if (!user.isLoggedIn() && (Type.ALL == type || Type.OWN == type)) {
            groups = Collections.emptySet();
        } else if (Type.ALL == type) {
            groups = sbm.getGroups(user);
        } else if (Type.OWN == type) {
            groups = sbm.getOwnGroups(user);
        } else {
            throw new BadRequestException("Unknown group type: " + type);
        }
        Iterator<Group> iter = groups.iterator();
        while (iter.hasNext()) {
            Group g = iter.next();
            addResultItem(g.toMap(), iter.hasNext());
        }
    }

    @Override
    protected void serveGroup() throws ServiceException {
        Group g = getGroup();

        Map<String, Object> groupDetails = g.toMap();

        List<Map<String, Object>> members = getMembers(g);
        groupDetails.put("members", members);

        List<Map<String, Object>> bags = getLists(g);
        groupDetails.put("lists", bags);

        addResultItem(groupDetails, false);
    }

    @Override
    protected void serveRelationship(Relationship relationship) throws ServiceException {
        Group g = getGroup();
        Iterator<Map<String, Object>> items = null;
        switch (relationship) {
        case members:
            items = getMembers(g).iterator();
            break;
        case lists:
            items = getLists(g).iterator();
            break;
        }
        if (items == null) throw new InternalErrorException("items is still null");
        while (items.hasNext()) {
            Map<String, Object> item = items.next();
            addResultItem(item, items.hasNext());
        }
    }

    private List<Map<String, Object>> getMembers(Group g) {
        List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
        for (Profile member: getSharedBagManager().getGroupMembers(g)) {
            members.add(getMemberInfo(member));
        }
        return members;
    }

    private List<Map<String, Object>> getLists(Group g) {
        List<Map<String, Object>> bags= new ArrayList<Map<String, Object>>();
        for (InterMineBag bag: getSharedBagManager().getBagsInGroup(g)) {
            Map<String, Object> bagMap = getBagInfo(bag);
            bags.add(bagMap);
        }
        return bags;
    }

    private Type getType() {
        try {
            return Type.valueOf(this.getOptionalParameter("type", Type.ALL.name()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Bad type", e);
        }
    }

    @Override
    protected String getGroupKey() {
        return "group";
    }

    @Override
    protected String getGroupsKey() {
        return "groups";
    }

    @Override
    protected String getRelationshipKey(Relationship relationship) {
        return relationship.name();
    }

}
