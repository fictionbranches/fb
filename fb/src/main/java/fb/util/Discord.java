package fb.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

public class Discord {
	public static synchronized void notifyError(String message) {
		try {
			if (message.length() > 2000) message = message.substring(0,2000);
			URL url = new URL("https://discordapp.com/api/channels/" + Strings.getDISCORD_ERROR_CHANNEL() + "/messages");
			Map<String, String> jsonMap = new LinkedHashMap<>();
			jsonMap.put("content", message);

			String postData = new Gson().toJson(jsonMap);
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");

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
	public static void notifyHook(String username, String message, String hookURL) {
		try {
			if (username.length() > 32) username = username.substring(0,32);
			if (message.length() > 2000) message = message.substring(0,2000);
			URL url = new URL(hookURL);
			Map<String, String> jsonMap = new LinkedHashMap<>();
			jsonMap.put("content", message);
			jsonMap.put("username", username);

			String postData = new Gson().toJson(jsonMap);
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");
			
			System.out.println(postData);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");

			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			//conn.setRequestProperty("Authorization", "Bot " + Strings.getDISCORD_TOKEN());
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
}
