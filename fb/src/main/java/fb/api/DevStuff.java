package fb.api;

import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.objects.FlatUser;
import fb.util.Strings;
import fb.util.Text;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("")
public class DevStuff {	
	
	//private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
		
	@GET
	public static Response getRoot() {
		return Response.seeOther(GetStuff.createURI("/fb")).build();
	}
	
	@Path("fb")
	public static class DevStuff2 {
		@GET
		@Path("becomeadmin")
		@Produces(MediaType.TEXT_HTML)
		public Response becomeAdmin(@CookieParam("fbtoken") Cookie fbtoken) {
			return Response.ok(devModeBecomeAdmin(fbtoken)).build();
		}

		@GET
		@Path("becomemod")
		@Produces(MediaType.TEXT_HTML)
		public Response becomeMod(@CookieParam("fbtoken") Cookie fbtoken) {
			return Response.ok(devModeBecomeMod(fbtoken)).build();
		}

		@GET
		@Path("becomenormal")
		@Produces(MediaType.TEXT_HTML)
		public Response becomeNormal(@CookieParam("fbtoken") Cookie fbtoken) {
			return Response.ok(devModeBecomeNormal(fbtoken)).build();
		}
	}
	
	@GET
	@Path("/images/{file}")
	@Produces("image/png")
	public static Response getimage(@PathParam("file") String file) {
		return getPNGFromResource("static_html/images/" + file);
	}
	
	@GET
	@Path("/static/css/{file}")
	@Produces("text/css")
	public static Response getcss(@PathParam("file") String file) {
		return readTextResource("static_html/static/css/" + file);
	}
	
	@GET
	@Path("/static/{file}.js")
	@Produces("text/javascript")
	public static Response getstaticjs(@PathParam("file") String file) {
		return readTextResource("static_html/static/" + file + ".js");
	}
	
	@GET
	@Path("/static/{file}.html")
	@Produces(MediaType.TEXT_HTML)
	public static Response getstatichtml(@PathParam("file") String file) {
		return readTextResource("static_html/static/" + file + ".html");
	}
	
	@GET
	@Path("/static/{file}.css")
	@Produces("text/css")
	public static Response getstaticcss(@PathParam("file") String file) {
		return readTextResource("static_html/static/" + file + ".css");
	}
	
	@GET
	@Path("/static/{file}.txt")
	@Produces("text/plain")
	public static Response getstatictxt(@PathParam("file") String file) {
		return readTextResource("static_html/static/" + file + ".txt");
	}
	
	public static String devModeBecomeNormal(Cookie token) {
		try {
			FlatUser user = Accounts.getFlatUser(token);
			DB.changeUserLevel(user.id, (byte)1);
			return Strings.getFile("generic.html", user).replace("$EXTRA", "You are now just a regular user.");
		} catch (DBException | FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
	}
	
	public static String devModeBecomeMod(Cookie token) {
		try {
			FlatUser user = Accounts.getFlatUser(token);
			DB.changeUserLevel(user.id, (byte)10);
			return Strings.getFile("generic.html", user).replace("$EXTRA", "You are now a moderator.<br/>Please only use your power for testing, not abuse.");
		} catch (DBException | FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
	}
	
	public static String devModeBecomeAdmin(Cookie token) {
		try {
			FlatUser user = Accounts.getFlatUser(token);
			DB.changeUserLevel(user.id, (byte)100);
			return Strings.getFile("generic.html", user).replace("$EXTRA", "You are now an admin.<br/>Please only use your power for testing, not abuse.");
		} catch (DBException | FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
	}
	
	private static Response notFound(String file) {
		return Response.ok("404 Not found: " + file).status(404).build();
	}
	
	private static Response getPNGFromResource(String filename) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ImageIO.write(ImageIO.read(Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)), "png", baos);
		    byte[] imageData = baos.toByteArray();
		    return Response.ok(imageData).build();
		} catch (Exception e) {
			return notFound(filename);
		}
	}
	
	private static Response readTextResource(String filename) {
		return Response.ok(Text.readFileFromJar(filename)).build();
	}
	
}
