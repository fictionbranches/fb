package fb.objects;

import java.util.Date;
import java.util.List;

import fb.db.DBEpisode;
import fb.db.DBUser;

/**
 * Immutable episode object. Contains all info, and all info for all children.
 */
public class EpisodeWithChildren {
	public final String id;
	public final String title;
	public final String link;
	public final String authorId;
	public final String authorName;
	public final String authorAvatar;
	public final boolean isLegacy;
	public final String body;
	public final Date date;
	public final Date editDate;
	public final String editorId;
	public final String editorName;
	public final int childCount;
	public final int depth;
	public final long hits;
	public final long views;
	public final long upvotes;
	public final FlatUser viewer;
	public final boolean viewerCanUpvote;
	public final List<Episode> children;
	public final String parentId;
	public final List<Comment> comments;
	public final List<FlatEpisode> pathbox;
	
	/**
	 * Construct a complete Episode from a DBEpisode database object
	 * @param ep
	 */
	public EpisodeWithChildren(DBEpisode ep, long views, long upvotes, DBUser viewer, boolean viewerCanUpvote, List<Episode> children, List<Comment> comments, List<FlatEpisode> pathbox) {
		this.id = ep.getMap();
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
		this.isLegacy = ep.getAuthor().getEmail() == null;
		this.depth = ep.getDepth();
		this.childCount = ep.getChildCount();
		this.hits = ep.getViewCount();
		this.views = views;
		this.upvotes = upvotes;
		this.children = children;
		this.parentId = (ep.getParent() == null) ? null : ep.getParent().getMap();
		this.viewer = (viewer==null)?null:(new FlatUser(viewer));
		this.viewerCanUpvote = viewerCanUpvote;
		this.comments = comments;
		this.pathbox = pathbox;
	}
}
