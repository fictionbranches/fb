package fb.api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fb.DB;
import fb.DB.DBException;
import fb.objects.FlatEpisode;
import fb.services.RssService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Component
@Path("fb")
public class RssStuff {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	@GET
	@Path("rss")
	@Produces("application/rss+xml")
	public Response getFeed() {
		String ret = RssService.feeds.get(0l);
		if (ret == null || ret.length() == 0) return Response.ok(RssService.emptyFeed()).build();
		return Response.ok(RssService.feeds.get(0l)).build();
	}
	
	@GET
	@Path("rss/{id}")
	@Produces("application/rss+xml")
	public Response getFeedStory(@PathParam("id") String id) {
		long story;
		try {
			story = Long.parseLong(id);
		} catch (NumberFormatException e) {
			return getFeed();
		}
		String ret = RssService.feeds.get(story);
		if (ret == null || ret.length() == 0) return Response.ok(RssService.emptyFeed()).build();
		return Response.ok(RssService.feeds.get(story)).build();
	}
	
	@GET
	@Path("feed")
	@Produces("application/rss+xml")
	public Response getFeedLegacy() {
		return getFeed();
	}
	
	@GET
	@Path("feed/{id}")
	public Response getFeedStoryLegacy(@PathParam("id") String id) {
		try {
			FlatEpisode ep = DB.getEpByOldMap(id);
			return getFeedStory(""+ep.generatedId);
		} catch (DBException e) {
			return Response.ok(RssService.emptyFeed()).build();
		}
	}
	
	@GET
	@Path("commentsfeed")
	@Produces("application/rss+xml")
	public Response getCommentsFeed() {
		return Response.ok(RssService.commentsFeed).build();
	}

	
}
