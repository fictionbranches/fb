package fb.db;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
@Entity
@Table(name="fbpotentialusers")
public class DBPotentialUser {
	@Id
	private String token;
	private String username;
	private String email;
	private String passwordHash;
	private String author;
	private Date date;
	
	public DBPotentialUser() {}
	
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPasswordHash() {
		return passwordHash;
	}
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBPotentialUser)) return false;
		DBPotentialUser that = (DBPotentialUser) o;
		return this.token.equals(that.token);
	}
	public int hashCode() {
		return this.token.hashCode();
	}
}
