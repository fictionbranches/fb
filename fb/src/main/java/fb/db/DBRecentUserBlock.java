package fb.db;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
@Entity
@Table(name="fbrecentuserblocks")
public class DBRecentUserBlock {
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;
	
	private Date date;
	
	@ManyToOne
	private DBUser blockingUser;
	
	@ManyToOne
	private DBUser blockedUser;

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

	public DBUser getBlockingUser() {
		return blockingUser;
	}

	public void setBlockingUser(DBUser blockingUser) {
		this.blockingUser = blockingUser;
	}

	public DBUser getBlockedUser() {
		return blockedUser;
	}

	public void setBlockedUser(DBUser blockedUser) {
		this.blockedUser = blockedUser;
	}

}
