package fb.objects;

import java.util.Date;

/**
 * Immutable episode object. Contains hits/views/upvotes, and minimal fields. Used for special cases. 
 */
public class Episode {
	public final String id;
	public final String title;
	public final String link;
	public final Date date;
	public final int childCount;
	public final long hits;
	public final long views;
	public final long upvotes;
	
	public Episode(String id, String link, String title, Date date, int childCount, long hits, long views, long upvotes) {
		this.id = id;
		this.link = link;
		this.title = title;
		this.date = date;
		this.hits = hits;
		this.upvotes = upvotes;
		this.views = views;
		this.childCount = childCount;
	}

	public String getId() {
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
	}
}
