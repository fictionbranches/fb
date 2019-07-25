package fb.util;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.gjerull.etherpad.client.EPLiteClient;

public class Etherpad {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	private static final String API_KEY = "039e067923f66bb6606e71470ee75196257d8eeb35fdeb49a069dfe51b7d2240"; // TODO add etherpad api key to database
	
	public static final String DOMAIN = "pad.localfbtest.carolinaphoenix.net";
	
	private static final EPLiteClient epClient = new EPLiteClient("https://"+DOMAIN, API_KEY); // TODO add etherpad URI to database
	
	/**
	 * Create a new etherpad authorID
	 * @param username fb username (used as etherpad authorMapper)
	 * @param authorName fb author name (used as etherpad author name)
	 * @return new etherpad authorID
	 * @throws EtherpadException
	 */
	public static String createAuthor(String username, String authorName) throws EtherpadException {
		try {
			System.out.println("Creating author for " + username);
			return getFrom(epClient.createAuthorIfNotExistsFor(username, authorName), "authorID");
		} catch (Exception e) {
			throw new EtherpadException(e);
		}
	}
	
	/**
	 * Creates a session to a group pad
	 * @param groupID id of group that owns pad
	 * @param authorID id of author 
	 * @return sessionID for new session
	 * @throws EtherpadException
	 */
	public static String createSession(String groupID, String authorID) throws EtherpadException {
		try {
			long weekFromNow = System.currentTimeMillis() / 1000 + 604800;
			String sessionID = getFrom(epClient.createSession(groupID, authorID, weekFromNow), "sessionID");
			System.out.println("Creating session for " + groupID + " " + authorID + " " + sessionID);
			return sessionID;
		} catch (Exception e) {
			throw new EtherpadException(e);
		}
	}
	
	/**
	 * Creates a pad with the give padID (unique)
	 * @param padID 
	 * @return groupID of group created to own pad
	 * @throws EtherpadException
	 */
	public static String createPad(String padID) throws EtherpadException {
		try {
			String groupID = getFrom(epClient.createGroupIfNotExistsFor(padID), "groupID");
			System.out.println("Creating pad " + padID + " with groupID " + groupID);
			epClient.createGroupPad(groupID, padID);
			return groupID;
		} catch (Exception e) {
			throw new EtherpadException(e);
		}
		
	}
	
	/**
	 * Safely extract a result value from an etherpad api result map
	 * @param m map resulting from api call
	 * @param key name of value to extract
	 * @return resulting value
	 * @throws EtherpadException
	 */
	private static String getFrom(Map<?, ?> m, String key) throws EtherpadException {
		try {
			String result = (String)m.get(key);
			if (result == null || result.length() == 0) throw EtherpadException.badResponse();
			return result;
		} catch (Exception e) {
			LOGGER.error("Etherpad error: " + e.getMessage(), e);
			throw new EtherpadException(e);
		}
	}
	
	public static class EtherpadException extends Exception {
		private static final long serialVersionUID = 2531439648236175456L;
		private EtherpadException(String message) {super(message);}
		private EtherpadException(Throwable cause) {super(cause);}
		private static EtherpadException badResponse(){return new EtherpadException("Bad response from etherpad backend");}
	}
	
}
