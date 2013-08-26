package us.kbase.workspace.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.Workspaces;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

//TODO test vs. auth'd mongo
@RunWith(Parameterized.class)
public class TestWorkspaces {

	public static final String M_USER = "test.mongo.user";
	public static final String M_PWD = "test.mongo.pwd";
	public static Workspaces[] TEST_WORKSPACES = new Workspaces[2];
	public static final String LONG_TEXT_PART = "Passersby were amazed by the unusually large amounts of blood. ";
	public static String LONG_TEXT = "";

	@Parameters
	public static Collection<Object[]> generateData() throws Exception {
		setUpWorkspaces();
		return Arrays.asList(new Object[][] {
				{TEST_WORKSPACES[0]},
				{TEST_WORKSPACES[1]}
		});
	}
	
	public final Workspaces ws;
	
	public static void setUpWorkspaces() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		String host = System.getProperty("test.mongo.host");
		String db1 = System.getProperty("test.mongo.db");
		String db2 = System.getProperty("test.mongo.db2");
		String mUser = System.getProperty(M_USER);
		String mPwd = System.getProperty(M_PWD);
		String shockurl = System.getProperty("test.shock.url");
		String shockuser = System.getProperty("test.user.noemail");
		String shockpwd = System.getProperty("test.pwd.noemail");
		
		if (mUser.equals("")) {
			mUser = null;
		}
		if (mPwd.equals("")) {
			mPwd = null;
		}
		if (mUser == null ^ mPwd == null) {
			System.err.println(String.format("Must provide both %s and %s ",
					M_USER, M_PWD) + "params for testing if authentication " + 
					"is to be used");
			System.exit(1);
		}
		System.out.print("Mongo auth params are user: " + mUser + " pwd: ");
		if (mPwd != null && mPwd.length() > 0) {
			System.out.println("[redacted]");
		} else {
			System.out.println(mPwd);
		}
		//Set up mongo backend database
		DB mdb = new MongoClient(host).getDB(db1);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		DBObject dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		mdb.getCollection("workspaces").remove(dbo);
		mdb.getCollection("workspaceACLs").remove(dbo);
		dbo.put("backend", "gridFS");
		mdb.getCollection("settings").insert(dbo);
		Database db = null;
		if (mUser != null) {
			db = new MongoDatabase(host, db1, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db1, shockpwd);
		}
		TEST_WORKSPACES[0] = new Workspaces(db);
		assertTrue("GridFS backend setup failed", TEST_WORKSPACES[0].getBackendType().equals("GridFS"));
		
		//Set up shock backend database
		mdb = new MongoClient(host).getDB(db2);
		if (mUser != null) {
			mdb.authenticate(mUser, mPwd.toCharArray());
		}
		dbo = new BasicDBObject();
		mdb.getCollection("settings").remove(dbo);
		mdb.getCollection("workspaces").remove(dbo);
		mdb.getCollection("workspaceACLs").remove(dbo);
		dbo.put("backend", "shock");
		dbo.put("shock_user", shockuser);
		dbo.put("shock_location", shockurl);
		mdb.getCollection("settings").insert(dbo);
		if (mUser != null) {
			db = new MongoDatabase(host, db2, shockpwd, mUser, mPwd);
		} else {
			db = new MongoDatabase(host, db2, shockpwd);
		}
		TEST_WORKSPACES[1] = new Workspaces(db);
		assertTrue("Shock backend setup failed", TEST_WORKSPACES[1].getBackendType().equals("Shock"));
		for (int i = 0; i < 17; i++) {
			LONG_TEXT += LONG_TEXT_PART;
		}
	}
	
	public TestWorkspaces(Workspaces ws) {
		this.ws = ws;
	}
	
	@Test
	public void testWorkspaceDescription() throws PreExistingWorkspaceException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException {
		ws.createWorkspace("auser", "lt", false, LONG_TEXT);
		ws.createWorkspace("auser", "ltp", false, LONG_TEXT_PART);
		ws.createWorkspace("auser", "ltn", false, null);
		String desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("lt"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT.substring(0, 1000)));
		desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("ltp"));
		assertThat("Workspace description incorrect", desc, is(LONG_TEXT_PART));
		desc = ws.getWorkspaceDescription("auser", new WorkspaceIdentifier("ltn"));
		assertNull("Workspace description incorrect", desc);
	}
	
	private void checkMeta(WorkspaceMetaData meta, String owner, String name,
			Permission perm, boolean globalread, int id, Date moddate) {
		checkMeta(meta, owner, name, perm, globalread);
		assertThat("ws id correct", meta.getId(), is(id));
		assertThat("ws mod date correct", meta.getModDate(), is(moddate));
	}
	
	private void checkMeta(WorkspaceMetaData meta, String owner, String name,
			Permission perm, boolean globalread) {
		assertThat("ws owner correct", meta.getOwner(), is(owner));
		assertThat("ws name correct", meta.getName(), is(name));
		assertThat("ws permissions correct", meta.getUserPermission(), is(perm));
		assertThat("ws global read correct", meta.isGloballyReadable(), is(globalread));
	}
	
	@Test
	public void testCreateWorkspaceAndGetMeta() throws PreExistingWorkspaceException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException {
		WorkspaceMetaData meta = ws.createWorkspace("auser", "foo", false, "eeswaffertheen");
		checkMeta(meta, "auser", "foo", Permission.OWNER, false);
		int id = meta.getId();
		Date moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData(new WorkspaceIdentifier(id), "auser");
		checkMeta(meta, "auser", "foo", Permission.OWNER, false, id, moddate);
		meta = ws.getWorkspaceMetaData(new WorkspaceIdentifier("foo"), "auser");
		checkMeta(meta, "auser", "foo", Permission.OWNER, false, id, moddate);
		
		meta = ws.createWorkspace("anotherfnuser", "anotherfnuser:MrT", true, "Ipitythefoolthatdon'teatMrTbreakfastcereal");
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true);
		id = meta.getId();
		moddate = meta.getModDate();
		meta = ws.getWorkspaceMetaData(new WorkspaceIdentifier(id), "anotherfnuser");
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
		meta = ws.getWorkspaceMetaData(new WorkspaceIdentifier("anotherfnuser:MrT"), "anotherfnuser");
		checkMeta(meta, "anotherfnuser", "anotherfnuser:MrT", Permission.OWNER, true, id, moddate);
	}
	
	@Test
	public void testCreateWorkspaceAndWorkspaceIdentifierWithBadInput()
			throws PreExistingWorkspaceException {
		List<List<String>> userWS = new ArrayList<List<String>>();
		//test a few funny chars in the ws name
		userWS.add(Arrays.asList("afaeaafe", "afe_aff*afea",
				"Illegal character in workspace name afe_aff*afea: *"));
		userWS.add(Arrays.asList("afaeaafe", "afeaff/af*ea",
				"Illegal character in workspace name afeaff/af*ea: /"));
		userWS.add(Arrays.asList("afaeaafe", "af?eaff*afea",
				"Illegal character in workspace name af?eaff*afea: ?"));
		//check missing ws name
		userWS.add(Arrays.asList("afaeaafe", null,
				"name cannot be null and must have at least one character"));
		userWS.add(Arrays.asList("afaeaafe", "",
				"name cannot be null and must have at least one character"));
		//check missing user and/or workspace name in compound name
		userWS.add(Arrays.asList("afaeaafe", ":",
				"Workspace name missing from :"));
		userWS.add(Arrays.asList("afaeaafe", "foo:",
				"Workspace name missing from foo:"));
		userWS.add(Arrays.asList("afaeaafe", ":foo",
				"User name missing from :foo"));
		//check multiple delims
		userWS.add(Arrays.asList("afaeaafe", "foo:a:foo",
				"Workspace name foo:a:foo may only contain one : delimiter"));
		userWS.add(Arrays.asList("afaeaafe", "foo::foo",
				"Workspace name foo::foo may only contain one : delimiter"));
		
		for (List<String> testdata: userWS) {
			String wksps = testdata.get(1);
			try {
				new WorkspaceIdentifier(wksps);
				fail(String.format("able to create workspace identifier with illegal input ws %s",
						wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
		}
		
		//check missing user name
		userWS.add(Arrays.asList(null, "foo",
				"user cannot be null and must have at least one character"));
		userWS.add(Arrays.asList("", "foo",
				"user cannot be null and must have at least one character"));
		//user must match prefix
		userWS.add(Arrays.asList("auser", "notauser:foo", 
				"Workspace name notauser:foo must only contain the user name auser prior to the : delimiter"));
		
		for (List<String> testdata: userWS) {
			String user = testdata.get(0);
			String wksps = testdata.get(1);
			try {
				ws.createWorkspace(user, wksps, false, "iswaffertheen");
				fail(String.format("able to create workspace with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
			try {
				new WorkspaceIdentifier(wksps, user);
				fail(String.format("able to create workspace identifier with illegal input user: %s ws %s",
						user, wksps));
			} catch (IllegalArgumentException e) {
				assertThat("incorrect exception message", e.getLocalizedMessage(),
						is(testdata.get(2)));
			}
		}
	}
	
	@Test
	public void preExistingWorkspace() throws Exception {
		ws.createWorkspace("a", "preexist", false, null);
		try {
			ws.createWorkspace("b", "preexist", false, null);
			fail("able to create same workspace twice");
		} catch (PreExistingWorkspaceException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Workspace preexist already exists"));
		}
	}
	
	@Test
	public void createWorkspaceWithIllegalUser() throws Exception {
		try {
			ws.createWorkspace("*", "foo", false, null);
			fail("able to create workspace with illegal character in username");
		} catch (IllegalArgumentException e) {
			assertThat("exception message correct", e.getLocalizedMessage(),
					is("Illegal user name: *"));
		}
	}
	
	public void permissions() throws PreExistingWorkspaceException, NoSuchWorkspaceException {
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("preexist");
		ws.createWorkspace("a", "perms", false, null);
		Map<String, Permission> expect = new HashMap<String, Permission>();
		expect.put("a", Permission.OWNER);
		assertThat("ws has correct perms for owner", ws.getPermissions(wsi, "a"), is(expect));
		expect.clear();
		expect.put("b", Permission.NONE);
		assertThat("ws has correct perms for random user", ws.getPermissions(wsi, "b"), is(expect));
//		ws.createWorkspace
	}
}