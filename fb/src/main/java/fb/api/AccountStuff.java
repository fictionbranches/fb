package fb.api;

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

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.InitWebsite;
import fb.Story;
import fb.objects.FlatUser;
import fb.util.GoogleRECAPTCHA;
import fb.util.GoogleRECAPTCHA.GoogleCheckException;
import fb.util.Strings;

@Path("fb")
public class AccountStuff {
	
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
		/*//if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		Strings.log("Someone's on the login page");
		if (Accounts.isLoggedIn(fbtoken)) return Response.ok("Already logged in").build();
		return Response.ok(Strings.getFile("loginform.html", null).replace("$EXTRA", "")).build();*/
	}
	
	@GET
	@Path("logout")
	@Produces(MediaType.TEXT_HTML)
	public Response logout(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("return") String returnPath) {
		//if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
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
		//if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		Strings.log("Login attempt: " + email);
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
		//if (InitWebsite.READ_ONLY_MODE) return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA", "This site is currently in read-only mode.")).build();
		
		Strings.log("Login2 attempt: " + email);
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
		
		Strings.log("Verifying: " + token);
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
		
		if (Accounts.isLoggedIn(fbtoken)) return Response.seeOther(GetStuff.createURI("/fb")).build();//ok("Already looged in").build();
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
		
		
		
		return Response.ok(Strings.getFile("useraccount.html", user)
				.replace("$COMMENT_SITE_CHECKED", user.commentSite?"checked":"")
				.replace("$COMMENT_MAIL_CHECKED", user.commentMail?"checked":"")
				.replace("$CHILD_SITE_CHECKED", user.childSite?"checked":"")
				.replace("$CHILD_MAIL_CHECKED", user.childMail?"checked":"")
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
		
		Strings.log("Verifying: " + token);
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
	public Response getusersearch(@CookieParam("fbtoken") Cookie fbtoken) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		return Response.ok(Accounts.getSearchForm(fbtoken)).build();
	}
	
	@POST
	@Path("usersearchpost")
	@Produces(MediaType.TEXT_HTML)
	public Response usersearchpost(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @FormParam("search") String search) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		return Response.ok(Accounts.searchPost(fbtoken, search, "1")).build();
	}
	
	@POST
	@Path("usersearchpost/{page}")
	@Produces(MediaType.TEXT_HTML)
	public Response usersearchpost(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id, @FormParam("search") String search, @PathParam("page") String page) {
		if (!InitWebsite.SEARCHING_ALLOWED) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Searching is disabled while the database is being indexed.")).build();
		return Response.ok(Accounts.searchPost(fbtoken, search, page)).build();
	}
	
	@GET
	@Path("notifications")
	@Produces(MediaType.TEXT_HTML)
	public Response notifications(@CookieParam("fbtoken") Cookie fbtoken, @QueryParam("all") String all) {
		//return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", Story.formatBody(Strings.getFile("announcements.md", null)))).build();
		boolean bAll = false;
		if (all != null) if (all.toLowerCase().equals("true")) bAll = true;
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
			
			if (comment_site != null) if (comment_site.length() > 0) commentSite = true;
			if (comment_mail != null) if (comment_mail.length() > 0) commentMail = true;
			if (child_site != null) if (child_site.length() > 0) childSite = true;
			if (child_mail != null) if (child_mail.length() > 0) childMail = true;
			
			DB.updateUserNotificationSettings(user.id, commentSite, commentMail, childSite, childMail);
		} catch (FBLoginException | DBException e) {
			return Response.ok(Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		return Response.seeOther(GetStuff.createURI("/fb/useraccount")).build();
	}
}
