package fb.api;

import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.springframework.stereotype.Component;

import fb.DB;
import fb.DB.DBException;
import fb.objects.FlatEpisode;
import fb.util.Strings;

@Component
@Path("fb")
public class LegacyStuff {
	
	//private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	@GET
	@Path("get/{oldmap}")
	@Produces(MediaType.TEXT_HTML)
	public Response oldmap(@PathParam("oldmap") String oldmap, @CookieParam("fbtoken") Cookie fbtoken) {
		FlatEpisode ep;
		try {
			ep = DB.getEpByOldMap(oldmap);
		} catch (DBException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Not found: " + oldmap)).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/story/" + ep.generatedId)).build();
	}
	
	/**
	 * Gets a legacy episode by its legacy id
	 * 
	 * @param id
	 *            id of episode (1-7-4-...-3)
	 * @return redirect to HTML episode
	 */
	@GET
	@Path("legacy/{legacyId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy(@PathParam("legacyId") String legacyId) {
		if (legacyId == null || (legacyId.trim().toLowerCase().compareTo("root") == 0)) return Response.seeOther(GetStuff.createURI("/fb")).build();
		FlatEpisode ep;
		try {
			ep = DB.getEpByLegacyId(legacyId);
		} catch (DBException e) {
			return Response.seeOther(GetStuff.createURI("/fb")).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/story/" + ep.generatedId)).build();
	}
	
	@GET
	@Path("legacy/the-forum/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy1(@PathParam("oldId") String oldId) {
		return legacy(oldId);
	}
	
	@GET 
	@Path("legacy/you-are-what-you-wish/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy2(@PathParam("oldId") String oldId) {
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/altered-fates/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy3(@PathParam("oldId") String oldId) {
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/the-future-of-gaming/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy4(@PathParam("oldId") String oldId) {
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/cgi-bin/fbstorypage.pl")
	@Produces(MediaType.TEXT_HTML)
	public Response legacy5(@QueryParam("page") String oldId) {
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/cgi-bin/fblatest.pl")
	@Produces(MediaType.TEXT_HTML)
	public Response legacyRecent() {
		return Response.seeOther(GetStuff.createURI("/fb/recent")).build();
	}
	
	@GET
	@Path("legacy/cgi-bin/fbindex.pl")
	@Produces(MediaType.TEXT_HTML)
	public Response legacyIndex() {
		return Response.seeOther(GetStuff.createURI("/fb")).build();
	}
	
	@GET
	@Path("legacy/{anything}/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacyCatchAll2(@PathParam("oldId") String oldId, @PathParam("anything") String anything, @QueryParam("page") String page) {
		if (page != null && page.trim().length()>0) return legacy(page);
		if (oldId.trim().toLowerCase().compareTo("root") == 0) return Response.seeOther(GetStuff.createURI("/fb")).build();
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/{anything}/{anything2}/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacyCatchAll(@PathParam("oldId") String oldId, @PathParam("anything") String anything, @PathParam("anything2") String anything2) {
		if (oldId.trim().toLowerCase().compareTo("root") == 0) return Response.seeOther(GetStuff.createURI("/fb")).build();
		return legacy(oldId);
	}
	
	@GET
	@Path("legacy/{anything}/{anything2}/{anything3}/{oldId}")
	@Produces(MediaType.TEXT_HTML)
	public Response legacyCatchAll(@PathParam("oldId") String oldId, @PathParam("anything") String anything, @PathParam("anything3") String anything3, @PathParam("anything2") String anything2) {
		if (oldId.trim().toLowerCase().compareTo("root") == 0) return Response.seeOther(GetStuff.createURI("/fb")).build();
		return legacy(oldId);
	}
	
}
