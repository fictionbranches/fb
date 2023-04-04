package fb.api;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.db.DBEpisode;
import fb.db.DBUser;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.util.GoogleRECAPTCHA;
import fb.util.GoogleRECAPTCHA.GoogleCheckException;
import fb.util.Strings;
import fb.util.Text;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("fb")
public class AccountStuff {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	@GET
	@Path("user/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response user(@PathParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {		
		return Response.ok(Accounts.getUserPage(id,fbtoken, 1)).build();
	}
	
	@GET
	@Path("user/{id}/{page}")
	@Produces(MediaType.TEXT_HTML)
	public Response user(@PathParam("id") String id, @PathParam("page") String page, @CookieParam("fbtoken") Cookie fbtoken) {	
		int pageInt;
		try {
			pageInt = Integer.parseInt(page);
		} catch (NumberFormatException e) {
			return Response.seeOther(GetStuff.createURI("/fb/user/" + id)).build();
		}
		return Response.ok(Accounts.getUserPage(id,fbtoken, pageInt)).build();
	}
	
	/**
	 * Returns the form for logging in
	 * 
	 * @return HTML form to log in
	 */
	@GET
	@Path("login")
	@Produces(MediaType.TEXT_HTML)
	public Response login(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.seeOther(GetStuff.createURI("/fb")).build();
	}
	
	@GET
	@Path("logout")
	@Produces(MediaType.TEXT_HTML)
	public Response logout(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("return") String returnPath) {		
		Accounts.logout(fbtoken);
		if (returnPath == null || returnPath.equals("")) return Response.seeOther(GetStuff.createURI("/fb")).build();
		else return Response.seeOther(GetStuff.createURI(returnPath)).build();
	}

	/**
	 * Logs in to the site. loginpost() is used by non-js browsers, loginpost2() is used by js browsers
	 * @param email email address
	 * @param password plaintext password
	 * @param google recaptcha 
	 * @return
	 */
	@POST
	@Path("loginpost")
	@Produces(MediaType.TEXT_HTML)
	public Response loginpost(@Context UriInfo uriInfo, @FormParam("email") String email, @FormParam("password") String password,
			@FormParam("g-recaptcha-response") String google) {
		LOGGER.info(password, google);
		LOGGER.info("Login attempt: %s", email);
		try {
			String token = Accounts.login(email, password);
			return Response.seeOther(GetStuff.createURI("/fb")).cookie(GetStuff.newCookie("fbtoken", token, uriInfo.getRequestUri().getHost())).build();
		} catch (FBLoginException e){
			return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", "Incorrect username/email or password, or username/email does not exist")).build();
		}
	}
	
	@POST
	@Path("loginpost2")
	@Produces(MediaType.TEXT_HTML)
	public Response loginpost2(@Context UriInfo uriInfo, @FormParam("email") String email, @FormParam("password") String password) {
		
		LOGGER.info("Login2 attempt: " + email);
		try {
			String token = Accounts.login(email, password);
			return Response.ok("loggedin").cookie(GetStuff.newCookie("fbtoken", token, uriInfo.getRequestUri().getHost())).build();
		} catch (FBLoginException e){
			return Response.ok("incorrect").build();
		}
	}
	
	/**
	 * Confirms that email address exists and is accessible by user
	 * @param token
	 * @return
	 */
	@GET
	@Path("confirmaccount/{token}")
	@Produces(MediaType.TEXT_HTML)
	public Response confirmaccount(@PathParam("token") String token) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		LOGGER.info("Verifying: " + token);
		return Response.ok(Accounts.verify(token)).build();
	}
	
	/**
	 * Returns form to create a new account
	 * @param fbtoken
	 * @return
	 */
	@GET
	@Path("createaccount")
	@Produces(MediaType.TEXT_HTML)
	public Response createaccount(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (Accounts.isLoggedIn(fbtoken)) return Response.seeOther(GetStuff.createURI("/fb")).build();
		return Response.ok(Strings.getFileWithToken("createaccountform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "")).build();
	}
	
	/**
	 * Stores account details in memory and email user with verification link
	 * @param email
	 * @param password
	 * @param password2
	 * @param author
	 * @param google
	 * @return
	 */
	@POST
	@Path("createaccountpost")
	@Produces(MediaType.TEXT_HTML)
	public Response createaccountpost(@Context UriInfo uriInfo, @FormParam("email") String email, @FormParam("username") String username, @FormParam("password") String password, @FormParam("password2") String password2, 
			@FormParam("author") String author, @FormParam("g-recaptcha-response") String google, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (InitWebsite.RECAPTCHA) {
			try {
				if (!GoogleRECAPTCHA.checkGoogle(google)) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "reCAPTCHA failed")).build();
			} catch (GoogleCheckException e) {
				return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();
			}
		}
		return Response.ok(Accounts.create(email, password, password2, author, username, uriInfo.getRequestUri().getHost())).build();
	}
	
	@GET
	@Path("useraccount")
	@Produces(MediaType.TEXT_HTML)
	public Response useraccount(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
				
		return Response.ok(Accounts.useraccountform(user, null)).build();
	}
	
	@POST
	@Path("changebodytextwidth")
	@Produces(MediaType.TEXT_HTML)
	public Response changebodytextwidth(@FormParam("bodytextwidth") int bodytextwidth, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Accounts.changeBodyTextWidth(fbtoken, bodytextwidth);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@POST
	@Path("changeauthorpost")
	@Produces(MediaType.TEXT_HTML)
	public Response changeauthorpost(@FormParam("author") String author, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Accounts.changeAuthor(fbtoken, author);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@POST
	@Path("changethemepost")
	@Produces(MediaType.TEXT_HTML)
	public Response changethemepost(@FormParam("theme") String theme, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		try {
			Accounts.changeTheme(fbtoken, theme);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@POST
	@Path("changebiopost")
	@Produces(MediaType.TEXT_HTML)
	public Response changebiopost(@FormParam("bio") String bio, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Accounts.changeBio(fbtoken, bio);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@POST
	@Path("changeavatarpost")
	@Produces(MediaType.TEXT_HTML)
	public Response changeavatarpost(@FormParam("avatar") String avatar, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		try {
			Accounts.changeAvatar(fbtoken, avatar);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@GET
	@Path("changepassword")
	@Produces(MediaType.TEXT_HTML)
	public Response changepassword(@CookieParam("fbtoken") Cookie fbtoken) {	
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		
		if (user==null) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be logged in to do that")).build();
		return Response.ok(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "")).build();
	}
	
	@POST
	@Path("changepasswordpost")
	@Produces(MediaType.TEXT_HTML)
	public Response changepasswordpost(@FormParam("newpass") String newpass, @FormParam("newpass2") String newpass2 ,@FormParam("password") String password, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (InitWebsite.RECAPTCHA) {
			try {
				if (!GoogleRECAPTCHA.checkGoogle(google)) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "reCAPTCHA failed")).build();
			} catch (GoogleCheckException e) {
				return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();
			}
		}
		try {
			Accounts.changePassword(fbtoken, newpass, newpass2, password);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build(); //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@GET
	@Path("changeemail")
	@Produces(MediaType.TEXT_HTML)
	public Response changeemail(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		
		if (user == null) return Response.ok("You must be logged in to do that").build();
		return Response.ok(Strings.getFile("changeemailform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "")).build();
	}
	
	@POST
	@Path("changeemailpost")
	@Produces(MediaType.TEXT_HTML)
	public Response changeemailpost(@Context UriInfo uriInfo, @FormParam("email") String email, @FormParam("password") String password, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (InitWebsite.RECAPTCHA) {
			try {
				if (!GoogleRECAPTCHA.checkGoogle(google)) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "reCAPTCHA failed")).build();
			} catch (GoogleCheckException e) {
				return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();
			}
		}
		return Response.ok(Accounts.changeEmail(fbtoken, email, password, uriInfo.getRequestUri().getHost())).build();
	}
	
	/**
	 * Confirms that email address exists and is accessible by user
	 * @param token
	 * @return
	 */
	@GET
	@Path("confirmemailchange/{token}")
	@Produces(MediaType.TEXT_HTML)
	public Response confirmemailchange(@PathParam("token") String token, @CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		LOGGER.info("Verifying: " + token);
		return Response.ok(Accounts.verifyNewEmail(token, fbtoken)).build();
	}
	
	@GET
	@Path("passwordreset")
	@Produces(MediaType.TEXT_HTML)
	public Response passwordreset(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		try {
			FlatUser user = Accounts.getFlatUser(fbtoken);
			return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA", "You are already logged in.")).build();
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("passwordresetform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "")).build();
		}
	}
	
	@POST
	@Path("passwordresetpost")
	@Produces(MediaType.TEXT_HTML)
	public Response passwordresetpost(@Context UriInfo uriInfo, @FormParam("g-recaptcha-response") String google, @FormParam("username") String username) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		if (InitWebsite.RECAPTCHA) {
			try {
				if (!GoogleRECAPTCHA.checkGoogle(google)) return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", "reCAPTCHA failed")).build();
			} catch (GoogleCheckException e) {
				return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", e.getMessage())).build();
			}
		}
		
		return Response.ok(Accounts.resetPassword(username, uriInfo.getRequestUri().getHost())).build();
	}
	
	@GET
	@Path("confirmpasswordreset/{token}")
	@Produces(MediaType.TEXT_HTML)
	public Response confirmpasswordreset(@PathParam("token") String token) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		return Response.ok(Accounts.confirmPasswordResetForm(token)).build();
	}
	
	@POST
	@Path("confirmpasswordresetpost/{token}")
	@Produces(MediaType.TEXT_HTML)
	public Response confirmpasswordresetpost(@PathParam("token") String token, @FormParam("passworda") String passworda, @FormParam("passwordb") String passwordb) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		return Response.ok(Accounts.confirmPasswordReset(token, passworda, passwordb)).build();
	}
	
	@GET
	@Path("usersearch")
	@Produces(MediaType.TEXT_HTML)
	public Response getusersearch(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("q") String q, @QueryParam("page") String pageString) {
		if (!InitWebsite.SEARCHING_ALLOWED) {
			String response = "Searching is disabled while the database is being indexed.";
			if (InitWebsite.INDEXER_MONITOR != null) {
				response += " " + InitWebsite.INDEXER_MONITOR.percent() + "% complete.";
			}
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", response)).build();
		}
		if (q==null || q.trim().length()==0) return Response.ok(Accounts.getSearchForm(fbtoken)).build();
		else {
			int page;
			try {
				page = Integer.parseInt(pageString);
			} catch (Exception e) {
				page = 1;
			}
			return Response.ok(Accounts.searchPost(fbtoken, q, page)).build();
		}	
	}
	
	@GET
	@Path("notifications")
	@Produces(MediaType.TEXT_HTML)
	public Response notifications(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("all") String all) {
		boolean bAll = false;
		if (all != null && all.equalsIgnoreCase("true")) bAll = true;
		return Response.ok(Accounts.getNotifications(fbtoken, bAll)).build();
	}

	@POST
	@Path("notificationsettings")
	@Produces(MediaType.TEXT_HTML)
	public Response notificationsettings(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("comment_site") String comment_site, @FormParam("comment_mail") String comment_mail, @FormParam("child_site") String child_site, @FormParam("child_mail") String child_mail) {
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
			
			boolean commentSite = false;
			boolean commentMail = false;
			boolean childSite = false;
			boolean childMail = false;
			
			if (comment_site != null && comment_site.length() > 0) commentSite = true;
			if (comment_mail != null && comment_mail.length() > 0) commentMail = true;
			if (child_site != null && child_site.length() > 0) childSite = true;
			if (child_mail != null && child_mail.length() > 0) childMail = true;
			
			DB.updateUserNotificationSettings(user.id, commentSite, commentMail, childSite, childMail);
		} catch (FBLoginException | DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build();
	}
	
	@GET
	@Path("favorites")
	@Produces(MediaType.TEXT_HTML)
	public Response getFavoriteEpisodes(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("delete") String delete, @QueryParam("sort") String sort) {
		
		if (delete != null && delete.length() > 0) {
			
			final Response redirectHere = Response.seeOther(GetStuff.createURI("/fb/favorites")).build();
			int epToDelete;
			try {
				epToDelete = Integer.parseInt(delete);
			} catch (Exception e) {
				return redirectHere;
			}
			
			FlatUser user;
			try {
				user = Accounts.getFlatUser(fbtoken);
			} catch (FBLoginException e) {
				return redirectHere;
			}
			
			try {
				DB.unfavoriteEp(epToDelete, user.id);
			} catch (DBException e) {}
			
			return redirectHere;
		}
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic_meta.html", null)
					.replace("$TITLE", "Favorite episodes")
					.replace("$OGDESCRIPTION", "Your own personal list of favorite episodes")
					.replace("$EXTRA","You must be logged in to do that")).build();
		}
		
		ArrayList<FlatEpisode> eps;
		
		String query = "SELECT fbepisodes.* FROM fbepisodes,fbfaveps WHERE generatedid=episode_generatedid AND fbfaveps.user_id='"+user.id+"' ORDER BY $ORDERBY LIMIT 100";
		
		// fbfaveps.date ASC
		if (sort == null) sort = "";
		switch (sort) {
		case "newest":
			query = query.replace("$ORDERBY", "fbepisodes.date DESC");
			break;
		case "oldest":
			query = query.replace("$ORDERBY", "fbepisodes.date ASC");
			break;
		case "oldfav":
			query = query.replace("$ORDERBY", "fbfaveps.date ASC");
			break;
		case "default", "newfav":
		default:
			query = query.replace("$ORDERBY", "fbfaveps.date DESC");
			break;
		}
		
		Session session = DB.openSession();
		try {
			eps = session.createNativeQuery(
					query,
					DBEpisode.class)
					.stream()
					.map(FlatEpisode::new)
					.collect(Collectors.toCollection(ArrayList::new));
		} finally {
			DB.closeSession(session);
		}
		

		
		StringBuilder html = new StringBuilder();
		html.append(
				"<h1>Favorite episodes</h1>\n"
				+ "<h4>You can add a favorite episode from that episode's page</h4>\n"
				+ Strings.getString("favorites_selector") + "\n");
		if (eps.isEmpty()) {
			html.append("<p>You do not have any favorite episodes (yet).</p>\n");
		} else {
			for (var ep : eps) {
				html.append("<p><a href=/fb/story/" + ep.generatedId + ">"+Text.escape(ep.link)+"</a>");
				if (!ep.title.toLowerCase().trim().equals(ep.link.toLowerCase().trim())) {
					html.append(" ("+Text.escape(ep.title)+") ");
				}
				html.append("&nbsp;-&nbsp;<a href=?delete=" + ep.generatedId + ">Remove</a></p>");
			}
		}
		
		
		return Response.ok(Strings.getFile("favoritespage.html", user)
				.replace("$TITLE", "Favorite episodes")
				.replace("$OGDESCRIPTION", "Your own personal list of favorite episodes")
				.replace("$EXTRA", html.toString())
				).build();
	}
	
	@GET
	@Path("togglehideimages")
	@Produces(MediaType.TEXT_HTML)
	public Response togglehideimages(@CookieParam("fbtoken") Cookie fbtoken) {		
		Session sesh = DB.openSession();
		try {
			final String username = Accounts.getUsernameFromCookie(fbtoken);
			DBUser user = sesh.get(DBUser.class, username);
			if (user == null) {
				return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
			}
			
			try {
				sesh.beginTransaction();
				user.setHideImages(!user.isHideImages());
				sesh.merge(user);
				sesh.getTransaction().commit();
			} catch (Exception e) {
				LOGGER.error(e.toString());
				LOGGER.error(e.getMessage());
				return Response.ok(Strings.getFile("generic.html", new FlatUser(user)).replace("$EXTRA","You must be logged in to do that")).build();
			}
			
			return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build();
			
		} finally {
			DB.closeSession(sesh);
		}
		
	}

	
}
