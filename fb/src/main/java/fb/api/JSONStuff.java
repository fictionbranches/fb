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

@Path("fbapi")
public class JSONStuff {

	private final ThreadLocal<Gson> g = new ThreadLocal<Gson>() {
		protected Gson initialValue() {
			return new GsonBuilder().setPrettyPrinting().create();
		}
	};
	public Gson g() {
		return g.get();
	}
	private final ThreadLocal<DateFormat> jdf = new ThreadLocal<DateFormat>() {
		protected DateFormat initialValue() {
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df;
		}
	};
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
		if (jtoken != null) if (jtoken.token != null) token = jtoken.token;
		List<FlatEpisode> roots = Story.getRoots();
		FlatUser user = null;
		try { user = Accounts.getFlatUserUsingTokenString(token); } catch (Exception e) {}
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
			String id;
			boolean sendhtml = false;
			if (jepreq != null) {
				if (jepreq.token != null) token = jepreq.token;
				if (jepreq.id != null) id = jepreq.id;
				else return Response.ok(g().toJson(new JSONError("Invalid episode id"))).build();
				if (jepreq.sendhtml != null) {
					if (jepreq.sendhtml.trim().toLowerCase().equals("true")) {
						sendhtml = true;
					}
				}
			} else return Response.ok(g().toJson(new JSONError("Invalid json"))).build();
			FlatUser user = null;
			try {
				user = Accounts.getFlatUserUsingTokenString(token);
			} catch (Exception e) {
			}
			EpisodeWithChildren ep;
			try {
				ep = DB.getFullEp(id, (user == null) ? null : user.author);
			} catch (DBException e1) {
				return Response.ok(g().toJson(new JSONError("Not found: " + id))).build();
			}

			return Response.ok(g().toJson(new JSONGetEpisodeResponse(ep, user, sendhtml))).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.ok(g().toJson(new JSONError(e.getMessage()))).build();
		}
	}
	
	class JSONError {
		public String error;
		public JSONError(String error) {
			this.error = error;
		}
	}
	class JSONToken {
		public String token;
	}
	class JSONGetEpisodeRequest {
		public String token;
		public String id;
		public String sendhtml;
	}
	class JSONGetEpisodeResponse {
		public JSONEpisode episode;
		public JSONSimpleUser user;
		public JSONGetEpisodeResponse(EpisodeWithChildren ep, FlatUser user, boolean sendhtml) {
			this.episode = new JSONEpisode(ep, sendhtml);
			this.user = (user==null)?null:new JSONSimpleUser(user);
		}
	}
	class JSONEpisode {
		public String id;
		public String title;
		public String link;
		public String authorUsername;
		public String authorName;
		public String authorAvatar;
		public String body;
		public String date;
		public String editDate;
		public String editorUsername;
		public String editorName;
		public int childCount;
		public int depth;
		public String parentId;
		public long hits;
		public List<JSONChildEpisode> children;
		public JSONEpisode(EpisodeWithChildren ep, boolean sendhtml) {
			this.id = ep.id;
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
			this.children = ep.children.stream().map(child -> new JSONChildEpisode(child)).collect(Collectors.toList());
		}
	}
	class JSONEpisodeList {
		public List<JSONSimpleEpisode> episodes;
		public JSONSimpleUser user;
		public JSONEpisodeList(List<FlatEpisode> eps, FlatUser user) {
			this.user = (user==null)?null:new JSONSimpleUser(user);
			episodes = eps.stream().map(ep -> new JSONSimpleEpisode(ep)).collect(Collectors.toList());
		}
	}
	class JSONSimpleUser {
		public String username;
		public String author;
		public String date;
		public byte level;
		public String theme; // HTML theme name
		public String avatar;
		public JSONSimpleUser(FlatUser user) {
			this.username = user.id;
			this.author = user.author;
			this.date = toUTC(user.date);
			this.level = user.level;
			this.theme = user.theme;
			this.avatar = user.avatar;
		}
	}
	class JSONChildEpisode {
		public String id;
		public String title;
		public String link;
		public String date;
		public int childCount;
		public long hits;
		public long views;
		public long upvotes;
		public JSONChildEpisode(Episode ep) {
			this.id = ep.id;
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
		public String id;
		public String title;
		public String link;
		public String authorUsername;
		public String authorName;
		public String authorAvatar;
		public String date;
		public String editDate;
		public String editorUsername;
		public String editorName;
		public int childCount;
		public int depth;
		public String parentId;
		public long hits;
		public JSONSimpleEpisode(FlatEpisode ep) {
			this.id = ep.id;
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
