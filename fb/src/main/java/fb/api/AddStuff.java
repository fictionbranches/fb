package fb.api;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fb.Accounts;
import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.Story;
import fb.Story.EpisodeException;
import fb.db.DBCommentSub;
import fb.db.DBEpisode;
import fb.db.DBUser;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.util.Strings;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Component
@Path("fb")
public class AddStuff {
		
	//private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	/**
	 * Returns the form for adding new episodes
	 * 
	 * @param id
	 *            id of parent episode
	 * @return HTML form to add episode
	 */
	@GET
	@Path("add/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response add(@Context UriInfo uriInfo, @PathParam("generatedId") long generatedId, @CookieParam("fbtoken") Cookie fbtoken, @CookieParam("fbjs") Cookie fbjs) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		return Response.ok(Story.addForm(generatedId, fbtoken, fbjs)).build();
	}

	/**
	 * Returns the form for adding new episodes
	 * 
	 * @param id
	 *            id of parent episode
	 * @return HTML form to add episode
	 */
	@GET
	@Path("modify/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response modify(@PathParam("generatedId") long generatedId, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.modifyForm(generatedId, fbtoken)).build();
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
	@Path("addpost/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response addpost(@Context UriInfo uriInfo, @PathParam("generatedId") long generatedId,
			@CookieParam("fbtoken") Cookie fbtoken, MultivaluedMap<String,String> formParams) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
				
		final String link = formParams.getFirst("link");
		final String title = formParams.getFirst("title");
		final String body = formParams.getFirst("body");

		try {
			long childID = Story.addPost(generatedId, link, title, body, fbtoken, formParams);
			return Response.seeOther(GetStuff.createURI("/fb/story/" + childID)).build();
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
	@Path("modifypost/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response modifypost(@PathParam("generatedId") long generatedId,
			@CookieParam("fbtoken") Cookie fbtoken, MultivaluedMap<String,String> formParams) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		final String link = formParams.getFirst("link");
		final String title = formParams.getFirst("title");
		final String body = formParams.getFirst("body");
		
		try {
			Story.modifyPost(generatedId, link, title, body, fbtoken, formParams);
			return Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId)).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	@POST
	@Path("addcommentpost/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response addcommentpost(@PathParam("generatedId") long generatedId, @FormParam("body") String body,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			long commentID = Story.commentPost(generatedId, body, fbtoken);
			return Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId + "#comment"+commentID)).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
	}
	
	@POST
	@Path("commentsubscribepost/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response commentsubscribepost(@PathParam("generatedId") long generatedId, @FormParam("commentsubvalue") String value,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		final boolean subscribe = value != null && value.trim().equalsIgnoreCase("true");
		
		Session session = DB.openSession();
		try {
			
			final String username = Accounts.getUsernameFromCookie(fbtoken).toLowerCase();
			if (username == null) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
			
			final DBUser dbUser = session.get(DBUser.class, username);
			if (dbUser == null) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
			
			final DBEpisode episode = session.get(DBEpisode.class, generatedId);
			if (episode == null) return Response.ok(Strings.getFile("generic.html", new FlatUser(dbUser)).replace("$EXTRA","Not found: " + generatedId)).build();
			
			if (episode.getAuthor().getId().equals(dbUser.getId())) Response.ok(Strings.getFile("generic.html", new FlatUser(dbUser)).replace("$EXTRA","You can't subscribe to your own episode.<br/>How did you even get here?")).build();

			final Response ret = Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId + "#comments")).build();
			
			Optional<DBCommentSub> opt = session.createQuery("from DBCommentSub ev where ev.episode.generatedId=" + generatedId + " and ev.user.id='" + username + "'", DBCommentSub.class).uniqueResultOptional();
			
			boolean userIsSubscribedToComments = opt.isPresent();
			if (userIsSubscribedToComments == subscribe) {
				return ret;
			}
			
			session.beginTransaction();
			if (subscribe) {
				DBCommentSub cs = new DBCommentSub();
				cs.setUser(dbUser);
				cs.setEpisode(episode);
				cs.setDate(new Date());
				session.save(cs);
			} else if (opt.isPresent()){
				session.delete(opt.get());
			}
			try {
				session.getTransaction().commit();
			} catch (Exception e) {
				return Response.ok(Strings.getFile("generic.html", new FlatUser(dbUser)).replace("$EXTRA",e.getMessage())).build();
			}
			
			return ret;
			
		} finally {
			DB.closeSession(session);
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
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "<p>Comment flagged</p><p><a href=/fb/story/" + ep.generatedId + "#comments>Return to episode</a></p>")).build();
		} catch (EpisodeException e) {
			return Response.ok(e.getMessage()).build();
		}
		
	}
	
	@GET
	@Path("flag/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response flag(@PathParam("generatedId") long generatedId, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		return Response.ok(Story.flagForm(generatedId, fbtoken)).build();
	}
	
	@POST
	@Path("flagpost/{generatedId}")
	@Produces(MediaType.TEXT_HTML)
	public Response flagpost(@PathParam("generatedId") long generatedId, @FormParam("body") String body,
			@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Story.flagPost(generatedId, body, fbtoken);
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
		
		
		ArchiveObject ao = new Gson().fromJson(json, ArchiveObject.class);
		if (ao.token == null || ao.parentid == null || ao.author == null || ao.title==null || ao.link==null || ao.body==null || ao.date == null) {
			ArchiveResponse ar = new ArchiveResponse(null, "NullError");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		
		if (!DB.isValidArchiveToken(ao.token)) {
			ArchiveResponse ar = new ArchiveResponse(null, "BadToken");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		Date date;
		try {
			date = parseDate(ao.date);
		} catch (Exception e1) {
			ArchiveResponse ar = new ArchiveResponse(null, "BadDate");
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		} 
		
		long generatedId;
		try {
			generatedId = DB.addArchiveEp(ao.parentid, ao.link, ao.title, ao.body, ao.author, date);
		} catch (DBException e) {
			ArchiveResponse ar = new ArchiveResponse(null, "BadParent");

			e.printStackTrace();
			
			return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).status(400).build();
		}
		ArchiveResponse ar = new ArchiveResponse(generatedId, null);
		return Response.ok(new GsonBuilder().setPrettyPrinting().create().toJson(ar)).build();
	}
	public static class ArchiveResponse {
		public final Long id;
		public final String error;
		public ArchiveResponse(Long id, String error) {
			this.id = id;
			this.error = error;
		}
	}
	public static class ArchiveObject {
		public final String token;
		public final Long parentid;
		public final String author;
		public final String link;
		public final String title;
		public final String body;
		public final String date;
		public ArchiveObject(String to, Long p, String a, String l, String ti, String b, String d) {
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
