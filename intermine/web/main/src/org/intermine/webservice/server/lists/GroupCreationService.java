package org.intermine.webservice.server.lists;

import org.intermine.api.InterMineAPI;
import org.intermine.api.bag.BadGroupPermission;
import org.intermine.api.bag.BagNotFound;
import org.intermine.api.bag.Group;
import org.intermine.api.bag.ShareStateError;
import org.intermine.api.bag.SharedBagManager;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.webservice.server.core.JSONService;
import org.intermine.webservice.server.exceptions.BadRequestException;
import org.intermine.webservice.server.exceptions.MethodNotAllowed;
import org.intermine.webservice.server.exceptions.ResourceNotFoundException;
import org.intermine.webservice.server.exceptions.ServiceException;
import org.intermine.webservice.server.exceptions.ServiceForbiddenException;
import org.intermine.webservice.server.exceptions.UnauthorizedException;

/**
 * Handles all creation activity for groups and their
 * relationships.
 * Responds to
 *  - POST /groups
 *  - POST /groups/:uuid/lists
 *  - POST /groups/:uuid/members
 * 
 * @author alex
 *
 */
public class GroupCreationService extends AbstractGroupService{

    public GroupCreationService(InterMineAPI im) {
        super(im);
    }

    @Override
    protected String getRelationshipKey(Relationship relationship) {
        switch (relationship) {
            case members: return "member";
            case lists: return "list";
            default: return null;
        }
    }

    @Override
    protected void serveRelationship(Relationship relationship)
            throws ServiceException {
        switch (relationship) {
        case members:
            addMember();
            break;
        case lists:
            addList();
            break;
        default:
            throw new ResourceNotFoundException("Don't know how to handle " + relationship);
        }
    }

    private void addList() {
        Group g = getGroup();
        Profile user = getPermission().getProfile();
        String bagName = getRequiredParameter("name");
        SharedBagManager sbm = getSharedBagManager();
        try {
            sbm.shareBagWithGroup(user, bagName, g);
        } catch (BagNotFound e) {
            throw new BadRequestException(e);
        } catch (BadGroupPermission e) {
            throw new UnauthorizedException(e);
        } catch (ShareStateError e) {
            throw new BadRequestException(e);
        }
        addResultItem(getBagInfo(im.getBagManager().getBag(user, bagName)), false);
    }

    private void addMember() {
        Group g = getGroup();
        Profile owner = getPermission().getProfile();
        if (!owner.isLoggedIn()) {
            throw new UnauthorizedException("You must be logged in to add group members.");
        }
        if (g.getOwnerId() != owner.getUserId()) {
            throw new UnauthorizedException("You must be the owner of a group to add members");
        }
        String userName = getRequiredParameter("name");
        Profile newMember = im.getProfileManager().getProfile(userName);
        if (newMember == null) {
            throw new BadRequestException("Unknown user: " + userName);
        }
        SharedBagManager sbm = getSharedBagManager();

        try {
            sbm.addUserToGroup(g, newMember);
        } catch (ShareStateError e) {
            throw new BadRequestException(e);
        }
        addResultItem(getMemberInfo(newMember), false);
    }

    @Override
    protected String getGroupKey() {
        return null;
    }

    @Override
    protected void serveGroup() throws ServiceException {
        throw new MethodNotAllowed("POST");
    }

    @Override
    protected String getGroupsKey() {
        return "group";
    }

    @Override
    protected void serveGroups() throws ServiceException {
        String name = getRequiredParameter("name");
        String description = getOptionalParameter("description");
        Profile user = getPermission().getProfile();
        if (!user.isLoggedIn()) {
            throw new UnauthorizedException();
        }
        Group g = getSharedBagManager().createGroup(user, name, description);
        addResultItem(g.toMap(), false);
    }

    @Override
    protected boolean returnsArray() {
        return false;
    }

}
