package fb.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import com.google.gson.Gson;

import fb.objects.FlatEpisode;

public class Discord {
	public static synchronized void notifyError(String message) {
		try {
			if (message.length() > 2000) message = message.substring(0,2000);
			URL url = new URL("https://discordapp.com/api/channels/" + Strings.getDISCORD_ERROR_CHANNEL() + "/messages");
			Map<String, String> jsonMap = new LinkedHashMap<>();
			jsonMap.put("content", message);

			String postData = new Gson().toJson(jsonMap);
			byte[] postDataBytes = postData.getBytes("UTF-8");

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");

			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setRequestProperty("Authorization", "Bot " + Strings.getDISCORD_TOKEN());
			conn.setRequestProperty("User-Agent", "Fiction Branches Discord Notifier https://fictionbranches.net");

			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder result = new StringBuilder();
			for (int c; (c = in.read()) >= 0;) result.append((char) c);

			System.out.println("Discord response: " + result);

		} catch (Exception e) {
			System.out.println("Failed to notify Discord " + e + " " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void notifyHook(FlatEpisode ep, FlatEpisode root) {
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
		
		try {
			String postData = new Gson().toJson(form);
			byte[] postDataBytes = postData.getBytes("UTF-8");
			
			URL url = new URL(Strings.getDISCORD_NEW_EPISODE_HOOK());

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");

			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			conn.setRequestProperty("User-Agent", "Fiction Branches Discord Notifier https://fictionbranches.net");

			conn.setDoOutput(true);
			conn.getOutputStream().write(postDataBytes);

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder result = new StringBuilder();
			for (int c; (c = in.read()) >= 0;) result.append((char) c);

			System.out.println("Discord response: " + result);

		} catch (Exception e) {
			System.out.println("Failed to notify Discord " + e + " " + e.getMessage());
			e.printStackTrace();
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
