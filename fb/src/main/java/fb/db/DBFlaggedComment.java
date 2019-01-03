package fb.db;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name="fbflaggedcomments")
public class DBFlaggedComment {

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;
	
	private Date date;
	
	@Column(columnDefinition = "text")
	private String text;
	
	@ManyToOne
	private DBComment comment;
	
	@ManyToOne
	private DBUser user;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public DBComment getComment() {
		return comment;
	}

	public void setComment(DBComment comment) {
		this.comment = comment;
	}

	public DBUser getUser() {
		return user;
	}

	public void setUser(DBUser user) {
		this.user = user;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBFlaggedEpisode)) return false;
		DBFlaggedEpisode that = (DBFlaggedEpisode)o;
		return this.getId() == that.getId();
	}
	
	public int hashCode() {
		return Objects.hash(this.getId());
	}
}
