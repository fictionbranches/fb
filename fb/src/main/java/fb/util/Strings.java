package fb.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Cookie;

import org.apache.commons.text.StringEscapeUtils;
import org.hibernate.Session;

import fb.Accounts;
import fb.Accounts.FBLoginException;
import fb.DB;
import fb.InitWebsite;
import fb.db.DBSiteSetting;
import fb.objects.FlatUser;

public class Strings {
	
	/**
	 * use for any random numbers needed anywhere
	 */
	public static final Random r; 
	
	private static final Map<String,String> files;
	private static final Map<String,String> strings;
	private static final Map<String,String> styles; // <HTML name, css file name (without .css)>
	
	private static final Object logLock;
	
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
		logLock = new Object();
		r = new Random();
		
		refreshSiteSettings();

		files = readInFilesMap();
		styles = readInStylesMap();
		strings = readInStringsMap();
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
		
		readSnippetsFile("accountconfirmed.html", fileMap);
		readSnippetsFile("addform.html", fileMap);
		readSnippetsFile("adminform.html", fileMap);
		readSnippetsFile("announcements.md", fileMap);
		readSnippetsFile("changeauthorform.html", fileMap);
		readSnippetsFile("changeavatarform.html", fileMap);
		readSnippetsFile("changebioform.html", fileMap);
		readSnippetsFile("changeemailform.html", fileMap);
		readSnippetsFile("changepasswordform.html", fileMap);
		readSnippetsFile("changethemeform.html", fileMap);
		readSnippetsFile("commentform.html", fileMap);
		readSnippetsFile("commentflagform.html", fileMap);
		readSnippetsFile("completestory.html", fileMap);
		readSnippetsFile("confirmpasswordresetform.html", fileMap);
		readSnippetsFile("createaccountform.html", fileMap);
		readSnippetsFile("emptygeneric.html", fileMap);
		readSnippetsFile("failure.html", fileMap);
		readSnippetsFile("faq.md", fileMap);
		readSnippetsFile("flagform.html", fileMap);
		readSnippetsFile("generic.html", fileMap);
		readSnippetsFile("genericfooter.html", fileMap);
		readSnippetsFile("genericheader.html", fileMap);
		readSnippetsFile("loginform.html", fileMap);
		readSnippetsFile("modhelp.md", fileMap);
		readSnippetsFile("modifyform.html", fileMap);
		readSnippetsFile("newrootform.html", fileMap);
		readSnippetsFile("outline.html", fileMap);
		readSnippetsFile("outlinescroll.html", fileMap);
		readSnippetsFile("passwordresetform.html", fileMap);
		readSnippetsFile("path.html", fileMap);
		readSnippetsFile("popularnav.html", fileMap);
		readSnippetsFile("profilepage.html", fileMap);
		readSnippetsFile("recents.html", fileMap);
		readSnippetsFile("searchform.html", fileMap);
		readSnippetsFile("searchhelp.html", fileMap);
		readSnippetsFile("sitesettingsform.html", fileMap);
		readSnippetsFile("story.html", fileMap);
		readSnippetsFile("success.html", fileMap);
		readSnippetsFile("useraccount.html", fileMap);
		readSnippetsFile("usersearchform.html", fileMap);
		readSnippetsFile("verifyaccount.html", fileMap);
		readSnippetsFile("welcome.html", fileMap);
		
		return Collections.unmodifiableMap(fileMap);
	}
	
	private static void readSnippetsFile(String filename, HashMap<String,String> fileMap) {
		System.out.println("Reading snippets/" + filename);
		try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream("snippets/" + filename))) { 
			StringBuilder sb = new StringBuilder(); 
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
			fileMap.put(filename, sb.toString());
		}
	}
	
	private static Map<String,String> readInStylesMap() {
		HashMap<String,String> stylesMap = new HashMap<>();
		try (Scanner scan = new Scanner(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("styles.txt")))) { 
			Strings.log("Updating styles");
			while (scan.hasNext()) {
				String key = scan.nextLine();
				String val = scan.nextLine();
				stylesMap.put(key, val);
			}
		}
		return Collections.unmodifiableMap(stylesMap);
	}
	
	private static Map<String,String> readInStringsMap() {
		HashMap<String,String> stringsMap = new HashMap<>();
		try (Scanner scan = new Scanner(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("strings.txt")))) {
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				if (line.trim().startsWith("#")) continue;
				else if (line.trim().length() == 0) continue;
				if (!line.contains("~")) {
					Strings.log("Misformatted strings.txt (uncommented nonempty line with no '~'): " + line);
					System.exit(1);
				}
				String[] arr = line.split("~");
				if (arr.length != 2) {
					Strings.log("Misformatted strings.txt (too many '~'s " + line + ")");
					System.exit(2);
				}
				if (stringsMap.put(arr[0], arr[1]) != null) {
					Strings.log("strings.txt duplicate key: " + arr[0]);
					System.exit(3);
				}
			}
		}
		return Collections.unmodifiableMap(stringsMap);
	}
	
	public static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
	
	public static String getString(String name) {
		String value = strings.get(name);
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
		
		String theme = null;
		if (user != null) theme = styles.get(user.theme);
		if (theme == null) theme = "default";
		String account = Accounts.getAccount(user);
		if (InitWebsite.DEV_MODE) account = "<h3>This site is in dev mode.</h3><p>Any changes you make <em><string>will</strong></em> be deleted.</p>" + account;
		return files.get(name)
				.replace("$DONATEBUTTON", Strings.getDONATE_BUTTON())
				.replace("$ACCOUNT", account)
				.replace("$STYLE", theme);
	}
	
	public static String readTextFile(String path) {
		return readTextFile(new File(path));
	}
	
	public static String readTextFile(File file) {
		StringBuilder sb = new StringBuilder();
		try (Scanner scan = new Scanner(file)) {
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
		} catch (FileNotFoundException e) {
			Strings.log(e);
		}
		return sb.toString();
	}
	
	/**
	 * Prepends message with the current date, and writes it to stdout
	 * @param message
	 */
	public static void log(String message) {
		synchronized (logLock) {
			Calendar c = Calendar.getInstance();
			int y = c.get(Calendar.YEAR);
			int mo = c.get(Calendar.MONTH);
			int d = c.get(Calendar.DAY_OF_MONTH);
			int h = c.get(Calendar.HOUR_OF_DAY);
			int mi = c.get(Calendar.MINUTE);
			int s = c.get(Calendar.SECOND);
			try (BufferedWriter out = new BufferedWriter(new FileWriter(InitWebsite.BASE_DIR + "/fblog.txt", true))) {
				out.write(String.format("%04d-%02d-%02d %02d:%02d:%02d %s", y, mo, d, h, mi, s, message));
				out.newLine();
			} catch (IOException e) {
				System.err.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, "Could not open log file");
			} finally {
				System.out.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, message);
			}
		}
	}
		
	public static void log(Exception e) {
		List<String> lines = traceToLines(e);
		if (lines.size() > 0) synchronized (logLock) {
			for (String line : lines) log(line);
		}
	}
	
	public static List<String> traceToLines(Throwable e) {
		if (e==null) return Stream.of("null").collect(Collectors.toList());
		try (StringWriter sw = new StringWriter()) {
			try (PrintWriter writer = new PrintWriter(sw)) {
				e.printStackTrace(writer);
			}
			ArrayList<String> lines = new ArrayList<>();
			try (Scanner s = new Scanner(sw.getBuffer().toString())) {
				while (s.hasNext()) lines.add(s.nextLine());
			}
			return lines;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			log(e.getMessage());
			log("Trouble logging previous exception's stack trace: " + ioe.getMessage());
			return new ArrayList<String>();
		}
	}
	
	public static String getSelectThemes() {
		ArrayList<String> list = new ArrayList<>(styles.keySet());
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
					Strings.log("Error deleting directory " + dirPath);
					e.printStackTrace();
				}
			} else f.delete();
		}
	}

}
