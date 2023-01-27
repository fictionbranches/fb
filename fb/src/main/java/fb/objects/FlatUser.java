package fb.objects;

import static fb.util.Text.escape;

import java.util.Date;
import java.util.Objects;

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
	
	public final boolean hideImages;
	
	public final int bodyTextWidth;
	
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
		this.bodyTextWidth = user.getBodyTextWidth();
		this.hideImages = user.isHideImages();
	}
	
	public String htmlLink() {
		return String.format("<a href='/fb/user/%s'>%s</a>", this.id, escape(this.author));
	}

	@Override
	public int hashCode() {
		return Objects.hash(author, avatar, bio, bodyTextWidth, childMail, childSite, commentMail, commentSite, date, email, hashedPassword, hideImages, id, level, theme);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof FlatUser)) return false;
		FlatUser other = (FlatUser) obj;
		return Objects.equals(author, other.author) && Objects.equals(avatar, other.avatar) && Objects.equals(bio, other.bio) && bodyTextWidth == other.bodyTextWidth && childMail == other.childMail && childSite == other.childSite
				&& commentMail == other.commentMail && commentSite == other.commentSite && Objects.equals(date, other.date) && Objects.equals(email, other.email) && Objects.equals(hashedPassword, other.hashedPassword)
				&& hideImages == other.hideImages && Objects.equals(id, other.id) && level == other.level && Objects.equals(theme, other.theme);
	}
}
