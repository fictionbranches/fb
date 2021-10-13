package fb.api;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.Story;
import fb.db.DBEpisode;
import fb.objects.Episode;
import fb.objects.EpisodeWithChildren;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("fbapi")
public class JSONStuff {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	private final ThreadLocal<Gson> g = ThreadLocal.withInitial(()->new GsonBuilder().setPrettyPrinting().create());
	public Gson g() {
		return g.get();
	}
	private final ThreadLocal<DateFormat> jdf = ThreadLocal.withInitial(()->{
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df;
	});
	private String toUTC(Date date) {
		return jdf.get().format(date);
	}
	
	@POST
	@Path("markdowntohtml")
	@Produces(MediaType.TEXT_HTML)
	public Response markdownToHTML(@FormParam("body") String body) {
		System.out.println("markdownToHTML API request: " + body.length());
		return Response.ok(Story.formatBody(body)).build();
	}
	
	/**
	 * Represents a complete user
	 */
	class JSONUser {
		public final String username;
		public final String author;
		public final String date;
		public final byte level;
		public final String theme; // HTML theme name
		public final String avatar;
		public JSONUser(FlatUser user) {
			this.username = user.id;
			this.author = user.author;
			this.date = toUTC(user.date);
			this.level = user.level;
			this.theme = user.theme.name;
			this.avatar = user.avatar;
		}
	}
	
	/**
	 * Represents a complete episode, including body text and simple child episodes
	 */
	class JSONEpisode {
		public final long id;
		public final String title;
		public final String link;
		public final String authorUsername;
		public final String authorName;
		public final String authorAvatar;
		public final String body;
		public final String date;
		public final String editDate;
		public final String editorUsername;
		public final String editorName;
		public final int childCount;
		public final int depth;
		public final Long parentId;
		public final long hits;
		public final List<JSONChildEpisode> children;
		public JSONEpisode(EpisodeWithChildren ep, boolean sendhtml) {
			this.id = ep.generatedId;
			this.title=ep.title;
			this.link=ep.link;
			this.authorUsername=ep.authorId;
			this.authorName=ep.authorName;
			this.authorAvatar=ep.authorAvatar;
			this.date=toUTC(ep.date);
			this.editDate=toUTC(ep.editDate);
			this.editorUsername=ep.editorId;
			this.editorName=ep.editorName;
			this.childCount=ep.childCount;
			this.depth=ep.depth;
			this.parentId=ep.parentId;
			this.hits=ep.hits;
			this.body=sendhtml?Story.formatBody(ep.body):ep.body;
			this.children = ep.children.stream().map(JSONChildEpisode::new).collect(Collectors.toList());
		}		
	}
	
	/**
	 * Represents a child episode, basic info and stats
	 */
	class JSONChildEpisode {
		public final long id;
		public final String title;
		public final String link;
		public final String date;
		public final int childCount;
		public final long hits;
		public final long views;
		public final long upvotes;
		public JSONChildEpisode(Episode ep) {
			this.id = ep.generatedId;
			this.title=ep.title;
			this.link=ep.link;
			this.date=toUTC(ep.date);
			this.childCount=ep.childCount;
			this.hits=ep.hits;
			this.views=ep.views;
			this.upvotes=ep.upvotes;
		}
	}
	
	/**
	 * Represents a simple episode. No children, body text, or stats
	 */
	class JSONSimpleEpisode {
		public final long id;
		public final String title;
		public final String link;
		public final String authorUsername;
		public final String authorName;
		public final String authorAvatar;
		public final String date;
		public final String editDate;
		public final String editorUsername;
		public final String editorName;
		public final int childCount;
		public final int depth;
		public final Long parentId;
		public final long hits;
		public JSONSimpleEpisode(FlatEpisode ep) {
			this.id = ep.generatedId;
			this.title=ep.title;
			this.link=ep.link;
			this.authorUsername=ep.authorId;
			this.authorName=ep.authorName;
			this.authorAvatar=ep.authorAvatar;
			this.date=toUTC(ep.date);
			this.editDate=toUTC(ep.editDate);
			this.editorUsername=ep.editorId;
			this.editorName=ep.editorName;
			this.childCount=ep.childCount;
			this.depth=ep.depth;
			this.parentId=ep.parentId;
			this.hits=ep.hits;
		}
	}
	
	class JSONError {
		public final String error;
		public JSONError(String error) {
			this.error = error;
		}
	}
	
	@GET
	@Path("getroots")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoots(@QueryParam("token") String token) {		
		
		List<FlatEpisode> roots = Story.getRootEpisodes();
		FlatUser user;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (FBLoginException e) {
			user = null;
		}
		HashMap<String,Object> ret = new HashMap<>();
		List<JSONSimpleEpisode> episodes = roots.stream().map(JSONSimpleEpisode::new).collect(Collectors.toList());
		ret.put("episodes",episodes);
		if (user != null) ret.put("user",new JSONUser(user));
		return Response.ok(g().toJson(ret)).build();
	}
	
	@GET
	@Path("getepisode/{generatedid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEpisode(@PathParam("generatedid") long generatedId, @QueryParam("token") String token, @QueryParam("sendhtml") Boolean sendHTML) {
		try {
			FlatUser user;
			try {
				user = Accounts.getFlatUserUsingTokenString(token);
			} catch (FBLoginException e) {
				user = null;
			}
			EpisodeWithChildren ep;
			try {
				ep = DB.getFullEp(generatedId, (user == null) ? null : user.author);
			} catch (DBException e1) {
				return Response.ok(g().toJson(new JSONError("Not found: " + generatedId))).build();
			}
			boolean sendhtml = (sendHTML != null) && (sendHTML == true);
			HashMap<String,Object> ret = new HashMap<>();
			ret.put("episode",new JSONEpisode(ep, sendhtml));
			if (user != null) ret.put("user",new JSONUser(user));
			return Response.ok(g().toJson(ret)).build();
		} catch (Exception e) {
			LOGGER.error("JSON getEpisode error", e);
			return Response.ok(g().toJson(new JSONError(e.getMessage()))).build();
		}
	}
	
	@GET
	@Path("recentepisodes")
	@Produces(MediaType.APPLICATION_JSON)
	public Response recentEpisodes(@QueryParam("token") String token, @QueryParam("before") Long before, @QueryParam("after") Long after, @QueryParam("reverse") String reverse) {
		
		
		FlatUser user;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (FBLoginException e) {
			user = null;
		}
		
		String order = "DESC";
		if (reverse != null && reverse.trim().toLowerCase().equals("true")) order = "ASC";
		
		String query = "SELECT * FROM fbepisodes ";
		if (before != null || after != null) query += "WHERE ";
		if (before != null) query += "date < to_timestamp(" + before + ") ";
		if (before != null && after != null) query += " AND ";
		if (after != null) query += "date > to_timestamp(" + after + ") ";
		query += "ORDER BY date " + order + " LIMIT 100";
		
		List<JSONSimpleEpisode> episodes;
		
		Session session = DB.openSession();
		try {
			
			episodes = session.createNativeQuery(query, DBEpisode.class).stream()
				.map(FlatEpisode::new)
				.map(JSONSimpleEpisode::new)
				.collect(Collectors.toList());
			
		} finally {
			DB.closeSession(session);
		}
		
		HashMap<String,Object> ret = new HashMap<>();
		ret.put("episodes",episodes);
		if (user != null) ret.put("user",new JSONUser(user));
		return Response.ok(g().toJson(ret)).build();
		
	}
}
