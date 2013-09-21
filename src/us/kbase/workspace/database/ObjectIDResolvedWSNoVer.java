package us.kbase.workspace.database;

import static us.kbase.workspace.database.WorkspaceObjectID.checkObjectName;

//these class names are getting ridiculous, need to think of a better way
public class ObjectIDResolvedWSNoVer {
	
	private final ResolvedWorkspaceID rwsi;
	private final String name;
	private final Integer id;
	
	public ObjectIDResolvedWSNoVer(final ResolvedWorkspaceID rwsi,
			final String name) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		checkObjectName(name);
		this.rwsi = rwsi;
		this.name = name;
		this.id = null;
	}
	
	public ObjectIDResolvedWSNoVer(final ResolvedWorkspaceID rwsi,
			final int id) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.rwsi = rwsi;
		this.name = null;
		this.id = id;
	}
	
	public ObjectIDResolvedWSNoVer(final ResolvedWorkspaceID rwsi, 
			final WorkspaceObjectID id) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id == null) {
			throw new IllegalArgumentException("id cannot be null");
		}
		this.rwsi = rwsi;
		if (id.getId() == null) {
			this.name = id.getName();
			this.id = null;
		} else {
			this.name = null;
			this.id = id.getId();
		}
	}
	
	public ResolvedWorkspaceID getWorkspaceIdentifier() {
		return rwsi;
	}

	public String getName() {
		return name;
	}

	public Integer getId() {
		return id;
	}

	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}

	@Override
	public String toString() {
		return "ObjectIDResolvedWSNoVer [rwsi=" + rwsi + ", name=" + name
				+ ", id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ObjectIDResolvedWSNoVer)) {
			return false;
		}
		ObjectIDResolvedWSNoVer other = (ObjectIDResolvedWSNoVer) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (rwsi == null) {
			if (other.rwsi != null) {
				return false;
			}
		} else if (!rwsi.equals(other.rwsi)) {
			return false;
		}
		return true;
	}
	
}
