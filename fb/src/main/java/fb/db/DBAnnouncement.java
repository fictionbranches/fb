package fb.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "fbannouncements")
public class DBAnnouncement {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Date date;

	@Column(columnDefinition = "text")
	private String body;

	@ManyToOne
	private DBUser author;

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

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public DBUser getAuthor() {
		return author;
	}

	public void setAuthor(DBUser author) {
		this.author = author;
	}
}
