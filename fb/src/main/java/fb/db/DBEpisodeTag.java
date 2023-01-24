package fb.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Many-to-many relation between DBTag and DBEpisode
 */

@Entity
@Table(name = "fbepisodetags")
public class DBEpisodeTag {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;	

	@ManyToOne
	private DBEpisode episode;
	
	@ManyToOne
	private DBTag tag;
	
	@ManyToOne
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
}
