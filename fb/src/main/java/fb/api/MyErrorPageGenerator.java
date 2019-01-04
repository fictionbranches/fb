package fb.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.glassfish.grizzly.http.server.ErrorPageGenerator;
import org.glassfish.grizzly.http.server.Request;

import fb.InitWebsite;
import fb.util.Discord;
import fb.util.Strings;

public class MyErrorPageGenerator implements ErrorPageGenerator {

	@Override
	public String generate(Request request, int status, String reasonPhrase, String description,
			Throwable exception) {
		System.out.println("************NEW ERROR PAGE*****************");
		System.out.printf("Error page URL\t%s%n", request.getRequestURL());
		System.out.printf("Error page request\t%s%n", request);
		System.out.printf("Error page status \t%s%n", status);
		System.out.printf("Error page reason \t%s%n", reasonPhrase);
		System.out.printf("Error page descrip\t%s%n", description);
		if (exception != null) System.out.printf("Error page exceptn\t%s%n", exception.getMessage());
		else System.out.printf("Error page exceptn\t%s%n", "null");
		
		
		if (exception != null) new Thread(()->notifyDiscord(request, status, reasonPhrase, description, exception)).start();
		
		
		try (StringWriter sw = new StringWriter()) {
			 try (PrintWriter pw = new PrintWriter(sw)) {
				exception.printStackTrace(pw);
			}
			StringBuilder sb = new StringBuilder();
			sb.append("<p><h1>You ran into an uncaught exception.</h1><br/>\n");
			sb.append("Please report the entire contents of this page to Phoenix at either our <a href=\"https://discord.gg/eGPxp5A\">Discord server</a> or <a href=/static/irc.html>IRC chat</a><br/></p>\n");
			sb.append("<code>" + sw + "</code>");
			return Strings.getFile("emptygeneric.html", null).replace("$EXTRA", sb.toString());
		} catch (IOException e) {
			StringBuilder sb = new StringBuilder();
			sb.append("<p>You ran into an uncaught ecxeption.<br/>\n");
			sb.append("Please report the entire contents of this page to Phoenix at either our <a href=\"https://discord.gg/eGPxp5A\">Discord server</a> or <a href=/static/irc.html>IRC chat</a><br/></p>\n");
			sb.append("<code>" + e.getMessage() + "</code>");
			return Strings.getFile("emptygeneric.html", null).replace("$EXTRA", sb.toString());
		}
	}
	
	private static AtomicLong lastError = new AtomicLong(0l);
	
	private static synchronized void notifyDiscord(Request request, int status, String reasonPhrase, String description, Throwable exception) {
		if (System.currentTimeMillis() - lastError.get() < (10000l /*1 minute*/)) {
			System.out.println("Skipping Discord notification, less than 1 minute since last request");
			return;
		}
		if (InitWebsite.DEV_MODE) {
			System.out.println("Skipping Discord notification, dev mode");
			return;
		}
		try {
			StringBuilder message = new StringBuilder();
			message.append("Error on page " + request.getRequestURL() + "\n");
			message.append("Status: " + status + "\n");
			if (reasonPhrase != null) message.append("Reason: " + reasonPhrase + "\n");
			if (description != null) message.append("Description: " + description + "\n");
			lastError.set(System.currentTimeMillis());
			Discord.notifyError(message.toString());

			if (exception != null) {
				List<String> lines = Strings.traceToLines(exception);
				final String header = "```\n";
				message = new StringBuilder(header);
				for (String line : lines) {
					message.append(line + "\n");
					if (message.length() > 1800) {
						message.append("```");
						lastError.set(System.currentTimeMillis());
						Discord.notifyError(message.toString());
						message = new StringBuilder(header);
					}
				}
				if (message.length() > header.length()) {
					message.append("```");
					lastError.set(System.currentTimeMillis());
					Discord.notifyError(message.toString());
				}
			}
		} catch (Exception e) {
			System.out.println("Failed to build Discord notification " + e + " " + e.getMessage());
			e.printStackTrace();
		}
	}

}
