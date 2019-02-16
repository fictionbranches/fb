package fb.api;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fb.Accounts;
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
	@Path("getroots")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRoots(String json) {
		JSONToken jtoken = g.get().fromJson(json, JSONToken.class);
		String token = null;
		if (jtoken != null && jtoken.token != null) token = jtoken.token;
		List<FlatEpisode> roots = Story.getRootEpisodes();
		FlatUser user = null;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (Exception e) {
			BadLogger.log(e);
		}
		return Response.ok(g().toJson(new JSONEpisodeList(roots, user))).build();
	}
	@POST
	@Path("getepisode")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEpisode(String json) {
		try {
			System.out.println("jsongetepisode: " + json);
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
			FlatUser user = null;
			try {
				user = Accounts.getFlatUserUsingTokenString(token);
			} catch (Exception e) {
				BadLogger.log(e);
			}
			EpisodeWithChildren ep;
			try {
				ep = DB.getFullEp(generatedId, (user == null) ? null : user.author);
			} catch (DBException e1) {
				return Response.ok(g().toJson(new JSONError("Not found: " + generatedId))).build();
			}

			return Response.ok(g().toJson(new JSONGetEpisodeResponse(ep, user, sendhtml))).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.ok(g().toJson(new JSONError(e.getMessage()))).build();
		}
	}
	
	class JSONRecentsRequest {
		public final Integer story;
		public final Integer page;
		public final Boolean reverse;
		public JSONRecentsRequest(Integer story, Integer page, Boolean reverse) {
			this.story = story;
			this.page = page;
			this.reverse = reverse;
		}
	}
	
	class JSONError {
		public final String error;
		public JSONError(String error) {
			this.error = error;
		}
	}
	class JSONToken {
		public final String token;
		public JSONToken(String token) {
			this.token = token;
		}
	}
	class JSONGetEpisodeRequest {
		public final String token;
		public final Long id;
		public final String sendhtml;
		public JSONGetEpisodeRequest(String token, Long id, String sendhtml) {
			this.token = token;
			this.id = id;
			this.sendhtml = sendhtml;
		}
	}
	class JSONGetEpisodeResponse {
		public final JSONEpisode episode;
		public final JSONSimpleUser user;
		public JSONGetEpisodeResponse(EpisodeWithChildren ep, FlatUser user, boolean sendhtml) {
			this.episode = new JSONEpisode(ep, sendhtml);
			this.user = (user==null)?null:new JSONSimpleUser(user);
		}
	}
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
		public final long parentId;
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
	class JSONEpisodeList {
		public final List<JSONSimpleEpisode> episodes;
		public final JSONSimpleUser user;
		public JSONEpisodeList(List<FlatEpisode> eps, FlatUser user) {
			this.user = (user==null)?null:new JSONSimpleUser(user);
			episodes = eps.stream().map(JSONSimpleEpisode::new).collect(Collectors.toList());
		}
	}
	class JSONSimpleUser {
		public final String username;
		public final String author;
		public final String date;
		public final byte level;
		public final String theme; // HTML theme name
		public final String avatar;
		public JSONSimpleUser(FlatUser user) {
			this.username = user.id;
			this.author = user.author;
			this.date = toUTC(user.date);
			this.level = user.level;
			this.theme = user.theme.name;
			this.avatar = user.avatar;
		}
	}
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
		public final long parentId;
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
}
