package fb.api;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.Story;
import fb.objects.Episode;
import fb.objects.EpisodeWithChildren;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.util.BadLogger;

@Path("fbapi")
public class JSONStuff {

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
	
	@SuppressWarnings("squid:ClassVariableVisibilityCheck")
	class JSONToken {
		public String token;
	}
	
	@SuppressWarnings("squid:ClassVariableVisibilityCheck")
	class JSONGetEpisodeRequest {
		public String token;
		public Long id;
		public String sendhtml;
	}
	
	class JSONError {
		public final String error;
		public JSONError(String error) {
			this.error = error;
		}
	}
	
	@POST
	@Path("getroots")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoots(String json) {		
		
		JSONToken jtoken = g.get().fromJson(json, JSONToken.class);
		String token = null;
		if (jtoken != null && jtoken.token != null) token = jtoken.token;
		List<FlatEpisode> roots = Story.getRootEpisodes();
		FlatUser user;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (FBLoginException e) {
			user = null;
		}
		HashMap<String,Object> ret = new HashMap<>();
		ret.put("episodes",roots.stream().map(JSONSimpleEpisode::new).collect(Collectors.toList()));
		if (user != null) ret.put("user",new JSONUser(user));
		return Response.ok(g().toJson(ret)).build();
	}
	
	@POST
	@Path("getepisode")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEpisode(String json) {
		try {
			JSONGetEpisodeRequest jepreq = g.get().fromJson(json, JSONGetEpisodeRequest.class);
			String token = null;
			long generatedId;
			boolean sendhtml = false;
			if (jepreq != null) {
				if (jepreq.token != null) token = jepreq.token;
				if (jepreq.id != null) generatedId = jepreq.id;
				else return Response.ok(g().toJson(new JSONError("Invalid episode id"))).build();
				if (jepreq.sendhtml != null && jepreq.sendhtml.trim().equalsIgnoreCase("true")) {
					sendhtml = true;

				}
			} else return Response.ok(g().toJson(new JSONError("Invalid json"))).build();
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

			HashMap<String,Object> ret = new HashMap<>();
			ret.put("episode",new JSONEpisode(ep, sendhtml));
			if (user != null) ret.put("user",new JSONUser(user));
			return Response.ok(g().toJson(ret)).build();
		} catch (Exception e) {
			BadLogger.log(e);
			return Response.ok(g().toJson(new JSONError(e.getMessage()))).build();
		}
	}
}
