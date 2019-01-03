package fb.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
@Entity
@Table(name="fbemailchanges")
public class DBEmailChange {
	@Id
	private String token;
	
	@OneToOne
	private DBUser user;
	
	@Column(columnDefinition = "text")
	private String newEmail;
	
	private Date date;
	
	public DBEmailChange() {}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public DBUser getUser() {
		return user;
	}

	public void setUser(DBUser user) {
		this.user = user;
	}

	public String getNewEmail() {
		return newEmail;
	}

	public void setNewEmail(String newEmail) {
		this.newEmail = newEmail;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBEmailChange)) return false;
		DBEmailChange that = (DBEmailChange)o;
		return this.token.equals(that.token);
	}
	
	public int hashCode() {
		return this.token.hashCode();
	}
}
