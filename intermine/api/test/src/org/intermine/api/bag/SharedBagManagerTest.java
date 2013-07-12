package org.intermine.api.bag;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.intermine.api.InterMineAPITestCase;
import org.intermine.api.bag.Group.Field;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.ProfileManager;
import org.intermine.api.profile.TagManager;
import org.intermine.api.tag.TagNames;
import org.intermine.api.types.Pair;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.junit.Test;

public class SharedBagManagerTest extends InterMineAPITestCase {

	private SharedBagManager underTest;
	private static int idCounter = 0;
	private ProfileManager pm;
	private Profile owner, memberA, memberB, thirdParty;
	private InterMineBag addressBag, bankBag, companyBag, departmentBag, employeeBag, globalBag;
	private BagManager bm;

	public SharedBagManagerTest(String arg) {
		super(arg);
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		pm = im.getProfileManager();
		bm = im.getBagManager();
		underTest = SharedBagManager.getInstance(pm);

		setUpProfiles();
		setUpBagsAndTags();
	}
	
	@Override
	protected void clearUserprofile() throws Exception {
		super.clearUserprofile();
		ObjectStoreInterMineImpl imos = (ObjectStoreInterMineImpl) uosw;
		Connection con = imos.getConnection();
		con.setAutoCommit(true);
		con.createStatement().executeUpdate("DELETE FROM " + SharedBagManager.USERGROUPS);
		con.createStatement().executeUpdate("DELETE FROM " + SharedBagManager.GROUP_MEMBERSHIPS);
		con.createStatement().executeUpdate("DELETE FROM " + SharedBagManager.GROUP_BAGS);
		imos.releaseConnection(con);
	}

	private void setUpProfiles() {
		owner = makeProfile("owner");
		memberA = makeProfile("memberA");
		memberB = makeProfile("memberB");
		thirdParty = makeProfile("thirdParty");
	}
	
	private void setUpBagsAndTags() throws Exception {
        Map<String, List<FieldDescriptor>>  classKeys = im.getClassKeys();

        Profile superUser = pm.getSuperuserProfile();
        globalBag = superUser.createBag("globalBag", "Thing", "", classKeys);
        addressBag = owner.createBag("addressBag", "Address", "", classKeys);
        bankBag = owner.createBag("bankBag", "Bank", "", classKeys);
        companyBag = owner.createBag("companyBag", "Company", "", classKeys);
        departmentBag = memberA.createBag("departmentBag", "Department", "", classKeys);
        employeeBag = thirdParty.createBag("employeeBag", "Employee", "", classKeys);
        
        TagManager tm = im.getTagManager();

        tm.addTag(TagNames.IM_PUBLIC, globalBag, superUser);
    }
	
	private Profile makeProfile(String name) {
		Profile p = new Profile(pm, name, idCounter++, "password",
		    new HashMap(), new HashMap(), new HashMap(), true, false);
		pm.createProfile(p);
		return p;
	}

	@Test
	public void testInitialState() {
		Map<String, InterMineBag> ownerBags = bm.getBags(owner);
		assertEquals(4, ownerBags.size());
		assertTrue(ownerBags.containsKey("globalBag"));
		assertTrue(ownerBags.containsKey("addressBag"));
		assertTrue(ownerBags.containsKey("bankBag"));
		assertTrue(ownerBags.containsKey("companyBag"));
		
		Map<String, InterMineBag> memberABags = bm.getBags(memberA);
		assertEquals(2, memberABags.size());
		assertTrue(memberABags.containsKey("globalBag"));
		assertTrue(memberABags.containsKey("departmentBag"));
	}
	
	@Test
	public void testCreateGroup() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertNotNull(g);
		assertEquals("my-lab", g.getName());
		assertEquals(owner.getUserId(), g.getOwner().getUserId());
		assertTrue(g.getUUID().length() > 0);
	}
	
	@Test
	public void testEditGroupName() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertEquals("my-lab", g.getName());
		Group edited = underTest.editGroupDetails(g, new Pair<Field, String>(Field.name, "your-lab"));
		assertEquals("your-lab", edited.getName());
		assertEquals(edited, underTest.getGroup(g.getUUID()));
	}
	
	@Test
	public void testEditGroupDesc() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertNull(g.getDescription());
		Group edited = underTest.editGroupDetails(g, new Pair<Field, String>(Field.description, "now with desc"));
		assertEquals("now with desc", edited.getDescription());
		assertEquals(edited, underTest.getGroup(g.getUUID()));
	}
	
	@Test
	public void testEditGroupDetails() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		Group edited = underTest.editGroupDetails(g,
				new Pair<Field, String>(Field.name, "different name"),
				new Pair<Field, String>(Field.description, "different desc"));
		assertEquals("different name", edited.getName());
		assertEquals("different desc", edited.getDescription());
		assertEquals(edited, underTest.getGroup(g.getUUID()));
	}
	
	@Test
	public void testGetGroup() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertTrue(g.equals(underTest.getGroup(g.getUUID())));
	}
	
	@Test
	public void testGetOwnGroups() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		Set<Group> groups = underTest.getOwnGroups(owner);
		assertTrue(groups.contains(g));
	}
	
	@Test
	public void testOwnerIsAlsoMember() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		Set<Group> groups = underTest.getGroupsUserBelongsTo(owner);
		assertTrue(groups.contains(g));
		assertTrue(underTest.isUserInGroup(owner, g));
	}
	
	@Test
	public void testNotMemberIsNotAMember() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		Set<Group> groups = underTest.getGroupsUserBelongsTo(thirdParty);
		assertFalse(groups.contains(g));
		assertFalse(underTest.isUserInGroup(thirdParty, g));
	}
	
	@Test
	public void testAddMember() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertTrue(underTest.getGroupsUserBelongsTo(memberA).isEmpty());
		underTest.addUserToGroup(g, memberA);
		Set<Group> groups = underTest.getGroupsUserBelongsTo(memberA);
		assertTrue(groups.contains(g));
		assertTrue(underTest.isUserInGroup(memberA, g));
		assertTrue(underTest.getOwnGroups(memberA).isEmpty());
	}
	
	@Test
	public void testGetMembers() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.addUserToGroup(g, memberA);
		Set<Profile> members = underTest.getGroupMembers(g);
		assertEquals(2, members.size());
		for (Profile member: members) {
			assertTrue("owner".equals(member.getName()) || "memberA".equals(member.getName()));
		}
	}
	
	@Test
	public void testRemoveMember() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.addUserToGroup(g, memberB);
		assertTrue(underTest.isUserInGroup(memberB, g));
		underTest.removeUserFromGroup(g, memberB);
		assertFalse(underTest.isUserInGroup(memberB, g));
	}
	
	@Test
	public void testShareBagWithGroup() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		assertFalse(underTest.isBagSharedWithGroup(owner, "addressBag", g));
		underTest.shareBagWithGroup(owner, "addressBag", g);
		assertTrue(underTest.isBagSharedWithGroup(owner, "addressBag", g));
	}
	
	@Test
	public void testUnshareBagWithGroup() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.shareBagWithGroup(owner, "addressBag", g);
		assertTrue(underTest.isBagSharedWithGroup(owner, "addressBag", g));
		underTest.unshareBagFromGroup(owner, "addressBag", g);
		assertFalse(underTest.isBagSharedWithGroup(owner, "addressBag", g));
	}
	
	@Test
	public void testMembersGetAccessToSharedBags() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.addUserToGroup(g, memberA);
		underTest.shareBagWithGroup(owner, "addressBag", g);
		Map<String, InterMineBag> sharedBags = underTest.getSharedBags(memberA);
		assertEquals(1, sharedBags.size());
		assertTrue(sharedBags.containsKey("addressBag"));
		Map<String, InterMineBag> accessibleBags = bm.getBags(memberA);
		assertEquals(3, accessibleBags.size());
		assertTrue(accessibleBags.containsKey("addressBag"));
	}
	
	@Test
	public void testRevokingMembershipRevokesAccess() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.addUserToGroup(g, memberA);
		underTest.addUserToGroup(g, memberB);
		underTest.shareBagWithGroup(owner, "bankBag", g);
		Map<String, InterMineBag> accessibleBags = bm.getBags(memberB);
		assertTrue(accessibleBags.containsKey("bankBag"));
		underTest.removeUserFromGroup(g, memberB);
		accessibleBags = bm.getBags(memberB);
		assertFalse(accessibleBags.containsKey("bankBag"));	
		accessibleBags = bm.getBags(memberA);
		assertTrue(accessibleBags.containsKey("bankBag"));
	}
	
	@Test
	public void testDeletingGroupRemovesMembershipsAndBags() {
		Group g = underTest.createGroup(owner, "my-lab", null);
		underTest.addUserToGroup(g, memberA);
		underTest.shareBagWithGroup(owner, "companyBag", g);
		assertTrue(underTest.getGroupsUserBelongsTo(memberA).contains(g));
		assertTrue(bm.getBags(memberA).containsKey("companyBag"));
		underTest.deleteGroup(g);
		Set<Group> memberAGroups = underTest.getGroupsUserBelongsTo(memberA);
		assertFalse(memberAGroups + " should not contain " + g, memberAGroups.contains(g));
		assertFalse(bm.getBags(memberA).containsKey("companyBag"));
	}
}

