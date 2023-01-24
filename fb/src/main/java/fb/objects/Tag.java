package fb.objects;

import java.util.Objects;

import fb.db.DBTag;

public class Tag {
	public final long id;	
	public final String shortName;
	public final String longName;
	public final String description;
	public final FlatUser createdBy;
	public final long createdDate;

	public Tag(DBTag tag) {
		this.id = tag.getId();
		this.shortName = tag.getShortName();
		this.longName = tag.getLongName();
		this.description = tag.getDescription();
		this.createdBy = new FlatUser(tag.getCreatedBy());
		this.createdDate = tag.getCreatedDate();
	}

	@Override
	public int hashCode() {
		return Objects.hash(createdBy, createdDate, description, id, longName, shortName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Tag)) return false;
		Tag other = (Tag) obj;
		return Objects.equals(createdBy, other.createdBy) && createdDate == other.createdDate && Objects.equals(description, other.description) && id == other.id && Objects.equals(longName, other.longName)
				&& Objects.equals(shortName, other.shortName);
	}
	
}
