package fb.db;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="fbarchivetokens")
public class DBArchiveToken {

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;
	
	private Date date;
	
	@Column(columnDefinition = "text", unique=true)
	private String token;
	
	@Column(columnDefinition = "text")
	private String comment;

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

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}
	
	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
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
