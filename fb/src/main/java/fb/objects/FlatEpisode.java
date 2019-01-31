package fb.objects;

import java.util.Date;

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
		this.depth = ep.getDepth();
		this.childCount = ep.getChildCount();
		this.hits = ep.getViewCount();
		this.parentId = (ep.getParent() == null) ? null : ep.getParent().getGeneratedId();
	}
}
