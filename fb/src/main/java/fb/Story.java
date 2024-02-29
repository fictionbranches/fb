package fb;

import static fb.util.Text.escape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fb.Accounts.FBLoginException;
import fb.DB.DBException;
import fb.DB.DeleteCommentConfirmation;
import fb.DB.RecentsResultList;
import fb.DB.SearchResultList;
import fb.objects.Announcement;
import fb.objects.Comment;
import fb.objects.Episode;
import fb.objects.EpisodeWithChildren;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.objects.Tag;
import fb.util.Dates;
import fb.util.Markdown;
import fb.util.Strings;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/**
 * Contains the actual logic that controls how the site works
 */
public class Story { 
	
//	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	/////////////////////////////////////// function to get episodes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	
	/**
	 * Gets an episode by its id, with children in sorted order
	 * Sort orders:
	 * 
	 * 0: oldest first (by keystring/submission date) DEFAULT
	 * 1: newest first (reverse keystring/submission date)
	 * 2: number of children (most to least)
	 * 3: number of children (least to most)
	 * 4: random shuffle
	 * 
	 * @param id id of episode
	 * @return HTML episode
	 */
	public static String getHTML(long generatedId, int sort, boolean advancedChildren, Cookie token, boolean parseMarkdown) {
		EpisodeWithChildren ep;
		try {
			ep = DB.getFullEp(generatedId, Accounts.getUsernameFromCookie(token));
		} catch (DBException e) {
			FlatUser user;
			try {
				user = Accounts.getFlatUser(token);
			} catch (FBLoginException e1) {
				user = null;
			}
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId);
		}
		
		FlatUser user = ep.viewer;
		
		String addEp;
		String modify="";	
		if (user == null) {
			addEp = Strings.getString("story_add_ep_not_logged_in");
		} else {
			if (ep.authorId.equals(user.id)) modify = Strings.getString("story_modify_owner").replace("$ID", ""+generatedId);
			else if (user.level >= ((byte)10)) modify = Strings.getString("story_modify_moderator").replace("$ID", ""+generatedId);
			else modify = Strings.getString("story_modify_logged_in").replace("$ID", ""+generatedId);
			
			addEp = 
					Strings.getString("story_add_ep_logged_in").replace("$ID", ""+generatedId) + "&nbsp;&nbsp;&nbsp;" + 
					(ep.viewerCanUpvote?(Strings.getString("story_upvote").replace("$ID", ""+generatedId)):(Strings.getString("story_downvote").replace("$ID", ""+generatedId))) + "</div>";
			modify += Strings.getString("story_logged_in_extras").replace("$ID", ""+generatedId) + 
					(InitWebsite.SEARCHING_ALLOWED?(Strings.getString("story_search_from_here").replace("$ID", ""+generatedId)):"") + 
					"<p>"+(ep.viewerCanUpvote?(Strings.getString("story_upvote").replace("$ID", ""+generatedId)):(Strings.getString("story_downvote").replace("$ID", ""+generatedId))) + " " + 
					(ep.isFavorite?(Strings.getString("story_unfavorite").replace("$ID", ""+generatedId)):(Strings.getString("story_favorite").replace("$ID", ""+generatedId)))+"</p>"
					;
		}
		
		if (modify.length() != 0) modify = "<p>" + modify + "</p>";
		
		ArrayList<Episode> children = new ArrayList<>(ep.children);
		String sortOrder;
		switch (sort) {
		case 1:
			Collections.sort(children, Comparator.comparing((Episode e)->e.newMap,DB.newMapComparator).reversed());
			sortOrder = "Newest first";
			break;
		case 2:
			Collections.sort(children, Comparator.comparing((Episode e)->e.childCount).reversed());
			sortOrder = "Children (descending)";
			break;
		case 3:
			Collections.sort(children, Comparator.comparing(e->e.childCount));
			sortOrder = "Children (ascending)";
			break;
		case 4:
			Collections.shuffle(children, Strings.r);
			sortOrder = "Random";
			break;
		case 0:
		default:
			Collections.sort(children, Comparator.comparing(e->e.newMap,DB.newMapComparator));
			sortOrder = "Oldest first (default)";
			break;
		}
		
		StringBuilder pathbox = new StringBuilder();
		for (FlatEpisode pathEp : ep.pathbox) {
			String link = pathEp.link.substring(0,Integer.min(30,pathEp.link.length()));
			pathbox.append("<p>" + pathEp.depth + ". <a href=/fb/story/" + pathEp.generatedId + ">" + escape(link) + "</a></p>\n");
		}
		
		StringBuilder childHTML = new StringBuilder();
		if (!children.isEmpty()) {
			String head;
			String row;
			String foot;
			if (advancedChildren) {
				head = "story_childtable_head_advanced";
				row = "story_childtable_row_advanced";
				foot = "story_childtable_foot_advanced";
			} else {
				head = "story_childtable_head";
				row = "story_childtable_row";
				foot = "story_childtable_foot";
			}
			childHTML.append(Strings.getString(head));
			for (Episode child : children) {
				//child.
				childHTML.append(Strings.getString(row)
					.replace("$AUTHORID", child.authorId)
					.replace("$AUTHORNAME", escape(child.authorName))
					.replace("$ID", ""+child.generatedId)
					.replace("$LINK", escape(child.link))
					.replace("$COMPLETEDATE", Dates.completeSimpleDateFormat2(child.date))
					.replace("$DATE", Dates.simpleDateFormat2(child.date))
					.replace("$CHILDCOUNT", Long.toString(child.childCount))
					.replace("$HITS", Long.toString(child.hits))
					.replace("$VIEWS", Long.toString(child.views))
					.replace("$UPVOTES", Long.toString(child.upvotes))
					.replace("$TAGS", tagsHtmlView(child.tags))
					+ System.lineSeparator());
			}
			childHTML.append(Strings.getString(foot));
		}
		
		String commentFormHTML =
				"<h4>Add a comment</h4>\n" + 
				"<form id='fbcommentform' action= \"/fb/addcommentpost/"+ep.generatedId+"\" method=\"post\">\n" + 
				"  <p>\n" + 
				"    <a name=\"addcomment\" /><textarea id='fbcommenttext' name= \"body\" placeholder=\"Comment\" ></textarea>\n" + 
				"  </p>\n" + 
				"  <p><div id='fbcommentformextra' ></div></p>\n" +
				"  <input id='fbcommentbutton' type= \"submit\" value= \"Submit\"/>\n" +  
				"</form>";
		if (ep.viewer != null && !ep.viewer.id.equals(ep.authorId)) {
			if (ep.userIsSubscribedToComments) {
				// unsubscribe button
				commentFormHTML += "<form id='fbcommentsubform' action= '/fb/commentsubscribepost/" + ep.generatedId
						+ "' method=\"post\">\n"
						+ " <p>You are currently subscribed to notifications for new comments.<br/>\n"
						+ " <input id='fbcommentsubvalue' type= 'hidden' value= 'false'/>\n"
						+ " <input id='fbcommentsubbutton' type= 'submit' value= 'Unsubscribe'/>\n" + "</form>";
			} else {
				// subscribe button
				commentFormHTML += "<form id='fbcommentsubform' action= '/fb/commentsubscribepost/" + ep.generatedId
						+ "' method=\"post\">\n"
						+ " <p>You are not currently subscribed to notifications for new comments.<br/>\n"
						+ " <input id='fbcommentsubvalue' type= 'hidden' name = 'commentsubvalue' value= 'true'/>\n"
						+ " <input id='fbcommentsubbutton' type= 'submit' value= 'Subscribe'/>\n" + "</form>";

			}
		}
		
		StringBuilder commentHTML = new StringBuilder();
		if (!ep.comments.isEmpty()) commentHTML.append("<h3>Comments</h3>\n");
		if (!InitWebsite.READ_ONLY_MODE && user != null && !ep.comments.isEmpty()) commentHTML.append("<p><a href=#addcomment>Add comment</a></p>");
		for (Comment c : ep.comments) {
			commentHTML.append("<div id='comment" + c.id + "' class=\"fbcomment\">\n");
			commentHTML.append("<a name=\"comment"+c.id+"\">\n");
			commentHTML.append("<p><div class=\"" + (parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown") + "\">" + (parseMarkdown?Story.formatBody(c.text):escape(c.text)) + "</div></p><hr/>\n");
			if (c.modVoice) commentHTML.append("<span style='border: 1px solid; margin: 2px; padding: 5px; white-space: nowrap;'>This comment is from a site Moderator</span>\n");
			commentHTML.append("<p>" + ((c.user.avatarUnsafe==null||c.user.avatarUnsafe.trim().length()==0)?"":("<img class=\"avatarsmall\" alt=\"avatar\" src=\""+escape(c.user.avatarUnsafe) + "\" />"))+" <a href=/fb/user/" + c.user.id + ">" + escape(c.user.authorUnsafe) + "</a></p>\n");			
			commentHTML.append("<p><a href=/fb/story/" + ep.generatedId + "#comment" + c.id + ">" + (Dates.outputDateFormat2(c.date)) + "</a>");
			if (user != null) {
				if (c.user.id.equals(user.id)) commentHTML.append(" - <a href=/fb/deletecomment/" + c.id + ">Delete</a>");
				else if (user.level>=10) commentHTML.append(" - <a href=/fb/deletecomment/" + c.id + ">Delete as mod</a>");
				else commentHTML.append(" - <a href=/fb/flagcomment/" + c.id + ">Flag</a>");
				
				if (user.level>=10 && c.user.id.equals(user.id)) { // add modvoice comment
					commentHTML.append(" - <a href=/fb/modvoicecomment/" + c.id + ">" + (c.modVoice ? "Disable" : "Enable") + " mod voice</a>");
				}

				
			}
			commentHTML.append("</p>\n");
			commentHTML.append("</div>\n");
		}
		if (!InitWebsite.READ_ONLY_MODE && user != null) commentHTML.append(commentFormHTML);

		if (InitWebsite.READ_ONLY_MODE) addEp = "";
		
		String editHTML;
		if (ep.date.equals(ep.editDate)) editHTML = "";
		else editHTML = Strings.getString("story_editor")
				.replace("$EDITORID", ep.editorId)
				.replace("$EDITORNAME", escape(ep.editorName))
				.replace("$EDITDATE", (Dates.outputDateFormat2(ep.editDate)));
				
		return Strings.getFile("story.html", user)
				.replace("$TITLE", escape(ep.title))
				.replace("$BODY", parseMarkdown?formatBody(ep.body):escape(ep.body))
				.replace("$MARKDOWNSTATUS", parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown")
				.replace("$RAWBODY", escape("By " + ep.authorName + System.lineSeparator() + ep.body))
				.replace("$AUTHORID", ep.authorId)
				.replace("$AUTHORNAME", escape(ep.authorName))
				.replace("$AVATARURL", (ep.authorAvatar==null||ep.authorAvatar.trim().length()==0)?"":(Strings.getString("story_avatar").replace("$AVATARURL", escape(ep.authorAvatar))))
				.replace("$PARENTID", (ep.parentId == null) ? ".." : (""+ep.parentId))
				.replace("$ID", ""+generatedId)
				.replace("$DATE", (Dates.outputDateFormat2(ep.date)) + editHTML)
				.replace("$MODIFY", modify)
				.replace("$ADDEP", addEp)
				.replace("$SORTORDER", sortOrder)
				.replace("$HITS", Long.toString(ep.hits))
				.replace("$VIEWS", Long.toString(ep.views))
				.replace("$UPVOTES", Long.toString(ep.upvotes))
				.replace("$COMMENTS", commentHTML.toString())
				.replace("$PATHTOHERE", pathbox.toString())
				.replace("$CHILDREN", childHTML.toString())
				.replace("$TAGS", tagsHtmlView(ep.tags));
	}
	
	private static String tagsHtmlView(Set<Tag> tags) {
		if (tags == null || tags.isEmpty()) return "";
		return tags.stream()
				.sorted()
				.map(tag -> 
					String.format("<span class='fbtag' title='%s'><a class='fbtag' href='/fb/recent?tag=%s'>%s</a></span>", 
							escape(tag.longName + (tag.description.length() == 0 ? "" : (" - " + tag.description))), 
							escape(tag.shortName),
							escape(tag.shortName))
					)
				.collect(Collectors.joining(" "));
	}
	
	public static String getWelcome(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
				
		StringBuilder sb = new StringBuilder();
		sb.append("<table class=\"noborder\">\n");
		for (FlatEpisode ep : Story.getRootEpisodes()) {
			sb.append("<h3><a href=/fb/story/" + ep.generatedId + ">" + ep.link + "</a> (" + ep.childCount + ")</h3>" + "<a href=/fb/rss/" + ep.generatedId + "><img width=20 height=20 src=/images/rss.png title=\"RSS feed for " + ep.link + "\" /></a>" + " <a href=/fb/recent?story=" + ep.generatedId + ">" + ep.link + "'s recently added episodes</a> " + "<br/><br/>");
		}
		sb.append("</table>");
		return Strings.getFile("welcome.html", user).replace("$EPISODES", sb.toString());
		
	}
	
	public static String getDeleteCommentConfirmation(Cookie token, long id) {
		
		DeleteCommentConfirmation dcc = DB.canDeleteComment(Accounts.getUsernameFromCookie(token), id);
		FlatUser user = dcc.user;
		if (user == null) return Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		
		if (InitWebsite.READ_ONLY_MODE) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("site_read_only")); 
		
		if (!dcc.canDelete) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_delete_comment_not_allowed"));
		
		return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_delete_comment_confirm").replace("$PARENTID", dcc.comment.episode.generatedId+"").replace("$ID", Long.toString(id)));
	}
	
	public static String deleteComment(Cookie token, long id) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		if (InitWebsite.READ_ONLY_MODE) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("site_read_only")); 
		try {
			DB.deleteComment(id, user.id);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA",  e.getMessage());
		}
		return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_comment_deleted"));
	}
	
	public static String getDeleteConfirmation(Cookie token, long generatedId) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		if (InitWebsite.READ_ONLY_MODE) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("site_read_only")); 
		
		FlatEpisode ep;
		try {
			ep =  DB.epHasChildren(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage());
		}
		
		if (!ep.authorId.equals(user.id) && user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_delete_not_allowed"));
		
		return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_delete_confirm").replace("$ID", ""+generatedId));
	}
	
	public static String deleteEpisode(Cookie token, long generatedId) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		if (InitWebsite.READ_ONLY_MODE) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("site_read_only")); 
		try {
			DB.deleteEp(generatedId, user.id);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA",  e.getMessage());
		}
		return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("story_deleted"));
	}
	
	public static String getAnnouncements(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<Announcement> list = DB.getAnnouncements((user==null)?null:user.id);
		
		StringBuilder sb = new StringBuilder("<h1>Announcements</h1>\n<hr/>\n");
		for (Announcement a : list) {
			sb.append("<p>" + Story.formatBody(a.body) + "</p>\n");
			sb.append("<p> - <a href=/fb/user/" + a.authorId + ">" + escape(a.authorName) + "</a> (" + 
					Dates.simpleDateFormat2(a.date) + 
					")</p>\n");
			if (!a.read) sb.append("<p><a href=/fb/markannouncementread/" + a.id + ">Mark read</a></p>\n");
			if (user != null && user.level >= 100) sb.append("<p><a href=/fb/admin/deleteannouncement/" + a.id + ">Delete as admin</a></p>\n");
			sb.append("<hr/>\n");
		}
		
		return Strings.getFile("generic_meta.html", user)
				.replace("$TITLE", "Fiction Branches Announcements")
				.replace("$OGDESCRIPTION", "Fiction Branches Announcements")
				.replace("$EXTRA", sb.toString());
	}
	
	public static String getSearchHelp(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("genericmeta.html", null)
					.replace("$TITLE", "Search Help")
					.replace("$@OGDESCRIPTION", "Search Fiction Branches")
					.replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		StringBuilder sb = new StringBuilder();
		
		for (FlatEpisode ep : Story.getRootEpisodes()) {
			sb.append(Strings.getString("search_help_line").replace("$ID", ""+ep.generatedId).replace("$LINK", escape(ep.link)) + "\n");
		}
		
		
		return Strings.getFile("searchhelp.html", user).replace("$EPISODES", sb.toString());
	}
	
	/**
	 * If tagFilter is a tag from a query param, return a String containing a valid tag name, or null if invalid
	 * @param tagFilter
	 * @return
	 */
	private static String validTag(String tagFilter) {
		return DB.getAllTags().stream().map(tag -> tag.shortName).filter(tag -> tag.equals(tagFilter)).findAny().orElse(null);
	}
	
	/**
	 * Return the complete HTML page of the recents table, including data
	 * 
	 * @return HTML recents
	 */
	public static String getRecents(Cookie token, String rootId, int page, boolean reverse, String tagFilter) {
		int root = getRecentsRoot(rootId);
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		tagFilter = validTag(tagFilter);
		
		RecentsResultList prof;
		try {
			prof = DB.getRecents(root, page, reverse, tagFilter, user);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage());
		}
		
		
		return Strings.getFile("recents.html", user)
				.replace("$TAGS", recentsPageTagsHtmlForm(root, page, tagFilter, reverse))
				.replace("$CHILDREN", getRecentsTableImpl(prof.episodes, root))
				.replace("$PREVNEXT", recentsTablePrevNext(prof, root, page, reverse, tagFilter))
				.replace("$NUMPAGES", Integer.toString(prof.numPages))
				.replace("$TITLE", reverse?"Oldest":"Recent");
	}
	
	/**
	 * Generate HTML recents table
	 * @param recents
	 * @param root
	 * @return
	 */
	private static String getRecentsTableImpl(Map<FlatEpisode, Set<Tag>> recents, int root) {
		StringBuilder sb = new StringBuilder(Strings.getString("recents_table_head" + (root==0?"_story_head":"")));
		for (Map.Entry<FlatEpisode, Set<Tag>> entry : recents.entrySet()) if (entry.getKey() != null){
			FlatEpisode child = entry.getKey();
			long rootId = DB.newMapToIdList(child.newMap).findFirst().get();
			String story;
			FlatEpisode rootEp;
			if (root==0){
				rootEp = Story.getRootEpisodeById(rootId);
				if (rootEp == null) story = "";
				else story = Strings.getString("recents_table_head_story_column").replace("$TITLE", rootEp.link);
			} else story = "";
			
			String row;
			if (child.title.toLowerCase().trim().equals(child.link.toLowerCase().trim())) row = Strings.getString("recents_table_row_same_linktitle");
			else row = Strings.getString("recents_table_row_different_linktitle");
			
			row = row.replace("$ID", ""+child.generatedId)
					.replace("$TAGS", tagsHtmlView(entry.getValue()))
					.replace("$TITLE", escape(child.title))
					.replace("$AUTHORID", child.authorId)
					.replace("$AUTHORNAME", escape(child.authorName))
					.replace("$DATE", Dates.simpleDateFormat2(child.date))
					.replace("$STORY", story)
					.replace("$EPISODEDEPTH",""+child.depth)
					.replace("$LINK", escape(child.link))
					.replace("$BODY", escape(child.body.substring(0, Integer.min(140, child.body.length()))));
			sb.append(row);
		}
		sb.append("</table>");
		return sb.toString();
	}
	
	private static String recentsPageTagsHtmlForm(int root, int page, String tagFilter, boolean reverse) {
		final Set<Tag> allTags = DB.getAllTags();		
		boolean tagSelected = false;
		StringBuilder tagsHTML = new StringBuilder();
		for (Tag tag : allTags) {
			if (tag.shortName.equals(tagFilter)) {
				tagsHTML.append(tag.shortName + " ");
				tagSelected = true;
			} else {
				tagsHTML.append(recentsTablePrevNextItem(root, 1, reverse, tag.shortName, false, tag.shortName));
			}
		}
		if (tagSelected) {
			tagsHTML.append(recentsTablePrevNextItem(root, 1, reverse, null, false, "Unselect All"));
		}
		return tagsHTML.toString();
	}
	
	private static String recentsTablePrevNext(RecentsResultList prof, int root, int page, boolean reverse, String tag) {
		StringBuilder prevNext = new StringBuilder();
		prevNext.append("<div id=recentcontainer>");
		if (prof.numPages <= 8) {
			for (int i = 1; i <= prof.numPages; ++i) {
				if (i == page) prevNext.append(i + " ");
				else prevNext.append(recentsTablePrevNextItem(root, i, reverse, tag, true, null));
			}
		} else {
			if (page <= 3) { // 1 2 3 4 ... n
				for (int i = 1; i <= 4; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append(recentsTablePrevNextItem(root, i, reverse, tag, true, null));
				}
				prevNext.append("... ");
				prevNext.append(recentsTablePrevNextItem(root, prof.numPages, reverse, tag, true, null));
			} else if (page >= prof.numPages - 3) { // 1 ... n-3 n-2 n-1 n
				prevNext.append(recentsTablePrevNextItem(root, 1, reverse, tag, true, null));
				prevNext.append("... ");
				for (int i = prof.numPages - 3; i <= prof.numPages; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append(recentsTablePrevNextItem(root, i, reverse, tag, true, null));
				}
			} else { // 1 ... x-2 x-1 x x+1 x+2 ... n
				prevNext.append(recentsTablePrevNextItem(root, 1, reverse, tag, true, null));
				prevNext.append("... ");
				for (int i = page - 2; i <= page + 2; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append(recentsTablePrevNextItem(root, i , reverse, tag, true, null));
				}
				prevNext.append("... ");
				prevNext.append(recentsTablePrevNextItem(root, prof.numPages, reverse, tag, true, null));
			}
		}

		prevNext.append("</div></p><p>");

		prevNext.append(recentsTablePrevNextItem(root, page, !reverse, tag, false, reverse ? "Recent episodes" : "Oldest episodes"));
		return prevNext.toString();
	}
	
	/**
	 * 
	 * @param root which root story to query, 0 for all stories
	 * @param page 
	 * @param reverse 
	 * @param tag single tag shortname, must be valid
	 * @param mono whether text should be monospaced
	 * @param name display name of item, or null to use page number as name
	 * @return
	 */
	private static String recentsTablePrevNextItem(int root, int page, boolean reverse, String tag, boolean mono, Object name) {
		if (tag != null && tag.length() == 0) tag = null;
		return "<a " + (mono ? "class=\"monospace\" " : "") + "href=?story=" + root + "&page=" + page + (reverse ? "&reverse" : "") + recentsTableTagParam(tag) + ">" + (name==null?page:name) + "</a> ";
	}
	
	private static String recentsTableTagParam(String tag) {
		return (tag==null ? "" : "&tag="+tag);
	}
		
	/**
	 * get the rootId of an episode id
	 * @param rootId
	 * @return
	 */
	private static int getRecentsRoot(String rootId) {
		int root = -1;
		// Check rootId is actually a root Id
		if (rootId == null || rootId.length() == 0) root = 0;
		else {
			for (char c : rootId.toCharArray()) if (c < '0' || c > '9') {
				root = 0;
				break;
			}
		}
		if (root == -1) try {
			root = Integer.parseInt(rootId);
		} catch (NumberFormatException e) {
			root = 0;
		}

		return root;
	}
		
	private static Object rootEpisodesCacheLock = new Object();
	private static LinkedHashMap<Long,FlatEpisode> rootEpisodesCache2 = new LinkedHashMap<>();
	static {
		updateRootEpisodesCache();
		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(Story::updateRootEpisodesCache, 5, 5, TimeUnit.MINUTES);
	}
	
	public static List<FlatEpisode> getRootEpisodes() {
		return Story.rootEpisodesCache2.values().stream().toList();
	}
	
	public static FlatEpisode getRootEpisodeById(long generatedId) {
		return Story.rootEpisodesCache2.get(generatedId);
	}
	
	public static void updateRootEpisodesCache() {
		synchronized (rootEpisodesCacheLock) {
			LinkedHashMap<Long, FlatEpisode> newCache = new LinkedHashMap<>();
			FlatEpisode[] arr = DB.getRoots();
			for (FlatEpisode root : arr) newCache.put(root.generatedId, root);
			rootEpisodesCache2 = newCache;
		}
	}
	
	public static String getOutlineScrollable(Cookie token, long generatedId) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "ID not found: " + generatedId);
		}
		return Strings.getFile("outlinescroll.html", user).replace("$ID", ""+generatedId).replace("$TITLE", ep.title).replace("$CHILDREN", Story.epToOutlineHTML(generatedId, ep.link, ep.authorId, ep.authorName, ep.depth, ep.depth));
	}
	
	public static String epToOutlineHTML(long generatedId, String link, String authorUsername, String authorName, int depth, int minDepth) {
		StringBuilder sb = new StringBuilder();
		for (int i=minDepth; i<depth; ++i) sb.append("&nbsp;");
		return sb.toString() + depth + ". <a href=\"/fb/story/" + generatedId + "\" target=\"_blank\" >" + escape(link) + "</a> (<a href='/fb/user/" + authorUsername + "' class='author'>" + escape(authorName) + "</a>)<br/>\n";
	}
	
	public static String epLine(FlatEpisode ep) {
		return "<a href='/fb/story/" + ep.generatedId + "'>" + escape(ep.link) + "</a> (<a href='/fb/user/" + ep.authorId + "' class='author'>" + escape(ep.authorName) + "</a>)<br/>\n";
	}
	
	public static String getPath(Cookie token, long generatedId) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		List<FlatEpisode> path;
		try {
			path = DB.getPath(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage());
		}
		StringBuilder sb = new StringBuilder();
		for (FlatEpisode child : path) if (child != null){
			sb.append(child.depth + ". " + epLine(child));
		}
		
		FlatEpisode ep = path.get(path.size()-1);
		
		return Strings.getFile("path.html", user)
				.replace("$ID", ""+generatedId)
				.replace("$CHILDREN", sb.toString())
				.replace("$OGDESCRIPTION", escape("By " + ep.authorName + System.lineSeparator() + ep.body))
				.replace("$TITLE", escape(ep.title))
				;
	}
	
	public static String getCompleteHTML(Cookie token, long generatedId, Cookie fbjs) {
		boolean parseMarkdown = fbjs==null || !"true".equals(fbjs.getValue());
		List<FlatEpisode> path; 
		try {
			path = DB.getFullStory(generatedId);
		} catch (DBException e) {
			return Strings.getFileWithToken("generic.html", token).replace("$EXTRA", e.getMessage());
		}
		StringBuilder sb = new StringBuilder();
		for (FlatEpisode child : path) if (child != null){ 
			sb.append("<h1><a href=/fb/story/" + child.generatedId + ">" + escape(child.title) + "</a></h1>");
			sb.append("<p><a href=/fb/user/" + child.authorId + ">" + escape(child.authorName) + "</a> " + 
					Dates.simpleDateFormat2(child.date) + "</p>");
			sb.append("<div class=\"fbepisodebody "+(parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown")+"\">" + (parseMarkdown?formatBody(child.body):escape(child.body)) + "</div><hr/>\n");
		}
		
		FlatEpisode ep = path.get(path.size()-1);
		
		return Strings.getFileWithToken("completestory.html", token)
				.replace("$TITLE", escape(ep.title))
				.replace("$BODY", sb.toString())
				.replace("$OGDESCRIPTION", escape("By " + ep.authorName + System.lineSeparator() + ep.body))
				;
	}
	
	public static String getSearchForm(Cookie token, long generatedId) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user == null) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId);
		}
		return Strings.getFile("searchform.html", user)
				.replace("$SEARCHTERM", "")
				.replace("$TITLE", "Searching '" + escape(ep.title) + "'")
				.replace("$ID", ""+generatedId)
				.replace("$TAGS", Story.tagsHtmlForm(DB.getAllTags(), false))
				.replace("$EXTRA", "");
	}
	
	public static String searchPost(Cookie fbtoken, long generatedId, String search, String page, String sort, MultivaluedMap<String, String> params) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user == null) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		int pageNum;
		try {
			pageNum = Integer.parseInt(page);
			if (pageNum < 1) pageNum = 1;
		} catch (NumberFormatException e) {
			pageNum = 1;
		}
		
		final Set<Tag> allTags = DB.getAllTags();
		
		final Set<String> selectedShortNames = allTags
				.stream()
				.map(tag -> tag.shortName)
				.filter(params::containsKey)
				.collect(Collectors.toCollection(HashSet::new));
		
		final Map<Tag, Boolean> selectedTags = allTags.stream().collect(Collectors.toMap(tag -> tag, tag -> selectedShortNames.contains(tag.shortName)));
		
		if (search.length() == 0 && selectedTags.size() == 0) Response.ok(Story.getSearchForm(fbtoken, generatedId)).build();
		
		SearchResultList results;
		try {
			results = DB.search(generatedId, search, pageNum, sort==null?"":sort, selectedShortNames);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage());
		} 
		Map<FlatEpisode, Set<Tag>> result = results.episodes;
		
		StringBuilder sb = new StringBuilder();
		if (pageNum > 1) sb.append(searchButton(generatedId,"Prev", search, pageNum-1, sort, selectedShortNames));
		if (results.morePages) sb.append(searchButton(generatedId,"Next", search, pageNum+1, sort, selectedShortNames));
		if (sb.length() > 0) {
			String asdf = sb.toString();
			sb = new StringBuilder("<p>" + asdf + "</p>");
		}
		String prevNext = sb.toString();
		if (!result.isEmpty()) {
			sb.append("<table class=\"fbtable\"><tr><th>Episode</th><th>Tags</th><th>Author</th><th>Date</th><th>Depth</th></tr>");
			for (Map.Entry<FlatEpisode, Set<Tag>> e : result.entrySet()) {
				final FlatEpisode ep = e.getKey();
				final Set<Tag> tags = e.getValue();
				final String row = 
						"<tr class=\"fbtable\">" + 
							"<td class=\"fbtable\">" + (ep.title.toLowerCase().trim().equals(ep.link.toLowerCase().trim())?"":(escape(ep.title) + "<br/>")) + 
								"<a href=/fb/story/" + ep.generatedId + " title='" + escape(ep.body.substring(0, Integer.min(140, ep.body.length()))) + "'>" + escape(ep.link) + "</a></td>" + 
							"<td class='fbtable'>" + Story.tagsHtmlView(tags) + "</td>" + 
							"<td class=\"fbtable\"><a href=/fb/user/" + ep.authorId + ">" + escape(ep.authorName) + "</a></td>" + 
							"<td class=\"fbtable\">" + Dates.simpleDateFormat2(ep.date) + "</td><td class=\"textalignright\">"+ep.depth+"</td></tr>\n";
				sb.append(row);
			}
			sb.append("</table>");
		} else {
			sb.append("No results (<a href=\"/fb/search\">search help</a>)");
		}
		sb.append(prevNext);
		return Strings.getFile("searchform.html", user)
				.replace("$SEARCHTERM", escape(search))
				.replace("$TITLE", "Search results")
				.replace("$ID", ""+generatedId)
				.replace("$TAGS", Story.tagsHtmlForm(selectedTags, false))
				.replace("$EXTRA", sb.toString());
	}
	
	private static String searchButton(long generatedId, String name, String search, int page, String sort, Set<String> tagShortNames) {
		return "<form class=\"simplebutton\" action=\"/fb/search/"+generatedId+"\" method=\"get\">\n" + 
				"  <input type=\"hidden\" name=\"q\" value=\""+escape(search)+"\" />\n" + 
				"  <input type=\"hidden\" name=\"page\" value=\""+page+"\" />\n" + 
				(sort == null ? "" : "  <input type=\"hidden\" name=\"sort\" value=\""+sort+"\" />\n") + 
				"  <input class=\"simplebutton\" type=\"submit\" value=\""+name+"\" />\n" + 
				tagShortNames.stream().map(tagShortName -> "<input type='hidden' name='"+tagShortName+"' value='' />").collect(Collectors.joining("\n")) + 
				"</form>";
	}
	
	public static String getMostHits(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<Episode> mostHits = DB.popularEpisodes(DB.PopularEpisode.HITS);
		StringBuilder html = new StringBuilder("<h1>Most hits</h1>\n<p>Each time an episode page is loaded, that's one hit.</p>\n");
		html.append(getPopularityTable(mostHits));
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	public static String getMostViews(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<Episode> mostViews = DB.popularEpisodes(DB.PopularEpisode.VIEWS);
		StringBuilder html = new StringBuilder("<h1>Most views</h1><p>This is the number of logged in users who have viewed the episode.</p>\n");
		html.append(getPopularityTable(mostViews));
		
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	public static String getMostUpvotes(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<Episode> mostUpvotes = DB.popularEpisodes(DB.PopularEpisode.UPVOTES);
		StringBuilder html = new StringBuilder("<h1>Most upvotes</h1><p>Episodes that have received the most upvotes from users.</p>\n");
		html.append(getPopularityTable(mostUpvotes));
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	private static String getPopularityTable(List<Episode> arr) {
		StringBuilder sb = new StringBuilder();
		sb.append("<table class=\"popular\"><thead><tr><th>Link/Title</th><th><a href=/fb/mosthits>Hits</a></th><th><a href=/fb/mostviews>Views</a></th><th><a href=/fb/mostupvotes>Upvotes</a></th><th>Story</th><th>Tags</th></tr></thead><tbody>\n");
		for (Episode ep : arr) {
			FlatEpisode rootEp = Story.getRootEpisodeById(DB.newMapToIdList(ep.newMap).findFirst().get());
			String story;
			if (rootEp == null) story = "";
			else story = rootEp.link;
			sb.append("<tr><td>" + (ep.link.toLowerCase().trim().equals(ep.title.toLowerCase().trim())?"":(escape(ep.title) + "<br/>")) + "<a href=/fb/story/" + ep.generatedId + ">" + escape(ep.link) + "</a></td><td>" + ep.hits + "</td><td>" + ep.views + "</td><td>" + ep.upvotes + "</td><td>" + escape(story) + "</td><td>"+Story.tagsHtmlView(ep.tags)+"</td></tr>\n");
		}
		sb.append("</tbody></table>\n");
		return sb.toString();
	}
	
	
	/////////////////////////////////////// functions to add episodes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	
	/**
	 * Returns the form for adding new episodes
	 * @param id id of parent episode
	 * @return HTML form
	 */
	public static String addForm(long parentId, Cookie token, Cookie fbjs) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user == null) return Strings.getFile("generic.html",user).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		FlatEpisode parent;
		try {
			parent = DB.getFlatEp(parentId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + parentId);
		}
		boolean parseMarkdown = fbjs==null || !"true".equals(fbjs.getValue());
		return Strings.getFile("addform.html", user)
				.replace("$MARKDOWNSTATUS", parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown")
				.replace("$TITLE", parent.title)
				.replace("$ID", parentId+"")
				.replace("$EXTRA", tagsHtmlForm(DB.getAllTags(), true))
				.replace("$OLDBODY",parseMarkdown?Story.formatBody(parent.body):escape(parent.body));
	}
	
	/**
	 * Adds an episode to the story
	 * @param id id of parent episode
	 * @param title title of new episode
	 * @param body body of new episode
	 * @param author author of new episode
	 * @return ID of new episode
	 * @throws EpisodeException if there's any error, e.getMessage() will contain HTML page for error
	 */
	public static long addPost(long generatedId, String link, String title, String body, Cookie token, MultivaluedMap<String, String> formParams) throws EpisodeException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		link = link.trim();
		title = title.trim();
		body = body.trim();
		
		String errors = checkEpisode(link, title, body);
		if (errors != null) throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", errors));
		try {
			final long newId = DB.addEp(generatedId, link, title, body, user.id, new Date());
			DB.updateTags(newId, user.id, formParams);
			return newId;
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", e.getMessage()));
		}
	}
	
	/**
	 * Returns the form for adding new root episodes, or error page if not admin
	 * @param id id of parent episode
	 * @return HTML form
	 */
	public static String newRootForm(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e1) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		if (user.level < 100) return Strings.getFile("generic.html",user).replace("$EXTRA", "Only admins can add new root episodes");

		return Strings.getFile("newrootform.html", user);
	}
	
	public static long newRootPost(String link, String title, String body, Cookie token) throws EpisodeException, FBLoginException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		if (user.level < 100) throw new FBLoginException(Strings.getFile("generic.html",user).replace("$EXTRA", "Only admins can add new root episodes"));
		link = link.trim();
		title = title.trim();
		body = body.trim();
		
		String errors = checkEpisode(link, title, body);
		if (errors != null) throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", errors));
		try {
			return DB.addRootEp(link, title, body, user.id, new Date());
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", e.getMessage()));
		}
	}
	
	public static class EpisodeException extends Exception {
		/** */
		private static final long serialVersionUID = 5020407245081273282L;
		public EpisodeException(String message) {
			super(message);
		}
	}
	
	/////////////////////////////////////// functions to modify episodes \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	
	/**
	 * Returns the form for adding new episodes
	 * @param id id of parent episode
	 * @return HTML form
	 */
	public static String modifyForm(long generatedId, Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		FlatEpisode ep;
		Map<Tag, Boolean> tags;
		try {
			ep = DB.getFlatEp(generatedId);
			tags = DB.getAllTagsForEpisode(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId);
		}
		if (!user.id.equals(ep.authorId) && user.level<10) return Strings.getFile("generic.html",user).replace("$EXTRA", "You can only edit episodes that you wrote");
		
		return Strings.getFile("modifyform.html", user)
				.replace("$TITLE", escape(ep.title))
				.replace("$BODY", escape(ep.body))
				.replace("$LINK", escape(ep.link))
				.replace("$EXTRA", tagsHtmlForm(tags, true))
				.replace("$ID", ""+generatedId);
	}
	
	/**
	 * Modifies an episode of the story
	 * @param id id of episode
	 * @param title title of new episode
	 * @param body body of new episode
	 * @param author author of new episode
	 * @return id of modified episode
	 * @throws EpisodeException if error occurs, e.getMessage() will contain HTML page with error
	 */
	public static void modifyPost(long generatedId, String link, String title, String body, Cookie token, MultivaluedMap<String,String> formParams) throws EpisodeException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e1) {
			e1.printStackTrace();
			throw new EpisodeException(Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId));
		}

		if (!user.id.equals(ep.authorId) && user.level<10) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "You can only edit episodes that you wrote"));
		
		link = link.trim();
		title = title.trim();
		body = body.trim();
		
		String errors = checkEpisode(link, title, body);
		if (errors != null) throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", errors));
		
		if (ep.link.equals(link) && ep.title.equals(title) && ep.body.equals(body)) {
			try {
				DB.updateTags(generatedId, user.id, formParams);
				return;
			} catch (DBException e) {
				throw new EpisodeException(Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage()));
			}
		}
		
		try {
			if (user.level > 1) DB.modifyEp(generatedId, link, title, body, user.id, formParams);
			else {
				int result = DB.checkIfEpisodeCanBeModified(generatedId);
				if (result == 0) DB.modifyEp(generatedId, link, title, body, user.id, formParams);
				else if (result == 2) throw new EpisodeException(Strings.getFile("generic.html", user).replace("$EXTRA", "You have already submitted a modification for this episode. <br/>Please wait for the moderation team to either accept or reject your previous modification before submitting another one."));
				else { // result == 1
					DB.newEpisodeMod(generatedId, link, title, body);
					DB.updateTags(generatedId, user.id, formParams);
					throw new EpisodeException(Strings.getFile("generic.html", user).replace("$EXTRA", "Because you do not own a child episode, your modification has been submitted for approval by the moderation team. Please be patient."));
				}
			}
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", "Not found: " + generatedId));
		}	
	}
	
	private static String tagsHtmlForm(Map<Tag, Boolean> tags, boolean vertical) {
		return tags.entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getKey()))
				.map(e -> tagHtmlForm(e.getKey(), e.getValue(), vertical))
				.collect(Collectors.joining(System.lineSeparator()));
	}
	
	private static String tagHtmlForm(Tag tag, boolean checked, boolean vertical) {
		return (vertical ? "" : "<span style='border: 1px solid; margin: 2px; padding: 2px; white-space: nowrap;'>") + String.format(
				"<input type='checkbox' id='%s' name='%s' value='' %s><label for='%s' title='%s'> %s</label>"+(vertical ? "<br>" : " "), 
				escape(tag.shortName), 
				escape(tag.shortName), 
				checked ? "checked" : "",
				escape(tag.shortName), 
				escape((!vertical ? (tag.longName + (tag.description.length()>0 ? " - " : "")) : "") + tag.description),
				escape(tag.shortName + (vertical ? (" - " + tag.longName) : ""))) + (vertical ? "" : "</span>");
	}
	
	private static String tagsHtmlForm(Collection<Tag> tags, boolean vertical) {
		return tagsHtmlForm((Map<Tag, Boolean>) tags.stream().collect(Collectors.toMap(e->e, e->false, (a,b)->a||b, LinkedHashMap::new)), vertical);
	}
	
	public static String commentForm(long generatedId, Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId);
		}
		return Strings.getFile("commentform.html", user)
				.replace("$TITLE", escape(ep.title))
				.replace("$ID", ""+generatedId);
	}
	
	public static String flagCommentForm(long id, Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		
		return Strings.getFile("commentflagform.html", user).replace("$ID", Long.toString(id));
	}
	
	/**
	 * 
	 * @param id
	 * @param comment
	 * @param token
	 * @return id of new comment
	 * @throws EpisodeException
	 */
	public static long commentPost(long generatedId, String comment, Cookie token) throws EpisodeException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		
		comment = comment.trim();
		
		if (comment.length() == 0) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Comment cannot be empty."));
		if (comment.length() > 5000) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Comment cannot be longer than 5000 (" + comment.length() + ")."));
		
		ArrayList<String> list = new ArrayList<>();
		for (String r : replacers) {
			if (comment.contains(r)) list.add(r);
		}
		if (!list.isEmpty()) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Comment text may not contain the following: " + list.stream().collect(Collectors.joining(" "))));
		
		try {
			return DB.addComment(generatedId, user.id, comment);
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", e.getMessage()));
		}
	}
	
	public static FlatEpisode flagCommentPost(long id, String body, Cookie token) throws EpisodeException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		
		body = body.trim();
		
		if (body.length() == 0) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Flag cannot be empty."));
		if (body.length() > 5000) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Flag cannot be longer than 5000 (" + body.length() + ")."));
		
		ArrayList<String> list = new ArrayList<>();
		for (String r : replacers) {
			if (body.contains(r)) list.add(r);
		}
		if (!list.isEmpty()) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Flag text may not contain the following: " + list.stream().collect(Collectors.joining(" "))));
		
		try {
			return DB.flagComment(id, user.id, body);
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", e.getMessage()));
		}
		
	}
	
	public static String flagForm(long generatedId, Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId);
		}
		if (user.id.equals(ep.authorId)) return Strings.getFile("generic.html",user).replace("$EXTRA", "You cannot flag your own episode.");
		return Strings.getFile("flagform.html", user)
				.replace("$TITLE", escape(ep.title))
				.replace("$ID", ""+generatedId);
	}
	
	public static void flagPost(long generatedId, String flag, Cookie token) throws EpisodeException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			throw new EpisodeException(Strings.getFile("generic.html",null).replace("$EXTRA", Strings.getString("must_be_logged_in")));
		}
		
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e1) {
			throw new EpisodeException(Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId));
		}
		if (user.id.equals(ep.authorId)) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "You cannot flag your own episode."));
		
		flag = flag.trim();
		
		if (flag.length() == 0) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Reason cannot be empty."));
		if (flag.length() > 100000) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Reason cannot be longer than 100000 (" + flag.length() + ")."));
		
		ArrayList<String> list = new ArrayList<>();
		for (String r : replacers) {
			if (flag.contains(r)) list.add(r);
		}
		if (!list.isEmpty()) throw new EpisodeException(Strings.getFile("generic.html",user).replace("$EXTRA", "Flag text may not contain the following: " + list));
		
		try {
			DB.flagEp(generatedId, user.id, flag);
		} catch (DBException e) {
			throw new EpisodeException(Strings.getFile("failure.html", user).replace("$EXTRA", e.getMessage()));
		}
		
	}	
	
	/////////////////////////////////////// utility functions \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	
	public static String commentListToHTML(List<Comment> comments, boolean parseMarkdown, boolean includeAttribution) {
		StringBuilder html = new StringBuilder();
		for (Comment c : comments) {
			html.append("<p><div class=\"" + (parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown") + "\">" + (parseMarkdown?Story.formatBody(c.text):escape(c.text)) + "</div></p>");
			html.append("<p>");
			if (includeAttribution) {
				html.append("By ");
				if (!(c.user.avatarUnsafe==null||c.user.avatarUnsafe.trim().length()==0)) html.append("<img class=\"avatarsmall\" alt=\"avatar\" src=\""+escape(c.user.avatarUnsafe) + "\" /> ");
				html.append("<a href=/fb/user/" + c.user.id + ">" + escape(c.user.authorUnsafe) + "</a> - \n");
			}
			html.append("<a href=/fb/story/" + c.episode.generatedId + "#comment" + c.id + ">" + (Dates.outputDateFormat2(c.date)) + "</a>\n");
			if (c.modVoice) html.append(" - <em>This comment is from a site Moderator</em>\n");
			html.append("</p><p>On " + "<a href=/fb/story/" + c.episode.generatedId + ">" + (escape(c.episode.link)) + "</a></p>\n");
			html.append("<hr/><hr/>\n");
		}
		return html.toString();
	}
	
	/**
	 * Checks that episode fields are non-empty within appropriate limits
	 * @param link
	 * @param title
	 * @param body
	 * @return null if no errors, else string containing errors
	 */
	private static String checkEpisode(String link, String title, String body) {
		StringBuilder errors = new StringBuilder();
		if (link.length() == 0) errors.append("Link text cannot be empty<br/>\n");
		if (title.length() == 0) errors.append("Title cannot be empty<br/>\n");
		if (body.length() == 0) errors.append("Body cannot be empty<br/>\n");
		
		if (link.length() > 255) errors.append("Link text cannot be longer than 255 (" + link.length() + ")<br/>\n");
		if (title.length() > 255) errors.append("Title cannot be longer than 255 (" + title.length() + ")<br/>\n");
		if (body.length() > 100000) errors.append("Body cannot be longer than 100000 (" + body.length() + ")<br/>\n");
				
		TreeSet<String> list = new TreeSet<>();
		for (String s : replacers) if (link.contains(s)) list.add(s);
		for (String s : replacers) if (title.contains(s)) list.add(s);
		for (String s : replacers) if (body.contains(s)) list.add(s);
		if (!list.isEmpty()) {
			errors.append("Link text, title, and body may not contain any of the following strings: ");
			for (String s : list) errors.append("\"" + s + "\"");
			errors.append("<br/>\n");
		}
		
		if (errors.length() > 0) return errors.toString();
		
		return (errors.length() > 0) ? errors.toString() : null;
	}

	public static final Set<String> replacers = Collections
			.unmodifiableSet(new HashSet<>(Stream.of(
					"$ACCOUNT", "$ADDEP", "$AUTHOR", "$AUTHORID", "$AUTHORNAME", "$AVATARURL", "$BODY", "$CHILD", 
					"$CHILDCOUNT", "$CHILDREN", "$COMMENT", "$COMMENTS", "$COMPLETEDATE", "$DATE", "$DONATEBUTTON", 
					"$EDITDATE", "$EDITORID", "$EDITORNAME", "$EPISODES", "$EXTRA", "$HITS", "$ID", "$LINK", 
					"$MODERATORSTATUS", "$MODIFY", "$NUMPAGES", "$OLDBODY", "$OLDDONATEBUTTON", "$PAGECOUNT", "$PARENTID", 
					"$PATHTOHERE", "$PREVNEXT", "$RAWBODY", "$RECAPTCHASITEKEY", "$SEARCHTERM", "$SORTORDER", 
					"$STORY", "$STYLE", "$THEMES", "$TIMELIMIT", "$TITLE", "$TOKEN", "$UPVOTES", "$VIEWS", "$OGDESCRIPTION", 
					"$HIDEIMAGEBUTTON", "$AVATARURLMETA", "$TAGS", "$USERSBLOCKEDFROMRECENTS")
					.collect(Collectors.toSet())));
	
	/**
	 * Escape body text and convert markdown/formatting to HTML
	 * @param body unformatted markdown body
	 * @return HTML formatted body
	 */
	public static String formatBody(String body) {
		return Markdown.formatBody(body);
	}

	private Story() {}
}
