package fb.api;

import static fb.util.Text.escape;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.CommentResultList;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.Story;
import fb.db.DBComment;
import fb.objects.Comment;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.util.Dates;
import fb.util.Strings;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

@Path("fb")
public class GetStuff {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	@GET
	@Path("asdf")
	public static Response asdf() throws DBException {
		LOGGER.error("asdf");
		throw new DBException("this is an exception asdf");
	}
	
	@GET
	@Path("areyoualive")
	public static String alive() {
		return "iamalive";
	}
	
	@SafeVarargs
	public static URI createURI(String url, Entry<String,String>... params) {
		URI uri = URI.create(url);
		if (params.length > 0) {
			UriBuilder ub = UriBuilder.fromUri(uri);
			for (Entry<String,String> param : params) ub = ub.queryParam(param.getKey(), param.getValue());
			uri = ub.build();
		}
		return uri;
	}
	
	public static NewCookie newCookie(String name, String value, String domain) {
		NewCookie ret;
		try {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.YEAR, 100);
			Date d = cal.getTime();
			ret = new NewCookie(name, 
				value, 
				"/", 
				domain, 
				1, 
				"fbtoken", 
				Integer.MAX_VALUE, 
				d, 
				true, 
				true);
		} catch (Exception e) {
			LOGGER.error("newCookie exception", e);
			throw new RuntimeException(e);
		}
		return ret;
	}
	
	/**
	 * Displays welcome page
	 * 
	 * @return HTML welcome page
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	public Response getRoot(@CookieParam("fbtoken") Cookie fbtoken) {
		
		return Response.ok(Story.getWelcome(fbtoken)).build();
	}

	/**
	 * Redirects to welcome page
	 * @return HTTP 302 redirect
	 */
	@GET
	@Path("story")
	@Produces(MediaType.TEXT_HTML)
	public Response getGet() {
		return Response.seeOther(createURI("/fb")).build();
	}

	/**
	 * Gets an episode by its id, default sort
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode with id
	 */
	@GET
	@Path("story/{generatedid}")
	@Produces(MediaType.TEXT_HTML)
	public Response get(
			@Context UriInfo uriInfo, 
			@PathParam("generatedid") long generatedId,
			@QueryParam("sort") String sort, 
			@QueryParam("vote") String vote, 
			@QueryParam("favorite") String favorite,
			@CookieParam("fbchildsort") Cookie fbchildsort,
			@CookieParam("fbadvancedchildren") Cookie fbadvancedchildren, 
			@CookieParam("fbtoken") Cookie fbtoken,
			@CookieParam("fbjs") Cookie fbjs
			) {
		if (vote != null && fbtoken != null) {
			final Response redirectHere = Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId)).build();
			String username = Accounts.getUsernameFromCookie(fbtoken);
			if (username == null) return redirectHere;

			try {
				if (vote.equalsIgnoreCase("up")) DB.upvote(generatedId, username);
				else if (vote.equalsIgnoreCase("down")) DB.downvote(generatedId, username);
			} catch (DBException e) {
				return redirectHere;
			}
			return redirectHere;
		}
		
		if (favorite != null && fbtoken != null) {
			final Response redirectHere = Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId)).build();
			String username = Accounts.getUsernameFromCookie(fbtoken);
			if (username == null) return redirectHere;

			try {
				if (favorite.equalsIgnoreCase("up")) DB.favoriteEp(generatedId, username);
				else if (favorite.equalsIgnoreCase("down")) DB.unfavoriteEp(generatedId, username);
			} catch (DBException e) {
				return redirectHere;
			}
			return redirectHere;
		}
		
		boolean advancedChildren = false;
		if (fbadvancedchildren != null && fbadvancedchildren.getValue().equals("true")) advancedChildren = true;
		
		if (sort != null) {
			ResponseBuilder ret = Response.seeOther(createURI("/fb/story/" + generatedId + "#children"));
			ret = switch (sort.toLowerCase()) {
				case "oldest", "newest", "mostfirst", "leastfisrt", "random" -> ret.cookie(newCookie("fbchildsort", sort.toLowerCase(), uriInfo.getRequestUri().getHost()));
				default -> ret;
			};
			return ret.build();
		}
		
		int sortNum = 0;
		if (fbchildsort != null) sortNum = switch (fbchildsort.getValue().toLowerCase()) {
			case "oldest" -> 0;
			case "newest" -> 1;
			case "mostfirst" -> 2;
			case "leastfirst" -> 3;
			case "random" -> 4;
			default -> 0;
		};
		
		boolean parseMarkdown = fbjs==null || !"true".equals(fbjs.getValue());
		return Response.ok(Story.getHTML(generatedId, sortNum, advancedChildren, fbtoken, parseMarkdown)).build();
	}
	
	/**
	 * Gets an episode as raw text
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
	@GET
	@Path("getraw/{generatedId}")
	@Produces(MediaType.TEXT_PLAIN)
	public String getraw(@PathParam("generatedId") long generatedId) {
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return "Not found: " + generatedId;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(ep.generatedId + "\n");
		sb.append(ep.newMap.substring(1,ep.newMap.length()).replace('B','-') + "\n");
		sb.append(ep.link + "\n");
		sb.append(ep.title + "\n");
		sb.append(ep.authorName + "\n");
		sb.append(Dates.outputDateFormat2(ep.date) + "\n");
		sb.append(ep.body + "\n");
		return sb.toString();
	}

	/**
	 * Redirects to new URL
	 * @param fbtoken
	 * @param generatedId
	 * @return
	 */
	@GET
	@Path("recent/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response recentbak(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId) {
		return Response.seeOther(GetStuff.createURI("/fb/recent?story=" + generatedId)).build();
	}
	
	/**
	 * Redirects to new URL
	 * @param fbtoken
	 * @param generatedId
	 * @param page
	 * @param reverseString
	 * @return
	 */
	@GET
	@Path("recent/{generatedId}/{page}")
	@Produces(MediaType.TEXT_HTML)
	public Response recentbak2(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId, @PathParam("page") String page, @QueryParam("reverse") String reverseString) {
		int pageNum;
		try {
			pageNum = Integer.parseInt(page);
			if (pageNum < 1) pageNum = 1;
		} catch (NumberFormatException e) {
			pageNum = 1;
		}
		boolean reverse = reverseString!=null;
		return Response.seeOther(GetStuff.createURI("/fb/recent?story=" + generatedId + "&page=" + pageNum + (reverse?"&reverse":""))).build();
	}
	
	@GET
	@Path("recent")
	@Produces(MediaType.TEXT_HTML)
	public Response recent(
			@CookieParam("fbtoken") Cookie fbtoken, 
			@QueryParam("story") String story,
			@QueryParam("page") String page, 
			@QueryParam("reverse") String reverseString,
			@QueryParam("tag") String tagShortName) {
		int pageNum;
		try {
			pageNum = Integer.parseInt(page);
			if (pageNum < 1) pageNum = 1;
		} catch (Exception e) {
			pageNum = 1;
		}
		boolean reverse = reverseString!=null;
		if (story == null || story.length() == 0) story = "0";
		return Response.ok(Story.getRecents(fbtoken, story, pageNum, reverse, tagShortName)).build();
	}
	
	@GET
	@Path("recentcomments")
	@Produces(MediaType.TEXT_HTML)
	public Response recentcomments(@CookieParam("fbtoken") Cookie fbtoken, @CookieParam("fbjs") Cookie fbjs, @QueryParam("page") String pageStr) throws DBException {
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e1) {
			user = null;
		}
		
		int page;
		try {
			page = Integer.parseInt(pageStr);
			if (page < 1) page = 1;
		} catch (Exception e) {
			page = 1;
		}
		
		boolean parseMarkdown = fbjs==null || !"true".equals(fbjs.getValue());
				
		CommentResultList crl = DB.getRecentComments(page);
		
		StringBuilder prevNext = new StringBuilder();
		if (crl.numPages <= 8) {
			for (int i=1; i<=crl.numPages; ++i) {
				if (i == page) prevNext.append(i + " ");
				else prevNext.append("<a class=\"monospace\" href=?page=" + i + ">" + i + "</a> ");
			}
		} else {
			if (page <= 3) { // 1 2 3 4 ... n
				for (int i=1; i<=4; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append("<a class=\"monospace\" href=?page=" + i + ">" + i + "</a> ");
				}
				prevNext.append("... ");
				prevNext.append("<a class=\"monospace\" href=?page=" + crl.numPages + ">" + crl.numPages + "</a> ");
			} else if (page >= crl.numPages-3) { // 1 ... n-3 n-2 n-1 n
				prevNext.append("<a class=\"monospace\" href=?page=" + 1 + ">" + 1 + "</a> ");
				prevNext.append("... ");
				for (int i=crl.numPages-3; i<=crl.numPages; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append("<a class=\"monospace\" href=?page=" + i + ">" + i + "</a> ");
				}
			} else { // 1 ... x-2 x-1 x x+1 x+2 ... n
				prevNext.append("<a class=\"monospace\" href=?page=" + 1 + ">" + 1 + "</a> ");
				prevNext.append("... ");
				for (int i=page-2; i<=page+2; ++i) {
					if (i == page) prevNext.append(i + " ");
					else prevNext.append("<a class=\"monospace\" href=?page=" + i + ">" + i + "</a> ");
				}
				prevNext.append("... ");
				prevNext.append("<a class=\"monospace\" href=?page=" + crl.numPages + ">" + crl.numPages + "</a> ");
			}
		}
		
		StringBuilder html = new StringBuilder();
		html.append("<h1>Recent comments</h1>");
		html.append("<p>"+prevNext+"</p>");
		html.append("<hr/><hr/>\n");
		for (Comment c : crl.comments) {
			html.append("<p><div class=\"" + (parseMarkdown?"fbparsedmarkdown":"fbrawmarkdown") + "\">" + (parseMarkdown?Story.formatBody(c.text):escape(c.text)) + "</div></p>");
			html.append("<p>By ");
			if (!(c.user.avatar==null||c.user.avatar.trim().length()==0)) html.append("<img class=\"avatarsmall\" alt=\"avatar\" src=\""+escape(c.user.avatar) + "\" /> ");
			html.append("<a href=/fb/user/" + c.user.id + ">" + escape(c.user.author) + "</a> - \n");
			html.append("<a href=/fb/story/" + c.episode.generatedId + "#comment" + c.id + ">" + (Dates.outputDateFormat2(c.date)) + "</a>\n");
			if (c.modVoice) html.append(" - <em>This comment is from a site Moderator</em>\n");
			html.append("</p><p>On " + "<a href=/fb/story/" + c.episode.generatedId + ">" + (escape(c.episode.link)) + "</a></p>\n");
			html.append("<hr/><hr/>\n");
		}
		
		return Response.ok(Strings.getFile("generic_meta.html", user)
				.replace("$EXTRA", html.toString())
				.replace("$TITLE", "Recent comments")
				.replace("$OGDESCRIPTION", "Just a list of the comments on the site.")
				).build();
		
	}
	
	private Response notLoggedInEpisode(long generatedId) {
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(generatedId);
		} catch (DBException e) {
			return notLoggedInOther();
		}
		
		return Response.ok(Strings.getFile("generic_meta.html",null)
			.replace("$EXTRA", "You must be logged in to do that")
			.replace("$TITLE", escape(ep.title))
			.replace("$OGDESCRIPTION", escape("By " + ep.authorName + System.lineSeparator() + ep.body))
			).build();
	}
	
	/**
	 * For pages that require login, but aren't episode-specific
	 * @param generatedId
	 * @return
	 */
	private Response notLoggedInOther() {
		return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
	}
	
	@GET
	@Path("outline/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response outline(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId, @QueryParam("page") String page) {
		if (!Accounts.isLoggedIn(fbtoken)) return notLoggedInEpisode(generatedId);
		
		if (page != null) try {
			int pageNum = Integer.parseInt(page);
			return Response.ok(DB.getOutlinePage(fbtoken, generatedId, pageNum)).build();
		} catch (Exception e) {
			return Response.seeOther(GetStuff.createURI("/fb/outline/" + generatedId)).build();
		}
		
		return Response.ok(Story.getOutlineScrollable(fbtoken, generatedId)).build();
	}
	
	@GET
	@Path("path/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response path(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId) {
		if (!Accounts.isLoggedIn(fbtoken)) return notLoggedInEpisode(generatedId);
		
		return Response.ok(Story.getPath(fbtoken, generatedId)).build();
	}
	
	@GET
	@Path("complete/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response getcomplete(@CookieParam("fbtoken") Cookie fbtoken, @CookieParam("fbjs") Cookie fbjs, @PathParam("generatedId") long generatedId) {
		if (!Accounts.isLoggedIn(fbtoken)) return notLoggedInEpisode(generatedId);
		
		String ret = Story.getCompleteHTML(fbtoken, generatedId, fbjs);
		return Response.ok(ret).build();
	}
	
	@GET
	@Path("search")
	@Produces(MediaType.TEXT_HTML)
	public Response getsearch(@CookieParam("fbtoken") Cookie fbtoken) {
		if (!InitWebsite.SEARCHING_ALLOWED) {
			String response = "Searching is disabled while the database is being indexed.";
			if (InitWebsite.INDEXER_MONITOR != null) {
				response += " " + InitWebsite.INDEXER_MONITOR.percent() + "% complete.";
			}
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", response)).build();
		}
		return Response.ok(Story.getSearchHelp(fbtoken)).build();
	}
	
	@GET
	@Path("search/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response searchform(@CookieParam("fbtoken") Cookie fbtoken, 
			@PathParam("generatedId") long generatedId, 
			@QueryParam("q") String q, 
			@QueryParam("page") Integer page, 
			@QueryParam("sort") String sort,
			@Context UriInfo uriInfo) {
		if (!InitWebsite.SEARCHING_ALLOWED) {
			String response = "Searching is disabled while the database is being indexed.";
			if (InitWebsite.INDEXER_MONITOR != null) {
				response += " " + InitWebsite.INDEXER_MONITOR.percent() + "% complete.";
			}
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", response)).build();
		}
		if (q!=null && q.length() > 0) {
			if (page==null) page = 1;
			if (page < 1) page = 1;
			if (sort != null && sort.length() == 0) sort = null;
			return Response.ok(Story.searchPost(fbtoken, generatedId, q, Integer.toString(page), sort, uriInfo.getQueryParameters())).build();
		} 
		return Response.ok(Story.getSearchForm(fbtoken, generatedId)).build();
	}
	
	@GET
	@Path("formatting")
	@Produces(MediaType.TEXT_HTML)
	public Response formatting(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Strings.getFileWithToken("formatting.html", fbtoken)).build();
	}
	
	@GET
	@Path("faq")
	@Produces(MediaType.TEXT_HTML)
	public Response faq(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Strings.getFileWithToken("generic_meta.html", fbtoken)
				.replace("$TITLE", "Fiction Branches FAQ")
				.replace("$OGDESCRIPTION", "Fiction Branches FAQ")
				.replace("$EXTRA", Story.formatBody(Strings.getFile("faq.md", null)))).build();
	}
	
	@GET
	@Path("announcements")
	@Produces(MediaType.TEXT_HTML)
	public Response announcements(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Story.getAnnouncements(fbtoken)).build();
	}
	
	@GET
	@Path("markannouncementread/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response announcements(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		try {
			long aid = Long.parseLong(id);
			FlatUser user = Accounts.getFlatUser(fbtoken);
			DB.markAnnouncementViewed(user.id, aid);
		} catch (Exception e) {
			LOGGER.warn("Account not found", e);
		}
		return Response.seeOther(GetStuff.createURI("/fb/announcements")).build();
	}
	
	@GET
	@Path("staff")
	@Produces(MediaType.TEXT_HTML)
	public Response staff(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.getStaff(fbtoken)).build();
	}
	
	@GET
	@Path("modhelp")
	@Produces(MediaType.TEXT_HTML)
	public Response modhelp(@CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<10) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be a moderator to do that")).build();
		return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA", Story.formatBody(Strings.getFile("modhelp.md", null)))).build();
	}
	
	@GET
	@Path("modvoicecomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response modvoicecomment(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") long id) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		
		Session sesh = DB.openSession();
		try {
			
			DBComment comment = sesh.get(DBComment.class, id);
			if (user.level < 10 || !user.id.equals(comment.getUser().getId())) {
				return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be a moderator to do that")).build();
			}
			
			try {
				sesh.beginTransaction();
				comment.setModVoice(!comment.isModVoice());
				sesh.merge(comment);
				sesh.getTransaction().commit();
			} catch (Exception e) {
				return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","Database error: " + e.getMessage())).build();
			}
			
			return Response.seeOther(GetStuff.createURI("/fb/story/" + comment.getEpisode().getGeneratedId() + "#comment" + comment.getId())).build();
			
		} finally {
			DB.closeSession(sesh);
		}
	}
	
	@GET
	@Path("deletecomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response deletecomment(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") long id) {
		return Response.ok(Story.getDeleteCommentConfirmation(fbtoken, id)).build();
	}
	
	@POST
	@Path("deletecomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response deletecommentconfirm(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") long id) {
		return Response.ok(Story.deleteComment(fbtoken, id)).build();
	}
	
	@GET
	@Path("delete/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response delete(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId) {
		return Response.ok(Story.getDeleteConfirmation(fbtoken, generatedId)).build();
	}
	
	@POST
	@Path("delete/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response deleteconfirm(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("generatedId") long generatedId) {
		return Response.ok(Story.deleteEpisode(fbtoken, generatedId)).build();
	}
	
	@GET
	@Path("mosthits")
	@Produces(MediaType.TEXT_HTML)
	public Response mosthits(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Story.getMostHits(fbtoken)).build();
	}
	
	@GET
	@Path("mostviews")
	@Produces(MediaType.TEXT_HTML)
	public Response mostviews(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Story.getMostViews(fbtoken)).build();
	}
	
	@GET
	@Path("mostupvotes")
	@Produces(MediaType.TEXT_HTML)
	public Response mostupvotes(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Story.getMostUpvotes(fbtoken)).build();
	}
	
	private static DB.PopularUserTime popularTime(String time) {
		if (time == null) return null;
		switch (time.trim().toLowerCase()) {
		case "week": return DB.PopularUserTime.WEEK;
		case "month": return DB.PopularUserTime.MONTH;
		case "all": return DB.PopularUserTime.ALL;
		default: return null;
		}
	}
	
	@GET
	@Path("leaderboardhits")
	@Produces(MediaType.TEXT_HTML)
	public Response leaderboardhits(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("time") String time) {
		DB.PopularUserTime t = popularTime(time);
		if (t==null) return Response.seeOther(GetStuff.createURI("/fb/leaderboardhits?time=week")).build();
		return Response.ok(Accounts.getMostHits(fbtoken, t)).build();
	}
	
	@GET
	@Path("leaderboardviews")
	@Produces(MediaType.TEXT_HTML)
	public Response leaderboardviews(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("time") String time) {
		DB.PopularUserTime t = popularTime(time);
		if (t==null) return Response.seeOther(GetStuff.createURI("/fb/leaderboardviews?time=week")).build();
		return Response.ok(Accounts.getMostViews(fbtoken, t)).build();
	}
	
	@GET
	@Path("leaderboardupvotes")
	@Produces(MediaType.TEXT_HTML)
	public Response leaderboardupvotes(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("time") String time) {
		DB.PopularUserTime t = popularTime(time);
		if (t==null) return Response.seeOther(GetStuff.createURI("/fb/leaderboardupvotes?time=week")).build();
		return Response.ok(Accounts.getMostUpvotes(fbtoken, t)).build();
	}
	
	@GET
	@Path("leaderboardepisodes")
	@Produces(MediaType.TEXT_HTML)
	public Response leaderboardepisodes(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("time") String time) {
		DB.PopularUserTime t = popularTime(time);
		if (t==null) return Response.seeOther(GetStuff.createURI("/fb/leaderboardepisodes?time=week")).build();
		return Response.ok(Accounts.getMostEpisodes(fbtoken, t)).build();
	}
	
	@GET
	@Path("stats")
	@Produces(MediaType.TEXT_HTML)
	public Response stats(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("start") String start, @QueryParam("end") String end) {
		
		ArrayList<String> dates = new ArrayList<>();
		ArrayList<BigInteger> counts = new ArrayList<>();
		
		if (start == null || end == null || !isDate(start) || !isDate(end)) {
			String[] defaultDates = defaultDates();
			start = defaultDates[0];
			end = defaultDates[1];
		}
		Session session = DB.openSession();
		try {
			
			String query = "SELECT d.day,count(fbepisodes.date) as ct from (\n" + 
					"    SELECT generate_series('"+start+"'\\:\\:timestamp, '"+end+"'\\:\\:timestamp, '1 day')\n" + 
					"  ) d(day)\n" + 
					"LEFT JOIN fbepisodes ON date_trunc('day', fbepisodes.date)=d.day\n" + 
					"group by d.day\n" + 
					"order by d.day asc;";
			
			@SuppressWarnings("unchecked")
			Stream<Object[]> stream = session.createNativeQuery(query)
			.getResultStream();
			
			stream.forEach(result->{
				String day = Dates.plainDate(Date.from(((java.sql.Timestamp)result[0]).toInstant())); // ugh
				BigInteger count = (BigInteger)result[1];
				dates.add(day);
				counts.add(count);
			});
			
		} finally {
			DB.closeSession(session);
		}
		
		Gson g = new Gson();
		
		String labelData = g.toJson(dates);
		String inputData = g.toJson(counts);
		
		
		
		return Response.ok(Strings.getFileWithToken("stats.html", fbtoken)
				.replace("$LABELDATA", labelData)
				.replace("$INPUTDATA", inputData)				
				.replace("$STARTDATE", start)				
				.replace("$ENDDATE", end)				
				).build();
	}
	
	private static String[] defaultDates() {
		Calendar now = Calendar.getInstance();
		String end = Dates.plainDate(now.getTime());
		now.add(Calendar.YEAR, -1);
		String start = Dates.plainDate(now.getTime());
		return new String[]{start, end};
	}
	
	public static boolean isDate(String date) {
		if (date.length() != 10) return false;
		int[] ints = {0,1,2,3,5,6,8,9};
		int[] dashes = {4,7};
		for (int i : ints) if (date.charAt(i) < '0' || date.charAt(i) > '9') return false;
		for (int i : dashes) if (date.charAt(i) != '-') return false;
		return true;
	}
}
