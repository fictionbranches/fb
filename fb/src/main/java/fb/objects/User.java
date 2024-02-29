package fb.objects;

import java.util.Date;

import fb.util.Text;

/**
 * Immutable user object. Includes basic info, plus episode count, and total number of hits/view/upvotes on owned episodes. Used for special cases. 
 */
public class User {
	public final String username;
	public final String authorUnsafe;
	public final Date date;
	public final long episodes;
	public final long hits;
	public final long views;
	public final long upvotes;
	public User(String username, String author, Date date, long episodes, long hits, long views, long upvotes) {
		this.username = username;
		this.authorUnsafe = author;
		this.date = date;
		this.episodes = episodes;
		this.hits = hits;
		this.views = views;
		this.upvotes = upvotes;
	}
	public String authorEscape() {
		return Text.escape(authorUnsafe);
	}
}
