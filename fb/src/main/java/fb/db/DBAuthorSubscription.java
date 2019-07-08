package fb.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name="fbauthorsubscriptions")
public class DBAuthorSubscription {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;
		
	@ManyToOne
	private DBUser author;
		
	@ManyToOne
	private DBUser subscriber;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public DBUser getAuthor() {
		return author;
	}

	public void setAuthor(DBUser author) {
		this.author = author;
	}

	public DBUser getSubscriber() {
		return subscriber;
	}

	public void setSubscriber(DBUser subscriber) {
		this.subscriber = subscriber;
	}
	
}
