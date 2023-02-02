package fb.objects;

import java.util.Objects;

import fb.db.DBTag;

public class Tag implements Comparable<Tag> {
	public final long id;	
	public final String shortName;
	public final String longName;
	public final String description;
	public final String createdById;
	public final String createdByAuthor;
	public final long createdDate;
	public final Long count;

	public Tag(DBTag tag) {
		this.id = tag.getId();
		this.shortName = tag.getShortName();
		this.longName = tag.getLongName();
		this.description = tag.getDescription();
		this.createdById = tag.getCreatedBy().getId();
		this.createdByAuthor = tag.getCreatedBy().getAuthor();
		this.createdDate = tag.getCreatedDate();
		this.count = null;
	}

	public Tag(long id, String shortName, String longName, String description, String createdById, String createdByAuthor, long createdDate, Long count) {
		this.id = id;
		this.shortName = shortName;
		this.longName = longName;
		this.description = description;
		this.createdById = createdById;
		this.createdByAuthor = createdByAuthor;
		this.createdDate = createdDate;
		this.count = count;
	}

	@Override
	public int hashCode() {
		return Objects.hash(count, createdByAuthor, createdById, createdDate, description, id, longName, shortName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Tag)) return false;
		Tag other = (Tag) obj;
		return Objects.equals(count, other.count) && Objects.equals(createdByAuthor, other.createdByAuthor) && Objects.equals(createdById, other.createdById) && createdDate == other.createdDate
				&& Objects.equals(description, other.description) && id == other.id && Objects.equals(longName, other.longName) && Objects.equals(shortName, other.shortName);
	}

	@Override
	public int compareTo(Tag that) {
		return this.shortName.compareTo(that.shortName);
	}
	
}
