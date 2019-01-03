package fb.db;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
@Entity
@Table(name="fbpasswordresets")
public class DBPasswordReset {
	@Id
	private String token;
	
	@OneToOne
	private DBUser user;
	
	private Date date;
	
	public DBPasswordReset() {}

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

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBPasswordReset)) return false;
		DBPasswordReset that = (DBPasswordReset)o;
		return this.token.equals(that.token);
	}
	
	public int hashCode() {
		return this.token.hashCode();
	}
}
