package fb.api;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.Story;
import fb.Story.EpisodeException;
import fb.objects.FlatEpisode;
import fb.util.Strings;

@Path("fb")
public class AddStuff {
	/**
	 * Returns the form for adding new episodes
	 * 
	 * @param id
	 *            id of parent episode
	 * @return HTML form to add episode
	 */
	@GET
	@Path("add/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response add(@Context UriInfo uriInfo, @PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		if (("https://" + uriInfo.getRequestUri().getHost() + "/fb/get/" + id).length() > 1990) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", EPISODE_TOO_LONG)).build();
		return Response.ok(Story.addForm(id, fbtoken)).build();
	}
	
	private static String EPISODE_TOO_LONG = ""
			+ "<p>At this time, no one may add episodes here as the URL is getting too long for some browsers to handle properly.</p>\n"
			+ "<p>We are aware of the problem and the solution is still in development. Please be patient, and feel free to contribute to other branches in the meantime!</p>";
	
	/**
	 * Returns the form for adding new episodes
	 * 
	 * @param id
	 *            id of parent episode
	 * @return HTML form to add episode
	 */
	@GET
	@Path("modify/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response modify(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.modifyForm(id, fbtoken)).build();
	}

	/**
	 * Adds an episode to the story
	 * 
	 * @param id
	 *            id of parent episode
	 * @param title
	 *            title of new episode
	 * @param body
	 *            body of new episode
	 * @param author
	 *            author of new episode
	 * @return HTML success page with link to new episode
	 */
	@POST
	@Path("addpost/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response addpost(@Context UriInfo uriInfo, @PathParam("id") String id, @FormParam("link") String link,
			@FormParam("title") String title, @FormParam("body") String body, 
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (("https://" + uriInfo.getRequestUri().getHost() + "/fb/get/" + id).length() > 1990) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", EPISODE_TOO_LONG)).build();
		
		try {
			String childID = Story.addPost(id, link, title, body, fbtoken);
			return Response.seeOther(GetStuff.createURI("/fb/get/" + childID)).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
	}
	
	/**
	 * Adds an episode to the story
	 * 
	 * @param id
	 *            id of parent episode
	 * @param title
	 *            title of new episode
	 * @param body
	 *            body of new episode
	 * @param author
	 *            author of new episode
	 * @return HTML success page with link to new episode
	 */
	@POST
	@Path("modifypost/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response modifypost(@PathParam("id") String id, @FormParam("link") String link,
			@FormParam("title") String title, @FormParam("body") String body, 
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			String modifiedID = Story.modifyPost(id, link, title, body, fbtoken);
			return Response.seeOther(GetStuff.createURI("/fb/get/" + modifiedID)).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	@GET
	@Path("addcomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response addcomment(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.commentForm(id, fbtoken)).build();
	}
	
	@POST
	@Path("addcommentpost/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response addcommentpost(@PathParam("id") String id, @FormParam("body") String body,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			long commentID = Story.commentPost(id, body, fbtoken);
			return Response.seeOther(GetStuff.createURI("/fb/get/" + id + "#comment"+commentID)).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	@GET
	@Path("flagcomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response flagcomment(@PathParam("id") long id, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.flagCommentForm(id, fbtoken)).build();
	}
	
	@POST
	@Path("flagcommentpost/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response flagcommentpost(@PathParam("id") long id, @FormParam("body") String body,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			FlatEpisode ep = Story.flagCommentPost(id, body, fbtoken);
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "<p>Comment flagged</p><p><a href=/fb/get/" + ep.id + "#comments>Return to episode</a></p>")).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	@GET
	@Path("flag/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response flag(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.flagForm(id, fbtoken)).build();
	}
	
	@POST
	@Path("flagpost/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response flagpost(@PathParam("id") String id, @FormParam("body") String body,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Story.flagPost(id, body, fbtoken);
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Episode successfully flagged")).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	private static Date parseDate(String dateString) throws Exception {
		Date date;
		try {
			date = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dateString)));
		} catch (Exception e0) {
			try {
				date = Date.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(dateString)));
			} catch (Exception e1) {
				try {
					date = Date.from(Instant.from(DateTimeFormatter.ISO_DATE.parse(dateString)));
				} catch (Exception e2) {
					try {
						date = Date.from(Instant.from(DateTimeFormatter.BASIC_ISO_DATE.parse(dateString)));
					} catch (Exception e3) {
						try {
							date = Date.from(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateString)));
						} catch (Exception e4) {
							throw new Exception("BadDate");
						}
					}
				}
			}
		}
		return date;
	}
	
	@POST
	@Path("archiveapi")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response archiveapi(String json) {
		ArchiveResponse ar = new ArchiveResponse();
		
		System.out.println(json);
		
		ArchiveObject ao = new Gson().fromJson(json, ArchiveObject.class);
		if (ao.token == null || ao.parentid == null || ao.author == null || ao.title==null || ao.link==null || ao.body==null || ao.date == null) {
			ar.error="NullError";
			System.out.println("NullArgument");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		
		if (!DB.isValidArchiveToken(ao.token)) {
			ar.error = "BadToken";
			System.out.println("BadToken");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		Date date;
		try {
			date = parseDate(ao.date);
		} catch (Exception e1) {
			ar.error = "BadDate";
			System.out.println("BadDate");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		} 
		
		String id;
		try {
			id = DB.addArchiveEp(ao.parentid, ao.link, ao.title, ao.body, ao.author, date);
		} catch (DBException e) {
			System.out.println(e.getMessage());
			ar.error = "BadParent";
			System.out.println("BadParent");

			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		ar.id = id;
		System.out.println("ID: " + id);
		return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).build();
	}
	public static class ArchiveResponse {
		public String id;
		public String error;
	}
	public static class ArchiveObject {
		public final String token;
		public final String parentid;
		public final String author;
		public final String link;
		public final String title;
		public final String body;
		public final String date;
		public ArchiveObject(String to, String p, String a, String l, String ti, String b, String d) {
			token = to;
			parentid = p;
			author = a;
			link = l;
			title = ti;
			body = b;
			date = d;
		}
	}
}
