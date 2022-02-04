package fb.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.InitWebsite;
import fb.db.DBSiteSetting;
import fb.objects.FlatUser;
import jakarta.ws.rs.core.Cookie;

public class Strings {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	/**
	 * use for any random numbers needed anywhere
	 */
	public static final Random r; 
	
	private static final Map<String,String> filesMap;
	private static final Map<String,String> stringsTxtMap;
		
	private static String DOMAIN;
	private static String SMTP_SERVER;
	private static String SMTP_EMAIL;
	private static String SMTP_PASSWORD;
	private static String RECAPTCHA_SECRET;
	private static String RECAPTCHA_SITEKEY;
	private static String DISCORD_TOKEN;
	private static String DISCORD_ERROR_CHANNEL;
	private static String DISCORD_NEW_EPISODE_HOOK;
	private static String DONATE_BUTTON;
	private static String BACKEND_PORT;

	static {
		r = new Random();
		
		refreshSiteSettings();

		filesMap = readInFilesMap();
		stringsTxtMap = readInStringsMap();
	}
	
	public static void refreshSiteSettings() {
		Session session = DB.openSession();
		try {
			DBSiteSetting setting;
			
			setting = session.get(DBSiteSetting.class, "donate_button");
			if (setting != null) DONATE_BUTTON = setting.getValue();
			else DONATE_BUTTON = "";
			
			setting = session.get(DBSiteSetting.class, "domain_name");
			if (setting != null) DOMAIN = setting.getValue();
			else DOMAIN = "localhost";
			
			setting = session.get(DBSiteSetting.class, "smtp_email");
			if (setting != null) SMTP_EMAIL = setting.getValue();
			else SMTP_EMAIL = null;
			
			setting = session.get(DBSiteSetting.class, "smtp_server");
			if (setting != null) SMTP_SERVER = setting.getValue();
			else SMTP_SERVER = null;
			
			setting = session.get(DBSiteSetting.class, "smtp_password");
			if (setting != null) SMTP_PASSWORD = setting.getValue();
			else SMTP_PASSWORD = null;
			
			setting = session.get(DBSiteSetting.class, "recaptcha_secret");
			if (setting != null) RECAPTCHA_SECRET = setting.getValue();
			else RECAPTCHA_SECRET = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe";
			
			setting = session.get(DBSiteSetting.class, "recaptcha_sitekey");
			if (setting != null) RECAPTCHA_SITEKEY = setting.getValue();
			else RECAPTCHA_SITEKEY = "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI";
			
			setting = session.get(DBSiteSetting.class, "discord_token");
			if (setting != null) DISCORD_TOKEN = setting.getValue();
			else DISCORD_TOKEN = "";
			
			setting = session.get(DBSiteSetting.class, "discord_channel");
			if (setting != null) DISCORD_ERROR_CHANNEL = setting.getValue();
			else DISCORD_ERROR_CHANNEL = "";
			
			setting = session.get(DBSiteSetting.class, "discord_new_episode_hook");
			if (setting != null) DISCORD_NEW_EPISODE_HOOK = setting.getValue();
			else DISCORD_NEW_EPISODE_HOOK = "";
			
			setting = session.get(DBSiteSetting.class, "backend_port");
			if (setting != null) BACKEND_PORT = setting.getValue();
			else BACKEND_PORT = "8080";
			
			
		} finally {
			DB.closeSession(session);
		}
	}
	
	public static String getDOMAIN() {
		return DOMAIN;
	}

	public static String getSMTP_SERVER() {
		return SMTP_SERVER;
	}

	public static String getSMTP_EMAIL() {
		return SMTP_EMAIL;
	}

	public static String getSMTP_PASSWORD() {
		return SMTP_PASSWORD;
	}

	public static String getRECAPTCHA_SECRET() {
		return RECAPTCHA_SECRET;
	}

	public static String getRECAPTCHA_SITEKEY() {
		return RECAPTCHA_SITEKEY;
	}

	public static String getDISCORD_TOKEN() {
		return DISCORD_TOKEN;
	}

	public static String getDISCORD_ERROR_CHANNEL() {
		return DISCORD_ERROR_CHANNEL;
	}
	
	public static String getDISCORD_NEW_EPISODE_HOOK() {
		return DISCORD_NEW_EPISODE_HOOK;
	}
	
	public static String getDONATE_BUTTON() {
		return DONATE_BUTTON;
	}
	
	public static String getBACKEND_PORT() {
		return BACKEND_PORT;
	}

	private static Map<String,String> readInFilesMap() {
		HashMap<String,String> fileMap = new HashMap<>();
		
		readSnippetsFile("snippets", "accountconfirmed.html", fileMap);
		readSnippetsFile("snippets", "addform.html", fileMap);
		readSnippetsFile("snippets", "adminform.html", fileMap);
		readSnippetsFile("snippets", "announcements.md", fileMap);
		readSnippetsFile("snippets", "changeemailform.html", fileMap);
		readSnippetsFile("snippets", "changepasswordform.html", fileMap);
		readSnippetsFile("snippets", "commentflagform.html", fileMap);
		readSnippetsFile("snippets", "completestory.html", fileMap);
		readSnippetsFile("snippets", "confirmpasswordresetform.html", fileMap);
		readSnippetsFile("snippets", "createaccountform.html", fileMap);
		readSnippetsFile("snippets", "emptygeneric.html", fileMap);
		readSnippetsFile("snippets", "failure.html", fileMap);
		readSnippetsFile("snippets", "faq.md", fileMap);
		readSnippetsFile("snippets", "flagform.html", fileMap);
		readSnippetsFile("snippets", "generic.html", fileMap);
		readSnippetsFile("snippets", "generic_meta.html", fileMap);
		readSnippetsFile("snippets", "genericfooter.html", fileMap);
		readSnippetsFile("snippets", "genericheader.html", fileMap);
		readSnippetsFile("snippets", "loginform.html", fileMap);
		readSnippetsFile("snippets", "modhelp.md", fileMap);
		readSnippetsFile("snippets", "modifyform.html", fileMap);
		readSnippetsFile("snippets", "newrootform.html", fileMap);
		readSnippetsFile("snippets", "outline.html", fileMap);
		readSnippetsFile("snippets", "outlinescroll.html", fileMap);
		readSnippetsFile("snippets", "passwordresetform.html", fileMap);
		readSnippetsFile("snippets", "path.html", fileMap);
		readSnippetsFile("snippets", "popularnav.html", fileMap);
		readSnippetsFile("snippets", "profilepage.html", fileMap);
		readSnippetsFile("snippets", "recents.html", fileMap);
		readSnippetsFile("snippets", "searchform.html", fileMap);
		readSnippetsFile("snippets", "searchhelp.html", fileMap);
		readSnippetsFile("snippets", "sitesettingsform.html", fileMap);
		readSnippetsFile("snippets", "story.html", fileMap);
		readSnippetsFile("snippets", "success.html", fileMap);
		readSnippetsFile("snippets", "useraccount.html", fileMap);
		readSnippetsFile("snippets", "usersearchform.html", fileMap);
		readSnippetsFile("snippets", "verifyaccount.html", fileMap);
		readSnippetsFile("snippets", "welcome.html", fileMap);
		readSnippetsFile("snippets", "stats.html", fileMap);
		readSnippetsFile("snippets", "favoritespage.html", fileMap);
		
		return Collections.unmodifiableMap(fileMap);
	}
	
	private static void readSnippetsFile(String dir, String filename, HashMap<String,String> fileMap) {
		LOGGER.info("Reading " + dir + "/" + filename);
		/*try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(dir + "/" + filename))) { 
			StringBuilder sb = new StringBuilder(); 
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
			fileMap.put(filename, sb.toString());
		}*/
		
		fileMap.put(filename, readRawFileFromJar(dir + "/" + filename)); 
	}
	
	public static String readRawFileFromJar(String filepath) {
		try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath))) { 
			StringBuilder sb = new StringBuilder(); 
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
			return sb.toString();
		}
	}
	
	private static Map<String,String> readInStringsMap() {
		HashMap<String,String> stringsMap = new HashMap<>();
		try (Scanner scan = new Scanner(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("strings.txt")))) {
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				if (line.trim().length() == 0 || line.trim().startsWith("#")) continue;
				if (!line.contains("~")) {
					LOGGER.error("Misformatted strings.txt (uncommented nonempty line with no '~'): " + line);
					System.exit(1);
				}
				String[] arr = line.split("~");
				if (arr.length != 2) {
					LOGGER.error("Misformatted strings.txt (too many '~'s " + line + ")");
					System.exit(2);
				}
				if (stringsMap.put(arr[0], arr[1]) != null) {
					LOGGER.error("strings.txt duplicate key: " + arr[0]);
					System.exit(3);
				}
			}
		}
		return Collections.unmodifiableMap(stringsMap);
	}
		
	public static String getString(String name) {
		String value = stringsTxtMap.get(name);
		if (value == null) value = "";
		return value;
	}
	
	/**
	 * Only use this method if you do not need a FlatUser for anything else (probably only in fb.api.*Stuff.java) 
	 * @param name
	 * @param token
	 * @return
	 */
	public static String getFileWithToken(String name, Cookie token) {
		FlatUser user;
		try {
			user = Accounts.getFlatUser(token);
		} catch (FBLoginException e) {
			user = null;
		}
		return getFile(name, user);
	}
	
	public static String getFile(String name, FlatUser user) {
		
		String account = Accounts.getAccount(user);
		if (InitWebsite.DEV_MODE) account = "<h3>This site is in dev mode.</h3><p>Any changes you make <strong>will</strong> be deleted.</p>" + account;
		return filesMap.get(name)
				.replace("$DONATEBUTTON", Strings.getDONATE_BUTTON())
				.replace("$ACCOUNT", account)
				.replace("$STYLE", themeToCss(user));
	}
	
	public static String themeToCss(FlatUser user) {
		
		final String base1 = 
				".fbepisodebody {\n" + 
				"	width: 90%;\n" + 
				"	text-align: left;\n" + 
				"}\n"
				;
		final String base2 = 
				"@media (min-width: $BIGWIDTHpx)  { \n" + 
				"	.fbepisodebody {\n" + 
				"		width: $WIDTHpx;\n" + 
				"	}\n" + 
				"}\n"
				;
		
		if (user == null) {
			int width = 900;
			return 
					"<style>\n" + 
					base1 + 
					base2
					.replace("$BIGWIDTH", ((int)(width/0.9))+"")
					.replace("$WIDTH", width+"")
					.replace("$THEME", "")
					+"</style>\n"
					;
		}
		
		String theme;
		if (user.theme == null) theme = "";
		else theme = user.theme.css;
		if (theme == null) theme = "";
		
		int width = user.bodyTextWidth;
		
		if (width <= 0) return 
				"<style>\n" + 
				theme + "\n" + 
				base1
				+"</style>\n"
				;
		
		if (width < 99) width = 99;
		
		return 
				"<style>\n" + 
				theme + "\n" + 
				base1 + 
				base2
				.replace("$BIGWIDTH", ((int)(width/0.9))+"")
				.replace("$WIDTH", width+"")
				.replace("$THEME", "")
				+"</style>\n"
				;
	}
		
	public static String getSelectThemes() {
		List<String> list = DB.getThemeNames();
		Collections.sort(list);
		StringBuilder sb = new StringBuilder();
		for (String theme : list) sb.append(String.format("<option value=\"%s\">%s</option>%n", theme, theme));
		return sb.toString();
	}
	
	public static void safeDeleteFileDirectory(String dirPath) {
		File f = new File(dirPath);
		if (f.exists()) {
			if (f.isDirectory()) {
				Path directory = Paths.get(dirPath);

				try {
					Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							Files.delete(file);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
							Files.delete(dir);
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (IOException e) {
					LOGGER.error("Error deleting directory " + dirPath, e);
				}
			} else {
				f.delete();
			}
		}
	}
	
	private Strings() {}

}
