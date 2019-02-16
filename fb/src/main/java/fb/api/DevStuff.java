package fb.api;

import java.io.ByteArrayOutputStream;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.objects.FlatUser;
import fb.util.Strings;

@Path("")
public class DevStuff {	
	
	private DevStuff() {}
	
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
			System.out.println("Become admin");
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
	public static Response getcss(@PathParam("file") String file) {
		return readTextResource("static_html/static/css/" + file);
	}
	
	@GET
	@Path("/static/{file}")
	public static Response getstatic(@PathParam("file") String file) {
		return readTextResource("static_html/static/" + file);
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
			System.out.println("Reading image resource: " + filename);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ImageIO.write(ImageIO.read(Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)), "png", baos);
		    byte[] imageData = baos.toByteArray();
		    return Response.ok(imageData).build();
		} catch (Exception e) {
			return notFound(filename);
		}
	}
	
	private static Response readTextResource(String filename) {
		try (Scanner in = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(filename))) {
			StringBuilder sb = new StringBuilder();
			while (in.hasNextLine()) sb.append(in.nextLine() + "\n");
			return Response.ok(sb.toString()).build();
		} catch (Exception e) {
			return notFound(filename);
		}
	}
}
