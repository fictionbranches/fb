package fb.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import fb.objects.FlatEpisode;

public class Discord {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
		
	public static synchronized void notifyError(String message) {
		if (message.length() > 2000) message = message.substring(0,2000);
		HookMessage form = new HookMessage("FB Errors", message);
		Discord.sendToDiscordHook(Strings.getDISCORD_ERROR_HOOK(), form);
	}
	
	public static void notifyNewEpisode(FlatEpisode ep, FlatEpisode root) {
		StringBuilder sb = new StringBuilder();
		try (Scanner scan = new Scanner(root.link)) {
			while (scan.hasNext()) {
				String next = scan.next();
				if (next.length() > 0) sb.append(next.charAt(0));
			}
		}
		String username = sb + " - " + ep.authorName;
				
		String desc = String.format(
				"By [%s](%s)%n%n", 
				ep.authorName, 
				"https://" + Strings.getDOMAIN() + "/fb/user/" + ep.authorId
			) + ep.body;
		
		if (desc.length() > 500) desc = desc.substring(0,500) + "...";
		
		HookEmbed embed = new HookEmbed(
				ep.title.substring(0,Integer.min(ep.title.length(), 256)), 
				desc, 
				"https://"+Strings.getDOMAIN() + "/fb/story/" + ep.generatedId, 
				0x4e98fc
			);
		
		String avatar = null;
		if (ep.authorAvatar != null && ep.authorAvatar.startsWith("http")) avatar = ep.authorAvatar;
		
		HookForm form = new HookForm(
				username.substring(0,Integer.min(32,username.length())), 
				avatar,
				new HookEmbed[]{embed}
			);
		sendToDiscordHook(Strings.getDISCORD_NEW_EPISODE_HOOK(), form);
	}
	
	private static void sendToDiscordHook(String hookURL, Object data) {
		try {
			final String postData = new Gson().toJson(data);
			final byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
			
			final HttpURLConnection conn = (HttpURLConnection) new URL(hookURL).openConnection();

			conn.setRequestMethod("POST");

			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setRequestProperty("User-Agent", "Fiction Branches Discord Notifier https://fictionbranches.net");

			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			try (Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				while (in.read() >= 0);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}
	
	@SuppressWarnings("unused")
	private static class HookMessage {
		public final String username;
		public final boolean tts = false;
		public final String content;
		public HookMessage(String username, String content) {
			this.username = username;
			this.content = content;
		}
	}
	
	@SuppressWarnings("unused")
	private static class HookForm {
		public final String username;
		public final String avatar_url;
		public final boolean tts = false;
		public final HookEmbed[] embeds;
		public HookForm(String username, String avatar_url, HookEmbed[] embeds) {
			this.username = username;
			this.embeds = embeds;
			this.avatar_url = avatar_url;
		}
	}
	
	@SuppressWarnings("unused")
	private static class HookEmbed {
		public final String title;
		public final String type = "rich";
		public final String description;
		public final String url;
		public final int color;
		public HookEmbed(String title, String description, String url, int color) {
			this.title = title;
			this.description = description;
			this.url = url;
			this.color = color;
		}
		 
	}
	
	private Discord() {}
}