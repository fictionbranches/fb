package fb.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Represents the following notification types...
 * (all types have id,date,user,read,type)
 * 
 * new_child_episode:
 *   id
 *   date
 *   user
 *   read
 *   type
 *   
 *   episode
 *   
 * new_comment_on_own_episode:
 *   id
 *   date
 *   user
 *   read
 *   type
 *   
 *   comment
 *
 *   
 * new_comment_on_subbed_episode:
 *   id
 *   date
 *   user
 *   read
 *   type
 *   
 *   comment
 *   
 *   
 * legacy_note:
 *   id
 *   date
 *   user
 *   read
 *   type
 *   
 *   body
 */

@Entity
@Table(name="fbnotifications")
public class DBNotification {
	
	/**
	 * legacy_note:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   body
	 */
	@Transient
	public static final String LEGACY_NOTE="legacy_note";
	
	/**
	 * new_child_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   episode
	 */
	@Transient
	public static final String NEW_CHILD_EPISODE="new_child_episode";
	
	/**
	 * new_comment_on_own_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   comment
	 */
	@Transient
	public static final String NEW_COMMENT_ON_OWN_EPISODE="new_comment_on_own_episode";

	
	/**
	 * new_comment_on_subbed_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   comment
	 */
	@Transient
	public static final String NEW_COMMENT_ON_SUBBED_EPISODE="new_comment_on_subbed_episode";

	/**
	 * new_child_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   episode
	 *   sender
	 *   approved
	 */
	@Transient
	public static final String MODIFICATION_RESPONSE="modification_response";
	
	/**
	 * new_author_subscription_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   episode
	 */
	@Transient
	public static final String NEW_AUTHOR_SUBSCRIPTION_EPISODE="new_author_subscription_episode";

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Date date;

	@ManyToOne
	private DBUser user;
	
	private boolean read;
	
	private String type;
	
	/**
	 * Used by legacy_comment
	 */
	@Column(columnDefinition = "text")
	private String body;
	
	/**
	 * Used by new_child_episode
	 */
	@ManyToOne
	private DBEpisode episode;
	
	/**
	 * Used by new_comment_on_own_episode
	 */
	@ManyToOne
	private DBComment comment;
	
	@ManyToOne
	private DBUser sender;
	
	private Boolean approved;

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

	public DBUser getUser() {
		return user;
	}

	public void setUser(DBUser user) {
		this.user = user;
	}
	
	public DBUser getSender() {
		return sender;
	}

	public void setSender(DBUser sender) {
		this.sender = sender;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getBody() {
		return body;
	}

	@Deprecated
	public void setBody(String body) {
		this.body = body;
	}

	public DBEpisode getEpisode() {
		return episode;
	}

	public void setEpisode(DBEpisode episode) {
		this.episode = episode;
	}

	public DBComment getComment() {
		return comment;
	}

	public void setComment(DBComment comment) {
		this.comment = comment;
	}

	public Boolean getApproved() {
		return approved;
	}

	public void setApproved(Boolean approved) {
		this.approved = approved;
	}

	
}
