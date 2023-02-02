package fb.objects;

import java.util.Date;
import java.util.Objects;

import fb.db.DBEpisode;

/**
 * Immutable episode object
 */
public class FlatEpisode {
	public final long generatedId;
	public final String oldMap;
	public final String newMap;
	public final String title;
	public final String link;
	public final String authorId;
	public final String authorName;
	public final String authorAvatar;
	public final String body;
	public final Date date;
	public final Date editDate;
	public final String editorId;
	public final String editorName;
	public final int childCount;
	public final int depth;
	public final Long parentId;
	public final long hits;
	
	/**
	 * Construct a complete FlatEpisode from a DBEpisode database object
	 * @param ep
	 */
	public FlatEpisode(DBEpisode ep) {
		this.generatedId = ep.getGeneratedId();
		this.newMap = ep.getNewMap();
		this.oldMap = ep.getOldMap();
		this.title = ep.getTitle();
		this.link = ep.getLink();
		this.authorId = ep.getAuthor().getId();
		this.authorName = ep.getAuthor().getAuthor();
		this.authorAvatar = ep.getAuthor().getAvatar();
		this.body = ep.getBody();
		this.date = ep.getDate();
		this.editDate = ep.getEditDate();
		this.editorId = ep.getEditor().getId();
		this.editorName = ep.getEditor().getAuthor();
		this.childCount = ep.getChildCount();
		this.hits = ep.getViewCount();
		this.parentId = (ep.getParent() == null) ? null : ep.getParent().getGeneratedId();
		this.depth = ep.episodeDepthFromNewMap();
	}

	// used by DB.getRecentsPage
	public FlatEpisode(long generatedId, String oldMap, String newMap, String title, String link, String authorId, String authorName, String authorAvatar, String body, Date date, Date editDate, String editorId, String editorName,
			int childCount, Long parentId, long hits) {
		this.generatedId = generatedId;
		this.oldMap = oldMap;
		this.newMap = newMap;
		this.title = title;
		this.link = link;
		this.authorId = authorId;
		this.authorName = authorName;
		this.authorAvatar = authorAvatar;
		this.body = body;
		this.date = date;
		this.editDate = editDate;
		this.editorId = editorId;
		this.editorName = editorName;
		this.childCount = childCount;
		this.depth = DBEpisode.episodeDepthFromNewMap(newMap);
		this.parentId = parentId;
		this.hits = hits;
	}

	@Override
	public int hashCode() {
		return Objects.hash(authorAvatar, authorId, authorName, body, childCount, date, depth, editDate, editorId, editorName, generatedId, hits, link, newMap, oldMap, parentId, title);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof FlatEpisode)) return false;
		FlatEpisode other = (FlatEpisode) obj;
		return Objects.equals(authorAvatar, other.authorAvatar) && Objects.equals(authorId, other.authorId) && Objects.equals(authorName, other.authorName) && Objects.equals(body, other.body) && childCount == other.childCount
				&& Objects.equals(date, other.date) && depth == other.depth && Objects.equals(editDate, other.editDate) && Objects.equals(editorId, other.editorId) && Objects.equals(editorName, other.editorName)
				&& generatedId == other.generatedId && hits == other.hits && Objects.equals(link, other.link) && Objects.equals(newMap, other.newMap) && Objects.equals(oldMap, other.oldMap) && Objects.equals(parentId, other.parentId)
				&& Objects.equals(title, other.title);
	}
	
}
