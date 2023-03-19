package fb.db;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
	name="fbupvotes", 
	uniqueConstraints=@UniqueConstraint(columnNames= {"episode_generatedid", "user_id"}),
	indexes = {
		@Index(columnList = "episode_generatedid"),
		@Index(columnList = "user_id"),
		@Index(columnList = "date"),
	}
)
public class DBUpvote {

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
		if (!(o instanceof DBUpvote)) return false;
		DBUpvote that = (DBUpvote)o;
		return this.id == that.id;
	}
	
	public int hashCode() {
		return Objects.hash(this.id);
	}
}
