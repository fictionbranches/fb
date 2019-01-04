package fb.api;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.Map.Entry;

import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.Story;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.util.Dates;
import fb.util.Strings;

@Path("fb")
public class GetStuff {
	
	@GET
	@Path("asdf")
	public static Response asdf() throws DBException {
		Strings.log("asdf");
		//return Response.seeOther(createURI("/fb?asdf=qwerty&qwery=asdf")).build();
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
			UriBuilder ub = UriBuilder.fromUri(uri);//.queryParam("q")
			for (Entry<String,String> param : params) ub = ub.queryParam(param.getKey(), param.getValue());
			uri = ub.build();
		}
		System.out.println("Redirecting to " + uri);
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
		//return new NewCookie(new NewCookie(name, value).toCookie(), "fbtoken", Integer.MAX_VALUE, Date.from(Instant.MAX), true, true);
		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return ret;
	}
	
	/**
	 * Displays welcome page (not intro page, which is statically served by nginx)
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
	@Path("get")
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
	@Path("get/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response get(@Context UriInfo uriInfo, @PathParam("id") String id, @QueryParam("sort") String sort, @QueryParam("vote") String vote, @CookieParam("fbchildsort") Cookie fbchildsort, @CookieParam("fbadvancedchildren") Cookie fbadvancedchildren, @CookieParam("fbtoken") Cookie fbtoken) {
		if (vote != null && fbtoken != null) {
			Response ret = Response.seeOther(GetStuff.createURI("/fb/get/" + id)).build();
			String username;
			switch (vote.toLowerCase()) {
			case "up":
				username = Accounts.getUsernameFromCookie(fbtoken);
				if (username == null) return ret;
				try {
					DB.upvote(id, username);
				} catch (DBException e) {
					return ret;
				}
				break;
			case "down":
				username = Accounts.getUsernameFromCookie(fbtoken);
				if (username == null) return ret;
				try {
					DB.downvote(id, username);
				} catch (DBException e) {
					return ret;
				}
				break;
			}
			return ret;
		}
		
		boolean advancedChildren = false;
		if (fbadvancedchildren != null && fbadvancedchildren.getValue().equals("true")) advancedChildren = true;
		
		if (sort != null) {
			ResponseBuilder ret = Response.seeOther(createURI("/fb/get/" + id + "#children"));
			switch (sort.toLowerCase()) {
			case "oldest":
			case "newest":
			case "mostfirst":
			case "leastfirst":
			case "random":
				ret = ret.cookie(newCookie("fbchildsort", sort.toLowerCase(), uriInfo.getRequestUri().getHost()));
			}
			return ret.build();
		}
		
		int sortNum = 0;
		if (fbchildsort != null) switch (fbchildsort.getValue().toLowerCase()) {
		case "oldest":
			sortNum = 0;
			break;
		case "newest":
			sortNum = 1;
			break;
		case "mostfirst":
			sortNum = 2;
			break;
		case "leastfirst":
			sortNum = 3;
			break;
		case "random":
			sortNum = 4;
			break;
		}
		return Response.ok(Story.getHTML(id, sortNum, InitWebsite.DEV_MODE||advancedChildren /* TODO advancedChildren*/, fbtoken)).build();
	}
	
	/**
	 * Gets an episode by its id, newest first sort
	 * 
	 * DEPRECATED! Will be removed in a future update
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
//	@GET
//	@Path("getoldest/{id}")
//	@Produces(MediaType.TEXT_HTML)
//	public Response getoldest(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
//		return Response.seeOther(createURI("/fb/get/" + id)).cookie(newCookie("fbchildsort", "oldest")).build();
//	}

	/**
	 * Gets an episode by its id, newest first sort
	 * 
	 * DEPRECATED! Will be removed in a future update
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
//	@GET
//	@Path("getnewest/{id}")
//	@Produces(MediaType.TEXT_HTML)
//	public Response getnewest(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
//		return Response.seeOther(createURI("/fb/get/" + id)).cookie(newCookie("fbchildsort", "newest")).build();
//	}

	/**
	 * Gets an episode by its id, most children first sort
	 * 
	 * DEPRECATED! Will be removed in a future update
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
//	@GET
//	@Path("getmostfirst/{id}")
//	@Produces(MediaType.TEXT_HTML)
//	public Response getmostfirst(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
//		//return Response.ok(Story.getHTML(id, 2, fbtoken)).build();
//		/*HashMap<String,String> params = new HashMap<>();
//		params.put("sort","mostfirst");
//		return Response.seeOther(createURI("/fb/get/" + id, params)).build();*/
//		return Response.seeOther(createURI("/fb/get/" + id)).cookie(newCookie("fbchildsort", "mostfirst")).build();
//	}

	/**
	 * Gets an episode by its id, least children first sort
	 * 
	 * DEPRECATED! Will be removed in a future update
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
//	@GET
//	@Path("getleastfirst/{id}")
//	@Produces(MediaType.TEXT_HTML)
//	public Response getleastfirst(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
//		/*//return Response.ok(Story.getHTML(id, 3, fbtoken)).build();
//		HashMap<String,String> params = new HashMap<>();
//		params.put("sort","leastfirst");
//		return Response.seeOther(createURI("/fb/get/" + id, params)).build();*/
//		return Response.seeOther(createURI("/fb/get/" + id)).cookie(newCookie("fbchildsort", "leastfirst")).build();
//	}

	/**
	 * Gets an episode by its id, least children first sort
	 * 
	 * DEPRECATED! Will be removed in a future update
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
//	@GET
//	@Path("getrandom/{id}")
//	@Produces(MediaType.TEXT_HTML)
//	public Response getrandom(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
//		/*//return Response.ok(Story.getHTML(id, 4, fbtoken)).build();
//		HashMap<String,String> params = new HashMap<>();
//		params.put("sort","random");
//		return Response.seeOther(createURI("/fb/get/" + id, params)).build();*/
//		return Response.seeOther(createURI("/fb/get/" + id)).cookie(newCookie("fbchildsort", "random")).build();
//	}
	
	/**
	 * Gets an episode as raw text
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return HTML episode
	 */
	@GET
	@Path("getraw/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public String getraw(@PathParam("id") String id) {
		FlatEpisode ep;
		try {
			ep = DB.getFlatEp(id);
		} catch (DBException e) {
			return "Not found: " + id;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(ep.id + "\n");
		sb.append(ep.link + "\n");
		sb.append(ep.title + "\n");
		sb.append(ep.authorName + "\n");
		sb.append(Dates.outputDateFormat(ep.date) + "\n");
		sb.append(ep.body + "\n");
		return sb.toString();
	}

	@GET
	@Path("recent")
	@Produces(MediaType.TEXT_HTML)
	public Response recentstory(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.seeOther(GetStuff.createURI("/fb/recent/0/1")).build();
	}
	
	@GET
	@Path("recent/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response recentstory(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		return Response.seeOther(GetStuff.createURI("/fb/recent/"+id+"/1")).build();
	}
	
	@GET
	@Path("recent/{id}/{page}")
	@Produces(MediaType.TEXT_HTML)
	public Response recent(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @PathParam("page") String page, @QueryParam("reverse") String reverseString) {
		int pageNum;
		try {
			pageNum = Integer.parseInt(page);
			if (pageNum < 1) pageNum = 1;
		} catch (NumberFormatException e) {
			pageNum = 1;
		}
		boolean reverse = reverseString!=null;
		return Response.ok(Story.getRecents(fbtoken, id, pageNum, reverse)).build();
	}
	
	@GET
	@Path("outline/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response outline(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @QueryParam("page") String page) {
		if (!Accounts.isLoggedIn(fbtoken)) return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		
		if (page != null) try {
			int pageNum = Integer.parseInt(page);
			return Response.ok(DB.getOutlinePage(fbtoken, id, pageNum)).build();
		} catch (Exception e) {
			return Response.seeOther(GetStuff.createURI("/fb/outline/" + id)).build();
		}
		
		return Response.ok(Story.getOutlineScrollable(fbtoken, id)).build();
	}
	
	@GET
	@Path("path/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response path(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user != null) return Response.ok(Story.getPath(fbtoken, id)).build();
		else return Response.ok(Strings.getFile("generic.html",user).replace("$EXTRA", "You must be logged in to do that")).build();
	}
	
	@GET
	@Path("complete/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response getcomplete(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		if (!Accounts.isLoggedIn(fbtoken)) return Response.ok(Strings.getFileWithToken("generic.html",fbtoken).replace("$EXTRA", "You must be logged in to do that")).build();
		
		System.out.println("Complete request : " + id);
		String ret = Story.getCompleteHTML(fbtoken, id);
		System.out.println("Complete return  : " + id);
		Response response = Response.ok(ret).build();
		System.out.println("Complete response: " + id);
		return response;
	}
	
	@GET
	@Path("search/")
	@Produces(MediaType.TEXT_HTML)
	public Response getsearch(@CookieParam("fbtoken") Cookie fbtoken) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		return Response.ok(Story.getSearchHelp(fbtoken)).build();
	}
	
	@GET
	@Path("search/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response searchform(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @QueryParam("q") String q, @QueryParam("page") Integer page) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		if (q!=null && q.length() > 0) {
			if (page==null) page = 1;
			if (page < 1) page = 1;
			System.out.println("Token " + fbtoken);
			System.out.println("id " + id);
			System.out.println("q " + q);
			System.out.println("page " + page);
			return Response.ok(Story.searchPost(fbtoken, id, q, Integer.toString(page))).build();
		} 
		return Response.ok(Story.getSearchForm(fbtoken, id)).build();
	}
	
	/*@POST
	@Path("search/{id}/{page}")
	@Produces(MediaType.TEXT_HTML)
	public Response searchpost(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @QueryParam("q") String q, @PathParam("page") String page) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		return Response.ok(Story.searchPost(fbtoken, id, q, page)).build();
	}*/
	
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
		return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", Story.formatBody(Strings.getFile("faq.md", null)))).build();
	}
	
	@GET
	@Path("announcements")
	@Produces(MediaType.TEXT_HTML)
	public Response announcements(@CookieParam("fbtoken") Cookie fbtoken) {
		//return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", Story.formatBody(Strings.getFile("announcements.md", null)))).build();
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
		} catch (Exception e) {}
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
	@Path("delete/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response delete(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		return Response.ok(Story.getDeleteConfirmation(fbtoken, id)).build();
	}
	
	@POST
	@Path("delete/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response deleteconfirm(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		return Response.ok(Story.deleteEpisode(fbtoken, id)).build();
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
}
