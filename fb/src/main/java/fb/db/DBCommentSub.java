package fb.db;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Represents a subscription to a comment section of an episode
 * So it links an author, the subscriber, to an episode (and its comment section)
 * @author lpreams
 *
 */

@Entity
@Table(name="fbcommentsubs", uniqueConstraints=@UniqueConstraint(columnNames= {"episode_generatedid", "user_id"}))
public class DBCommentSub {

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;

	@ManyToOne
	private DBEpisode episode;
	
	@ManyToOne
	private DBUser user;
	
	private Date date;
		
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

	public DBUser getUser() {
		return user;
	}

	public void setUser(DBUser user) {
		this.user = user;
	}
	
	public Date getDate() {
		return this.date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public boolean equals(Object o) {
		if (!(o instanceof DBCommentSub)) return false;
		DBCommentSub that = (DBCommentSub)o;
		return this.id == that.id;
	}
	
	public int hashCode() {
		return Objects.hash(this.id);
	}
}
