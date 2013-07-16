package org.intermine.api.bag;

/*
 * Copyright (C) 2002-2013 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import static java.lang.String.format;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.api.bag.SharingInvite.NotFoundException;
import org.intermine.api.profile.BagDoesNotExistException;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.profile.StorableBag;
import org.intermine.api.profile.UserAlreadyShareBagException;
import org.intermine.api.profile.UserNotFoundException;
import org.intermine.api.search.ChangeEvent;
import org.intermine.api.search.CreationEvent;
import org.intermine.api.search.DeletionEvent;
import org.intermine.api.types.Pair;
import org.intermine.model.userprofile.SavedBag;
import org.intermine.model.userprofile.UserProfile;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.intermine.ObjectStoreWriterInterMineImpl;
import org.intermine.objectstore.intermine.SQLOperation;
import org.intermine.sql.DatabaseUtil;

/**
 * Singleton manager class for shared bags.
 * Implements retrieving, adding and deleting bag shared between users.
 * @author Daniela Butano
 */
public class SharedBagManager
{
    private static final Map<ProfileManager, SharedBagManager> sharedBagManagers
        = new HashMap<ProfileManager, SharedBagManager>();
    /** the table name **/
    public static final String SHARED_BAGS = "sharedbag";
    /** The name of the table to persist offers to share a bag with others. **/
    public static final String BAG_INVITES = "baginvites";
    public static final String USERGROUPS = "usergroups";
    public static final String GROUP_MEMBERSHIPS = "groupmemberships";
    public static final String GROUP_BAGS= "groupbags";
    protected ObjectStoreWriterInterMineImpl uosw;
    protected ProfileManager profileManager;
    private static final Logger LOG = Logger.getLogger(SharedBagManager.class);

    /**
     * Return the singleton SharedBagManager instance
     * @param profileManager the profile manager
     * @return the instance
     */
    public static SharedBagManager getInstance(ProfileManager profileManager) {
        if (!sharedBagManagers.containsKey(profileManager)) {
            sharedBagManagers.put(profileManager, new SharedBagManager(profileManager));
        }
        return sharedBagManagers.get(profileManager);
    }

    /**
     * Constructor. Use TagManagerFactory for creating tag manager.
     * @param profileOsWriter user profile object store
     */
    private SharedBagManager(ProfileManager profileManager) {
        this.profileManager = profileManager;
        try {
            this.uosw = (ObjectStoreWriterInterMineImpl) profileManager.getProfileObjectStoreWriter();
        } catch (ClassCastException e) {
            throw new RuntimeException("Hey, that wasn't an intermine object store writer");
        }
        try {
            checkDBTablesExist();
        } catch (SQLException sqle) {
            LOG.error("Error trying to create extra tables", sqle);
        }
    }
    
    private class TableCreator extends SQLOperation<Boolean> {

        private String table;

        TableCreator(String tableName) {
            this.table = tableName;
        }

        @Override
        public Boolean run(PreparedStatement stm) throws SQLException {
            if (!DatabaseUtil.tableExists(stm.getConnection(), table)) {
                LOG.info("Creating new table: " + table);
                stm.execute();
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    }
    private class StatementExecutor extends SQLOperation<Void> {
        private String description;
        StatementExecutor(String description) {
            this.description = description;
        }
        @Override
        public Void run(PreparedStatement stm) throws SQLException {
            LOG.info(description);
            stm.execute();
            return null;
        }
    }

    private void checkDBTablesExist() throws SQLException {
        /* Create and index shared bag table */
        final Boolean createdTable = uosw.performUnsafeOperation(
                getStatementCreatingTable(), new TableCreator(SHARED_BAGS));
        if (createdTable) {
            uosw.performUnsafeOperation(
                    getStatementCreatingIndex(),
                    new StatementExecutor("Creating shared bag table index"));
        }

        /* Create invite table */
        uosw.performUnsafeOperation(SharingInvite.getTableDefinition(), new TableCreator(SharingInvite.TABLE_NAME));
        /* Create the user-group table */
        uosw.performUnsafeOperation(getUserGroupTableDefinition(), new TableCreator(USERGROUPS));

        /* Create and index the membership table */
        final Boolean createdMembershipTable =
            uosw.performUnsafeOperation(getMembershipTableDefinition(), new TableCreator(GROUP_MEMBERSHIPS));
        if (createdMembershipTable) {
            uosw.performUnsafeOperation(getGroupMembershipIndexDefinition(), new StatementExecutor("Creating group membership index"));
        }
        /* Create and index the group items table */
        final Boolean createdGroupItemsTable =
            uosw.performUnsafeOperation(getGroupItemsTableDefinition(), new TableCreator(GROUP_BAGS));
        if (createdGroupItemsTable) {
            uosw.performUnsafeOperation(getGroupBagsIndexDefinition(), new StatementExecutor("Creating group items index"));
        }
    }

    /**
     * Return the sql query to create the table 'sharedbag'
     * @return the string containing the sql query
     */
    private static String getStatementCreatingTable() {
        return "CREATE TABLE " + SHARED_BAGS
             + "(bagid integer NOT NULL, userprofileid integer NOT NULL)";
    }

    private static String getUserGroupTableDefinition() {
        return "CREATE TABLE " + USERGROUPS
              + "(id SERIAL, uuid TEXT NOT NULL, name text NOT NULL, description text, ownerid integer NOT NULL)";
    }
    private static String getMembershipTableDefinition() {
        return "CREATE TABLE " + GROUP_MEMBERSHIPS
              + "(groupid integer NOT NULL, memberid integer NOT NULL)";
    }
    private static String getGroupItemsTableDefinition() {
        return "CREATE TABLE " + GROUP_BAGS
              + "(groupid integer NOT NULL, bagid integer NOT NULL)";
    }
 
    /**
     * Return the sql query to create the index in the 'sharedbag'
     * @return the string containing the sql query
     */
    private static String getStatementCreatingIndex() {
        return "CREATE UNIQUE INDEX sharedbag_index1 ON " + SHARED_BAGS
                + "(bagid, userprofileid)";
    }

    private static String getGroupMembershipIndexDefinition() {
        return "CREATE UNIQUE INDEX " + GROUP_MEMBERSHIPS + "_index1 ON "
              + GROUP_MEMBERSHIPS + " (groupid, memberid)";
    }
    private static String getGroupBagsIndexDefinition() {
        return "CREATE UNIQUE INDEX " + GROUP_BAGS + "_index1 ON "
                + GROUP_BAGS + " (groupid, bagid)";
    }
    
    private static final String GET_SHARED_BAGS_SQL =
        "SELECT bag.name as bagname, u.username as sharer"
        + " FROM savedbag as bag, userprofile as u, " + SHARED_BAGS + " as share"
        + " WHERE share.bagid = bag.id AND share.userprofileid = ? AND bag.userprofileid = u.id";

    private static final String GET_GROUP_BAGS_SQL =
        "SELECT g.name as group, u.username as bagowner, b.name as bagname"
        + " FROM " + USERGROUPS + " AS g, userprofile as u, savedbag as b,"
        + " " + GROUP_MEMBERSHIPS + " AS gm, " + GROUP_BAGS + " AS gb"
        + " WHERE gm.memberid = ? AND gm.groupid = g.id AND gb.groupid = g.id "
        + " AND gb.bagid = b.id AND b.userprofileid = u.id";

    /**
     * Return a map containing the bags that the user in input has access to because they were shared by
     * someone else
     * @param profile the user profile
     * @return a map from bag name to bag
     */
    public Map<String, InterMineBag> getSharedBags(final Profile profile) {
        if (profile == null || !profile.isLoggedIn()) {
            return Collections.emptyMap();
        }
        // We have to loop over things twice, because otherwise we end up in 
        // the dreaded ObjectStore deadlock, since this DB has only a single
        // connection.
        Map<String, Set<String>> whatTheSharersShared = getDirectlySharedBags(profile);
        Map<String, Set<Pair<String, String>>> sharedThroughGroupMembership = getSharedThroughGroup(profile);
        Map<String, InterMineBag> ret = new HashMap<String, InterMineBag>();
        for (Entry<String, Set<String>> sharerAndTheirBags: whatTheSharersShared.entrySet()) {
            Profile sharer = profileManager.getProfile(sharerAndTheirBags.getKey());
            for (String bagName: sharerAndTheirBags.getValue()) {
                InterMineBag bag = sharer.getSavedBags().get(bagName);
                if (bag == null) {
                    LOG.warn("Shared bag doesn't exist: " + bagName);
                } else {
                    ret.put(bagName, bag);
                }
            }
        }
        for (Set<Pair<String, String>> groupSet: sharedThroughGroupMembership.values()) {
            for (Pair<String, String> sharerAndBag: groupSet) {
                Profile sharer = profileManager.getProfile(sharerAndBag.getKey());
                String bagName = sharerAndBag.getValue();
                InterMineBag bag = sharer.getSavedBags().get(bagName);
                if (bag == null) {
                    LOG.warn("Shared bag doesn't exist: " + bagName);
                } else {
                    ret.put(bagName, bag);
                }
            }
        }
        return ret;
    }
 
    private Map<String, Set<Pair<String, String>>> getSharedThroughGroup(final Profile profile) {
        try {
            return uosw.performUnsafeOperation(GET_GROUP_BAGS_SQL, new SQLOperation<Map<String, Set<Pair<String, String>>>>() {
                @Override
                public Map<String, Set<Pair<String, String>>> run(PreparedStatement stm) throws SQLException {
                    final Map<String, Set<Pair<String, String>>> ret = new HashMap<String, Set<Pair<String, String>>>();
                    stm.setInt(1, profile.getUserId());
                    ResultSet rs = stm.executeQuery();
                    while (rs.next()) {
                        String groupName = rs.getString("group");
                        String bagOwner = rs.getString("bagowner");
                        String bagName = rs.getString("bagname");
                        if (!ret.containsKey(groupName)) {
                            ret.put(groupName, new HashSet<Pair<String, String>>());
                        }
                        ret.get(groupName).add(new Pair<String, String>(bagOwner, bagName));
                    }
                    return ret;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving the group bags "
                    + "for the user : " + profile.getUserId(), e);
        }
    }

    private static final String GET_BAGS_IN_GROUP_SQL =
        "SELECT u.username, sb.name"
        + " FROM userprofile AS u, savedbag as sb, " + GROUP_BAGS + " AS gb"
        + " WHERE gb.groupid = ? AND gb.bagid = sb.id AND sb.userprofileid = u.id";

    public Set<InterMineBag> getBagsInGroup(final Group group) {
        if (group == null) throw new NullPointerException("group must not be null");
        List<Pair<String, String>> bagsInGroup;
        try {
            bagsInGroup = uosw.performUnsafeOperation(
                GET_BAGS_IN_GROUP_SQL,
                new SQLOperation<List<Pair<String, String>>>() {
                    @Override
                    public List<Pair<String, String>> run(PreparedStatement stm) throws SQLException {
                        List<Pair<String, String>> retval = new ArrayList<Pair<String, String>>();
                        stm.setInt(1, group.getGroupId());
                        ResultSet rs = stm.executeQuery();
                        while (rs.next()) {
                            Pair<String, String> row = new Pair<String, String>(rs.getString(1), rs.getString(2));
                            retval.add(row);
                        }
                        return retval;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving bag information");
        }
        Set<InterMineBag> retval = new HashSet<InterMineBag>();
        for (Pair<String, String> userAndBag: bagsInGroup) {
            Profile p = profileManager.getProfile(userAndBag.getKey());
            if (p == null) throw new RuntimeException("Profile not found: " + userAndBag.getKey());
            InterMineBag b = p.getSavedBags().get(userAndBag.getValue());
            if (b == null) throw new RuntimeException("Bag not found: " + userAndBag.getValue());
            retval.add(b);
        }
        return retval;
    }

    private Map<String, Set<String>> getDirectlySharedBags(final Profile profile) {
        try {
            return uosw.performUnsafeOperation(GET_SHARED_BAGS_SQL, new SQLOperation<Map<String, Set<String>>>() {
                @Override
                public Map<String, Set<String>> run(PreparedStatement stm) throws SQLException {
                    final Map<String, Set<String>> ret = new HashMap<String, Set<String>>();
                    stm.setInt(1, profile.getUserId());
                    ResultSet rs = stm.executeQuery();
                    while (rs.next()) {
                        String bagName = rs.getString("sharer");
                        if (!ret.containsKey(bagName)) {
                            ret.put(bagName, new HashSet<String>());
                        }
                        ret.get(bagName).add(rs.getString("bagname"));
                    }
                    return ret;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving the shared bags "
                    + "for the user : " + profile.getUserId(), e);
        }
    }
    
    private final static String USERS_WITH_ACCESS_SQL =
        "SELECT u.username"
        + " FROM userprofile as u, sharedbag as share"
        + " WHERE u.id = share.userprofileid AND share.bagid = ?"
        + " ORDER BY u.username ASC";

    /**
     * Return the users this bag is shared with.
     * 
     * This set does not include the name of the owner of the bag, and it doesn't take
     * global sharing into account.
     * 
     * @param bag the bag the users share
     * @return the list of users sharing the bag
     */
    public Set<String> getUsersWithAccessToBag(final StorableBag bag) {
        try {
            return uosw.performUnsafeOperation(USERS_WITH_ACCESS_SQL, new SQLOperation<Set<String>>() {
                @Override
                public Set<String> run(PreparedStatement stm) throws SQLException {
                    final Set<String> usersWithAccess = new LinkedHashSet<String>();
                    stm.setInt(1, bag.getSavedBagId());
                    ResultSet rs = stm.executeQuery();
                    while (rs.next()) {
                        usersWithAccess.add(rs.getString(1));
                    }
                    return usersWithAccess;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving the users sharing "
                    + "the bag : " + bag.getName(), e);
        }
    }
    
    /**
     * Generate an invitation to share a bag.
     * 
     * The invitation is a record of the invitation to share a bag, and records the
     * bag that is shared and whom it is meant to be shared with. This method generates a new
     * invitation, stores it in the persistent data-store that the bag is stored in,
     * and returns an object that represents that invitation.
     *
     * @param bag The list that we mean to share with someone.
     * @param userEmail An email address we are sending this invitation to.
     * @return An invitation.
     */
    public static SharingInvite inviteToShare(InterMineBag bag, String userEmail) {
        SharingInvite invite = new SharingInvite(bag, userEmail);
        try {
            invite.save();
        } catch (SQLException e) {
            throw new RuntimeException("SQL error. Possible token collision.", e);
        }
        return invite;
    }
    
    public void resolveInvitation(SharingInvite invitation, Profile accepter, boolean accepted)
            throws UserNotFoundException, UserAlreadyShareBagException, NotFoundException {
        if (accepted) {
            acceptInvitation(invitation, accepter);
        } else {
            rejectInvitation(invitation);
        }
        resolveInvitation(invitation, accepter, true);
    }
    
    public void rejectInvitation(SharingInvite invitation)
            throws UserNotFoundException, UserAlreadyShareBagException, NotFoundException {
        try { // Try this first, as we don't want to share unless this worked.
            invitation.setAccepted(false);
        } catch (SQLException e) {
            throw new RuntimeException("Error rejecting invitation", e);
        }
    }
    
    public void acceptInvitation(
            SharingInvite invitation,
            Profile accepter)
        throws UserNotFoundException, UserAlreadyShareBagException, NotFoundException {
        
        try { // Try this first, as we don't want to share unless this worked.
            invitation.setAccepted(true);
        } catch (SQLException e) {
            throw new RuntimeException("Error accepting invitation", e);
        }
        try {
            shareBagWithUser(invitation.getBag(), accepter.getUsername());
        } catch (UserNotFoundException e) {
            // Probably a temporary (non-persistent) user. Revert the invitation acceptance.
            try {
                invitation.unaccept();
            } catch (SQLException sqle) {
                throw new RuntimeException(
                    "Error accepting invitation. This invitation is no longer valid");
            }
            throw new NotFoundException("This is not a permanent user. Please log in");
        }
    }

    /**
     * Share the bag given in input with user which userName is given in input
     * @param bag the bag to share
     * @param userName the user which the bag is shared with
     * @throws UserNotFoundException if the user doesn't exist
     * @throws UserAlreadyShareBagException if the user already shares the list
     */
    public void shareBagWithUser(InterMineBag bag, String userName)
        throws UserNotFoundException, UserAlreadyShareBagException {
        UserProfile userProfile = profileManager.getUserProfile(userName);
        if (userProfile == null) {
            throw new UserNotFoundException("User " + userName + " doesn't exist");
        }
        storeShare(bag, userProfile);
    }
    
    private static final String STORE_SHARE_SQL = "INSERT INTO " + SHARED_BAGS + " VALUES(?, ?)";

    private void storeShare(final InterMineBag bag, final UserProfile sharedWith) 
        throws UserAlreadyShareBagException {
        final String userName = sharedWith.getUsername();
        try {
            uosw.performUnsafeOperation(STORE_SHARE_SQL, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, bag.getSavedBagId());
                    stm.setInt(2, sharedWith.getId());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new UserAlreadyShareBagException("Error sharing the "
                    + " the bag : " + bag.getSavedBagId()
                    + " with the user " + sharedWith.getId(), e);
        }
        informProfileOfChange(userName, new CreationEvent(bag));
    }

    /**
     * Perform a query to retrieve a bag's backing SavedBag
     * @param bagName the bagName
     * @param dateCreated the date when the bag has been created
     * @return the relevant SavedBag
     */
    public SavedBag getSavedBag(String bagName, String dateCreated) {
        SavedBag bag = new SavedBag();
        bag.setName(bagName);
        bag.setDateCreated(new Date(Long.parseLong(dateCreated)));
        Set<String> fieldNames = new HashSet<String>();
        fieldNames.add("name");
        fieldNames.add("dateCreated");
        try {
            bag = (SavedBag) uosw.getObjectByExample(bag, fieldNames);
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Unable to load user profile", e);
        }
        return bag;
    }

    /**
     * Share the bag given in input with user which userName is given in input
     * To be used ONLY when deserialising the user-profile from XML.
     * @param bagName the bag name to share
     * @param dateCreated the date when the bag has been created
     * @param userName the user which the bag is shared with
     * @throws UserNotFoundException if the user does't exist
     * @throws BagDoesNotExistException if the bag does't exist
     */
    private void shareBagWithUser(String bagName, String dateCreated, String userName)
        throws UserNotFoundException, BagDoesNotExistException {
        final UserProfile sharedWith = profileManager.getUserProfile(userName);
        if (sharedWith == null) {
            throw new UserNotFoundException("User " + userName + " doesn't exist");
        }
        final SavedBag bag = getSavedBag(bagName, dateCreated);
        if (bag == null) {
            throw new BagDoesNotExistException("There is not bag named '" + bagName + "'");
        }
        try {
            uosw.performUnsafeOperation(STORE_SHARE_SQL, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, bag.getId());
                    stm.setInt(2, sharedWith.getId());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new UserAlreadyShareBagException(bag, sharedWith);
        }
    }

    private static final String DELETE_SHARE_SQL =
        "DELETE FROM sharedbag WHERE userprofileid = ? AND bagid = ?";

    private static final String NOT_ALREADY_SHARED_MSG =
        "This bag (%s) was not shared with this user (%s)";
    
    private static final String UNSHARING_ERROR_MSG =
        "Error unsharing this bag (%s:%d) from this user (%s:%d)";

    /**
     * Delete the sharing between the user and the bag given in input
     * @param bag the bag shared
     * @param userName the user name sharing the bag
     */
    public void unshareBagWithUser(final InterMineBag bag, final String userName) {
        final UserProfile userProfile = profileManager.getUserProfile(userName);
        if (userProfile == null) {
            LOG.warn("User " + userName + " doesn't exist");
            return;
        }
        try {
            Integer deleted = uosw.performUnsafeOperation(DELETE_SHARE_SQL, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, userProfile.getId());
                    stm.setInt(2, bag.getSavedBagId());
                    return stm.executeUpdate();
                }
            });
            if (deleted > 0) {
                informProfileOfChange(userName, new DeletionEvent(bag));
            } else {
                LOG.warn(format(NOT_ALREADY_SHARED_MSG, bag, userName));
            }
        } catch (SQLException e) {
            throw new RuntimeException(format(UNSHARING_ERROR_MSG,
                bag.getName(), bag.getSavedBagId(),
                userProfile.getUsername(), userProfile.getId()), e);
        }
    }

    private void informProfileOfChange(final String name, final ChangeEvent evt) {
        if (profileManager.isProfileCached(name)) {
            informProfileOfChange(profileManager.getProfile(name), evt);
        }
    }

    private void informProfileOfChange(final Profile profile, final ChangeEvent evt) {
        profile.getSearchRepository().receiveEvent(evt);
    }

    private static final String UNSHARE_BAG_SQL = 
        "DELETE FROM " + SHARED_BAGS + " WHERE bagid = ?";

    private static final String UNSHARE_BAG_ERROR_MSG =
        "Error removing all shares of this bag: %s:%d";

    /**
     * Delete the sharing between the bag and all the users sharing the bag.
     * Method used when a bag is deleted.
     * @param bag the bag that has been shared by users
     */
    public void unshareBagWithAllUsers(final StorableBag bag) {
        Collection<String> usersWithAccess = getUsersWithAccessToBag(bag);
        if (usersWithAccess.isEmpty()) {
            return;
        }
        try {
            uosw.performUnsafeOperation(UNSHARE_BAG_SQL, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, bag.getSavedBagId());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(
                format(UNSHARE_BAG_ERROR_MSG, bag.getName(), bag.getSavedBagId()),
                e
            );
        }
        //update user repository for all users sharing that bag
        ChangeEvent evt = new DeletionEvent(bag);
        for (String userName : usersWithAccess) {
            informProfileOfChange(userName, evt);
        }
    }

    private static final String REMOVE_USERS_INVITES_SQL =
        "DELETE FROM " + SharingInvite.TABLE_NAME + " WHERE inviterid = ?";

    public void removeAllInvitesBy(final Integer userId) {
        if (userId == null) {
            LOG.warn("I can't remove invites when the user-id is null");
            return;
        }
        try {
            uosw.performUnsafeOperation(REMOVE_USERS_INVITES_SQL, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, userId.intValue());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Errors removing invites", e);
        }
    }
    
    private static final String DELETE_SHARES_WITH =
        "DELETE FROM " + SHARED_BAGS + " WHERE userprofileid = ?";

    public void removeAllSharesInvolving(final Integer userId) {
        if (userId == null) {
            return;
        }
        try {
            uosw.performUnsafeOperation(DELETE_SHARES_WITH, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, userId.intValue());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Errors removing shares", e);
        }
    }
    
    private static final String DELETE_USERS_SHARES_WITH = DELETE_SHARES_WITH +
        " AND bagid IN (SELECT b.id FROM savedbag AS b WHERE b.userprofileid = ?)";

    public void unshareAllBagsFromUser(final Profile owner, final Profile recipient) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        if (recipient == null) {
            throw new IllegalArgumentException("recipient must not be null");
        }
        try {
            uosw.performUnsafeOperation(DELETE_USERS_SHARES_WITH, new SQLOperation<Integer>() {
                @Override
                public Integer run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, recipient.getUserId());
                    stm.setInt(2, owner.getUserId());
                    return stm.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error removing shares", e);
        }
    }

    private static final String GET_GROUP_MEMBERS_SQL =
        "SELECT u.username AS member"
        + " FROM userprofile as u, " + GROUP_MEMBERSHIPS + " AS gm"
        + " WHERE gm.groupid = ? AND gm.memberid = u.id";

    /**
     * Return the members of a group.
     * @param groupName The name of the group.
     * @return A collection of profiles.
     */
    public Set<Profile> getGroupMembers(final Group group) {
        if (group == null) throw new NullPointerException("group must not be null");
        final Set<String> memberNames = new HashSet<String>();
        try {
            uosw.performUnsafeOperation(GET_GROUP_MEMBERS_SQL, new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, group.getGroupId());
                    ResultSet rs = stm.executeQuery();
                    while (rs.next()) {
                        String member = rs.getString("member");
                        memberNames.add(member);
                    }
                    return null;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching group members", e);
        }
        final Set<Profile> ret = new HashSet<Profile>();
        for (String member: memberNames) {
            ret.add(profileManager.getProfile(member));
        }
        return ret;
    }

    private static final String IS_USER_IN_GROUP_SQL =
        "SELECT * FROM " + GROUP_MEMBERSHIPS + " AS gm"
        + " WHERE gm.memberid = ? AND gm.groupid= ?";

    public boolean isUserInGroup(final Profile user, final Group group) {
        if (user == null) throw new NullPointerException("user is null");
        if (group == null) throw new NullPointerException("group is null");
        try {
            return uosw.performUnsafeOperation(IS_USER_IN_GROUP_SQL, new SQLOperation<Boolean>() {
                @Override
                public Boolean run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, user.getUserId());
                    stm.setInt(2, group.getGroupId());
                    ResultSet rs = stm.executeQuery();
                    return rs.next();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Could not read group details.", e);
        }
    }

    public Group getGroup(final String uuid) {
        if (StringUtils.isBlank(uuid)) {
            throw new IllegalArgumentException("uuid must not be blank");
        }
        try {
            return uosw.performUnsafeOperation(
                "SELECT id, name, description, ownerid FROM " + USERGROUPS + " WHERE uuid = ?", new SQLOperation<Group>() {
                    @Override
                    public Group run(PreparedStatement stm) throws SQLException {
                        stm.setString(1, uuid);
                        ResultSet rs = stm.executeQuery();

                        while (rs.next()) {
                            int id, owner;
                            String name, description;
                            id = rs.getInt(1);
                            owner = rs.getInt(4);
                            name = rs.getString(2);
                            description = rs.getString(3);
                            Group g = new Group(id, name, description, owner, uuid);
                            g.setProfileManager(profileManager);
                            return g;
                        }
                        return null;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving group info.", e);
        }
    }

    public Set<Group> getOwnGroups(final Profile owner) {
        if (owner == null) throw new NullPointerException("owner must not be null");
        if (!owner.isLoggedIn()) throw new IllegalStateException("This is a temporary profile");
        try {
            return uosw.performUnsafeOperation(
                "SELECT id, uuid, name, description FROM " + USERGROUPS + " WHERE ownerid = ?",
                new SQLOperation<Set<Group>>() {
                    @Override
                    public Set<Group> run(PreparedStatement stm) throws SQLException {
                        Set<Group> ret = new HashSet<Group>();
                        stm.setInt(1, owner.getUserId());
                        ResultSet rs = stm.executeQuery();
                        while (rs.next()) {
                            int id;
                            String uuid, name, description;
                            id = rs.getInt(1);
                            uuid = rs.getString(2);
                            name = rs.getString(3);
                            description = rs.getString(4);
                            Group g = new Group(id, name, description, owner.getUserId(), uuid);
                            g.setProfileManager(profileManager);
                            ret.add(g);
                        }
                        return ret;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve groups", e);
        }
    }

    private static final String GET_GROUPS_FOR_USER_SQL =
            "SELECT g.id, g.uuid, g.name, g.description, g.ownerid"
            + " FROM " + USERGROUPS + " AS g, " + GROUP_MEMBERSHIPS + " AS gm"
            + " WHERE g.id = gm.groupid AND gm.memberid = ?";

    public Set<Group> getGroups(final Profile user) {
        if (user == null) throw new NullPointerException("owner must not be null");
        if (!user.isLoggedIn()) throw new IllegalStateException("This is a temporary profile");
        try {
            return uosw.performUnsafeOperation(
                GET_GROUPS_FOR_USER_SQL,
                new SQLOperation<Set<Group>>() {
                    @Override
                    public Set<Group> run(PreparedStatement stm) throws SQLException {
                        Set<Group> ret = new HashSet<Group>();
                        stm.setInt(1, user.getUserId());
                        ResultSet rs = stm.executeQuery();
                        while (rs.next()) {
                            int id, ownerId;
                            String uuid, name, description;
                            id = rs.getInt(1);
                            uuid = rs.getString(2);
                            name = rs.getString(3);
                            description = rs.getString(4);
                            ownerId = rs.getInt(5);
                            Group g = new Group(id, name, description, ownerId, uuid);
                            g.setProfileManager(profileManager);
                            ret.add(g);
                        }
                        return ret;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve groups", e);
        }
    }

    private static final String GET_GROUPS_FOR_BAG_SQL =
        "SELECT g.id, g.uuid, g.name, g.description, g.ownerid"
        + " FROM " + USERGROUPS + " AS g, " + GROUP_BAGS + " AS gb"
        + " WHERE gb.bagid = ? AND g.id = gb.groupid";

    public Set<Group> getGroups(final InterMineBag bag) {
        if (bag == null) throw new NullPointerException("bag must not be null");
        try {
            return uosw.performUnsafeOperation(
                GET_GROUPS_FOR_BAG_SQL,
                new SQLOperation<Set<Group>>() {
                    @Override
                    public Set<Group> run(PreparedStatement stm) throws SQLException {
                        Set<Group> ret = new HashSet<Group>();
                        stm.setInt(1, bag.getSavedBagId());
                        ResultSet rs = stm.executeQuery();
                        while (rs.next()) {
                            int id, ownerId;
                            String uuid, name, description;
                            id = rs.getInt(1);
                            uuid = rs.getString(2);
                            name = rs.getString(3);
                            description = rs.getString(4);
                            ownerId = rs.getInt(5);
                            Group g = new Group(id, name, description, ownerId, uuid);
                            g.setProfileManager(profileManager);
                            ret.add(g);
                        }
                        return ret;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving groups.", e);
        }
    }
    
    private static final String EDIT_GROUP_SQL = "UPDATE " + USERGROUPS
            + " SET %s WHERE id = ?";
    
    public final Group editGroupDetails(final Group group, final Entry<Group.Field, String>... newValues) {
        Map<Group.Field, String> valueMap = new HashMap<Group.Field, String>();
        for (Entry<Group.Field, String> entry: newValues) {
            valueMap.put(entry.getKey(), entry.getValue());
        }
        return editGroupDetails(group, valueMap);
    }
    
    public Group editGroupDetails(final Group group, final Map<Group.Field, String> newValues) {
        if (group == null) throw new NullPointerException("group must not be null");
        if (newValues == null) throw new NullPointerException("newValues must not be null");
        if (newValues.isEmpty()) {
            return group; // No change.
        }
        StringBuilder sb = new StringBuilder();
        final List<Group.Field> fields = new ArrayList<Group.Field>(newValues.keySet());
        for (Group.Field field: fields) {
            if (sb.length() > 0) sb.append(",");
            sb.append(" ").append(field).append(" = ?");
        }
        String sql = String.format(EDIT_GROUP_SQL, sb.toString());
        try {
            uosw.performUnsafeOperation(sql, new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    int paramIdx = 1;
                    for (Group.Field field: fields) {
                        stm.setString(paramIdx++, newValues.get(field));
                    }
                    stm.setInt(paramIdx, group.getGroupId());
                    int changed = stm.executeUpdate();
                    if (changed != 1) {
                        throw new SQLException("Expected to change one row; affected " + changed);
                    }
                    return null;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Could not edit group.", e);
        }
        return getGroup(group.getUUID());
    }

    private static final String STORE_GROUP_SQL = "INSERT INTO " + USERGROUPS
            + " (uuid, name, description, ownerid) VALUES (?, ?, ?, ?)";

    public Group createGroup(final Profile owner, final String name, final String description) {
        if (owner == null) {
            throw new NullPointerException("owner must not be null.");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank.");
        }
        if (!owner.isLoggedIn()) throw new IllegalArgumentException("This owner is a temporary profile");
        final UUID uuid = UUID.randomUUID();
        try {
            uosw.performUnsafeOperation(STORE_GROUP_SQL, new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    stm.setString(1, uuid.toString());
                    stm.setString(2, name);
                    stm.setString(3, description);
                    stm.setInt(4, owner.getUserId());
                    int inserted = stm.executeUpdate();
                    if (inserted != 1) {
                        throw new SQLException("No group created.");
                    }
                    return null;
                }
                
            });
        } catch (SQLException e) {
            throw new RuntimeException("Could not create group '" + name + "'.", e);
        }
        Group g = getGroup(uuid.toString());
        try {
            // Owners are always members of their own groups.
            addUserToGroup(g, owner);
        } catch (ShareStateError e) {
            throw new RuntimeException(e);
        }
        return g;
    }
    
    private class ItemDeleter extends SQLOperation<Void> {
        private int id;
        private String sql;
        private Integer min, max;

        ItemDeleter(int id, String sql, Integer min, Integer max) {
            this.id = id;
            this.sql = sql;
            this.min = min;
            this.max = max;
        }

        @Override
        public Void run(PreparedStatement stm) throws SQLException {
            stm.setInt(1, id);
            int changed = stm.executeUpdate();
            if (min != null && changed < min) {
                error("fewer", changed, min);
            }
            if (max != null && changed > max) {
                error("more", changed, max);
            }
            return null;
        }
        
        private void error(String comp, int real, int expected) throws SQLException {
            throw new SQLException(String.format(
                "Error running [%s], deleted %s rows (%d) than expected (%d)", sql, comp, real, expected));
        }
    }

    /**
     * The caller is responsible for checking that this group may be legitimately deleted.
     * @param group
     */
    public void deleteGroup(final Group group) {
        Map<String, ItemDeleter> operations = new LinkedHashMap<String, ItemDeleter>();
        String sql;
        sql = "DELETE FROM " + USERGROUPS + " WHERE id = ?";
        operations.put(sql, new ItemDeleter(group.getGroupId(), sql, 1, 1));
        sql = "DELETE FROM " + GROUP_MEMBERSHIPS + " WHERE groupid = ?";
        operations.put(sql, new ItemDeleter(group.getGroupId(), sql, 0, null));
        sql = "DELETE FROM " + GROUP_BAGS + " WHERE groupid = ?";
        operations.put(sql, new ItemDeleter(group.getGroupId(), sql, 0, null));
        
        for (String cmd: operations.keySet()) {
            try {
                uosw.performUnsafeOperation(cmd, operations.get(cmd));
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting group.", e);
            }
        }
    }

    private static final String ADD_MEMBER_SQL = "INSERT INTO " + GROUP_MEMBERSHIPS + " VALUES (?, ?)";

    /**
     * The caller is responsible for checking that this member may be added to this group.
     * @param group The group to add the user to.
     * @param newMember The new member.
     * @throws ShareStateError 
     */
    public void addUserToGroup(final Group group, final Profile newMember) throws ShareStateError {
        if (group == null) throw new NullPointerException("group cannot be null");
        if (newMember == null) throw new NullPointerException("new member cannot be null");
        if (!newMember.isLoggedIn()) throw new IllegalArgumentException("this this a temporary profile");

        if (isUserInGroup(newMember, group)) {
            throw new ShareStateError("This user is already in this group");
        }
        try {
            uosw.performUnsafeOperation(ADD_MEMBER_SQL, new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, group.getGroupId());
                    stm.setInt(2, newMember.getUserId());
                    int inserted = stm.executeUpdate();
                    if (inserted != 1) {
                        throw new SQLException("Failed to add member to group: " + inserted + " rows inserted");
                    }
                    return null;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error adding member to group", e);
        }
        for (InterMineBag bag: getBagsInGroup(group)) {
            informProfileOfChange(newMember.getUsername(), new CreationEvent(bag));
        }
    }

    private static final String REMOVE_USERS_BAGS_FROM_GROUP =
        "DELETE FROM " + GROUP_BAGS
        + " WHERE groupid = ?"
        + " AND bagid IN (SELECT b.id FROM savedbag as b WHERE b.userprofileid = ?)";
 
    public void removeUserFromGroup(final Group group, final Profile oldMember) throws ShareStateError {
        if (group == null) throw new NullPointerException("group cannot be null");
        if (oldMember == null) throw new NullPointerException("old member cannot be null");
        if (group.getOwnerId() == oldMember.getUserId()) {
            throw new IllegalStateException("Cannot remove this member - they own the group.");
        }
        if (!isUserInGroup(oldMember, group)) {
            throw new ShareStateError("This user is not in this group");
        }
        // Remove the bags first, because we really don't want to remove the user fail to
        // unshare their bags.
        try {
            uosw.performUnsafeOperation(REMOVE_USERS_BAGS_FROM_GROUP, new SQLOperation<Void>() {
               @Override
               public Void run(PreparedStatement stm) throws SQLException {
                   stm.setInt(1, group.getGroupId());
                   stm.setInt(2, oldMember.getUserId());
                   stm.executeUpdate();
                   return null;
               }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error removing bags from group.", e);
        }
        try {
            uosw.performUnsafeOperation(
                "DELETE FROM " + GROUP_MEMBERSHIPS + " WHERE groupid = ? AND memberid = ?",
                new SQLOperation<Void>() {
                    @Override
                    public Void run(PreparedStatement stm) throws SQLException {
                        stm.setInt(1, group.getGroupId());
                        stm.setInt(2, oldMember.getUserId());
                        int removed = stm.executeUpdate();
                        if (removed != 1) throw new SQLException("Wrong number of rows changed: " + removed);
                        return null;
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Could not remove user %s from group %s", oldMember.getName(), group.getName()));
        }
        for (InterMineBag bag: getBagsInGroup(group)) {
            informProfileOfChange(oldMember, new DeletionEvent(bag));
        }
    }

    public boolean isBagSharedWithGroup(final Profile owner, final String bagName, final Group group) throws BagNotFound, BadGroupPermission {
        if (owner == null) throw new NullPointerException("owner cannot be null");
        if (bagName == null) throw new NullPointerException("bagName cannot be null");
        if (group == null) throw new NullPointerException("group cannot be null");
        if (!owner.isLoggedIn()) throw new IllegalStateException("this is a temporary profile");
        final InterMineBag bag = owner.getSavedBags().get(bagName);
        if (bag == null) throw new BagNotFound(bagName);
        if (!isUserInGroup(owner, group)) {
            throw new BadGroupPermission(owner.getName(), group.getUUID());
        }

        try {
            return uosw.performUnsafeOperation(
                "SELECT * FROM " + GROUP_BAGS + " WHERE groupid = ? AND bagid = ?",
                new SQLOperation<Boolean>() {
                    @Override
                    public Boolean run(PreparedStatement stm) throws SQLException {
                        stm.setInt(1, group.getGroupId());
                        stm.setInt(2, bag.getSavedBagId());
                        ResultSet rs = stm.executeQuery();
                        return rs.next();
                    }
                }
            );
        } catch (SQLException e) {
            throw new RuntimeException("Could not read group contents", e);
        }
    }

    public void shareBagWithGroup(final Profile owner, final String bagName, final Group group)
            throws BagNotFound, BadGroupPermission, ShareStateError {
        if (owner == null) throw new NullPointerException("owner cannot be null");
        if (bagName == null) throw new NullPointerException("bagName cannot be null");
        if (group == null) throw new NullPointerException("group cannot be null");
        if (!owner.isLoggedIn()) throw new IllegalStateException("this is a temporary profile");
        final InterMineBag bag = owner.getSavedBags().get(bagName);
        if (bag == null) throw new BagNotFound(bagName);
        if (isBagSharedWithGroup(owner, bagName, group)) {
            throw new ShareStateError(String.format("%s is already shared with the %s group", bag.getName(), group.getName()));
        }

        try {
            uosw.performUnsafeOperation("INSERT INTO " + GROUP_BAGS + " VALUES (?, ?)", new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, group.getGroupId());
                    stm.setInt(2, bag.getSavedBagId());
                    int inserted = stm.executeUpdate();
                    if (inserted != 1) {
                        throw new SQLException("Expected one new record; " + inserted + " rows changed");
                    }
                    return null;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Could not share bag with group.", e);
        }
        for (Profile member: getGroupMembers(group)) {
            if (member.getUserId() != owner.getUserId()) {
                informProfileOfChange(member, new CreationEvent(bag));
            }
        }
    }

    public void unshareBagFromGroup(final Profile owner, final String bagName, final Group group)
            throws BagNotFound, BadGroupPermission, ShareStateError {
        if (owner == null) throw new NullPointerException("owner cannot be null");
        if (bagName == null) throw new NullPointerException("bagName cannot be null");
        if (group == null) throw new NullPointerException("group cannot be null");
        if (!owner.isLoggedIn()) throw new IllegalStateException("this is a temporary profile");
        final InterMineBag bag = owner.getSavedBags().get(bagName);
        if (bag == null) throw new BagNotFound(bagName);
        if (!isBagSharedWithGroup(owner, bagName, group)) {
            throw new ShareStateError(String.format("%s is not shared with the %s group", bag.getName(), group.getName()));
        }

        try {
            uosw.performUnsafeOperation("DELETE FROM " + GROUP_BAGS + " WHERE groupid = ? AND bagid = ?", new SQLOperation<Void>() {
                @Override
                public Void run(PreparedStatement stm) throws SQLException {
                    stm.setInt(1, group.getGroupId());
                    stm.setInt(2, bag.getSavedBagId());
                    int removed = stm.executeUpdate();
                    if (removed != 1) {
                        throw new SQLException("Expected one removed record; " + removed + " rows changed");
                    }
                    return null;
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException("Could not unshare bag with group.", e);
        }
        for (Profile member: getGroupMembers(group)) {
            if (member.getUserId() != owner.getUserId()) {
                informProfileOfChange(member, new DeletionEvent(bag));
            }
        }
    }
}
