//package fb.api;
//
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.io.StringWriter;
//import java.net.URISyntaxException;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicLong;
//
//import org.glassfish.grizzly.http.server.ErrorPageGenerator;
//import org.glassfish.grizzly.http.server.Request;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import fb.InitWebsite;
//import fb.util.Discord;
//import fb.util.Strings;
//import fb.util.Text;
//
///**
// * BE CAREFUL EDITING THIS CLASS!!!!!!!!!!!!
// * 
// * Something's with the way errors are handled is very finicky. 
// * Small changes can have weird effects. Be careful.
// */
//public class MyErrorPageGenerator implements ErrorPageGenerator {
//	
//	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
//
//	@Override
//	public String generate(Request request, int status, String reasonPhrase, String description,
//			Throwable exception) {
//				
//		if (exception instanceof URISyntaxException || exception instanceof IllegalArgumentException) {
//			
//			String mess = exception.getMessage();
//			mess = mess.substring(mess.indexOf("http"));
//			
//			StringBuilder sb = new StringBuilder();
//			sb.append("<p><h1>Invalid URL</h1><br/>\n");
//			sb.append("<p>" + Text.escape(mess) + "</p>");
//			return Strings.getFile("emptygeneric.html", null).replace("$EXTRA", sb.toString());
//		}
//		
//		LOGGER.warn("************NEW ERROR PAGE*****************");
//		LOGGER.warn(String.format("Error page URL\t%s", request.getRequestURL()));
//		LOGGER.warn(String.format("Error page request\t%s", request));
//		LOGGER.warn(String.format("Error page status \t%s", status));
//		LOGGER.warn(String.format("Error page reason \t%s", reasonPhrase));
//		LOGGER.warn(String.format("Error page descrip\t%s", description));
//		if (exception != null) LOGGER.warn(String.format("Error page exceptn\t%s", exception.getMessage()));
//		else LOGGER.warn(String.format("Error page exceptn\t%s", "null"));
//		
//		final String requestURL = request.getRequestURL().toString();
//		if (exception != null) new Thread(()->notifyDiscord(requestURL, status, reasonPhrase, description, exception)).start();
//		
//		try (StringWriter sw = new StringWriter()) {
//			 try (PrintWriter pw = new PrintWriter(sw)) {
//				exception.printStackTrace(pw); // NOSONAR this isn't really logging
//			}
//			StringBuilder sb = new StringBuilder();
//			sb.append("<p><h1>You ran into an uncaught exception.</h1><br/>\n");
//			sb.append("Please report the entire contents of this page to Phoenix on our <a href=\"https://discord.gg/eGPxp5A\">Discord server</a><br/></p>\n");
//			sb.append("<code>" + sw + "</code>");
//			return Strings.getFile("emptygeneric.html", null).replace("$EXTRA", sb.toString());
//		} catch (IOException e) {
//			StringBuilder sb = new StringBuilder();
//			sb.append("<p>You ran into an uncaught ecxeption.<br/>\n");
//			sb.append("Please report the entire contents of this page to Phoenix on our <a href=\"https://discord.gg/eGPxp5A\">Discord server</a><br/></p>\n");
//			sb.append("<code>" + e.getMessage() + "</code>");
//			return Strings.getFile("emptygeneric.html", null).replace("$EXTRA", sb.toString());
//		}
//	}
//	
//	private static AtomicLong lastError = new AtomicLong(0l);
//	
//	private static synchronized void notifyDiscord(String requestURL, int status, String reasonPhrase, String description, Throwable exception) {
//		if (System.currentTimeMillis() - lastError.get() < (10000l /*1 minute*/)) {
//			LOGGER.warn("Skipping Discord notification, less than 1 minute since last request", exception);
//			return;
//		}
//		if (InitWebsite.DEV_MODE) {
//			LOGGER.warn("Skipping Discord notification, dev mode", exception);
//			return;
//		}
//		if (Strings.getDISCORD_ERROR_HOOK() == null || Strings.getDISCORD_ERROR_HOOK().length()==0) {
//			LOGGER.warn("Skipping Discord notification, no hook", exception);
//			return;
//		}
//		try {
//			StringBuilder message = new StringBuilder();
//			message.append("Error on page " + requestURL + "\n");
//			message.append("Status: " + status + "\n");
//			if (reasonPhrase != null) message.append("Reason: " + reasonPhrase + "\n");
//			if (description != null) message.append("Description: " + description + "\n");
//			lastError.set(System.currentTimeMillis());
//			Discord.notifyError(message.toString());
//
//			if (exception != null) {
//				List<String> lines = Text.traceToLines(exception);
//				final String header = "```\n";
//				message = new StringBuilder(header);
//				for (String line : lines) {
//					message.append(line + "\n");
//					if (message.length() > 1800) {
//						message.append("```");
//						lastError.set(System.currentTimeMillis());
//						Discord.notifyError(message.toString());
//						message = new StringBuilder(header);
//					}
//				}
//				if (message.length() > header.length()) {
//					message.append("```");
//					lastError.set(System.currentTimeMillis());
//					Discord.notifyError(message.toString());
//				}
//			}
//		} catch (Exception e) {
//			LOGGER.error("Failed to build Discord notification ", e);
//		}
//	}
//
//}