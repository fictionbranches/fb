package fb.objects;

import java.util.Date;

import fb.db.DBUser;

/**
 * Immutable user object, without episodes
 */
public class FlatUser {
	public final String id;
	public final String author;
	public final Date date;
	public final String bio;
	public final byte level;
	public final Theme theme; // HTML theme name
	public final String email;
	public final String hashedPassword;
	public final String avatar;
	
	public final boolean commentSite;
	public final boolean commentMail;
	public final boolean childSite;
	public final boolean childMail;
	public final boolean authorSubSite;
	public final boolean authorSubMail;
	
	/**
	 * Construct from DBUser database object
	 * @param user
	 */
	public FlatUser(DBUser user) {
		this.id = user.getId();
		this.author = user.getAuthor();
		this.date = user.getDate();
		this.bio = user.getBio();
		this.level = user.getLevel();
		this.theme = new Theme(user.getTheme());
		this.email = user.getEmail();
		this.avatar = user.getAvatar();
		this.hashedPassword = user.getPassword();
		this.commentSite = user.isCommentSite();
		this.commentMail = user.isCommentMail();
		this.childSite = user.isChildSite();
		this.childMail = user.isChildMail();
		this.authorSubSite = user.isAuthorSubSite();
		this.authorSubMail = user.isAuthorSubMail();
	}
}
