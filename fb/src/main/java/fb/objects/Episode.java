package fb.objects;

import java.util.Date;

/**
 * Immutable episode object. Contains hits/views/upvotes, and minimal fields. Used for special cases. 
 */
public class Episode {
	public final long generatedId;
	public final String newMap;
	public final String title;
	public final String link;
	public final Date date;
	public final int childCount;
	public final long hits;
	public final long views;
	public final long upvotes;
	public final String authorId;
	public final String authorName;
	
	public Episode(long generatedId, String newMap, String link, String title, Date date, int childCount, long hits, long views, long upvotes, String authorId, String authorName) {
		this.generatedId = generatedId;
		this.newMap = newMap;
		this.link = link;
		this.title = title;
		this.date = date;
		this.hits = hits;
		this.upvotes = upvotes;
		this.views = views;
		this.childCount = childCount;
		this.authorId = authorId;
		this.authorName = authorName;
	}

	/*public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getLink() {
		return link;
	}

	public Date getDate() {
		return date;
	}

	public int getChildCount() {
		return childCount;
	}

	public long getHits() {
		return hits;
	}

	public long getViews() {
		return views;
	}

	public long getUpvotes() {
		return upvotes;
	}*/
}
