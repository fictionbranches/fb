package fb.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.db.DBAuthorSubscription;
import fb.db.DBUser;
import fb.objects.FlatUser;
import fb.util.Etherpad;
import fb.util.Etherpad.EtherpadException;
import fb.util.GoogleRECAPTCHA;
import fb.util.GoogleRECAPTCHA.GoogleCheckException;
import fb.util.StringUtils;
import fb.util.Strings;

@Path("fb")
public class AccountStuff {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
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
		
		LOGGER.info("Login attempt: " + email);
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
		
		final String checked = "checked";
		
		return Response.ok(Strings.getFile("useraccount.html", user)
				.replace("$COMMENT_SITE_CHECKED", user.commentSite?checked:"")
				.replace("$COMMENT_MAIL_CHECKED", user.commentMail?checked:"")
				.replace("$CHILD_SITE_CHECKED", user.childSite?checked:"")
				.replace("$CHILD_MAIL_CHECKED", user.childMail?checked:"")
				.replace("$AUTHORSUB_SITE_CHECKED", user.childSite?checked:"")
				.replace("$AUTHORSUB_MAIL_CHECKED", user.childMail?checked:"")
				.replace("$ID", user.id)).build();
	}
	
	@GET
	@Path("changeauthor")
	@Produces(MediaType.TEXT_HTML)
	public Response changeauthor(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user == null) return Response.ok("You must be logged in to do that").build();
		return Response.ok(Strings.getFile("changeauthorform.html", user).replace("$EXTRA", "")).build();
	}
	
	@POST
	@Path("changeauthorpost")
	@Produces(MediaType.TEXT_HTML)
	public Response changeauthorpost(@FormParam("author") String author, @CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();

		try {
			Accounts.changeAuthor(fbtoken, author);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();  //failed, try again
		}
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build(); //redirect on success
	}
	
	@GET
	@Path("changetheme")
	@Produces(MediaType.TEXT_HTML)
	public Response changetheme(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		
		if (user == null) return Response.ok("You must be logged in to do that").build();
		return Response.ok(Strings.getFile("changethemeform.html", user).replace("$EXTRA", "").replace("$THEMES", Strings.getSelectThemes())).build();
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
	
	@GET
	@Path("changebio")
	@Produces(MediaType.TEXT_HTML)
	public Response changebio(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		
		if (user == null) return Response.ok("You must be logged in to do that").build();

		String bio = user.bio;

		return Response.ok(Strings.getFile("changebioform.html", user).replace("$EXTRA", "").replace("$BODY", bio)).build();
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
	
	@GET
	@Path("changeavatar")
	@Produces(MediaType.TEXT_HTML)
	public Response changeavatar(@CookieParam("fbtoken") Cookie fbtoken) {
		if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		
		if (user == null) return Response.ok("You must be logged in to do that").build();

		return Response.ok(Strings.getFile("changeavatarform.html", user).replace("$EXTRA", "")).build();
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
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
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
	public Response notificationsettings(
			@CookieParam("fbtoken") Cookie fbtoken, 
			@FormParam("comment_site") String comment_site, 
			@FormParam("comment_mail") String comment_mail, 
			@FormParam("child_site") String child_site, 
			@FormParam("child_mail") String child_mail/*,
			@FormParam("child_site") String authorsub_site, 
			@FormParam("child_mail") String authorsub_mail*/
			) {
		
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
			
			boolean commentSite = false;
			boolean commentMail = false;
			boolean childSite = false;
			boolean childMail = false;
			/*boolean authorSubSite = false;
			boolean authorSubMail = false;*/
			
			if (comment_site != null && comment_site.length() > 0) commentSite = true;
			if (comment_mail != null && comment_mail.length() > 0) commentMail = true;
			if (child_site != null && child_site.length() > 0) childSite = true;
			if (child_mail != null && child_mail.length() > 0) childMail = true;
			/*if (authorsub_site != null && authorsub_site.length() > 0) authorSubSite = true;
			if (authorsub_mail != null && authorsub_mail.length() > 0) authorSubMail = true;*/
			
			DB.updateUserNotificationSettings(user.id, commentSite, commentMail, childSite, childMail/*, authorSubSite, authorSubMail*/);
		} catch (FBLoginException | DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build();
	}
	
	@GET
	@Path("subauthor/{username}")
	@Produces(MediaType.TEXT_HTML)
	public Response subauthor(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("username") String username) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		Session session = DB.openSession();
		try {
			
			DBUser subscriber = DB.getUserById(session, fu.id);
			DBUser author = DB.getUserById(session, username);
			if (author == null) return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "Not found: " + username)).build();
			if (author.getId().equals(subscriber.getId())) return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You may not subscribe to yourself")).build();

			if (session.createQuery("from DBAuthorSubscription s where s.author.id='"+author.getId()+"' and s.subscriber.id='"+subscriber.getId()+"'").list().isEmpty()) {
				try {
					DBAuthorSubscription as = new DBAuthorSubscription();
					as.setAuthor(author);
					as.setSubscriber(subscriber);
					session.beginTransaction();
					session.save(as);
					session.getTransaction().commit();
				} catch (Exception e) {
					return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "Database error")).build();
				}
			}
		} finally {
			DB.closeSession(session);
		}
		return Response.seeOther(GetStuff.createURI("/fb/user/" + username)).build();
	}
	
	@GET
	@Path("unsubauthor/{username}")
	@Produces(MediaType.TEXT_HTML)
	public Response unsubauthor(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("username") String username) {
		try {
			unsubauthorImpl(fbtoken, username);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		} catch (DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "Database error")).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/user/" + username)).build();
	}
	
	@GET
	@Path("unsubauthor2/{username}")
	@Produces(MediaType.TEXT_HTML)
	public Response unsubauthor2(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("username") String username) {
		try {
			unsubauthorImpl(fbtoken, username);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		} catch (DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "Database error")).build();
		}
		
		return Response.seeOther(GetStuff.createURI("/fb/manageauthorsubs")).build();
	}
	
	private void unsubauthorImpl(Cookie fbtoken, String username) throws FBLoginException, DBException {
		FlatUser fu = Accounts.getFlatUser(fbtoken); // throws FBLoginException
		Session session = DB.openSession();
		try {
			
			DBUser subscriber = DB.getUserById(session, fu.id);
			DBUser author = DB.getUserById(session, username);
			if (author == null) throw new FBLoginException("You must be logged in to do that");
			
			DBAuthorSubscription as = session.createQuery("from DBAuthorSubscription s where s.author.id='"+author.getId()+"' and s.subscriber.id='"+subscriber.getId()+"'", DBAuthorSubscription.class).uniqueResult();
			if (as != null) {
				try {
					session.beginTransaction();
					session.delete(as);
					session.getTransaction().commit();
				} catch (Exception e) {
					throw new DBException("Database error");
				}
			}
		} finally {
			DB.closeSession(session);
		}
	}
	
	@GET
	@Path("manageauthorsubs")
	@Produces(MediaType.TEXT_HTML)
	public Response manageauthorsubs(@CookieParam("fbtoken") Cookie fbtoken){
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		String html;
		
		Session session = DB.openSession();
		try {
			
			List<DBAuthorSubscription> list = session.createQuery("from DBAuthorSubscription s where s.subscriber.id='"+fu.id+"'", DBAuthorSubscription.class).list();
			StringBuilder sb = new StringBuilder("<h1>Manage author subscriptions</h1>\n");
			if (list.isEmpty()) sb.append("(nothing here)");
			else {
				final String f = "<p><a href=/fb/unsubauthor2/%s>Unsubscribe</a> from <a href=/fb/user/%s>%s</a></p>%n";
				sb.append(list.stream().map(as->
					String.format(f, as.getAuthor().getId(), as.getAuthor().getId(), StringUtils.escape(as.getAuthor().getAuthor()))
				).collect(Collectors.joining()));
			}
			html = sb.toString();
			
		} finally {
			DB.closeSession(session);
		}
		return Response.ok(Strings.getFile("generic.html", fu).replace("$EXTRA",html)).build();
	}
	
	@GET
	@Path("etherpad")
	@Produces(MediaType.TEXT_HTML)
	public Response etherpad(@CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		StringBuilder sb = new StringBuilder("<h1>Etherpad management</h1>\n");
		sb.append("<p><form action= \"/fb/createpad\" method=\"post\">\n" + 
				"Create new pad: <input type= \"text\" name= \"name\" size=\"100\" placeholder=\"pad name\"/> \n" + 
				"<input type= \"submit\" value= \"Create\"/></form>\n");
		List<String[]> pads;
		try {
			pads = DB.listPads(fu.id);
		} catch (DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		if (!pads.isEmpty()) {
			sb.append("<h2>Pads you own</h2>\n<p>\n");
			for (String[] pad : pads) {
				sb.append("<a href=/fb/launchpad/" + pad[0] + ">" + StringUtils.escape(pad[1]) + "</a><br/>\n");
			}
			sb.append("\n</p>\n");
		}
		return Response.ok(Strings.getFile("generic.html", fu).replace("$EXTRA", sb.toString())).build();
	}
	
	@POST
	@Path("createpad")
	@Produces(MediaType.TEXT_HTML)
	public Response createpad(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("name") String padName) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		if (padName == null || padName.length() == 0) return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", "Pad name cannot be empty")).build();
		String result[];
		try {
			result = DB.createPad(fu.id, padName);
		} catch (DBException | EtherpadException e) {
			return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", e.getMessage())).build();
		} 
		String padID = result[0];
		String sessionID = result[1];
		return Response.seeOther(GetStuff.createURI(Strings.getETHERPAD_DOMAIN(), "/fb/etherpadcookie/"+padID+"/"+sessionID)).build();
	}
	
	@GET
	@Path("launchpad/{padID}")
	@Produces(MediaType.TEXT_HTML)
	public Response launchpad(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("padID") String padID) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		if (fu.etherpadID == null) return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", "You are not authorized to do that")).build();
		
		try {
			String groupID = DB.userCanAccessPad(fu.id, Long.parseLong(padID));
			if (groupID == null) return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", "You are not authorized to do that")).build();
			String sessionID = Etherpad.createSession(groupID, fu.etherpadID);
			return Response.seeOther(GetStuff.createURI(Strings.getETHERPAD_DOMAIN(), "/fb/etherpadcookie/"+padID+"/"+sessionID)).build();
			
		} catch (DBException | EtherpadException e) {
			return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", e.getMessage())).build();
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", "Not found: " + padID)).build();
		}
	}
	
	@GET
	@Path("etherpad/{padID}")
	@Produces(MediaType.TEXT_HTML)
	public Response etherpad(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("padID") String padIDString) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		long padID; 
		try {
			padID = Long.parseLong(padIDString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", "Not found: " + StringUtils.escape(padIDString))).build();
		}
		
		String padName;
		String groupID;
		try {
			String[] padInfo = DB.padInfoFromId(padID);
			padName = padInfo[1];
			groupID = padInfo[0];
		} catch (DBException e) {
			return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", e.getMessage())).build();
		}
		
		String s = "<h1>"+StringUtils.escape(padName)+"</h1>\n<iframe src='https://"+Strings.getETHERPAD_DOMAIN()+"/p/"+groupID+"$"+padID+"' width=600 height=400></iframe>";
		return Response.ok(Strings.getFile("generic.html",fu).replace("$EXTRA", s)).build();
		
	}
	
	@GET
	@Path("etherpadcookie/{padID}/{sessionID}")
	@Produces(MediaType.TEXT_HTML)
	public Response fbetherpadcookie(@PathParam("padID") String padID, @PathParam("sessionID") String sessionID, @CookieParam("sessionID") Cookie sessionIDs) {
		HashSet<String> ids = new HashSet<>();
		if (sessionIDs != null && sessionIDs.getValue()!=null && sessionIDs.getValue().length()>0) {
			ids.addAll(Arrays.asList(sessionIDs.getValue().split(Pattern.quote(","))));
		}
		ids.add(sessionID);
		String cookie = ids.stream().collect(Collectors.joining(","));
		return Response.seeOther(GetStuff.createURI(Strings.getDOMAIN(),"/fb/etherpad/" + padID)).header("Set-Cookie", "sessionID="+cookie+"; Path=/; Secure").build();
	}
	
	@GET
	@Path("etherpadinvites/{username}")
	@Produces(MediaType.TEXT_HTML)
	public Response etherpadinvites(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("username") String username) {
		FlatUser fu;
		try {
			fu = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		
		
		StringBuilder sb = new StringBuilder("<h1>Invite to etherpad</h1>\n");
		sb.append("<p><form action= \"/fb/invitetopad\" method=\"post\">\n" + 
				"Create new pad: <input type= \"text\" name= \"name\" size=\"100\" placeholder=\"pad name\"/> \n" + 
				"<input type= \"submit\" value= \"Create\"/></form>\n");
		
		List<String[]> pads;
		try {
			pads = DB.listPads(fu.id);
		} catch (DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		if (!pads.isEmpty()) {
			sb.append("<h2>Pads you own</h2>\n<p>\n");
			for (String[] pad : pads) {
				sb.append("<a href=/fb/launchpad/" + pad[0] + ">" + StringUtils.escape(pad[1]) + "</a><br/>\n");
			}
			sb.append("\n</p>\n");
		}
		return Response.ok(Strings.getFile("generic.html", fu).replace("$EXTRA", sb.toString())).build();
	}
}
