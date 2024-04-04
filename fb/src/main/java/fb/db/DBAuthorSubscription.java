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
	
	private long createdDate;
	
	@ManyToOne
	private DBUser subscriber;

	@ManyToOne
	private DBUser subscribedTo;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(long createdDate) {
		this.createdDate = createdDate;
	}

	public DBUser getSubscriber() {
		return subscriber;
	}

	public void setSubscriber(DBUser subscriber) {
		this.subscriber = subscriber;
	}

	public DBUser getSubscribedTo() {
		return subscribedTo;
	}

	public void setSubscribedTo(DBUser subscribedTo) {
		this.subscribedTo = subscribedTo;
	}

}
