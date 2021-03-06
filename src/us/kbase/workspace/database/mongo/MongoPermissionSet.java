package us.kbase.workspace.database.mongo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoPermissionSet implements PermissionSet {
	
	private final WorkspaceUser user;
	private final User globalUser;
	private final Map<ResolvedWorkspaceID, Permission> userPerms = 
			new HashMap<ResolvedWorkspaceID, Permission>();
	private final Map<ResolvedWorkspaceID, Boolean> worldRead = 
			new HashMap<ResolvedWorkspaceID, Boolean>();
	
	MongoPermissionSet(final WorkspaceUser user, final User globalUser) {
		if (globalUser == null) {
			throw new IllegalArgumentException(
					"Global user cannot be null");
		}
		this.user = user;
		this.globalUser = globalUser;
	}

	@Override
	public WorkspaceUser getUser() {
		return user;
	}
	
	@Override
	public User getGlobalUser() {
		return globalUser;
	}
	
	void setPermission(final ResolvedMongoWSID rwsi, Permission userPerm,
			final Permission globalPerm) {
		if (rwsi == null) {
			throw new IllegalArgumentException(
					"Mongo workspace ID cannot be null");
		}
		if (userPerms.containsKey(rwsi)) {
			throw new IllegalArgumentException("Permissions for workspace " + 
					rwsi.getID() + " have already been set");
		}
		if (userPerm == null) {
			userPerm = Permission.NONE;
		}
		if (globalPerm != null && Permission.READ.compareTo(globalPerm) < 0) {
			throw new IllegalArgumentException(
					"Illegal global permission in database: " + globalPerm);
		}
		final boolean globalread = Permission.READ.equals(globalPerm);
		if (userPerm.equals(Permission.NONE) && !globalread) {
			throw new IllegalArgumentException("Cannot add unreadable workspace");
		}
		userPerms.put(rwsi, userPerm);
		worldRead.put(rwsi, globalread);
	}
	
	@Override
	public Permission getPermission(final ResolvedWorkspaceID rwsi,
			final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getPermission(rwsi);
	}
	
	@Override
	public Permission getPermission(final ResolvedWorkspaceID rwsi) {
		final Permission p = getUserPermission(rwsi);
		if (Permission.NONE.equals(p)) {
			return isWorldReadable(rwsi) ? Permission.READ : p;
		} else {
			return p;
		}
	}
	
	@Override
	public boolean hasPermission(final ResolvedWorkspaceID rwsi,
			final Permission perm) {
		return getPermission(rwsi).compareTo(perm) > -1;
	}

	@Override
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi,
			final boolean returnNone) {
		if (returnNone && !hasWorkspace(rwsi)) {
			return Permission.NONE;
		}
		return getUserPermission(rwsi);
	}
	
	@Override
	public Permission getUserPermission(final ResolvedWorkspaceID rwsi) {
		if (!userPerms.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return userPerms.get(rwsi);
	}
	
	@Override
	public boolean hasUserPermission(final ResolvedWorkspaceID rwsi,
			final Permission perm) {
		return getUserPermission(rwsi).compareTo(perm) > -1;
	}

	@Override
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi,
			final boolean returnFalse) {
		if (returnFalse && !hasWorkspace(rwsi)) {
			return false;
		}
		return isWorldReadable(rwsi);
	}
	
	@Override
	public boolean isWorldReadable(final ResolvedWorkspaceID rwsi) {
		if (!worldRead.containsKey(rwsi)) {
			throw new IllegalArgumentException(
					"Workspace not registered: " + rwsi);
		}
		return worldRead.get(rwsi);
	}

	@Override
	public Set<ResolvedWorkspaceID> getWorkspaces() {
		return userPerms.keySet();
	}
	
	@Override
	public boolean hasWorkspace(final ResolvedWorkspaceID ws) {
		return userPerms.containsKey(ws) || worldRead.containsKey(ws);
	}
	
	@Override
	public boolean isEmpty() {
		return userPerms.isEmpty() && worldRead.isEmpty();
	}

	@Override
	public String toString() {
		return "MongoPermissionSet [user=" + user + ", globalUser="
				+ globalUser + ", userPerms=" + userPerms + ", worldRead="
				+ worldRead + "]";
	}
}
