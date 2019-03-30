package fb.api;

import java.net.URI;
import java.util.stream.Collectors;

import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.Session;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.DB.DBException;
import fb.Story;
import fb.Story.EpisodeException;
import fb.db.DBSiteSetting;
import fb.objects.FlatUser;
import fb.util.Dates;
import fb.util.Strings;

@Path("fb")
public class AdminStuff {
	
	//private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	@GET
	@Path("admin")
	@Produces(MediaType.TEXT_HTML)
	public Response admin(@CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser user; 
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		
		String oldDonateButton = "";
		 
		Session session = DB.openSession();
		try {
			DBSiteSetting button = session.get(DBSiteSetting.class, "donate_button");
			if (button != null) oldDonateButton = Strings.escape(button.getValue());
		} finally {
			DB.closeSession(session);
		}
		
		return Response.ok(Strings.getFile("adminform.html", user)
				.replace("$OLDDONATEBUTTON", oldDonateButton)
				.replace("$EXTRA", " ")).build(); 
	}
	
	@GET
	@Path("admin/sitesettings")
	@Produces(MediaType.TEXT_HTML)
	public Response sitesettings(@CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		return Response.ok(Strings.getFile("sitesettingsform.html", user).replace("$EXTRA", " ")).build(); 
	}
	
	@GET
	@Path("admin/archivetokens")
	@Produces(MediaType.TEXT_HTML)
	public Response archiveapitokens(@CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		
		StringBuilder sb = new StringBuilder();
		sb.append("<h1>Archive API Tokens</h1>\n");
		sb.append("<p><form action=/fb/admin/createarchivetoken method=\"post\">Comment: <input type=\"text\" name=\"comment\" size=100 placeholder=\"Comment\" /> ");
		sb.append("<input type=\"submit\" value=\"Create new token\" /></form></p>\n");
		sb.append("<p><table><thead><tr><th>Token</th><th>Comment</th><th>Date</th><th></th></tr></thead><tbody>\n");
		sb.append(DB.getArchiveTokens().stream()
			.map(at->"<tr><td>" + at.token + "</td><td>" + at.comment + "</td><td>" + Dates.simpleDateFormat(at.date) + "</td><td><a href=/fb/admin/deletearchivetoken/" + at.id + ">Delete</a></td></tr>")
			.collect(Collectors.joining("\n")));
		sb.append("</tbody></table></p>\n");
		
		return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA", sb.toString())).build();
	}
	
	@POST
	@Path("admin/createarchivetoken")
	@Produces(MediaType.TEXT_HTML)
	public Response createarchivetoken(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("comment") String comment) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		
		DB.createArchiveToken(comment);
		
		return Response.seeOther(GetStuff.createURI("/fb/admin/archivetokens")).build();
	}
	
	@POST
	@Path("admin/emailsettings")
	@Produces(MediaType.TEXT_HTML)
	public Response createarchivetoken(@CookieParam("fbtoken") Cookie fbtoken, 
			@FormParam("smtp_server") String smtp_server, 
			@FormParam("smtp_email") String smtp_email,
			@FormParam("smtp_password1") String smtp_password1, 
			@FormParam("smtp_password2") String smtp_password2) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		
		if (smtp_password1.compareTo(smtp_password2) != 0) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","Passwords don't match")).build();
		
		Session session = DB.openSession();
		try {
			DBSiteSetting smtpEmail = new DBSiteSetting();
			smtpEmail.setKey("smtp_email");
			smtpEmail.setValue(smtp_email);
			
			DBSiteSetting smtpServer = new DBSiteSetting();
			smtpServer.setKey("smtp_server");
			smtpServer.setValue(smtp_server);
			
			DBSiteSetting smtpPassword = new DBSiteSetting();
			smtpPassword.setKey("smtp_password");
			smtpPassword.setValue(smtp_password1);
			
			try {
				session.beginTransaction();
				session.merge(smtpEmail);
				session.merge(smtpServer);
				session.merge(smtpPassword);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","Database error: " + e.getMessage())).build();
			}
		} finally {
			DB.closeSession(session);
		}
		Strings.refreshSiteSettings();
		
		return Response.seeOther(GetStuff.createURI("/fb/admin/sitesettings")).build();
	}
	
	@POST
	@Path("admin/recaptchasettings")
	@Produces(MediaType.TEXT_HTML)
	public Response recaptchasettings(@CookieParam("fbtoken") Cookie fbtoken, 
			@FormParam("recaptcha_sitekey") String recaptcha_sitekey, 
			@FormParam("recaptcha_secret") String recaptcha_secret) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
				
		Session session = DB.openSession();
		try {
			DBSiteSetting rsitekey = new DBSiteSetting();
			rsitekey.setKey("recaptcha_sitekey");
			rsitekey.setValue(recaptcha_sitekey);
			
			DBSiteSetting rsecret = new DBSiteSetting();
			rsecret.setKey("recaptcha_secret");
			rsecret.setValue(recaptcha_secret);
			
			try {
				session.beginTransaction();
				session.merge(rsitekey);
				session.merge(rsecret);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","Database error: " + e.getMessage())).build();
			}
		} finally {
			DB.closeSession(session);
		}
		Strings.refreshSiteSettings();
		
		return Response.seeOther(GetStuff.createURI("/fb/admin/sitesettings")).build();
	}
	
	@POST
	@Path("admin/domainsettings")
	@Produces(MediaType.TEXT_HTML)
	public Response domainsettings(@CookieParam("fbtoken") Cookie fbtoken, 
			@FormParam("domain_name") String domain_name) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
				
		Session session = DB.openSession();
		try {
			DBSiteSetting rdomain = new DBSiteSetting();
			rdomain.setKey("domain_name");
			rdomain.setValue(domain_name);
			
			try {
				session.beginTransaction();
				session.merge(rdomain);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","Database error: " + e.getMessage())).build();
			}
		} finally {
			DB.closeSession(session);
		}
		Strings.refreshSiteSettings();
		
		return Response.seeOther(GetStuff.createURI("/fb/admin/sitesettings")).build();
	}
	
	@GET
	@Path("admin/deletearchivetoken/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response deleteapitoken(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA","You must be logged in to do that")).build();
		}
		if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA","You must be an admin to do that")).build();
		
		try {
			DB.deleteArchiveToken(Long.parseLong(id));
		} catch (DBException | NumberFormatException e) {
			return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA", e.getMessage())).build();
		} 
		
		return Response.seeOther(GetStuff.createURI("/fb/admin/archivetokens")).build();
	}
	
	
	@GET
	@Path("commentflagqueue")
	@Produces(MediaType.TEXT_HTML)
	public Response commentflagqueue(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.getCommentFlagQueue(fbtoken)).build();
	}
	
	@GET
	@Path("getflaggedcomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response getflaggedcomment(@PathParam("id") long id, @CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.getCommentFlag(id, fbtoken)).build();
	}
	
	@GET
	@Path("clearflaggedcomment/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response clearflaggedcomment(@PathParam("id") long id, @CookieParam("fbtoken") Cookie fbtoken) {
		try {
			Accounts.clearFlaggedComment(id, fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/commentflagqueue")).build();
	}
	
	@GET
	@Path("modqueue")
	@Produces(MediaType.TEXT_HTML)
	public Response modqueue(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.getModQueue(fbtoken)).build();
	}
	
	@GET
	@Path("getmod/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response getmod(@PathParam("id") String idString, @CookieParam("fbtoken") Cookie fbtoken, @QueryParam("diff") String diff) {
		long id;
		try {
			id = Long.parseLong(idString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Modification not found: " + idString)).build();
		}
		return Response.ok(Accounts.getMod(id, fbtoken, diff!=null)).build();
	}
	
	@GET
	@Path("acceptmod/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response acceptmod(@PathParam("id") String idString, @CookieParam("fbtoken") Cookie fbtoken) {
		long id;
		try {
			id = Long.parseLong(idString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Mod not found: " + idString)).build();
		}
		try {
			Accounts.clearMod(id, fbtoken, true);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/modqueue")).build();
	}
	
	@GET
	@Path("rejectmod/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response rejectmod(@PathParam("id") String idString, @CookieParam("fbtoken") Cookie fbtoken) {
		long id;
		try {
			id = Long.parseLong(idString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Mod not found: " + idString)).build();
		}
		try {
			Accounts.clearMod(id, fbtoken, false);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/modqueue")).build();
	}
	
	
	@GET
	@Path("flagqueue")
	@Produces(MediaType.TEXT_HTML)
	public Response flagqueue(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.getFlagQueue(fbtoken)).build();
	}
	
	@GET
	@Path("getflag/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response getflag(@PathParam("id") String idString, @CookieParam("fbtoken") Cookie fbtoken) {
		long id;
		try {
			id = Long.parseLong(idString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Flag not found: " + idString)).build();
		}
		return Response.ok(Accounts.getFlag(id, fbtoken)).build();
	}
	
	@GET
	@Path("clearflag/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response clearflag(@PathParam("id") String idString, @CookieParam("fbtoken") Cookie fbtoken) {
		long id;
		try {
			id = Long.parseLong(idString);
		} catch (NumberFormatException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Flag not found: " + idString)).build();
		}
		try {
			Accounts.clearFlag(id, fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(e.getMessage()).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/flagqueue")).build();
	}
	
	@POST
	@Path("admin/makemod")
	@Produces(MediaType.TEXT_HTML)
	public Response makemod(@FormParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.changeLevel(id, (byte)10, fbtoken)).build(); //failed, try again
	}
	
	@POST
	@Path("admin/makeadmin")
	@Produces(MediaType.TEXT_HTML)
	public Response makeadmin(@FormParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.changeLevel(id, (byte)100, fbtoken)).build(); //failed, try again
	}
	
	@POST
	@Path("admin/makenormal")
	@Produces(MediaType.TEXT_HTML)
	public Response makenormal(@FormParam("id") String id, @CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Accounts.changeLevel(id, (byte)1, fbtoken)).build(); //failed, try again
	}
	
	@GET
	@Path("admin/newroot")
	@Produces(MediaType.TEXT_HTML)
	public Response newroot(@CookieParam("fbtoken") Cookie fbtoken) {
		return Response.ok(Story.newRootForm(fbtoken)).build();
	}
	
	@POST
	@Path("admin/newrootpost")
	@Produces(MediaType.TEXT_HTML)
	public Response addpost(@FormParam("link") String link,
			@FormParam("title") String title, @FormParam("body") String body, 
			@CookieParam("fbtoken") Cookie fbtoken, @FormParam("g-recaptcha-response") String google) {
		try {
			long childGeneratedId = Story.newRootPost(link, title, body, fbtoken);
			return Response.seeOther(URI.create("/fb/story/" + childGeneratedId)).build();
		} catch (EpisodeException | FBLoginException e) {
			return Response.ok(e.getMessage()).build();
		}
	}
	
	@POST
	@Path("admin/mergeaccounts")
	@Produces(MediaType.TEXT_HTML)
	public Response mergeaccounts(@FormParam("usera") String usera, @FormParam("userb") String userb, @CookieParam("fbtoken") Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
			if (user.level<100) return Response.ok(Strings.getFile("generic.html", user).replace("$EXTRA", "You must be admin to do that")).build();
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that")).build();
		}
		
		try {
			DB.mergeAccounts(usera, userb);
		} catch (DBException e) {
			return Response.ok(Strings.getFile("adminform.html", user).replace("$EXTRA", e.getMessage())).build();
		}
		return Response.ok(Strings.getFile("adminform.html", user).replace("$EXTRA", "Accounts merged successfully")).build();
	}
	
	@GET
	@Path("admin/deleteannouncement/{id}")
	@Produces(MediaType.TEXT_HTML)
	public Response deleteAnnouncement(@CookieParam("fbtoken") Cookie fbtoken, @PathParam("id") String id) {
		try {
			DB.deleteAnnouncement(Accounts.getFlatUser(fbtoken).id, Long.parseLong(id));
			return Response.seeOther(GetStuff.createURI("/fb/announcements")).build();
		} catch (DBException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();			
		} catch (Exception e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "You are not authorized to do that")).build();			
		}
	}
	
	@POST
	@Path("admin/createannouncementpost")
	@Produces(MediaType.TEXT_HTML)
	public Response createAnnouncementPost(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("body") String body) {
		try {
			DB.addAnnouncement(Accounts.getFlatUser(fbtoken).id, body);
			return Response.seeOther(GetStuff.createURI("/fb/announcements")).build();
		} catch (DBException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();			
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "You are not authorized to do that")).build();			
		}
	}
	
	@POST
	@Path("admin/changedonatebutton")
	@Produces(MediaType.TEXT_HTML)
	public Response changeDonateButtonPost(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("body") String body) {
		try {
			DB.changeDonateButton(Accounts.getFlatUser(fbtoken).id, body);
			Strings.refreshSiteSettings();
			return Response.seeOther(GetStuff.createURI("/fb/admin")).build();
		} catch (DBException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();			
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "You are not authorized to do that")).build();			
		}
	}
	
	@POST
	@Path("admin/movetoroot") 
	@Produces(MediaType.TEXT_HTML) 
	public Response moveToRootPost(@CookieParam("fbtoken") Cookie fbtoken, @FormParam("epid") String epid) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "You are not authorized to do that")).build();
		}
		long generatedId;
		try {
			generatedId = Long.parseLong(epid);
		} catch (Exception e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "Not found: " + epid)).build();
		}
		if (user.level < 100) return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", "You are not authorized to do that")).build();
		try {
			DB.moveEpisodeToRoot(generatedId);
		} catch (DBException e) {
			return Response.ok(Strings.getFileWithToken("generic.html", fbtoken).replace("$EXTRA", e.getMessage())).build();
		}
		return Response.seeOther(GetStuff.createURI("/fb/story/" + generatedId)).build();
	}
}
