package fb.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Formula;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

@Entity
@Table(name="fbusers")
@Indexed
public class DBUser {
	
	@Id
	private String id;
	
	@Column(unique=true)
	private String email;
		
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String author;
	
	private Date date;
	
	@Column(columnDefinition = "text")
	private String avatar;
	
	@Column(columnDefinition = "text") 
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String bio;
	
	private String password;
	
	private byte level; // 1=user, 10=mod, 100=admin, 
	
	@ManyToOne
	private DBTheme theme;
	
	@OneToOne(mappedBy = "user")
	private DBEmailChange emailChange;
	
	@OneToOne(mappedBy = "user")
	private DBPasswordReset passwordReset;
	
	private boolean commentSite;
	private boolean commentMail;
	private boolean childSite;
	private boolean childMail;
	
	@Column(nullable = false, columnDefinition = "boolean default false")
	private boolean hideImages;
	
	@Column(columnDefinition = "int default 900") 
	private int bodyTextWidth;
	
	@Formula("(select id)")
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String virtualId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
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

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public byte getLevel() {
		return level;
	}

	public void setLevel(byte level) {
		this.level = level;
	}

	public DBTheme getTheme() {
		return theme;
	}

	public void setTheme(DBTheme theme) {
		this.theme = theme;
	}

	public DBEmailChange getEmailChange() {
		return emailChange;
	}

	public void setEmailChange(DBEmailChange emailChange) {
		this.emailChange = emailChange;
	}
	
	public DBPasswordReset getPasswordReset() {
		return passwordReset;
	}

	public void setPasswordReset(DBPasswordReset passwordReset) {
		this.passwordReset = passwordReset;
	}

	public boolean isCommentSite() {
		return commentSite;
	}

	public void setCommentSite(boolean commentSite) {
		this.commentSite = commentSite;
	}

	public boolean isCommentMail() {
		return commentMail;
	}

	public void setCommentMail(boolean commentMail) {
		this.commentMail = commentMail;
	}

	public boolean isChildSite() {
		return childSite;
	}

	public void setChildSite(boolean childSite) {
		this.childSite = childSite;
	}

	public boolean isChildMail() {
		return childMail;
	}

	public void setChildMail(boolean childMail) {
		this.childMail = childMail;
	}

	public int getBodyTextWidth() {
		return bodyTextWidth;
	}

	public void setBodyTextWidth(int bodyTextWidth) {
		this.bodyTextWidth = bodyTextWidth;
	}

	public boolean isHideImages() {
		return hideImages;
	}

	public void setHideImages(boolean hideImages) {
		this.hideImages = hideImages;
	}

	public String getVirtualId() {
		return virtualId;
	}

	public void setVirtualId(String virtualId) {
		this.virtualId = virtualId;
	}

	public boolean equals(Object o) {
		if (!(o instanceof DBUser)) return false;
		DBUser that = (DBUser) o;
		return this.id.equals(that.id);
	}
	
	public int hashCode() {
		return this.id.hashCode();
	}
}
