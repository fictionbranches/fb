package fb.db;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;

/**
 * Many-to-many relation between DBTag and DBEpisode
 */

@Entity
@Table(
	name="fbepisodetags", 
	uniqueConstraints=@UniqueConstraint(columnNames= {"episode_generatedid", "tag_id"}),
	indexes = {
		@Index(columnList = "episode_generatedid"),
		@Index(columnList = "tag_id"),
		@Index(columnList = "taggedby_id"),
		@Index(columnList = "taggeddate"),
	}
)
@Indexed
public class DBEpisodeTag {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;	

	@ManyToOne
	private DBEpisode episode;
	
	@ManyToOne
	@IndexedEmbedded
	private DBTag tag;
	
	@ManyToOne
	@IndexedEmbedded
	private DBUser taggedBy;
	
	private long taggedDate;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public DBEpisode getEpisode() {
		return episode;
	}

	public void setEpisode(DBEpisode episode) {
		this.episode = episode;
	}

	public DBTag getTag() {
		return tag;
	}

	public void setTag(DBTag tag) {
		this.tag = tag;
	}

	public DBUser getTaggedBy() {
		return taggedBy;
	}

	public void setTaggedBy(DBUser taggedBy) {
		this.taggedBy = taggedBy;
	}

	public long getTaggedDate() {
		return taggedDate;
	}

	public void setTaggedDate(long taggedDate) {
		this.taggedDate = taggedDate;
	}
	
	public DBEpisodeTag() {}

	public DBEpisodeTag(DBEpisode episode, DBTag tag, DBUser taggedBy) {
		this.episode = episode;
		this.tag = tag;
		this.taggedBy = taggedBy;
		this.taggedDate = System.currentTimeMillis();
	}

	@Override
	public int hashCode() {
		return Objects.hash(episode, id, tag, taggedBy, taggedDate);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof DBEpisodeTag)) return false;
		DBEpisodeTag other = (DBEpisodeTag) obj;
		return Objects.equals(episode, other.episode) && id == other.id && Objects.equals(tag, other.tag) && Objects.equals(taggedBy, other.taggedBy) && taggedDate == other.taggedDate;
	}
	
	
}