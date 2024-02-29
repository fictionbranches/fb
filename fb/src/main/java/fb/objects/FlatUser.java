package fb.objects;

import java.util.Date;
import java.util.Objects;

import fb.db.DBUser;
import fb.util.Text;

/**
 * Immutable user object, without episodes
 */
public class FlatUser {
	public final String id;
	public final String authorUnsafe;
	public final Date date;
	public final String bioUnsafe;
	public final byte level;
	public final Theme theme; // HTML theme name
	public final String email;
	public final String hashedPassword;
	public final String avatarUnsafe;
	
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
		this.authorUnsafe = user.getAuthor();
		this.date = user.getDate();
		this.bioUnsafe = user.getBio();
		this.level = user.getLevel();
		this.theme = new Theme(user.getTheme());
		this.email = user.getEmail();
		this.avatarUnsafe = user.getAvatar();
		this.hashedPassword = user.getPassword();
		this.commentSite = user.isCommentSite();
		this.commentMail = user.isCommentMail();
		this.childSite = user.isChildSite();
		this.childMail = user.isChildMail();
		this.bodyTextWidth = user.getBodyTextWidth();
		this.hideImages = user.isHideImages();
	}
	
	public String htmlLink() {
//		return String.format("<a href='/fb/user/%s'>%s</a>", id, authorEscape());
		return htmlLink(this.id, this.authorUnsafe);
	}
	
	public static String htmlLink(String id, String author) {
		return String.format("<a href='/fb/user/%s'>%s</a>", id, Text.escape(author));
	}
	
	public String authorEscape() {
		return Text.escape(authorUnsafe);
	}

	@Override
	public int hashCode() {
		return Objects.hash(authorUnsafe, avatarUnsafe, bioUnsafe, bodyTextWidth, childMail, childSite, commentMail, commentSite, date, email, hashedPassword, hideImages, id, level, theme);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof FlatUser)) return false;
		FlatUser other = (FlatUser) obj;
		return Objects.equals(authorUnsafe, other.authorUnsafe) && Objects.equals(avatarUnsafe, other.avatarUnsafe) && Objects.equals(bioUnsafe, other.bioUnsafe) && bodyTextWidth == other.bodyTextWidth && childMail == other.childMail && childSite == other.childSite
				&& commentMail == other.commentMail && commentSite == other.commentSite && Objects.equals(date, other.date) && Objects.equals(email, other.email) && Objects.equals(hashedPassword, other.hashedPassword)
				&& hideImages == other.hideImages && Objects.equals(id, other.id) && level == other.level && Objects.equals(theme, other.theme);
	}
}
