package org.intermine.webservice.server.lists;

import java.util.Map;

import org.intermine.api.InterMineAPI;
import org.intermine.api.bag.BadGroupPermission;
import org.intermine.api.bag.BagNotFound;
import org.intermine.api.bag.Group;
import org.intermine.api.bag.ShareStateError;
import org.intermine.api.bag.SharedBagManager;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.webservice.server.exceptions.BadRequestException;
import org.intermine.webservice.server.exceptions.InternalErrorException;
import org.intermine.webservice.server.exceptions.MethodNotAllowed;
import org.intermine.webservice.server.exceptions.ResourceNotFoundException;
import org.intermine.webservice.server.exceptions.ServiceException;
import org.intermine.webservice.server.exceptions.UnauthorizedException;

public class GroupDeletionService extends AbstractGroupService {

    public GroupDeletionService(InterMineAPI im) {
        super(im);
    }

    @Override
    protected String getGroupKey() {
        return null;
    }

    @Override
    protected void serveGroup() throws ServiceException {
        Profile user = getPermission().getProfile();
        if (!user.isLoggedIn()) {
            throw new UnauthorizedException();
        }
        Group g = getGroup();
        if (g == null || user.getUserId() != g.getOwnerId()) {
            throw new UnauthorizedException("You must be the group owner to delete it");
        }
        getSharedBagManager().deleteGroup(g);
    }

    @Override
    protected String getRelationshipKey(Relationship relationship) {
        return null;
    }

    @Override
    protected void serveRelationship(Relationship relationship)
            throws ServiceException {
        switch (relationship) {
        case members:
            removeMember();
            break;
        case lists:
            removeList();
            break;
        default:
            throw new ResourceNotFoundException("Don't know how to handle " + relationship);
        }
    }

    private void removeList() {
        Profile user = getPermission().getProfile();
        if (!user.isLoggedIn()) {
            throw new UnauthorizedException();
        }
        String listName = getRequiredParameter("name");
        Map<String, InterMineBag> bags = im.getBagManager().getBags(user);
        InterMineBag bag = bags.get(listName);
        if (bag == null) {
            throw new BadRequestException("Unknown list: " + listName);
        }
        Group g = getGroup();
        // Owners can remove any bag, and users can remove their own bags.
        if (!(g.getOwnerId() == user.getUserId() || bag.getProfileId() != user.getUserId())) {
            throw new UnauthorizedException("You do not have permission to remove this list.");
        }
        Profile bagOwner = im.getProfileManager().getProfile(bag.getProfileId());
        SharedBagManager sbm = getSharedBagManager();
        try {
            sbm.unshareBagFromGroup(bagOwner, bag.getName(), g);
        } catch (BagNotFound e) {
            throw new InternalErrorException(e);
        } catch (BadGroupPermission e) {
            throw new BadRequestException(e);
        } catch (ShareStateError e) {
            throw new BadRequestException(e);
        }
    }

    private void removeMember() {
        Profile user = getPermission().getProfile();
        if (!user.isLoggedIn()) {
            throw new UnauthorizedException();
        }
        // If no name is provided, remove the requester.
        String memberName = getOptionalParameter("name", user.getName());
        Profile oldMember = im.getProfileManager().getProfile(memberName);
        if (oldMember == null) throw new BadRequestException("Unknown member: " + oldMember);
        Group g = getGroup();
        SharedBagManager sbm = getSharedBagManager();
        // Owners can remove anyone, normal members can only remove themselves.
        if (!(g.getOwnerId() != user.getUserId() || oldMember.getUserId() != user.getUserId())) {
            throw new UnauthorizedException("You do not have permission to remove this member");
        }
        try {
            sbm.removeUserFromGroup(g, oldMember);
        } catch (ShareStateError e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    protected String getGroupsKey() {
        return null;
    }

    @Override
    protected void serveGroups() throws ServiceException {
        throw new MethodNotAllowed("DELETE");
    }


    @Override
    protected boolean returnsArray() {
        return false;
    }
}
