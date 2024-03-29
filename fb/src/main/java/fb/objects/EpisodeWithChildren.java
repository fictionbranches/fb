package fb.objects;

import java.util.Date;
import java.util.List;
import java.util.Set;

import fb.db.DBEpisode;
import fb.db.DBUser;

/**
 * Immutable episode object. Contains all info, and all info for all children.
 */
public class EpisodeWithChildren {
	public final long generatedId;
	public final String newMap;
	public final String oldMap;
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
	public final boolean isFavorite;
	public final List<Episode> children;
	public final Long parentId;
	public final List<Comment> comments;
	public final List<FlatEpisode> pathbox;
	public final boolean userIsSubscribedToComments;
	public final Set<Tag> tags;
	
	/**
	 * Construct a complete Episode from a DBEpisode database object
	 * @param ep
	 */
	public EpisodeWithChildren(DBEpisode ep, long views, long upvotes, DBUser viewer, boolean viewerCanUpvote, boolean isFavorite, List<Episode> children, List<Comment> comments, List<FlatEpisode> pathbox, boolean userIsSubscribedToComments, Set<Tag> tags) {
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
		this.isLegacy = ep.getAuthor().getEmail() == null;
		this.childCount = ep.getChildCount();
		this.hits = ep.getViewCount();
		this.views = views;
		this.upvotes = upvotes;
		this.children = children;
		this.parentId = (ep.getParent() == null) ? null : ep.getParent().getGeneratedId();
		this.viewer = (viewer==null)?null:(new FlatUser(viewer));
		this.viewerCanUpvote = viewerCanUpvote;
		this.isFavorite = isFavorite;
		this.comments = comments;
		this.pathbox = pathbox;
		this.userIsSubscribedToComments = userIsSubscribedToComments;
		this.tags = tags;
		this.depth = ep.episodeDepthFromNewMap();
	}
}
