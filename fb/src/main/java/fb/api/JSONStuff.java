package fb.api;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
import fb.objects.FlatEpisodeWithTags;
import fb.objects.FlatUser;
import fb.objects.Tag;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
@Component
@Path("fbapi")
public class JSONStuff {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

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
		LOGGER.info("markdownToHTML API request: " + body.length());
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
		public final long[] path;
		public final long hits;
		public final List<JSONChildEpisode> children;
		public final List<String> tags;
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
			this.path = DB.newMapToIdList(ep.newMap).mapToLong(x->x).toArray();
			this.hits=ep.hits;
			this.body=sendhtml?Story.formatBody(ep.body):ep.body;
			this.children = ep.children.stream().map(JSONChildEpisode::new).toList();
			this.tags = ep.tags.stream().map(tag -> tag.shortName).sorted().toList();
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
		public final List<String> tags;
		public JSONChildEpisode(Episode ep) {
			this.id = ep.generatedId;
			this.title=ep.title;
			this.link=ep.link;
			this.date=toUTC(ep.date);
			this.childCount=ep.childCount;
			this.hits=ep.hits;
			this.views=ep.views;
			this.upvotes=ep.upvotes;
			this.tags = ep.tags.stream().map(tag -> tag.shortName).sorted().toList();
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
		public final long rootId;
		public final long hits;
		public final List<String> tags;
		public JSONSimpleEpisode(FlatEpisodeWithTags ep) {
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
			this.rootId = DB.newMapToIdList(ep.newMap).findFirst().get();
			this.hits=ep.hits;
			this.tags = ep.tags.stream().map(tag -> tag.shortName).sorted().toList();
		}
	}
	
	class JSONTag {
		public final String shortName, longName, description;

		public JSONTag(Tag tag) {
			this.shortName = tag.shortName;
			this.longName = tag.longName;
			this.description = tag.description;
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
		
		List<FlatEpisodeWithTags> roots;
		FlatUser user;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (FBLoginException e) {
			user = null;
		}
		
		Session sesh = DB.openSession();
		try {
			
			CriteriaBuilder cb = sesh.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);			
			root.fetch("lazytags", JoinType.LEFT);
			query.select(root).where(cb.isNull(root.get("parent"))).orderBy(cb.asc(root.get("date")));
			roots = sesh.createQuery(query).stream()
				.map(FlatEpisodeWithTags::new)
				.toList();
			
		} finally {
			DB.closeSession(sesh);
		}
		
		HashMap<String,Object> ret = new HashMap<>();
		List<JSONSimpleEpisode> episodes = roots.stream().map(JSONSimpleEpisode::new).toList();
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
				
		List<JSONSimpleEpisode> episodes;
		
		Session session = DB.openSession();
		try {
			String order = "DESC";
			if (reverse != null && reverse.trim().equalsIgnoreCase("true")) order = "ASC";
			
			String query = "SELECT generatedid FROM fbepisodes ";
			if (before != null || after != null) query += "WHERE ";
			if (before != null) query += "date < to_timestamp(" + before + ") ";
			if (before != null && after != null) query += " AND ";
			if (after != null) query += "date > to_timestamp(" + after + ") ";		
				
			@SuppressWarnings("unchecked")
			List<Long> ids = ((Stream<Object>)session.createNativeQuery(query).stream()).map(x -> ((BigInteger)x).longValue()).toList();			
			episodes = session.createQuery(
					"FROM DBEpisode ep JOIN FETCH ep.lazytags tags WHERE ep.generatedId IN :ids ORDER BY ep.date " + order, DBEpisode.class)
					.setParameter("ids", ids)
					.stream()
					.map(FlatEpisodeWithTags::new)
					.map(JSONSimpleEpisode::new)
					.toList();			
		} finally {
			DB.closeSession(session);
		}
		
		HashMap<String,Object> ret = new HashMap<>();
		ret.put("episodes",episodes);
		if (user != null) ret.put("user",new JSONUser(user));
		return Response.ok(g().toJson(ret)).build();
		
	}
	
	@GET
	@Path("gettags")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTags(@QueryParam("token") String token) {		
		FlatUser user;
		try { 
			user = Accounts.getFlatUserUsingTokenString(token); 
		} catch (FBLoginException e) {
			user = null;
		}
		
		Map<String, JSONTag> map = DB.getAllTags()
				.stream()
				.sorted(Comparator.comparing(tag -> tag.shortName))
				.collect(Collectors.toMap(tag -> tag.shortName, tag -> new JSONTag(tag)));
				
		HashMap<String,Object> ret = new HashMap<>();
		ret.put("tags",map);
		if (user != null) ret.put("user",new JSONUser(user));
		return Response.ok(g().toJson(ret)).build();
	}
}
