package fb;

import static fb.util.Text.escape;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.gson.Gson;

import fb.DB.AuthorProfileResult;
import fb.DB.AuthorSearchResult;
import fb.DB.DBException;
import fb.DB.PasswordResetException;
import fb.db.DBNotification;
import fb.objects.Comment;
import fb.objects.FlaggedComment;
import fb.objects.FlaggedEpisode;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.objects.ModEpisode;
import fb.objects.Notification;
import fb.objects.User;
import fb.util.Dates;
import fb.util.Strings;
import fb.util.Text;
import jakarta.ws.rs.core.Cookie;

public class Accounts {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	public static final int AUTHOR_LENGTH_LIMIT = 32;
	
	private static ConcurrentHashMap<String,UserSession> active = new ConcurrentHashMap<>(); //<loginToken>, user>
	
	private static final String SESSION_PATH = InitWebsite.BASE_DIR + "/fbtemp/sessions/";
	
	/**
	 * Writes out all current login sessions to json files inside the home directory
	 */
	public static void writeSessionsToFile() {
		LOGGER.info("Writing queues to file");
		new File(SESSION_PATH).mkdirs();
		active.entrySet().forEach(entry->{
			try (BufferedWriter out = new BufferedWriter(new FileWriter(SESSION_PATH + entry.getKey()))) {
				out.write(new Gson().toJson(entry.getValue()));
				out.flush();
			} catch (IOException e) {
				LOGGER.error("Error writing user sessions queue", e);
			}
		});
		LOGGER.info("Done writing queues to file");
	}
	
	/**
	 * Reads in all current login sessions from json files inside the home directory
	 */
	private static void readSessionsFromFile() {
		LOGGER.info("Reading queues from file");
		File dir = new File(SESSION_PATH);
		if (dir.exists()) {
			if (dir.isDirectory()) {
				for (File f : dir.listFiles()) {
					UserSession sesh = new Gson().fromJson(Text.readTextFile(f), UserSession.class);
					String token = f.getName();
					active.put(token, sesh);
				}
			} else LOGGER.error("Session directory " + SESSION_PATH + " exists but is a file");
		} else LOGGER.error("Session directory " + SESSION_PATH + " does not exist");
		Strings.safeDeleteFileDirectory(SESSION_PATH);
		LOGGER.info("Done reading queues from file");
	}
	
	/**
	 * Removes any login sessions more than 7 days old
	 */
	private static void pruneSessions() {
		final long now = System.currentTimeMillis();
		final long sevenDaysMillis = 24l*7l*60l*60l*1000l;
		active.entrySet().removeIf(e->now - e.getValue().lastActive().getTime() > sevenDaysMillis);
	}
	
	public static void bump() {
		// Intentionally empty, used to force initAccounts to start
	}
	
	/**
	 * Scan the active sessions and createQueue maps for expired
	 */
	static {
		initAccounts();
	}
	
	/**
	 * Reads in active sessions from json, and spawns a background thread to delete expired sessions from memory and expired email/password changes from the db
	 */
	private static void initAccounts() {
		readSessionsFromFile();
		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
			Accounts.pruneSessions();
			DB.pruneQueues();
		}, 0, 1, TimeUnit.HOURS);
	}
	
	/**
	 * Represents a single login session. 
	 * 
	 * A single user may own multiple login sessions (multiple browsers/devices, browser forgot a still-valid cookie, etc)
	 */
	public static class UserSession { 
		public final String userID;
		private Date lastActive;
		public UserSession(String userID) {
			this.userID = userID;
			this.lastActive = new Date();
		}
		public Date lastActive() {
			return lastActive;
		}
		public void ping() {
			lastActive = new Date();
		}
	}
	
	/**
	 * Get HTML account stuff (click here to log in, or go to your user page)
	 * @param user
	 * @return HTML
	 */
	public static String getAccount(FlatUser user) {
		if (InitWebsite.READ_ONLY_MODE) {
			StringBuilder sb = new StringBuilder();
			sb.append("<div class=\"loginstuff\">Site is currently read-only<br/>\n");
			if (user == null) sb.append("<a href=/fb/login>Log in</a>");
			else sb.append("Logged in as " + escape(user.author) + "<br/><a href=/fb/logout>Log out</a>");
			sb.append("</div>");
			return sb.toString();
		}
		if (user == null) return """
				<div class="loginstuff">
				<p><a href=/fb/createaccount>Create an account</a></p><p>or log in:<br/>
				<form id="loginForm" action="/fb/loginpost" method="post">
				<input type="text" id="loginEmail" class="logintext" name="email" placeholder="username or email" /><br/>
				<input type= "password" id="loginPassword" class="logintext" name= "password" placeholder="password" /><br/>
				<input type= "submit" id="loginButton" value= "Log in" />
				</form><div id="loginResultDiv" ></div></p><p><a href=/fb/passwordreset>I forgot my password</a></p></div>
				""";
		
		String logoutButton = "<form id=\"logoutButton\" class=\"simplebutton\" action= \"/fb/logout\" method=\"get\"><input class=\"simplebutton\" type= \"submit\" value= \"Log out\"/></form>";
				
		StringBuilder response = new StringBuilder("<p>Logged in as <a href=/fb/useraccount>" + escape(user.author) + "</a></p><p>" + logoutButton + "</p><p>");
		if (user.level>=(byte)100) response.append("<a href=/fb/admin>Admin stuff</a><br/>");
		if (user.level>=(byte)10) {
			int[] sizes = DB.queueSizes();
			if (sizes[0] > 0) response.append("<a href=/fb/flagqueue>" + sizes[0] + " flagged episodes</a><br/>");
			if (sizes[1] > 0) response.append("<a href=/fb/modqueue>" + sizes[1] + " episode modification requests</a><br/>");
			if (sizes[2] > 0) response.append("<a href=/fb/commentflagqueue>" + sizes[2] + " flagged comments</a><br/>");
			
			response.append("<a href=/fb/modhelp>Moderator guidelines</a><br/><a href=/fb/modtagedit>Manage Tags</a>");
		}
		int unreadAnnouncements;
		try {
			unreadAnnouncements = (user==null)?0:DB.unreadAnnouncements(user.id);
		} catch (DBException e) {
			unreadAnnouncements = 0;
		}
		if (unreadAnnouncements > 0) response.append("<br/><a href=/fb/announcements>" + unreadAnnouncements + " new announcement" + ((unreadAnnouncements>1)?"s":"") + "</a>");
		response.append("</p>");
		int unreadNotifications;
		try {
			unreadNotifications = (user==null)?0:DB.unreadNotifications(user.id);
		} catch (DBException e) {
			unreadNotifications=0;
		}
		if (unreadNotifications > 0) response.append("<p><a href=/fb/notifications>" + unreadNotifications + " new notification" + ((unreadNotifications>1)?"s":"") + "</a></p>");
		
		response.append("<p><a href=/fb/favorites>Favorite episodes</a></p>\n");
		
		if (InitWebsite.DEV_MODE) {
			response.append("<p>");
			if (user.level != ((byte)1)) response.append("<br/><a href=/fb/becomenormal>Become a normal user</a>");
			if (user.level != ((byte)10)) response.append("<br/><a href=/fb/becomemod>Become a moderator</a>");
			if (user.level != ((byte)100)) response.append("<br/><a href=/fb/becomeadmin>Become an admin</a>");
		}		
		return "<div class=\"loginstuff\">" + response + "</div>";
	}
	
	/**
	 * 
	 * @param id user id
	 * @param fbtoken
	 * @return HTML user page for id
	 */
	public static String getUserPage(String id, Cookie fbtoken, int page) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}
		if (id == null || id.length() == 0) return Strings.getFile("generic.html", user).replace("$EXTRA", "User ID " + id + " does not exist");
		id = id.toLowerCase();
		AuthorProfileResult profileUser;
		try {
			profileUser = DB.getUserProfile(id, page);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "User ID " + id + " does not exist");
		}
		StringBuilder table = new StringBuilder();
		table.append("<table class=\"fbtable\"><tr><th>Episode</th><th>Date</th><th>Story</th><th>Depth</th></tr>");
		for (FlatEpisode ep : profileUser.episodes) {
			String story;
			FlatEpisode rootEp = Story.getRootEpisodeById(DB.newMapToIdList(ep.newMap).findFirst().get());
			if (rootEp == null) story = "";
			else story = rootEp.link;
			table.append("<tr class=\"fbtable\"><td class=\"fbtable\">" + (ep.title.trim().equalsIgnoreCase(ep.link.trim().toLowerCase())?"":(escape(ep.title) + "<br/>")) + "<a href=/fb/story/" + ep.generatedId + " title='"+escape(ep.body.substring(0, Integer.min(140, ep.body.length())))+"'>" + escape(ep.link) + "</a></td><td class=\"fbtable\">" + 
					Dates.simpleDateFormat2(ep.date) + 
					"</td><td class=\"fbtable\">" + escape(story) + "</td><td class=\"textalignright\">"+ep.depth+"</td></tr>");
		}
		table.append("</table>");
		String avatar = (profileUser.user.avatar==null||profileUser.user.avatar.trim().length()==0)?"":("<img class=\"avatarimg\" alt=\"avatar\" src=\"" + escape(profileUser.user.avatar) + "\" /> ");
		String avatarMeta = (profileUser.user.avatar==null||profileUser.user.avatar.trim().length()==0)?"/favicon-192x192.png":escape(profileUser.user.avatar);
		String bio = profileUser.user.bio==null?"":Story.formatBody(profileUser.user.bio);
		String pageCount = "";
		
		String moderator;
		if (profileUser.user.level > 1) {
			if (profileUser.user.level >= 100) moderator = "<p>Fiction Branches admin</p>";
			else moderator = "<p>Fiction Branches moderator</p>";
		} else moderator = "";
		
		String date = (profileUser.user.date==null)?"the beforefore times":Dates.outputDateFormat2(profileUser.user.date);
		
		if (page > 1) pageCount += "<a href=\"/fb/user/" + profileUser.user.id + "/" + (page-1) + "\">Previous</a> ";
		if (profileUser.morePages) pageCount += "<a href=\"/fb/user/" + profileUser.user.id + "/" + (page+1) + "\">Next</a>";
		return Strings.getFile("profilepage.html", user)
				.replace("$MODERATORSTATUS", moderator)
				.replace("$DATE", date)
				.replace("$PAGECOUNT", pageCount)
				.replace("$AUTHOR", profileUser.user.author)
				.replace("$AVATARURLMETA", avatarMeta)
				.replace("$AVATARURL", avatar)
				.replace("$BODY", bio)
				.replace("$EPISODES", table.toString());
	}
	
	public static String getMostEpisodes(Cookie token, DB.PopularUserTime time) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<User> mostEpisodes = DB.popularUsers(DB.PopularUser.EPISODES, time);
		StringBuilder html = new StringBuilder("<h1>Most episodes "+popularTime(time)+"</h1>\n<p>Authors who have written the greatest number of episodes.</p>\n");
		html.append(getPopularityTable(mostEpisodes, time));
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	public static String getMostHits(Cookie token, DB.PopularUserTime time) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<User> mostHits = DB.popularUsers(DB.PopularUser.HITS, time);
		StringBuilder html = new StringBuilder("<h1>Most hits "+popularTime(time)+"</h1>\n<p>Each time an episode page is loaded, that's one hit.</p>\n");
		html.append(getPopularityTable(mostHits, time));
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	public static String getMostViews(Cookie token, DB.PopularUserTime time) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<User> mostViews = DB.popularUsers(DB.PopularUser.VIEWS, time);
		StringBuilder html = new StringBuilder("<h1>Most views "+popularTime(time)+"</h1><p>This is the number of logged in users who have viewed an episode by the author.</p>\n");
		html.append(getPopularityTable(mostViews, time));
		
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	private static String popularTime(DB.PopularUserTime time) {
		switch (time) {
		case ALL: return "all time";
		case WEEK: return "this week";
		case MONTH: return "this month";
		default: return "";
		}
	}
	
	private static String popularTimeParam(DB.PopularUserTime time) {
		switch (time) {
		case ALL: return "all";
		case MONTH: return "month";
		case WEEK:
		default: return "week";
		}
	}
	
	public static String getMostUpvotes(Cookie token, DB.PopularUserTime time) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		
		List<User> mostUpvotes = DB.popularUsers(DB.PopularUser.UPVOTES, time);
		StringBuilder html = new StringBuilder("<h1>Most upvotes "+popularTime(time)+"</h1><p>Users who have received the most upvotes from other users.</p>\n");
		html.append(getPopularityTable(mostUpvotes, time));
		return Strings.getFile("popularnav.html", user).replace("$EXTRA", html.toString());
	}
	
	private static String getPopularityTable(List<User> arr, DB.PopularUserTime time) {
		StringBuilder sb = new StringBuilder();
		String timeParam = popularTimeParam(time);
		sb.append("<table class=\"popular\"><thead><tr><th>Author</th><th><a href=/fb/leaderboardepisodes?time="+timeParam+">Episodes</a></th><th><a href=/fb/leaderboardhits?time="+timeParam+">Hits</a></th><th><a href=/fb/leaderboardviews?time="+timeParam+">Views</a></th><th><a href=/fb/leaderboardupvotes?time="+timeParam+">Upvotes</a></th></tr></thead><tbody>\n");
		for (User user : arr) {
			sb.append("<tr><td><a href=/fb/user/" + user.username + ">" + escape(user.author) + "</a></td><td>" + user.episodes + "</td><td>" + user.hits + "</td><td>" + user.views + "</td><td>" + user.upvotes + "</td></tr>\n");
		}
		sb.append("</tbody></table>\n");
		return sb.toString();
	}
	
	public static String getStaff(Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e) {
			user = null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<h1> Fiction Branches Staff</h1><hr/>");
		
		for (FlatUser staff : DB.getStaff()) {
			String avatar = (staff.avatar==null||staff.avatar.trim().length()==0)?"":("<img class=\"avatarsmall\" alt=\"avatar\" src=\"" + escape(staff.avatar) + "\" /> ");
			sb.append("<h3>" + avatar + "<a href=/fb/user/" + staff.id + ">" + escape(staff.author) + "</a></h3>\n");
			if (staff.level >= 100) sb.append("<p>Admin</p>\n");
			else if (staff.level >= 10) sb.append("<p>Moderator</p>\n");
			sb.append("<p>Member since " + Dates.outputDateFormat2(staff.date) + "</p>\n");
			sb.append(Story.formatBody(staff.bio) + "\n<hr/>\n");
		}

		return Strings.getFile("generic_meta.html", user)
				.replace("$TITLE", "Fiction Branches Staff")
				.replace("$OGDESCRIPTION", "Fiction Branches Staff")
				.replace("$EXTRA", sb.toString());
	}
	
	/**
	 * Get DBUser object from session token cookie
	 * @param token
	 * @return
	 * @throws FBLoginException if not logged in, or if id does not exist
	 */
	public static FlatUser getFlatUser(Cookie token) throws FBLoginException {
		if (token == null) throw new FBLoginException("");
		return getFlatUserUsingTokenString(token.getValue());
	}
	
	/**
	 * Get DBUser object from session token string
	 * @param token
	 * @return
	 * @throws FBLoginException if not logged in, or if id does not exist
	 */
	public static FlatUser getFlatUserUsingTokenString(String token)  throws FBLoginException {
		if (token == null || token.trim().length()==0) throw new FBLoginException("");
		UserSession sesh = active.get(token);
		if (sesh == null) throw new FBLoginException("");
		try {
			FlatUser user = DB.getFlatUser(sesh.userID);
			sesh.ping();
			return user;
		} catch (DBException e) {
			throw new FBLoginException("");
		}
	}
	
	/**
	 * Get username from session token, or null if sesh does not exist
	 * @param token
	 * @return
	 */
	public static String getUsernameFromCookie(Cookie token) {
		if (token == null) return null;
		UserSession sesh = active.get(token.getValue());
		if (sesh == null) return null;
		return sesh.userID;
	}
	
	/**
	 * Return whether fbtoken is an active login session
	 * 
	 * Also updates active session with current time
	 * 
	 * @param fbtoken token from cookie
	 * @return true if active user session, else false
	 */
	public static boolean isLoggedIn(Cookie fbtoken) {
		if (fbtoken == null) return false;
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) return false;
		if (DB.userIdInUse(sesh.userID)) {
			sesh.ping();
			return true;
		} else return false;
	}
	
	/**
	 * Log in using email and password
	 * @param email email address
	 * @param password plaintext password
	 * @return login token to be added as cookie
	 * @throws FBLoginException if email or password is wrong (e.getMessage() contains HTML)
	 */
	public static String login(String email, String password) throws FBLoginException {
		FlatUser user;
		try {
			email = email.toLowerCase();
			user = email.contains("@") ? DB.getFlatUserByEmail(email) : DB.getFlatUser(email);
			if (!DB.checkPassword(user.id, password)) throw new FBLoginException(Strings.getFile("loginform.html", null).replace("$EXTRA", "Incorrect username/email or password, or username/email does not exist"));
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("loginform.html", null).replace("$EXTRA", "Incorrect username/email or password, or username/email does not exist"));
		}
		
		String newToken = newToken(active);
		active.put(newToken, new UserSession(user.id));
		
		return newToken;
	}
	
	public static void logout(Cookie fbtoken) {
		if (fbtoken == null) return;
		active.remove(fbtoken.getValue());
	}
	
	public static class FBLoginException extends Exception {
		/** */
		private static final long serialVersionUID = -3144814684721361990L;
		public FBLoginException(String message) {
			super(message);
		}
	}
	
	/**
	 * Verify a new account
	 * @param createToken 
	 * @return HTML account confirmed, or plaintext error
	 */
	public static String verify(String createToken) {
		try {
			String user = DB.addUser(createToken);
			LOGGER.info("Created user " + user);
			return Strings.getFile("accountconfirmed.html", null);
		} catch (DBException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", e.getMessage());
		}
	}
	
	/**
	 * Create a new account
	 * @param email email address
	 * @param password password
	 * @param password2 confirm password
	 * @param author author name
	 * @return HTML with success or form with error 
	 */
	public static String create(String email, String password, String password2, String author, String username, String domain) {
		
		String htmlForm = Strings.getFile("createaccountform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY());
		if (email == null || email.length() == 0) return htmlForm.replace("$EXTRA", "Email address is required");

		email = email.toLowerCase();

		if (DB.emailInUse(email)) return htmlForm.replace("$EXTRA", "Email address " + email + " is already in use");
		if (!EmailValidator.getInstance().isValid(email)) return htmlForm.replace("$EXTRA", "Invalid email address " + email);
		if (!password.equals(password2)) return htmlForm.replace("$EXTRA", "Passwords do not match");
		if (password.length() < 8) return htmlForm.replace("$EXTRA", "Password must be at least 8 characters long");
		if (author == null) return htmlForm.replace("$EXTRA", "Author name is required");
		author = author.trim();
		if (author.length() == 0) return htmlForm.replace("$EXTRA", "Author name is required");
		if (author.length() > Accounts.AUTHOR_LENGTH_LIMIT) return htmlForm.replace("$EXTRA", "Author name cannot be longer than 32 characters");
		if (username == null || username.length() == 0) return htmlForm.replace("$EXTRA", "Username is required");
		username = username.toLowerCase();
		if (DB.userIdInUse(username)) return htmlForm.replace("$EXTRA", "Username " + username + " is already in use");
		for (char c : username.toCharArray()) if (!allowedUsernameChars.contains(c)) return htmlForm.replace("$EXTRA", "Username may not contain " + c);

		String createToken = DB.addPotentialUser(username, email, BCrypt.hashpw(password, BCrypt.gensalt(10)), author);
		if (InitWebsite.DEV_MODE) {
			verify(createToken);
			return Strings.getFile("generic.html",null).replace("$EXTRA", "Since you are in dev mode, your account has been created without email verification required.");
 
		}
		if (!sendEmail(email, "Confirm your Fiction Branches account", 
				"<html><body>Please click the following link (or copy/paste it into your browser) to verify your account: <a href=https://" + domain + "/fb/confirmaccount/" + createToken + ">https://" + domain + "/fb/confirmaccount/" + createToken + "</a> (This link is only good for 24 hours.)</body></html>")) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", "Unable to send verification email, talk to Phoenix about it");
		}
		return Strings.getFile("generic.html",null).replace("$EXTRA", "Check your email (and your spam folder) for a confirmation email from noreply@fictionbranches.net");
	}
	
	public static String useraccountform(FlatUser user, String error) {
		final String checked = "checked";
		
		final String widthHTML = 
				"<p>Select a body text width:</p>\n" + 
				"<form action=\"/fb/changebodytextwidth\" method=\"post\">\n" + 
				"  <input type=\"radio\" name=\"bodytextwidth\" value=\"540\" "+(user.bodyTextWidth==540?"checked":"")+" /> Narrow (600px)<br/>\n" + 
				"  <input type=\"radio\" name=\"bodytextwidth\" value=\"900\" "+(user.bodyTextWidth==900?"checked":"")+" /> Medium (900px)<br/>\n" + 
				"  <input type=\"radio\" name=\"bodytextwidth\" value=\"1440\" "+(user.bodyTextWidth==1440?"checked":"")+" /> Wide (1600px)<br/>\n" + 
				"  <input type=\"radio\" name=\"bodytextwidth\" value=\"0\" "+(user.bodyTextWidth==0?"checked":"")+" /> Unconstrained<br/>\n" + 
				"  <input type= \"submit\" value= \"Submit\"/>\n" + 
				"</form>\n" + 
				"<p>(Text will fit to window if window is narrower than selection.)</p>"
				;
		
		final String hideImageButton = "Images are currently " + (user.hideImages ? "hidden" : "shown") + ". <a href='/fb/togglehideimages'>Click here to " + (user.hideImages ? "show" : "hide") + " them.</a>";
		
		return Strings.getFile("useraccount.html", user)
				.replace("$COMMENT_SITE_CHECKED", user.commentSite?checked:"")
				.replace("$COMMENT_MAIL_CHECKED", user.commentMail?checked:"")
				.replace("$CHILD_SITE_CHECKED", user.childSite?checked:"")
				.replace("$CHILD_MAIL_CHECKED", user.childMail?checked:"")
				.replace("$ID", user.id)
				.replace("$AUTHORNAME", escape(user.author))
				.replace("$THEMES", Strings.getSelectThemes())
				.replace("$BIOBODY", escape(user.bio))
				.replace("$AVATARURL", escape(user.avatar==null?"":user.avatar))
				.replace("$EXTRA", error==null||error.length()==0?"":"ERROR: " + error)
				.replace("$BODYTEXTWIDTHFORM", widthHTML)
				.replace("$HIDEIMAGEBUTTON", hideImageButton)
				;
	}
	
	/**
	 * Changes the author name of the currently logged in user
	 * @param fbtoken
	 * @param author new author name
	 * @throws FBLoginException if user is not logged in, or is root
	 */
	public static void changeBodyTextWidth(Cookie fbtoken, int bodyTextWidth) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "ERROR: You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		FlatUser user;
		try {
			user = DB.getFlatUser(sesh.userID);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "Invalid user"));
		}
		if (user.id.equals(DB.ROOT_ID)) throw new FBLoginException(useraccountform(user, "ERROR: This user account may not be modified"));
		
		if (bodyTextWidth < 0) bodyTextWidth = 0;
		
		try {
			DB.changeBodyTextWidth(user.id, bodyTextWidth);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA", "Invalid user"));
		}
	}
	
	/**
	 * Changes the author name of the currently logged in user
	 * @param fbtoken
	 * @param author new author name
	 * @throws FBLoginException if user is not logged in, or is root
	 */
	public static void changeAuthor(Cookie fbtoken, String author) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "ERROR: You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		FlatUser user;
		try {
			user = DB.getFlatUser(sesh.userID);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "Invalid user"));
		}
		if (user.id.equals(DB.ROOT_ID)) throw new FBLoginException(useraccountform(user, "ERROR: This user account may not be modified"));
		if (author.length() == 0) throw new FBLoginException(useraccountform(user, "ERROR: Author cannot be empty"));
		if (author.length() > Accounts.AUTHOR_LENGTH_LIMIT) throw new FBLoginException(useraccountform(user, "ERROR: Author cannot be longer than "+Accounts.AUTHOR_LENGTH_LIMIT+" characters"));
		try {
			DB.changeAuthorName(user.id, author);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA", "Invalid user"));
		}
	}
	
	/**
	 * Changes the author name of the currently logged in user
	 * @param fbtoken
	 * @param theme new theme name
	 * @throws FBLoginException if user is not logged in, or is root
	 */
	public static void changeTheme(Cookie fbtoken, String theme) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		try {
			DB.changeTheme(sesh.userID, theme);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "Invalid user"));
		}
	}
	
	/**
	 * Changes the author bio of the currently logged in user
	 * @param fbtoken
	 * @param theme new theme name
	 * @throws FBLoginException if user is not logged in, or is root, or bio is incorrect
	 */
	public static void changeBio(Cookie fbtoken, String bio) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		FlatUser user;
		try {
			user = DB.getFlatUser(sesh.userID);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "Invalid user"));
		}
		
		// Check new bio for correctness
		StringBuilder errors = new StringBuilder();
		if (bio.length() == 0) errors.append("Body cannot be empty<br/>\n");
		if (bio.length() > 10000) errors.append("Body cannot be longer than 10000 (" + bio.length() + ")<br/>\n");
		TreeSet<String> list = new TreeSet<>();
		for (String s : Story.replacers) if (bio.contains(s)) list.add(s);
		if (!list.isEmpty()) {
			errors.append("Bio may not contain any of the following strings: ");
			for (String s : list) errors.append("\"" + s + "\"");
			errors.append("<br/>\n");
		}
		if (errors.length() > 0) throw new FBLoginException(useraccountform(user, errors.toString()));
		
		try {
			DB.changeBio(user.id, bio);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA", "Invalid user"));
		}
	}
	
	/**
	 * Changes the author bio of the currently logged in user
	 * @param fbtoken
	 * @param theme new theme name
	 * @throws FBLoginException if user is not logged in, or is root, or bio is incorrect
	 */
	public static void changeAvatar(Cookie fbtoken, String avatar) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		try {
			DB.changeAvatar(sesh.userID, avatar);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "Invalid user"));
		}
	}
		
	/**
	 * 
	 * @param fbtoken
	 * @param newpass
	 * @param newpass2
	 * @param password
	 * @throws FBLoginException if password does not meet requirements, passwords do not match, or user does not exist
	 */
	public static void changePassword(Cookie fbtoken, String newpass, String newpass2, String password) throws FBLoginException {
		if (fbtoken == null) throw new FBLoginException(Strings.getFile("changepasswordform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "You must be logged in to do that"));
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) throw new FBLoginException(Strings.getFile("changepasswordform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "You must be logged in to do that"));
		FlatUser user;
		try {
			user = DB.getFlatUser(sesh.userID);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("changepasswordform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Invalid user"));
		}
		if (user.id.equals(DB.ROOT_ID)) throw new FBLoginException("This user account may not be modified");
		if (newpass.length() < 8) throw new FBLoginException(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Password cannot be shorter than 8 characters"));
		if (!newpass.equals(newpass2)) throw new FBLoginException(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Passwords do not match"));
		try {
			if (!DB.checkPassword(user.id, password)) throw new FBLoginException(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Incorrect current password"));
		} catch (DBException e1) {
			throw new FBLoginException(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Invalid user"));
		}
		try {
			DB.changePassword(user.id, BCrypt.hashpw(newpass, BCrypt.gensalt(10)));
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("changepasswordform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Invalid user"));
		}
	}
	
	/**
	 * Begin process of changing current user's email address
	 * @param fbtoken
	 * @param email
	 * @param password
	 * @return HTML page with instructions on how to fix input or continue process
	 */
	public static String changeEmail(Cookie fbtoken, String email, String password, String domain) {
		if (fbtoken == null) return Strings.getFile("changeemailform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "You must be logged in to do that");
		UserSession sesh = active.get(fbtoken.getValue());
		if (sesh == null) return Strings.getFile("changeemailform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "You must be logged in to do that");
		

		if (sesh.userID.equals(DB.ROOT_ID)) return "This user account may not be modified";
		
		FlatUser user;
		try {
			user = DB.getFlatUser(sesh.userID);
		} catch (DBException e) {
			return Strings.getFile("changeemailform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "You must be logged in to do that");
		}
		
		if (!DB.checkPassword(user, password)) return Strings.getFile("changeemailform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Incorrect current password");
		
		if (!EmailValidator.getInstance().isValid(email)) return Strings.getFile("changeemailform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Invalid email address " + email);
		
		if (DB.emailInUse(email)) return Strings.getFile("changeemailform.html", user).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", email + " is already in use by another account");
		String changeToken;
		try {
			changeToken = DB.addEmailChange(sesh.userID, email);
		} catch (DBException e) {
			return Strings.getFile("changeemailform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", e.getMessage());
		}
		
		if (InitWebsite.DEV_MODE) {
			verifyNewEmail(changeToken, fbtoken);
			return Strings.getFile("generic.html",null).replace("$EXTRA", "Since you are in dev mode, your email has been changed without email verification required.");
 
		}

		if (!sendEmail(email, "Confirm your new Fiction Branches account email", 
				"<html><body>Please click the following link (or copy/paste it into your browser) to verify your new email address: <a href=https://" + domain + "/fb/confirmemailchange/" + changeToken + ">https://" + domain + "/fb/confirmemailchange/" + changeToken + "</a> (This link is only good for 24 hours.)\nAfter taking this action, you will have to use your new email address to log in.</body></html>")) {
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Unable to send verification email, please come to the Discord and tell Phoenix about this.");
		}
		return Strings.getFile("generic.html",user).replace("$EXTRA", "Check your email (and your spam folder) for a confirmation email from noreply@fictionbranches.net");
	}
	
	/**
	 * Verify a new account
	 * @param createToken 
	 * @return HTML email change confirmed, or error page
	 */
	public static String verifyNewEmail(String changeToken, Cookie fbtoken) {	
			FlatUser user;
			try {
				DB.changeEmail(changeToken);
			} catch (DBException e) {
				return "Confirmation link is expired, invalid, or has already been used";
			}
			try {
				user = Accounts.getFlatUser(fbtoken);
			} catch (FBLoginException e) {
				user = null;
			}
			return Strings.getFile("generic.html", user).replace("$EXTRA", "Email address successfully changed");
	}
	
	/**
	 * @param userID
	 * @param level
	 * @param fbtoken
	 * @return HTML page containing result
	 */
	public static String changeLevel(String userID, byte level, Cookie fbtoken) {
		FlatUser admin;
		try {
			admin = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (userID.equals(DB.ROOT_ID)) return Strings.getFile("adminform.html", admin).replace("$EXTRA", "This user account may not be modified");
		if (admin.level<100) return Strings.getFile("generic.html", admin).replace("$EXTRA","You must be an admin to do that");
		try {
			DB.changeUserLevel(userID, level);
		} catch (DBException e) {
			return Strings.getFile("adminform.html", admin).replace("$EXTRA", "User id does not exist");
		}
		return Strings.getFile("adminform.html", admin).replace("$EXTRA", "User " + userID + " level successfully changed");
	}
	
	public static String getFlagQueue(Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		StringBuilder sb = new StringBuilder();
		sb.append("<h1> Flag queue</h1>\n");
		for (FlaggedEpisode flag : DB.getFlags()) {
			sb.append("<a href=/fb/getflag/" + flag.id + ">" + escape(flag.episode.link) + "</a> flagged by <a href=/fb/user/" + flag.user.id + ">" + escape(flag.user.author) + "</a> on " + Dates.outputDateFormat2(flag.date) + "<br/>\n");
		}
		return Strings.getFile("generic.html", user).replace("$EXTRA", sb.toString());
	}
	
	public static String getModQueue(Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		StringBuilder sb = new StringBuilder();
		sb.append("<h1> Mod queue</h1>\n");
		for (ModEpisode mod : DB.getMods()) {
			sb.append("<a href=/fb/getmod/" + mod.modId + ">" + escape(mod.link) + "</a> submitted by <a href=/fb/user/" + mod.userId + ">" + escape(mod.author) + "</a> on " + Dates.outputDateFormat2(mod.date) + "<br/>\n");
		}
		return Strings.getFile("generic.html", user).replace("$EXTRA", sb.toString());
	}
	
	public static String getCommentFlagQueue(Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		StringBuilder sb = new StringBuilder();
		sb.append("<h1>Comment flag queue</h1>\n");
		for (FlaggedComment c : DB.getFlaggedComments()) {
			sb.append("<a href=/fb/getflaggedcomment/" + c.id + ">" + c.id + "</a> submitted by <a href=/fb/user/" + c.user.id + ">" + escape(c.user.author) + "</a> on " + Dates.outputDateFormat2(c.date) + "<br/>\n");
		}
		return Strings.getFile("generic.html", user).replace("$EXTRA", sb.toString());
	}
	
	public static String getCommentFlag(long commentId, Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		FlaggedComment flag;
		try {
			flag = DB.getFlaggedComment(commentId);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<h1>Flagged comment:</h1>\n");
		
			StringBuilder commentHTML = sb;
			Comment c = flag.comment;
			commentHTML.append("<div class=\"fbcomment\">\n");
			commentHTML.append("<p>" + Story.formatBody(c.text) + "</p><hr/>");
			commentHTML.append("<img class=\"avatarsmall\" alt=\"avatar\" src=\""+escape(c.user.avatar) + "\" /><a href=/fb/user/" + c.user.id + ">" + escape(c.user.author) + "</a><br/>\n");
			commentHTML.append(Dates.outputDateFormat2(c.date));
			commentHTML.append("</div>\n");
			
		sb.append("<h1>Flag text:</h1>");
		
		sb.append("<a href=/fb/story/" + flag.comment.episode.generatedId + ">" + escape(flag.comment.episode.link) + "</a> flagged by <a href=/fb/user/" + flag.user.id + ">" + escape(flag.user.author) + "</a> on " + 
				Dates.outputDateFormat2(flag.date) + "<br/>\n");
		sb.append("<a href=/fb/clearflaggedcomment/" + flag.id + ">Delete this flag</a><br/>\n");
		sb.append("<p>" + escape(flag.text) + "</p>");
		return Strings.getFile("generic.html", user).replace("$EXTRA",sb.toString());
	}
	
	public static void clearFlaggedComment(long id, Cookie fbtoken) throws FBLoginException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		}
		if (user.level<10) throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that"));
		try {
			DB.clearFlaggedComment(id);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage()));
		}
	}
	
	public static String getFlag(long id, Cookie fbtoken) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		FlaggedEpisode flag;
		try {
			flag = DB.getFlag(id);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<h1> Flagged episode</h1>\n");
		sb.append("<a href=/fb/story/" + flag.episode.generatedId + ">" + escape(flag.episode.link) + "</a> flagged by <a href=/fb/user/" + flag.user.id + ">" + escape(flag.user.author) + "</a> on " + 
				Dates.outputDateFormat2(flag.date) + "<br/>\n");
		sb.append("<a href=/fb/clearflag/" + flag.id + ">Delete this flag</a><br/>\n");
		sb.append("<p>" + escape(flag.text) + "</p>");
		return Strings.getFile("generic.html", user).replace("$EXTRA",sb.toString());
	}
	
	public static String getMod(long id, Cookie fbtoken, boolean doDiff) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that");
		}
		if (user.level<10) return Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that");
		ModEpisode mod;
		try {
			mod = DB.getMod(id);
		} catch (DBException e) {
			return Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage());
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<h1> Modification of episode</h1>\n");
		sb.append("<p>This is the proposed new version. The current version is available " + "<a href=/fb/story/" + mod.episodeGeneratedId + ">here</a></p>");
		if (doDiff) sb.append("<p><a href=/fb/getmod/" + id + ">Get complete modification</a></p>");
		else sb.append("<p><a href=/fb/getmod/" + id + "?diff>Get diff</a></p>");
		sb.append("<p><a href=/fb/story/" + mod.episodeGeneratedId + ">" + escape(mod.oldLink) + "</a> submitted by <a href=/fb/user/" + mod.userId + ">" + escape(mod.author) + "</a> on " + 
				Dates.outputDateFormat2(mod.date) + "</p>\n");
		sb.append("<p><a href=/fb/acceptmod/" + mod.modId + ">Accept this modification</a></p>\n");
		sb.append("<p><a href=/fb/rejectmod/" + mod.modId + ">Reject this modification</a></p>\n");
		sb.append("<p><hr/><h4>New link:</h4> " + escape(mod.link) + "</p>\n");
		sb.append("<p><hr/><h4>New title:</h4> " + escape(mod.title) + "</p>\n");
		if (doDiff) {
			String oldBody = mod.currentEpisode.body;
			try {
				
				DiffRowGenerator generator = DiffRowGenerator.create()
		                .showInlineDiffs(true)
		                .inlineDiffByWord(true)
		                .oldTag(f -> (Boolean.TRUE.equals(f)?"<del>":"</del>"))
		                .newTag(f -> (Boolean.TRUE.equals(f)?"<strong>":"</strong>"))
		                .build();
				List<DiffRow> rows = generator.generateDiffRows(listify(escape(oldBody)), listify(escape(mod.body)));
				StringBuilder out = new StringBuilder();
				out.append("<table><thead><tr><th>original</th><th>new</th></tr></thead><tbody>\n");
				
				TreeSet<Integer> set = new TreeSet<>();
				for (int i=0; i<rows.size(); ++i) {
					DiffRow row = rows.get(i);
					if (!row.getOldLine().equals(row.getNewLine())) {
						set.add(i);
						if (i>0) set.add(i-1);
						if (i<rows.size()-1) set.add(i+1);
					}
				}
				for (int i : set) {
					DiffRow row = rows.get(i);
					String oldLine = row.getOldLine();
					String newLine = row.getNewLine();
					out.append("<tr><td>" + (oldLine) + "</td><td>" + (newLine) + "</td></tr>\n");
				}
				out.append("</tbody></table>");
				sb.append("<p><hr/><h4>Diffed body:</h4> " + out.toString() + "</p>\n");
			} catch (Exception e) {
				LOGGER.error("Diff threw an exception", e);
				sb.append("<p><hr/><h4>New body:</h4> " + Story.formatBody(mod.body) + "</p>\n");
				return Strings.getFile("generic.html", user).replace("$EXTRA","<p>Diff threw an exception: " + e.getMessage() + "</p>" + sb.toString());
			}
		} else sb.append("<p><hr/><h4>New body:</h4> " + Story.formatBody(mod.body) + "</p>\n");
		return Strings.getFile("generic.html", user).replace("$EXTRA",sb.toString());
	}
	
	private static List<String> listify(String s) {
		return new BufferedReader(new StringReader(s)).lines().toList();
	}
	
	public static void clearMod(long id, Cookie fbtoken, boolean accepted) throws FBLoginException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		}
		if (user.level<10) throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that"));
		try {
			DB.clearMod(id, accepted, user.id);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage()));
		}
	}
	
	public static void clearFlag(long id, Cookie fbtoken) throws FBLoginException {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(fbtoken);
		} catch (FBLoginException e ) {
			throw new FBLoginException(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
		}
		if (user.level<10) throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA","You must be a mod to do that"));
		try {
			DB.clearFlag(id);
		} catch (DBException e) {
			throw new FBLoginException(Strings.getFile("generic.html", user).replace("$EXTRA",e.getMessage()));
		}
	}
	
	/**
	 * Add a new DBPasswordReset to the database and send an email with a verification link
	 * @param username
	 * @return
	 */
	public static String resetPassword(String username, String domain) {
		String[] pr;
		try {
			if (EmailValidator.getInstance().isValid(username)) pr = DB.newPasswordResetEmail(username);
			else pr = DB.newPasswordResetUsername(username);
		} catch (DBException e) {
			return Strings.getFile("passwordresetform.html", null).replace("$RECAPTCHASITEKEY", Strings.getRECAPTCHA_SITEKEY()).replace("$EXTRA", "Username or email address " + username + " not found");
		} catch (PasswordResetException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "We already sent you an email. Please check your spam folder and be patient. Emails expire after 24 hours.");
		}
		
		String token = pr[0];
		String email = pr[1];
		
		if (!sendEmail(email, "Fiction Branches password reset", 
				"<html><body>Please click the following link (or copy/paste it into your browser) to verify your new email address: <a href=https://" + domain + "/fb/confirmpasswordreset/" + token + ">https://" + domain + "/fb/confirmpasswordreset/" + token + "</a> (This link is only good for 24 hours.)</body></html>")) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", "Unable to send verification email, please come to the Discord and tell Phoenix about this.");
		}
		return Strings.getFile("generic.html",null).replace("$EXTRA", "Check your email (and your spam folder) for an email from noreply@fictionbranches.net");
	}
	
	/**
	 * Verify that token is valid and return password reset form
	 * @param token
	 * @return
	 */
	public static String confirmPasswordResetForm(String token) {
		if (!DB.passwordResetTokenExists(token)) return Strings.getFile("generic.html",null).replace("$EXTRA", "Confirmation link is expired, invalid, or has already been used");
		return Strings.getFile("confirmpasswordresetform.html", null).replace("$EXTRA", "").replace("$TOKEN", token);
	}
	
	/**
	 * Check passwords, 
	 * @param token
	 * @param passworda
	 * @param passwordb
	 * @return
	 */
	public static String confirmPasswordReset(String token, String passworda, String passwordb) {
		if (passworda.equals(passwordb)) {
			
			if (passworda.length() < 8) return Strings.getFile("confirmpasswordresetform.html", null).replace("$EXTRA", "Password must be at least 8 characters long").replace("$TOKEN", token);
			try {
				DB.resetPassword(token, passworda);
			} catch (DBException e) {
				return Strings.getFile("generic.html",null).replace("$EXTRA", "Confirmation link is expired, invalid, or has already been used");
			}
			return Strings.getFile("generic.html", null).replace("$EXTRA", "You password has been successfully changed! You may now <a href=/fb/login>log in</a>.");
		} else return Strings.getFile("confirmpasswordresetform.html", null).replace("$EXTRA", "Passwords do not match").replace("$TOKEN", token);
	}
	
	public static String getSearchForm(Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html", null).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		}
		return Strings.getFile("usersearchform.html", user).replace("$SEARCHTERM", "").replace("$EXTRA", "");
	}
	
	public static String searchPost(Cookie token, String search, int page) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		if (user == null) return Strings.getFile("generic.html", user).replace("$EXTRA", Strings.getString("must_be_logged_in"));
		if (page < 1) page = 1;
		
		AuthorSearchResult results = DB.searchUser(search, page);

		List<FlatUser> result = results.users;
		StringBuilder sb = new StringBuilder();
		if (page > 1) sb.append(searchButton("Prev", search, page-1));
		if (results.morePages) sb.append(searchButton("Next", search, page+1));
		if (sb.length() > 0) {
			String asdf = sb.toString();
			sb = new StringBuilder("<p>" + asdf + "</p>");
		}
		String prevNext = sb.toString();
		sb.append("<table class=\"fbtable\">");
		for (FlatUser fu : result) {
			sb.append("<tr class=\"fbtable\"><td class=\"fbtable\"><a href=/fb/user/" + fu.id + ">"+escape(fu.author)+"</td>" 
					+ "<td class=\"fbtable\">" + Dates.simpleDateFormat2(fu.date) + "</td></tr>\n");
		}
		sb.append("</table>");
		sb.append(prevNext);
		return Strings.getFile("usersearchform.html", user).replace("$SEARCHTERM", search).replace("$EXTRA", sb.toString());
	}
	
	private static String searchButton(String name, String search, int page) {
		return "<form class=\"simplebutton\" action=\"/fb/usersearch\" method=\"get\">\n" + 
				"  <input type=\"hidden\" name=\"q\" value=\""+escape(search)+"\" />\n" + 
				"  <input type=\"hidden\" name=\"page\" value=\""+page+"\" />\n" + 
				"  <input class=\"simplebutton\" type=\"submit\" value=\""+name+"\" />\n" + 
				"</form>";
	}
	
	public static String getNotifications(Cookie token, boolean all) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that");
		}
				
		List<Notification> list;
		try {
			list = DB.getNotificationsForUser(user.id, all, 1);
		} catch (DBException e) {
			return Strings.getFile("generic.html",null).replace("$EXTRA", "You must be logged in to do that");
		}
		
		StringBuilder sb = new StringBuilder("<h1>Notifications</h1>\n");
		if (!all) sb.append("<p><a href=\"/fb/notifications?all=true\">Show all</a></p>\n");
		sb.append("<hr/>\n");
		for (Notification a : list) {
			
			switch (a.type) {
			case DBNotification.LEGACY_NOTE:
				sb.append("<p>" + a.body + "</p>\n");
				break;
			case DBNotification.NEW_CHILD_EPISODE:
				sb.append("<p><a href=\"/fb/user/" + a.episode.authorId + "\">" + escape(a.episode.authorName) + "</a> wrote a <a href=\"/fb/story/" + a.episode.generatedId + "\">new child episode</a> of <a href=/fb/story/" + a.parentEpisode.generatedId +">" + escape(a.parentEpisode.title) + "</a></p>\n");
				break;
			case DBNotification.NEW_COMMENT_ON_OWN_EPISODE:
				sb.append("<a href=\"/fb/user/" + a.comment.user.id + "\">" + escape(a.comment.user.author) + "</a> left a <a href=\"/fb/story/" + a.comment.episode.generatedId + "#comment" + a.comment.id + "\">comment</a> on " + escape(a.comment.episode.title));
				break;
			case DBNotification.NEW_COMMENT_ON_SUBBED_EPISODE:
				sb.append("<a href=\"/fb/user/" + a.comment.user.id + "\">" + escape(a.comment.user.author) + "</a> left a <a href=\"/fb/story/" + a.comment.episode.generatedId + "#comment" + a.comment.id + "\">comment</a> on " + escape(a.comment.episode.title));
				break;
			case DBNotification.MODIFICATION_RESPONSE:
				sb.append("<a href=\"/fb/user/" + a.sender.id + "\">" + escape(a.sender.author) + "</a> "+(Boolean.TRUE.equals(a.approved)?"approved":"rejected")+" your request to modify <a href=\"/fb/story/" + a.episode.generatedId + " \">"+escape(a.episode.link)+"</a>");
				break;
			default:
				if (a.body != null && a.body.length() > 0) sb.append("<p>" + a.body + "</p>");
				else sb.append("<p>Invalid notification</p>");
			}
			sb.append("<p>(" + Dates.outputDateFormat2(a.date) + ")</p>\n");
			sb.append("<hr/>\n");
		}
		
		if (list.isEmpty()) {
			if (all) sb.append("<p>(no notifications)</p>");
			else sb.append("<p>(no new notifications)</p>");
		}
		
		return Strings.getFile("generic.html", user).replace("$EXTRA", sb.toString());
	}
	
	/**
	 * Generate a new 32-char token, ensuring that the map does not contain it as a key
	 * @param map map to check for collision
	 * @return token
	 */
 	private static String newToken(ConcurrentHashMap<String,?> map) {
		StringBuilder token = new StringBuilder();
		for (int i=0; i<32; ++i) token.append((char)('a'+Strings.r.nextInt(26)));
		while (map.containsKey(token.toString())) {
			token = new StringBuilder();
			for (int i=0; i<32; ++i) token.append((char)('a'+Strings.r.nextInt(26)));
		}
		return token.toString();
	}
 	
	
	/**
	 * Send an email
	 * @param toAddress
	 * @param subject
	 * @param body
	 * @return whether it sent successfully or not
	 */
	public static boolean sendEmail(String toAddress, String subject, String body) {
		Properties props = new Properties();
		props.put("mail.smtp.host", Strings.getSMTP_SERVER());
		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		Authenticator auth = new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(Strings.getSMTP_EMAIL(), Strings.getSMTP_PASSWORD());
			}
		};
		Session session = Session.getInstance(props, auth);
		try {
			MimeMessage msg = new MimeMessage(session);
			msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
			msg.addHeader("format", "flowed");
			msg.addHeader("Content-Transfer-Encoding", "8bit");
			msg.setFrom(new InternetAddress(Strings.getSMTP_EMAIL(), "Fiction Branches"));
			msg.setReplyTo(InternetAddress.parse(Strings.getSMTP_EMAIL(), false));
			msg.setSubject(subject, "UTF-8");
			msg.setText(body, "UTF-8", "html");
			msg.setSentDate(new Date());
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress, false));
			Transport.send(msg);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	private static HashSet<Character> allowedUsernameChars;
	static {
		allowedUsernameChars = new HashSet<>();
		for (char c = '0'; c<='9'; ++c) allowedUsernameChars.add(c);
		for (char c = 'a'; c<='z'; ++c) allowedUsernameChars.add(c);
		for (char c = 'A'; c<='Z'; ++c) allowedUsernameChars.add(c);
		allowedUsernameChars.add('-');
		allowedUsernameChars.add('_');
		allowedUsernameChars.add('.');
	}
	
	private Accounts() {}
}
