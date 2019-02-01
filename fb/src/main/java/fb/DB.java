package fb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.StreamingOutput;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.util.automaton.RegExp;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.springframework.security.crypto.bcrypt.BCrypt;

import fb.db.DBAnnouncement;
import fb.db.DBAnnouncementView;
import fb.db.DBArchiveToken;
import fb.db.DBComment;
import fb.db.DBEmailChange;
import fb.db.DBEpisode;
import fb.db.DBEpisodeView;
import fb.db.DBFlaggedComment;
import fb.db.DBFlaggedEpisode;
import fb.db.DBModEpisode;
import fb.db.DBNotification;
import fb.db.DBPasswordReset;
import fb.db.DBPotentialUser;
import fb.db.DBSiteSetting;
import fb.db.DBTheme;
import fb.db.DBUpvote;
import fb.db.DBUser;
import fb.objects.Announcement;
import fb.objects.ArchiveToken;
import fb.objects.Comment;
import fb.objects.Episode;
import fb.objects.EpisodeWithChildren;
import fb.objects.FlaggedComment;
import fb.objects.FlaggedEpisode;
import fb.objects.FlatEpisode;
import fb.objects.FlatUser;
import fb.objects.ModEpisode;
import fb.objects.Notification;
import fb.objects.Theme;
import fb.objects.User;
import fb.util.Discord;
import fb.util.Strings;

public class DB {
	public static final String ROOT_ID = "fbadministrator1";
	public static final String MESSENGER_ID = "fictionbranches";
	
	static SessionFactory mySessionFactory;
	private static Object epLock = new Object(); // exists because DBEpisode.id is generated in java
	private static Object userLock = new Object(); // exists because multiple users could submit for the same username simultaneously 
	private static Object ecLock = new Object(); // exists because token is generated in java
	private static Object puLock = new Object(); // exists because token is generated in java
	private static Object dumpLock = new Object(); // exists to prevent multiple threads from overwriting files
	private static Object prLock = new Object(); // exists because token is generated in java
	private static Object archiveTokenLock = new Object(); // exists because token is generated in java
	private static ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock(); // used to ensure no sessions are open when sessionfactory is closed
	private static final char EP_PREFIX = 'A';
	private static final char EP_INFIX = 'B';
	private static final int STREAM_SIZE = 1000;
	static {
		synchronized (epLock) {
		synchronized (userLock) { synchronized (ecLock) { synchronized (puLock) { synchronized (dumpLock) {
			mySessionFactory = newSessionFactory();
			Strings.log("Database success");
		}
		} } } } 
	}
	
	private static SessionFactory newSessionFactory() {
		Configuration configuration = new Configuration();//.configure();
		String dbSettingsFilename = InitWebsite.BASE_DIR + "/dbsettings.txt";
		File dbSettingsFile = new File(dbSettingsFilename);
		String connectionURL = "";
		String connectionUsername = "";
		String connectionPassword = "";
		if (dbSettingsFile.exists() && dbSettingsFile.isFile()) {
			try (Scanner scan = new Scanner(dbSettingsFile)) {
				if (scan.hasNextLine()) connectionURL = scan.nextLine().trim();
				else throw new RuntimeException(dbSettingsFilename + " does not contain connection URL");
				if (scan.hasNextLine()) connectionUsername = scan.nextLine().trim();
				if (scan.hasNextLine()) connectionPassword = scan.nextLine().trim();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
				throw new RuntimeException(e);
			}
		} else {
			RuntimeException e = new RuntimeException(dbSettingsFilename + " not found");
			e.printStackTrace();
			System.exit(1);
			throw e;
		}
		
		configuration.setProperty("hibernate.search.default.indexBase", InitWebsite.BASE_DIR + "/search-indexes");
		configuration.setProperty("hibernate.search.default.directory_provider", "filesystem");

		configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
		configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
		configuration.setProperty("hibernate.connection.url", "jdbc:postgresql://" + connectionURL);
		configuration.setProperty("hibernate.c3p0.min_size", "1");
		configuration.setProperty("hibernate.c3p0.max_size", "3");
		configuration.setProperty("hibernate.c3p0.timeout", "1800");

		configuration.setProperty("hibernate.connection.username", connectionUsername);
		configuration.setProperty("hibernate.connection.password", connectionPassword);
		configuration.setProperty("hibernate.show_sql", "false");
		configuration.setProperty("hibernate.hbm2ddl.auto", "update");
		configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
								
		configuration.addAnnotatedClass(DBEpisode.class);
		configuration.addAnnotatedClass(DBUser.class);
		configuration.addAnnotatedClass(DBFlaggedEpisode.class);
		configuration.addAnnotatedClass(DBModEpisode.class);
		configuration.addAnnotatedClass(DBEmailChange.class);
		configuration.addAnnotatedClass(DBPotentialUser.class);
		configuration.addAnnotatedClass(DBPasswordReset.class);
		configuration.addAnnotatedClass(DBFlaggedEpisode.class);
		configuration.addAnnotatedClass(DBEpisodeView.class);
		configuration.addAnnotatedClass(DBUpvote.class);
		configuration.addAnnotatedClass(DBComment.class);
		configuration.addAnnotatedClass(DBFlaggedComment.class);
		configuration.addAnnotatedClass(DBArchiveToken.class);
		configuration.addAnnotatedClass(DBSiteSetting.class);
		configuration.addAnnotatedClass(DBAnnouncement.class);
		configuration.addAnnotatedClass(DBAnnouncementView.class);
		configuration.addAnnotatedClass(DBNotification.class);
		configuration.addAnnotatedClass(DBTheme.class);

		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties());
		try {
			return configuration.buildSessionFactory(builder.build());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException(e);
		}
	}
	
	public static void closeSessionFactory() {
		try {
			Strings.log("Trying to close Session factory");
			sessionLock.writeLock().lock();
			Strings.log("Closing Session factory");
			mySessionFactory.close();
		} finally {
			sessionLock.writeLock().unlock();
			Strings.log("Session closed");
		}
	}
	
	public static void restartSessionFactory() {
		try {
			Strings.log("Trying to restart Session factory");
			sessionLock.writeLock().lock();
			Strings.log("Closing Session factory");
			mySessionFactory.close();
			Strings.log("Opening Session factory");
			mySessionFactory = newSessionFactory();
		} finally {
			sessionLock.writeLock().unlock();
			Strings.log("Session restarted");
		}	
	}
	
	public static Session openSession() {
		sessionLock.readLock().lock();
		return mySessionFactory.openSession();
	}
	
	public static void closeSession(Session session) {
		try {
			if (session.isOpen())
				session.close();
		} finally {
			sessionLock.readLock().unlock();
		}
	}
	
	public static class DBException extends Exception {
		/** */
		private static final long serialVersionUID = -1610662405195508706L;

		public DBException(String message) {
			super(message);
		}

		public DBException(Exception e) {
			super(e);
		}
	}
	
	/**
	 * Thrown to break a stream().forEach() loop
	 */
	public static class BreakException extends RuntimeException {
		
		private static final long serialVersionUID = -1610662405195508706L;

		public BreakException(String message) {
			super(message);
		}

		public BreakException(Exception e) {
			super(e);
		}
	}
	
	/**
	 * Thrown when different exceptions need to be handled differently, during pasword resets
	 */
	public static class PasswordResetException extends Exception {
		/** */
		private static final long serialVersionUID = -1610662405195508706L;

		public PasswordResetException(String message) {
			super(message);
		}

		public PasswordResetException(Exception e) {
			super(e);
		}
	}
	
	public static String mapToId(String map) {
		return EP_PREFIX + map.replace('-', EP_INFIX);
	}
	
	public static String idToMap(String id) {
		return id.substring(1, id.length()).replace(EP_INFIX,'-');
	}
	
	/**
	 * 
	 * @return {flags, mods, commentflags}
	 */
	public static int[] queueSizes() {
		Session session = openSession();
		try {
			int flags = session.createQuery("select count(*) from DBFlaggedEpisode", Long.class).uniqueResult().intValue();
			int mods = session.createQuery("select count(*) from DBModEpisode", Long.class).uniqueResult().intValue();
			int comments = session.createQuery("select count(*) from DBFlaggedComment", Long.class).uniqueResult().intValue();
			return new int[]{flags, mods, comments};
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param id 1-2-3
	 * @return null if episodes does not exist
	 */
	static DBEpisode getEpById(Session session, String id) {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
		Root<DBEpisode> root = query.from(DBEpisode.class);
		query.select(root).where(cb.equal(root.get("id"), mapToId(id)));
		DBEpisode result = session.createQuery(query).uniqueResult();
		return result;
	}
	
	/**
	 * Returns null if id does not exist
	 * @param id
	 * @return
	 */
	static DBUser getUserById(Session session, String id) {
		id = id.toLowerCase();
		DBUser result = session.get(DBUser.class, id);
		return result;
	}
	
	/**
	 * Use an already-open session to get user by email
	 * 
	 * Only use this during in-progress DB operations (within DB and InitDB)
	 * @param session
	 * @param email
	 * @return
	 */
	static DBUser getUserByEmail(Session session, String email) {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class);
		Root<DBUser> root = query.from(DBUser.class);
		query.select(root).where(cb.equal(root.get("email"), email));
		DBUser result = session.createQuery(query).uniqueResult();
		return result;
	}
	
	public static FlatUser getFlatUserByEmail(String email) throws DBException {
		Session session = openSession();
		try {	
			DBUser result = DB.getUserByEmail(session, email);
			if (result == null) throw new DBException("Email not found: " + email);
			else return new FlatUser(result);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Adds an episode to the story
	 * 
	 * This method checks that the parent episode and author exist, and that the new child id will not be longer than the allowed 4096 characters
	 * 
	 * This method DOES NOT check that link/title/body are within acceptable limits
	 * 
	 * @param parentId id of parent episode
	 * @param title title of new episode
	 * @param body body of new episode
	 * @param author author of new episode
	 * @return generatedid of newly added episode
	 * @throws DBException if parent ep or author does not exist, or if new keystring is too long
	 */
	public static Long addEp(long parentId, String link, String title, String body, String authorId, Date date) throws DBException {
		synchronized (epLock) {
		Session session = openSession();
		try {
			DBEpisode parent = session.get(DBEpisode.class, parentId);
			//DBEpisode parent = getEpById(session, parentId);
			DBUser author = getUserById(session, authorId);

			if (parent == null) throw new DBException("Not found: " + parentId);
			if (author == null) throw new DBException("Author does not exist");

			DBEpisode child;
			child = new DBEpisode();
			
			child.setTitle(title);
			child.setLink(link);
			child.setBody(body);
			child.setAuthor(author);
			child.setParent(parent);
			child.setDate(date);
			child.setChildCount(1);
			child.setEditDate(date);
			child.setEditor(author);
			
			Long childId;
			try {
				session.beginTransaction();
				childId = (Long) session.save(child);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
			/*String oldChildId;
			{
				List<DBEpisode> children = session.createQuery("from DBEpisode ep where ep.parent.generatedId=" + parent.getGeneratedId(), DBEpisode.class).list();
				ArrayList<String> list = new ArrayList<>();
				//for (DBEpisode ep : parent.getChildren()) list.add(ep.getMap());
				for (DBEpisode ep : children) list.add(ep.getMap());
				if (list.size() == 0) {
					oldChildId = parent.getMap() + "-1";
				} else {
					Collections.sort(list, DB.keyStringComparator);
					int[] arr = DB.keyToArr(list.get(list.size()-1));
					oldChildId = parent.getMap() + "-" + (arr[arr.length-1]+1);
				}
			}
			
			child.setMap(oldChildId);*/
			
			child.setNewMap(parent.getNewMap() + DB.EP_INFIX + childId);
			child.setDepth(parent.getDepth()+1);
			
			
		
			String updateCounts;{
					StringBuilder sb = new StringBuilder("update DBEpisode ep set ep.childCount=(ep.childCount+1) where ");
					for (Long pathId : DB.newMapToIdList(parent.getNewMap())) {
						sb.append("ep.generatedId=" + pathId + " or ");
					}
					updateCounts = sb.substring(0, sb.length() - 4);
				}
			
			boolean sendSiteNotification = false;
			boolean sendMailNotification = false;
			
			if (!parent.getAuthor().getId().equals(child.getAuthor().getId())) { // only sent notification if users are different
				sendSiteNotification = parent.getAuthor().isChildSite();
				sendMailNotification = parent.getAuthor().isChildMail();
			}
			
			try {
				session.beginTransaction();
				session.merge(child);
				session.createQuery(updateCounts).executeUpdate();
				session.merge(author);
				
				
				if (sendSiteNotification) {
					DBNotification note = new DBNotification();
					note.setDate(new Date());
					note.setRead(false);
					note.setUser(parent.getAuthor());
					note.setBody("<a href=\"/fb/user/" + child.getAuthor().getId() + "\">" + Strings.escape(child.getAuthor().getAuthor()) + "</a> wrote a <a href=\"/fb/get/" + child.getMap() + "\">new child episode</a> of <a href=/fb/get/" + parent.getMap() +">" + Strings.escape(parent.getTitle()) + "</a>");
					session.save(note);
				}
				if (sendMailNotification) new Thread(()->{
					Accounts.sendEmail(parent.getAuthor().getEmail(), "Someone added a new child to your episode", "<a href=\"https://"+Strings.getDOMAIN()+"/fb/user/" + child.getAuthor().getId() + "\">" + Strings.escape(child.getAuthor().getAuthor()) + "</a> wrote a <a href=\"https://"+Strings.getDOMAIN()+"/fb/get/" + child.getMap() + "\">new child episode</a> of <a href=https://"+Strings.getDOMAIN()+"/fb/get/" + parent.getMap() +">" + Strings.escape(parent.getTitle()) + "</a>");
				}).start();
				
				session.getTransaction().commit();
				
				
				
				
				if (Strings.getDISCORD_NEW_EPISODE_HOOK().length() > 0) {
					String rootId = Integer.toString(DB.keyToArr(oldChildId)[0]);
					DBEpisode root = DB.getEpById(session, rootId);
					final String rootTitle = root.getLink();// + " - " + author.getAuthor();
					final String authorName = author.getAuthor();
					final String epId = child.getMap();
					new Thread(()->{
						StringBuilder sb = new StringBuilder();
						try (Scanner scan = new Scanner(rootTitle)) {
							while (scan.hasNext()) {
								String next = scan.next();
								if (next.length() > 0) sb.append(next.charAt(0));
							}
						}
						String username = sb + " - " + authorName;
						Discord.notifyHook(username, "https://" + Strings.getDOMAIN() + "/fb/get/" + epId, Strings.getDISCORD_NEW_EPISODE_HOOK());
					}).start();
				}
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			Strings.log(String.format("New: <%s> %s %s", author, title, childId));
			return childId;
		} finally {
			closeSession(session);
		}
		}
	}
	
	public static long addArchiveEp(long parentId, String link, String title, String body, String authorName, Date date) throws DBException {
		synchronized (epLock) {
		Session session = openSession();
		try {
			//DBEpisode parent = getEpById(session, oldParentId);
			DBEpisode parent = session.get(DBEpisode.class, parentId);
			DBUser author = null;
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class);
				Root<DBUser> root = query.from(DBUser.class);
				query.select(root).where(cb.and(cb.like(root.get("id"), "archive%")), cb.equal(root.get("author"), authorName));
				author = session.createQuery(query).setMaxResults(1).uniqueResult();
			}
			boolean newAuthor = false;
			if (author == null) {
				newAuthor = true;
				author = new DBUser();
				author.setAuthor(authorName);
				author.setDate(date);
				author.setEmail(null);
				do {
					StringBuilder sb = new StringBuilder();
					for (int i=0; i<16; ++i) sb.append(Strings.r.nextInt(10));
					author.setId("archive" + sb);
				} while (session.get(DBUser.class, author.getId()) != null);
				author.setLevel((byte)1);
				author.setPassword("disabled");
			}

			if (parent == null) throw new DBException("Parent not found: " + parentId);

			DBEpisode child;
			child = new DBEpisode();
			child.setTitle(title);
			child.setLink(link);
			child.setBody(body);
			child.setAuthor(author);
			child.setParent(parent);
			child.setDate(date);
			child.setChildCount(1);
			child.setEditDate(date);
			child.setEditor(author);
			child.setDepth(parent.getDepth()+1);
			
			Long childId;
			try {
				session.beginTransaction();
				childId = (Long) session.save(child);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
			/*String oldChildId;
			{
				List<DBEpisode> children = session.createQuery("from DBEpisode ep where ep.parent.generatedId=" + parent.getGeneratedId(), DBEpisode.class).list();
				ArrayList<String> list = new ArrayList<>();
				for (DBEpisode ep : children) list.add(ep.getMap());
				if (list.size() == 0) {
					oldChildId = parent.getMap() + "-1";
				} else {
					Collections.sort(list, DB.keyStringComparator);
					int[] arr = DB.keyToArr(list.get(list.size()-1));
					oldChildId = parent.getMap() + "-" + (arr[arr.length-1]+1);
				}
			}*/
			
			child.setNewMap(parent.getNewMap() + EP_INFIX + "" + childId);
			

			
			StringBuilder sb = new StringBuilder("update DBEpisode ep set ep.childCount=(ep.childCount+1) where ");

			for (Long pathId : DB.newMapToIdList(parent.getNewMap())) {
				sb.append("ep.generatedId=" + pathId + " or ");
			}
			String updateCounts = sb.substring(0, sb.length() - 4);
			
			try {
				session.beginTransaction();
				session.save(child);
				if (newAuthor) session.save(author);
				else session.merge(author);
				session.createQuery(updateCounts).executeUpdate();
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				e.printStackTrace();
				throw new DBException("Database error");
			}
			Strings.log(String.format("New: <%s> %s %s", author, title, child.getGeneratedId()));
			return childId;
		} finally {
			closeSession(session);
		}
		}
	}
	
	/**
	 * Get an episode, while checking if it has children
	 * @param id
	 * @return FlatEpisode if episode has no children
	 * @throws DBException if episode has children, or does not exist
	 */
	public static FlatEpisode epHasChildren(Long generatedId) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			if (session.createQuery("select count(*) from DBEpisode ep where ep.parent.generatedId=" + ep.getGeneratedId(), Long.class).uniqueResult() > 0l) throw new DBException("You may not delete an episode that has children.");
			return new FlatEpisode(ep);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param id
	 * @throws DBException if episodes does not exist, or has children
	 */
	public static void deleteEp(long generatedId, String username) throws DBException {
		synchronized(epLock) {
			Session session = openSession();
			try {				
				//DBEpisode ep = DB.getEpById(session, id);
				DBEpisode ep = session.get(DBEpisode.class, generatedId);
				if (ep == null) throw new DBException("Not found: " + generatedId);
				if (session.createQuery("select count(*) from DBEpisode ep where ep.parent.generatedId=" + ep.getGeneratedId(), Long.class).uniqueResult() > 0l) throw new DBException("Episode " + generatedId + " has children");
				
				//long generatedId = ep.getGeneratedId();
				
				String parentMap = ep.getParent().getNewMap();
				
				DBUser actor = DB.getUserById(session, username);
				if (actor == null || (actor.getLevel() < 10 && !actor.getId().equals(ep.getAuthor().getId()))) throw new DBException("You are not authorized to delete this episode.");
				
				try {
					session.beginTransaction();

					HashSet<DBUser> mergeUsers = new HashSet<>();
					HashSet<DBFlaggedEpisode> deleteFlags = new HashSet<>();

					mergeUsers.add(ep.getAuthor());
					ep.setAuthor(null);

					mergeUsers.add(ep.getEditor());
					ep.setEditor(null);
					
					/*for (DBFlaggedEpisode flag : session.createQuery("from DBFlaggedEpisode flag where flag.episode.generatedId=" + generatedId, DBFlaggedEpisode.class).list()) {
						mergeUsers.add(flag.getUser());
						flag.setUser(null);
						flag.setEpisode(null);
						deleteFlags.add(flag);
					}*/
					
					session.createQuery("delete DBFlaggedEpisode flag where flag.episode.generatedId=" + generatedId).executeUpdate();
					session.createQuery("delete DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					
					//session.createQuery("delete DBFlaggedComment fc where fc.comment.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createNativeQuery(
							"delete from fbflaggedcomments "
							+ "using fbcomments, fbepisodes "
							+ "where fbflaggedcomments.comment_id=fbcomments.id and fbcomments.episode_generatedid=fbepisodes.generatedid and "
							+ "fbepisodes.generatedid=" + ep.getGeneratedId() + ";").executeUpdate();
					
					session.createQuery("delete DBComment co where co.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
										
					DBModEpisode mod = ep.getMod();
					
					if (mod != null) {
						ep.setMod(null);
						mod.setEpisode(null);
						session.delete(mod);
					}
					for (DBUser user : mergeUsers) session.merge(user);
					for (DBFlaggedEpisode flag : deleteFlags) session.delete(flag);
					session.delete(ep);
					
					StringBuilder sb = new StringBuilder("update DBEpisode ep set ep.childCount=(ep.childCount-1) where "); 

					for (long pathId : DB.newMapToIdList(parentMap)) {
						sb.append("ep.generatedId=" + pathId + " or ");
					}
					String updateCounts = sb.substring(0, sb.length() - 4);
					session.createQuery(updateCounts).executeUpdate();
					
					session.getTransaction().commit();
					
				} catch (Exception e) {
					session.getTransaction().rollback();
					e.printStackTrace();
					throw new DBException("Database error");
				}
			} finally {
				closeSession(session);
			}
		}
	}
	
	/**
	 * Adds a new root episode to the site
	 * 
	 * This method checks that the author exists, and that the new child id will not be longer than the allowed 4096 characters
	 * 
	 * This method DOES NOT check that link/title/body are within acceptable limits
	 * 
	 * @param title title of new episode
	 * @param body body of new episode
	 * @param author author of new episode
	 * @return child DBEpisode object
	 * @throws DBException if parent ep or author does not exist, or if new keystring is too long
	 */
	public static long addRootEp(String link, String title, String body, String authorId, Date date) throws DBException {
		synchronized (epLock) {
		Session session = openSession();
		try {
			DBUser author = getUserById(session, authorId);
			
			List<DBEpisode> roots = getRoots(session);

			if (author == null) throw new DBException("Author does not exist");

			DBEpisode child = new DBEpisode();
			
			//String childId = "" + (Integer.parseInt(roots.get(roots.size()-1).getMap()) + 1);

			//child.setMap(childId);
			child.setDepth(1);
			
			child.setTitle(title);
			child.setLink(link);
			child.setBody(body);
			child.setAuthor(author);
			child.setParent(null);
			child.setDate(date);
			child.setChildCount(1);
			child.setEditDate(date);
			child.setEditor(author);
			
			Long childId;
			try {
				session.beginTransaction();
				childId = (Long) session.save(child);
				session.merge(author);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
			child.setNewMap(EP_PREFIX + "" + childId);
		
			try {
				session.beginTransaction();
				session.merge(child);
				session.getTransaction().commit();
				Story.rootNames.put(childId+"", link);
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			Strings.log(String.format("New: <%s> %s %s", author, title, childId+""));
			//return new FlatEpisode(child);
			return childId;
		} finally {
			closeSession(session);
		}
		}
	}
	
	public static List<FlatUser> getStaff() {
		Session session = openSession();
		try {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class);
			Root<DBUser> root = query.from(DBUser.class);
			
			Predicate levelPred = cb.greaterThan(root.get("level"), 1);
			Predicate notRootPred = cb.notEqual(root.get("id"), DB.ROOT_ID);
			Predicate combPred = cb.and(levelPred, notRootPred);
			query.select(root).where(combPred).orderBy(cb.asc(root.get("author")));
			
			return session.createQuery(query).stream().map(x->new FlatUser(x)).collect(Collectors.toList());
			
			/*List<DBUser> result = session.createQuery(query).list();
			FlatUser[] ret = new FlatUser[result.size()];
			for (int i=0; i<result.size(); ++i) ret[i] = new FlatUser(result.get(i));
			return ret;*/
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Modifies an episode of the story
	 * @param id id of episode
	 * @param title new title of new episode
	 * @param body new body of new episode
	 * @param author new author of new episode
	 * @throws DBException if id not found
	 */
	public static void modifyEp(long generatedId, String link, String title, String body, String editorId) throws DBException {
		Session session = openSession();
		try {
			session.beginTransaction();
			DB.modifyEp(session, generatedId, link, title, body, editorId);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			Strings.log(String.format("Database error modifying: %s", generatedId+""));
			throw new DBException("Database error");
		} finally {
			closeSession(session);
		}
	}
	
	private static void modifyEp(Session session, long generatedId, String link, String title, String body, String editorId) throws DBException {
		// DBEpisode ep = getEpById(session, id);
		DBEpisode ep = session.get(DBEpisode.class, generatedId);
		if (ep == null) throw new DBException("Not found: " + generatedId);
		DBUser editor = getUserById(session, editorId);
		if (editor == null) throw new DBException("Editor not found: " + editorId);
		DBUser oldEditor = ep.getEditor();
		ep.setTitle(title);
		ep.setLink(link);
		ep.setBody(body);
		ep.setEditDate(new Date());
		ep.setEditor(editor);

		session.merge(ep);
		session.merge(oldEditor);
		session.merge(editor);

		Strings.log(String.format("Modified: <%s> %s", title, generatedId));
	}
		
	public static void newEpisodeMod(long generatedId, String link, String title, String body) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			DBModEpisode newMod = new DBModEpisode();
			newMod.setBody(body);
			newMod.setDate(new Date());
			newMod.setEpisode(ep);
			newMod.setLink(link);
			newMod.setTitle(title);
			ep.setMod(newMod);
			try {
				session.beginTransaction();
				session.save(newMod);
				session.merge(ep);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				Strings.log(e);
				Strings.log(String.format("Database error submitting new modify: %s", generatedId+""));
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static final String MOD_KEYWORD = "MOD";
	
	/**
	 * If specified episode already has a mod request submitted, return -2.
	 * Else if a child episodes is not owned by owner of the specified episode (or if no children exist), return child's id.
	 * Else return -1
	 * @param id id of episode
	 * @return
	 * @throws DBException if episode does not exist
	 */
	public static long checkIfEpisodeCanBeModified(long generatedId) throws DBException {
		Session session = openSession();
		try {
			DBEpisode episode = session.get(DBEpisode.class, generatedId);
			if (episode == null) throw new DBException("Not found: " + generatedId);
			
			if (episode.getMod() != null) return -2l;
			
			String q = "SELECT * FROM fbepisodes WHERE author_id!='" + episode.getAuthor().getId() + "' AND newmap LIKE '" + episode.getNewMap() + "%';";
			
			
			/*
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);
			
			query.select(root).where(cb.and(cb.notEqual(root.join("author").get("id"), episode.getAuthor().getId()), cb.like(root.get("id"), mapToId(id)+EP_INFIX+"%")));
			
			List<DBEpisode> result = session.createQuery(query).setMaxResults(1).list();*/
			
			
			List<DBEpisode> result = session.createNativeQuery(q, DBEpisode.class).setMaxResults(1).list();
			if (result.size() > 0) return result.get(0).getGeneratedId();
			return -1l;
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Retrieves an episode from the db by id and updates its viewCount
	 * @param id
	 * @return 
	 * @throws DBException if episode id does not exist
	 */
	@SuppressWarnings("unchecked")
	public static EpisodeWithChildren getFullEp(long generatedId, String username) throws DBException {
		boolean canUpvote = false;
		Session session = openSession();
		try {
			DBUser user = null;
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			
			long visitorCount = (Long)session.createQuery("select count(*) from DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId()).uniqueResult();
			long upvotes = (Long)session.createQuery("select count(*) from DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId()).uniqueResult();
						
			ArrayList<Comment> comments = session.createQuery("from DBComment c where c.episode.generatedId=" + ep.getGeneratedId() + " order by c.date", DBComment.class)
					.setMaxResults(PAGE_SIZE)
					.stream()
					.map(c->new Comment(c))
					.collect(Collectors.toCollection(ArrayList::new));
			
			if (!InitWebsite.READ_ONLY_MODE) {
				try {
					session.beginTransaction();
					session.createQuery("update DBEpisode ep set ep.viewCount=(ep.viewCount+1) where ep.generatedId=" + ep.getGeneratedId() + "").executeUpdate();
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
				}
				
				if (username != null) {
					username = username.toLowerCase().trim();
					user = DB.getUserById(session, username);
					if (user != null) {
						canUpvote = session.createQuery("from DBUpvote vote where vote.episode.generatedId=" + ep.getGeneratedId() + " and vote.user.id='" + user.getId() + "'").setMaxResults(1).list().size() == 0;
						if (session.createQuery("from DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId() + " and ev.user.id='" + username + "'").setMaxResults(1).list().size() == 0) {
							try {
							session.beginTransaction();
							DBEpisodeView ev = new DBEpisodeView();
							ev.setEpisode(ep);
							ev.setUser(user);
							session.save(ev);
							session.getTransaction().commit();
							session.beginTransaction();
						} catch (Exception e) {
							session.getTransaction().rollback();
						}
					}
					}
				}
				
			}
			
			
			
			String query = CHILD_QUERY + ep.getGeneratedId() + CHILD_QUERY_POST;
			
			List<Episode> children = (List<Episode>) session.createNativeQuery(query).stream()
			.map(x->{
				Object[] arr = (Object[])x;
				String childId = idToMap((String)arr[2]);
				String link = (String)arr[3];
				String title = (String)arr[4];
				Date date = (Date)arr[5];
				int childcount = (int) arr[6];
				long hits = ((BigInteger)arr[7]).longValue();
				long views = ((BigInteger)arr[8]).longValue();
				long childUpvotes = ((BigInteger)arr[9]).longValue();
				String authorId = (String)arr[10];
				String authorName = (String)arr[11];
				return new Episode(childId,link,title,date,childcount,hits,views,childUpvotes,authorId,authorName);
			}).collect(Collectors.toCollection(ArrayList::new));
			
			/*ArrayList<Episode> children = new ArrayList<>();
			@SuppressWarnings("unchecked")
			List<Object[]> result = session.createNativeQuery(query).list();
			result.forEach(x->{
				String childId = idToMap((String)x[2]);
				String link = (String)x[3];
				String title = (String)x[4];
				Date date = (Date)x[5];
				int childcount = (int) x[6];
				long hits = ((BigInteger)x[7]).longValue();
				long views = ((BigInteger)x[8]).longValue();
				long childUpvotes = ((BigInteger)x[9]).longValue();
				children.add(new Episode(childId,link,title,date,childcount,hits,views,childUpvotes));
			});*/
			
			//"from DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId() + " and uv.user.id='" + user.getId() + "'", DBUpvote.class
			
			ArrayList<String> pathIds = new ArrayList<>();
			{
				LinkedList<Long> list = new LinkedList<>(DB.newMapToIdList(ep.getNewMap()));
				
				while (pathIds.size() < 20 && !list.isEmpty()) {
					pathIds.add("ep.generatedId="+list.removeLast());
				}
			}
			
			List<FlatEpisode> pathbox = session
					.createQuery("from DBEpisode ep where " + pathIds.stream().collect(Collectors.joining(" or "))+" order by ep.id desc",DBEpisode.class)
					.stream().map(e->new FlatEpisode(e))
					.collect(Collectors.toList());
			
			return new EpisodeWithChildren(ep, visitorCount, upvotes, user, canUpvote, children, comments, pathbox);
		} finally {
			closeSession(session);
		}
	}
	
	private static final String CHILD_QUERY = "select parent_generatedid,generatedid,episodeid,link,title,episodedate,childcount, max(hitscount) as hits,max(viewscount) as views, max(upvotescount) as upvotes, author_id, fbusers.author as author_name\n" + 
			"from (\n" + 
			"    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.id as episodeid,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, count(*) as viewscount, 0 as upvotescount\n" + 
			"        from fbepisodes, fbepisodeviews\n" + 
			"        where fbepisodes.generatedid=fbepisodeviews.episode_generatedid\n" + 
			"        group by fbepisodes.generatedid)\n" + 
			"    union\n" + 
			"    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.id as episodeid,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, 0 as viewscount, count(*) as upvotescount\n" + 
			"        from fbepisodes,fbupvotes\n" + 
			"        where fbepisodes.generatedid=fbupvotes.episode_generatedid\n" + 
			"        group by fbepisodes.generatedid)\n" + 
			"    union\n" + 
			"    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.id as episodeid,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, 0 as viewscount, 0 as upvotescount\n" + 
			"    from fbepisodes)\n" + 
			"    ) as countstuff, fbusers\n" + 
			"where fbusers.id=countstuff.author_id and countstuff.parent_generatedid=";
	private static final String CHILD_QUERY_POST = " group by parent_generatedid,generatedid,episodeid,link,title,episodedate,childcount,author_id,author_name";
	
	public static void upvote(long generatedId, String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Not found: " + username);
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException ("Not found: " + generatedId);
			
			try {
				session.beginTransaction();
				DBUpvote upvote = new DBUpvote();
				upvote.setEpisode(ep);
				upvote.setUser(user);
				session.save(upvote);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error " + e.getMessage());
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static void downvote(String id, String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Not found: " + username);
			DBEpisode ep = DB.getEpById(session, id);
			if (ep == null) throw new DBException ("Not found: " + id);
			
			DBUpvote upvote = session.createQuery("from DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId() + " and uv.user.id='" + user.getId() + "'", DBUpvote.class).uniqueResult();
			
			if (upvote != null)try {
				session.beginTransaction();
				upvote.setEpisode(null);
				upvote.setUser(null);
				session.delete(upvote);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error " + e.getMessage());
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static FlatEpisode getFlatEp(String id) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = getEpById(session, id);
			if (ep == null) throw new DBException("Not found: " + id);
			return new FlatEpisode(ep);
		} finally {
			closeSession(session);
		}
	}
	
	public static EpisodeResultList search(String id, String search, int page) throws DBException {
		Strings.log(String.format("Searching \"%s\" on page %s (page number %d", search, id, page));
		Session session = openSession();
		page-=1;
		try {
			if (DB.getEpById(session, id) == null) throw new DBException("Not found: " + id);
			FullTextSession sesh = Search.getFullTextSession(session);
			QueryBuilder qb = sesh.getSearchFactory().buildQueryBuilder().forEntity(DBEpisode.class).get();
						
			RegexpQuery idQuery = new RegexpQuery(new Term("id", (mapToId(id)+EP_INFIX).toLowerCase()+".*"), RegExp.NONE);
			
			Query searchQuery = qb.simpleQueryString().onFields("title","link","body").matching(search).createQuery();
			Query combinedQuery = qb.bool().must(searchQuery).must(idQuery).createQuery();
			System.out.println("Searching from page " + page);
			
			try {
				@SuppressWarnings("unchecked")
				List<FlatEpisode> list = (List<FlatEpisode>) sesh.createFullTextQuery(combinedQuery, DBEpisode.class)
																	.setFirstResult(PAGE_SIZE*page)
																	.setMaxResults(PAGE_SIZE+1)
																	.stream()
																	.map(e->new FlatEpisode((DBEpisode)e))
																	.collect(Collectors.toCollection(ArrayList::new));
				System.out.println("Got results list");
				boolean hasNext = list.size() > PAGE_SIZE;
				System.out.println("Got hasNext: " + hasNext);
				EpisodeResultList ret = new EpisodeResultList(null, hasNext?list.subList(0, PAGE_SIZE):list, hasNext, -1 /* TODO */);
				System.out.println("Got return value");
				return ret;
			} catch (Exception e) {
				throw new RuntimeException("Search exception on id " + id + " with search query \"" + search + "\" -- "  + e + " -- " + e.getMessage());
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static AuthorSearchResult searchUser(String search, int page) {
		Session session = openSession();
		page-=1;
		try {
			FullTextSession sesh = Search.getFullTextSession(session);
			QueryBuilder qb = sesh.getSearchFactory().buildQueryBuilder().forEntity(DBUser.class).get();
			
			Query searchQuery = qb.simpleQueryString().onFields("author","id").matching(search).createQuery();
			System.out.println("Searching from page " + page);			
			
			@SuppressWarnings("unchecked")
			List<FlatUser> list = (List<FlatUser>) sesh.createFullTextQuery(searchQuery, DBUser.class).setFirstResult(PAGE_SIZE*page).setMaxResults(PAGE_SIZE+1).stream().map(e->new FlatUser((DBUser)e)).collect(Collectors.toCollection(ArrayList::new));
			
			boolean hasNext = list.size() > PAGE_SIZE;
			return new AuthorSearchResult(hasNext?list.subList(0, PAGE_SIZE):list, hasNext);

		} finally {
			closeSession(session);
		}
	}

	public static class AuthorSearchResult {
		public final List<FlatUser> users;
		public final boolean morePages;
		public AuthorSearchResult(List<FlatUser> users, boolean morePages) {
			this.users = users;
			this.morePages = morePages;
		}
	}
	
	public static void doSearchIndex() {
		Session session = DB.openSession();
		try {
			FullTextSession sesh = Search.getFullTextSession(session);
			long start = System.nanoTime();
			System.out.println("Beginning indexing");
			sesh.createIndexer().startAndWait();
			System.out.println("Done indexing " + (((double)(System.nanoTime()-start))/1000000000.0));
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			DB.closeSession(session);
		}
	}

	/**
	 * Retrieves an episode by its oldId
	 * @param id
	 * @return
	 * @throws DBException if id not found
	 */
	public static String getEpByLegacyId(String oldId) throws DBException {	
		Session session = openSession();
		try {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
		Root<DBEpisode> root = query.from(DBEpisode.class);
		
		query.select(root).where(cb.equal(root.get("legacyId"), oldId));
		
		List<DBEpisode> result = session.createQuery(query).list();
		
		if (result.size() == 0) throw new DBException("Not found: " + oldId);
		else if (result.size() > 1) {
			StringBuilder sb = new StringBuilder();
			for (DBEpisode ep : result) sb.append(ep.getGeneratedId() + " ");
			throw new RuntimeException("Multiple episodes have matching id: " + oldId + " " + sb);
		} else
			return result.get(0).getMap();
		} finally {
			closeSession(session);
		}
	}
	
	public static List<FlatEpisode> getRecentsPage(int rootId, int page, boolean reverse) throws DBException {
		Session session = openSession();
		page-=1;
		try {
			if (rootId != 0) if (DB.getEpById(session, ""+rootId) == null) throw new DBException("Not found: " + rootId);
			
			ArrayList<FlatEpisode> alist = session.createNativeQuery(
					"SELECT * FROM fbepisodes " + 
					(rootId==0?"":" WHERE id='" + EP_PREFIX + rootId + "' OR id LIKE '" + EP_PREFIX + rootId + "%' ") + 
					" ORDER BY date " +(reverse?"ASC":"DESC") + 
					" OFFSET " + (PAGE_SIZE*page) + 
					" LIMIT " + PAGE_SIZE, 
				DBEpisode.class
			).stream().map(ep->new FlatEpisode(ep)).collect(Collectors.toCollection(ArrayList::new));
						
			return Collections.unmodifiableList(alist);
			 
		}finally {
			closeSession(session);
		}
	}
	
	/**
	 * Get num most recent episodes of a particular story, or of all stories
	 * @param story root id for story, or 0 to get all stories
	 * @param num number of episodes to get
	 * @return
	 * @throws DBException
	 */
	public static EpisodeResultList getRecents(long story, int page, boolean reverse) throws DBException {
		System.out.println("recents");
		Session session = openSession();
		page-=1;
		try {
			if (story != 0) if (DB.getEpById(session, ""+story) == null) throw new DBException("Not found: " + story);
			
			String sql = "SELECT COUNT(*) FROM fbepisodes";
			if (story != 0) sql += " WHERE id='" + EP_PREFIX+Integer.toString(story) + "' OR id LIKE '" + EP_PREFIX + Integer.toString(story) + EP_INFIX + "%" + "'";		
			int totalCount = ((BigInteger)(session.createNativeQuery(sql).list().get(0))).intValue();
			
			ArrayList<FlatEpisode> alist = session.createNativeQuery(
					"SELECT * FROM fbepisodes " + 
					(story==0?"":" WHERE id='" + EP_PREFIX + story + "' OR id LIKE '" + EP_PREFIX + story + "%' ") + 
					" ORDER BY date " +(reverse?"ASC":"DESC") + 
					" OFFSET " + (PAGE_SIZE*page) + 
					" LIMIT " + PAGE_SIZE, 
				DBEpisode.class
			).stream().map(ep->new FlatEpisode(ep)).collect(Collectors.toCollection(ArrayList::new));
						
			List<FlatEpisode> list = Collections.unmodifiableList(alist);
			
			System.out.println("Found " + totalCount + " episodes");
			
			return new EpisodeResultList(null, list, false, totalCount/PAGE_SIZE+1);
		}finally {
			closeSession(session);
		}
	}
	
	public static StreamingOutput getOutlinePage(Cookie token, String rootId, int pageNum) {
		System.out.println("Getting outline " + rootId + " page " + pageNum);
		final int page = pageNum - 1;
		final int OUTLINE_PAGE_SIZE = 300;
		StreamingOutput outputStream = new StreamingOutput() {
			public void write(OutputStream os) throws IOException, WebApplicationException {
				try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {

				Session session = openSession();
				try {
					
					if (token == null) {
						writer.write(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
						writer.flush();
						return;
					}
					String username = Accounts.getUsernameFromCookie(token);
					if (username == null) {
						writer.write(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
						writer.flush();
						return;
					}
					DBUser dbUser = DB.getUserById(session, username);
					if (dbUser == null) {
						writer.write(Strings.getFile("generic.html", null).replace("$EXTRA", "You must be logged in to do that"));
						writer.flush();
						return;
					}
					FlatUser user = new FlatUser(dbUser);
					DBEpisode ep = DB.getEpById(session, rootId);
					if (ep == null) {
						writer.write(Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + rootId));
						writer.flush();
						return;
					}
					int minDepth = ep.getDepth();
					
					writer.write("\n<!-- BEGIN PAGE " + page + "-->\n");
					
					String query = "" 
							+ "select replace(replace(fbepisodes.id,'B','-'),'A',''), link, depth, fbusers.id, fbusers.author "
							+ "from fbepisodes,fbusers where fbepisodes.id like '" + ep.getId() + EP_INFIX + "%' and fbepisodes.author_id=fbusers.id "
							+ "order by (CAST(string_to_array(replace(replace(fbepisodes.id,'B','-'),'A',''),'-') AS integer[])) asc LIMIT " + OUTLINE_PAGE_SIZE + " OFFSET " + (page*OUTLINE_PAGE_SIZE) + "";
						try {							
							final ReturnedSomething returnedSomething = new ReturnedSomething();

							@SuppressWarnings("unchecked")
							Stream<Object[]> stream = session.createNativeQuery(query).stream();
							stream.forEach(x -> {
								String id = (String) x[0];
								String link = (String) x[1];
								int depth = (int) x[2];
								String authorUsername = (String) x[3];
								String authorName = (String) x[4];
								try {
									writer.write(Story.epToOutlineHTML(id, link, authorUsername, authorName, depth, minDepth));
									writer.flush();
								} catch (IOException e) {
									throw new BreakException(e);
								}
								returnedSomething.set();
							});

							if (returnedSomething.get()) writer.write("<div class=\"next\"><a href=\"/fb/outline/" + rootId + "?page=" + (page + 2) + "\">next</a></div>");

						} catch (BreakException e) {
							writer.flush();
						}
						writer.write("\n<!-- END PAGE " + page + "-->\n");
						writer.flush();
				} finally {
					closeSession(session);
				}
				}
			}
		};
		return outputStream;
	}
	
	private static class ReturnedSomething {
		private boolean value = false;
		public void set() { value = true; }
		public boolean get() { return value; }
	}
	
	public static FlatEpisode[] getPath(String id) throws DBException {
		long start = System.nanoTime();
		Strings.log("Processing path " + id);
		FlatEpisode[] episodeList;
		Session session = openSession();
		try {
			if (getEpById(session, id) == null) throw new DBException("Not found: " + id);
			
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);
			
			
			List<String> ids = getPathIds(id);
			Predicate[] preds = new Predicate[ids.size()];
			for (int i=0; i<ids.size(); ++i) preds[i] = cb.equal(root.get("id"), mapToId(ids.get(i)));
			
			query.select(root).where(cb.or(preds)).orderBy(cb.asc(root.get("depth")));
			
			ArrayList<FlatEpisode> list = new ArrayList<>(ids.size());
			for (int i=0; i<=ids.size(); i+=STREAM_SIZE) {
				Stream<DBEpisode> stream = session.createQuery(query).setFirstResult(i).setMaxResults(STREAM_SIZE).stream();
				stream.forEach(ep->{
					list.add(new FlatEpisode(ep));
				});
			}
			episodeList = new FlatEpisode[list.size()];
			episodeList = list.toArray(episodeList);
		} finally {
			closeSession(session);
		}
		Strings.log("Total path took " + (((double)(System.nanoTime()-start))/1000000000.0) + " to generate");
		return episodeList;
	}
	
	public static FlatEpisode[] getFullStory(String id) throws DBException {
		long start = System.nanoTime();
		Strings.log("Processing path " + id);
		FlatEpisode[] episodeList;
		Session session = openSession();
		try {
			if (getEpById(session, id) == null) throw new DBException("Not found: " + id);
			
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);
			
			long aStart = System.nanoTime();
			
			List<String> ids = getPathIds(id);
			if (ids.size() > 30) ids = ids.subList(ids.size()-30, ids.size());
			Predicate[] preds = new Predicate[ids.size()];
			for (int i=0; i<ids.size(); ++i) preds[i] = cb.equal(root.get("id"), mapToId(ids.get(i)));
			
			query.select(root).where(cb.or(preds)).orderBy(cb.asc(root.get("depth")));
			Strings.log("Took " + (((double)(System.nanoTime()-aStart))/1000000000.0) + " to build predicate");
			aStart = System.nanoTime();
			
			List<DBEpisode> result = session.createQuery(query).list();
			Strings.log("Took " + (((double)(System.nanoTime()-aStart))/1000000000.0) + " to get result");
			aStart = System.nanoTime();
			
			episodeList = new FlatEpisode[result.size()];
			for (int i=0; i<result.size(); ++i) episodeList[i] = new FlatEpisode(result.get(i));
			Strings.log("Took " + (((double)(System.nanoTime()-aStart))/1000000000.0) + " to convert result to final array");
		} finally {
			closeSession(session);
		}
		Strings.log("Total path took " + (((double)(System.nanoTime()-start))/1000000000.0) + " to generate");
		return episodeList;
	}
	
	/*private static List<String> getPathIds(String parentId) {
		String[] arr = parentId.split("-");
		List<String> ret = new ArrayList<>(arr.length);
		for (int i=0; i<arr.length; ++i) {
			StringBuilder sb = new StringBuilder();
			for (int j=0; j<=i; ++j) sb.append(arr[j] + "-");
			ret.add(sb.substring(0, sb.length()-1));
		}
		return ret;
	}*/
	
	private static List<Long> newMapToIdList(String newMap) {
		String[] arr = newMap.substring(1,newMap.length()).split(""+EP_INFIX);
		ArrayList<Long> list = new ArrayList<>();
		for (String id : arr) {
			list.add(Long.parseLong(id));
		}
		return list;
	}
	
	public static FlatEpisode[] getRoots() throws DBException {
		Session session = openSession();
		try {
			List<DBEpisode> result = getRoots(session);
			FlatEpisode[] list = new FlatEpisode[result.size()];
			for (int i = 0; i < result.size(); ++i)
				list[i] = new FlatEpisode(result.get(i));
			return list;
		} finally {
			closeSession(session);
		}
	}
	private static List<DBEpisode> getRoots(Session session) throws DBException {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);			
						
			query.select(root).where(cb.isNull(root.get("parent"))).orderBy(cb.asc(root.get("id")));
			
			List<DBEpisode> result = session.createQuery(query).list();
			
			return result;
	}
	
	/**
	 * Change a user's author name
	 * @param id id of user
	 * @param newAuthor new author name
	 * @throws DBException if id not found
	 */
	public static void changeAuthorName(String id, String newAuthor) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setAuthor(newAuthor);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Change a user's theme
	 * @param id id of user
	 * @param newTheme new theme (HTML name, not file name)
	 * @throws DBException if id not found
	 */
	public static void changeTheme(String id, String newTheme) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			//user.setTheme(newTheme);
			DBTheme theme = session.get(DBTheme.class, newTheme);
			if (theme == null) throw new DBException("Theme " + newTheme + " does not exist"); // TODO maybe do this better?
			user.setTheme(theme);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Change a user's bio
	 * @param id id of user
	 * @param newBio new bio 
	 * @throws DBException if id not found
	 */
	public static void changeBio(String id, String newBio) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setBio(newBio);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			} 
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Change a user's bio
	 * @param id id of user
	 * @param newBio new bio 
	 * @throws DBException if id not found
	 */
	public static void changeAvatar(String id, String newAvatar) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setAvatar(newAvatar);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	

	/**
	 * Change a user's author name
	 * @param id id of user
	 * @param newPassword new HASHED password (NOT PLAINTEXT)
	 * @throws DBException if id not found
	 */
	public static void changePassword(String id, String newPassword) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setPassword(newPassword);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Change a user's user level (1=user, 10=mod, 100=admin)
	 * @param id user id
	 * @param newLevel new user level
	 * @throws DBException if user id not found
	 */
	public static void changeUserLevel(String id, byte newLevel) throws DBException {
		Session session = openSession(); 
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setLevel(newLevel);
			try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static FlatUser getFlatUser(String id) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) {
				throw new DBException("User id does not exist");
			}
			FlatUser ret = new FlatUser(user);
			return ret;
		} finally {
			closeSession(session);
		}
	}
	
	private static final int PAGE_SIZE = 100;
	public static EpisodeResultList getUserProfile(String userId, int page) throws DBException {
		page-=1;
		userId = userId.toLowerCase();
		Session session = openSession();
		try {
			DBUser user = session.get(DBUser.class, userId);
			if (user == null) throw new DBException("User ID " + userId + " does not exist");
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);
			
			Join<DBEpisode,DBUser> join = root.join("author");
			Predicate pred = cb.equal(join.get("id"), userId);

			query.select(root).where(pred).orderBy(cb.desc(root.get("date")));
			
			List<FlatEpisode> list = (List<FlatEpisode>)session.createQuery(query).setFirstResult(PAGE_SIZE*page).setMaxResults(PAGE_SIZE+1).stream().map(e->new FlatEpisode((DBEpisode)e)).collect(Collectors.toCollection(ArrayList::new));
			boolean hasNext = list.size() > PAGE_SIZE;
			return new EpisodeResultList(new FlatUser(user), hasNext?list.subList(0, PAGE_SIZE):list, hasNext, -1 /* TODO */);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * List of FlatEpisodes, along with a FlatUser and boolean.
	 * Serves multiple purposes
	 */
	public static class EpisodeResultList {
		public final FlatUser user;
		public final List<FlatEpisode> episodes;
		public final boolean morePages;
		public final int numPages;
		public EpisodeResultList(FlatUser user, List<FlatEpisode> episodes, boolean morePages, int numPages) {
			this.user = user;
			this.episodes = episodes;
			this.morePages = morePages;
			this.numPages = numPages;
		}
	}
	
	public static boolean emailInUse(String email) {
		try {
			getFlatUserByEmail(email);
		} catch (DBException e) {
			return false;
		}
		return true;
	}
	
	public static boolean userIdInUse(String id) {
		Session session = openSession();
		try {
			return getUserById(session, id.toLowerCase()) != null;
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Checks a plaintext password against the stored hash
	 * @param id id of user
	 * @param password plaintext possible password
	 * @return true if password matches, else false
	 * @throws DBException if id not found
	 */
	public static boolean checkPassword(String id, String password) throws DBException {
		String hashedPassword;
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User does not exist");
			if (user.getEmail() == null) throw new DBException("You may not log in to a legacy account");
			hashedPassword = user.getPassword();
		} finally {
			closeSession(session);
		}
		boolean result;
		try {
			result = BCrypt.checkpw(password, hashedPassword);
		} catch (Exception e) {
			result = false;
		}
		return result;
	}
	
	public static boolean checkPassword(FlatUser user, String password) {
		String hashedPassword = user.hashedPassword;
		boolean result;
		try {
			result = BCrypt.checkpw(password, hashedPassword);
		} catch (Exception e) {
			result = false;
		}
		return result;
	}
	
	/**
	 * Comment on an episode
	 * @param episodeId
	 * @param authorId id of user doing the flagging
	 * @param commentText
	 * @return id of newly created comment
	 * @throws DBException
	 */
	public static long addComment(String episodeId, String authorId, String commentText) throws DBException {
		Date commentDate = new Date();
		Session session = openSession();
		try {
			DBEpisode ep = getEpById(session, episodeId);
			DBUser author = getUserById(session, authorId);

			if (ep == null) throw new DBException("Episode not found: " + episodeId);
			if (author == null) throw new DBException("Author does not exist");

			DBComment comment = new DBComment();
			
			comment.setText(commentText);
			comment.setDate(commentDate);
			comment.setEditDate(commentDate);
			comment.setEpisode(ep);
			comment.setUser(author);
			comment.setEditor(author);
			
			boolean sendSiteNotification = false;
			boolean sendMailNotification = false;
			
			if (!comment.getEpisode().getAuthor().getId().equals(author.getId())) { // don't sent notification to episode author if they wrote the comment
				sendSiteNotification = comment.getEpisode().getAuthor().isCommentSite();
				sendMailNotification = comment.getEpisode().getAuthor().isCommentMail();
			}
			
			long commentID;
						
			try {
				session.beginTransaction();
				session.save(comment);
				session.merge(ep);
				session.merge(author);
				
				commentID = comment.getId();
				
				if (sendSiteNotification) {
					DBNotification note = new DBNotification();
					note.setBody("<a href=\"/fb/user/" + author.getId() + "\">" + Strings.escape(author.getAuthor()) + "</a> left a <a href=\"/fb/get/" + comment.getEpisode().getMap() + "#comment" + comment.getId() + "\">comment</a> on " + Strings.escape(comment.getEpisode().getTitle()));
					note.setDate(new Date());
					note.setRead(false);
					note.setUser(comment.getEpisode().getAuthor());
					session.save(note);
				}
				
				if (sendMailNotification) new Thread(()->{ // send the email
					Accounts.sendEmail(comment.getEpisode().getAuthor().getEmail(), "Someone commented on your episode", 
							"<a href=\"https://"+Strings.getDOMAIN()+"/fb/user/" + author.getId() + "\">" + Strings.escape(author.getAuthor()) + "</a> left a <a href=\"https://"+Strings.getDOMAIN()+"/fb/get/" + comment.getEpisode().getMap() + "#comment" + comment.getId() + "\">comment</a> on " + Strings.escape(comment.getEpisode().getTitle()));
					

				}).start();
				
				session.getTransaction().commit();
				
				
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			Strings.log(String.format("Comment: <%s> %s %s", authorId, episodeId, commentDate));
			return commentID;
		} finally {
			closeSession(session);
		}
	}
	
	public static void editComment(long commentId, String username, String commentText) throws DBException {
		Date commentDate = new Date();
		Session session = openSession();
		try {
			DBUser editor = getUserById(session, username);
			DBComment comment = session.get(DBComment.class, commentId);

			if (editor == null) throw new DBException("Editor does not exist");
			if (comment == null) throw new DBException("Comment does not exist");
			
			if (editor.getLevel() < 10 && editor.getAuthor().compareTo(comment.getEditor().getId()) != 0)  throw new DBException("You are not authorized to do that.");
			
			comment.setText(commentText);
			comment.setEditDate(commentDate);
			comment.setEditor(editor);
									
			try {
				session.beginTransaction();
				session.save(comment);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
		Strings.log(String.format("Comment edit: <%s> %s %s", commentId, username, commentDate));
	}
	
	public static class DeleteCommentConfirmation {
		public final boolean canDelete;
		public final FlatUser user;
		public final Comment comment;
		public DeleteCommentConfirmation(boolean canDelete, FlatUser user, Comment comment) {
			this.canDelete = canDelete;
			this.user = user;
			this.comment = comment;
		}
	}
	
	public static DeleteCommentConfirmation canDeleteComment(String username, long commentId) {
		Session session = openSession();
		try {
			
			DBUser user = DB.getUserById(session, username);
			DBComment comment = session.get(DBComment.class, commentId);
			if (user == null || comment == null) return new DeleteCommentConfirmation(false, (user==null)?null:(new FlatUser(user)), (comment==null)?null:(new Comment(comment)));

			
			return new DeleteCommentConfirmation((comment.getUser().getId().equals(user.getId()) || user.getLevel()>=10), new FlatUser(user), new Comment(comment));
			
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Deletes a comment 
	 * @param commentId
	 * @param authorId
	 * @throws DBException if user or comment doesn't exist, or if user doesn't own comment and is not a mod
	 */
	public static void deleteComment(long commentId, String authorId) throws DBException {
		Session session = openSession();
		try {
			DBComment comment = session.get(DBComment.class, commentId);
			if (comment == null) throw new DBException("Comment not found: " + commentId);
			DBUser user = getUserById(session, authorId);
			if (user == null) throw new DBException("User does not exist");
			if (!comment.getUser().getId().equals(user.getId()) && user.getLevel() < 10) throw new DBException(authorId + " does not own comment " + commentId + " and is not a moderator");
			try {
				session.beginTransaction();
				session.createQuery("delete DBFlaggedComment fc where fc.comment.id=" + commentId).executeUpdate();
				session.delete(comment);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException(e);
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Deletes a comment flag. DOES NOT CHECK THAT USER IS A MOD
	 * @param commentFlagId
	 * @throws DBException
	 */
	public static void deleteCommentFlag(long commentFlagId) throws DBException {
		Session session = openSession();
		try {
			DBFlaggedComment flag = session.get(DBFlaggedComment.class, commentFlagId);
			if (flag == null) throw new DBException("Comment flag not found: " + commentFlagId);
		
			try {
				session.beginTransaction();
				session.delete(flag);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException(e);
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static FlatEpisode flagComment(long commentId, String authorId, String flagCommentText) throws DBException {
		Date flagDate = new Date();
		Session session = openSession();
		try {
			DBComment comment = session.get(DBComment.class, commentId);
			if (comment == null) throw new DBException("Comment not found " + commentId);
			DBUser author = getUserById(session, authorId);
			if (author == null) throw new DBException("Author does not exist");

			DBFlaggedComment flag = new DBFlaggedComment();
			
			flag.setText(flagCommentText);
			flag.setDate(flagDate);
			flag.setComment(comment);
			flag.setUser(author);
						
			try {
				session.beginTransaction();
				session.save(flag);
				session.merge(comment);
				session.merge(author);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			Strings.log(String.format("Comment flag: <%s> %s %s", authorId, commentId, flagDate));
			return new FlatEpisode(comment.getEpisode());
		} finally {
			closeSession(session);
		}
		
	}
	
	/**
	 * Flag an episode
	 * @param episodeId
	 * @param authorId id of user doing the flagging
	 * @param flagText
	 * @throws DBException
	 */
	public static void flagEp(String episodeId, String authorId, String flagText) throws DBException {
		Date flagDate;
		Session session = openSession();
		try {
			DBEpisode ep = getEpById(session, episodeId);
			DBUser author = getUserById(session, authorId);

			if (ep == null) throw new DBException("Episode not found: " + episodeId);
			if (author == null) throw new DBException("Author does not exist");

			DBFlaggedEpisode flag = new DBFlaggedEpisode();
			
			flag.setText(flagText);
			flag.setDate(new Date());
			flag.setEpisode(ep);
			flag.setUser(author);
			
			flagDate = flag.getDate();
			
			try {
				session.beginTransaction();
				session.save(flag);
				session.merge(ep);
				session.merge(author);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
		Strings.log(String.format("Flag: <%s> %s %s", authorId, episodeId, flagDate));
	}
	
	public static List<FlaggedEpisode> getFlags() {
		Session session = openSession();
		try {
			return session.createQuery("from DBFlaggedEpisode flag order by flag.date desc", DBFlaggedEpisode.class).stream().map(x->new FlaggedEpisode(x)).collect(Collectors.toList());
		} finally {
			closeSession(session);
		}
	}
	
	public static FlaggedEpisode getFlag(long id) throws DBException {
		Session session = openSession();
		try {
			DBFlaggedEpisode flag = session.get(DBFlaggedEpisode.class, id);
			if (flag == null) throw new DBException("Flag not found: " + id);
			return new FlaggedEpisode(flag);
		} finally {
			closeSession(session);
		}
	}
	
	public static FlaggedComment getFlaggedComment(long id) throws DBException {
		Session session = openSession();
		try {
			DBFlaggedComment flag = session.get(DBFlaggedComment.class, id);
			if (flag == null) throw new DBException("Flag not found: " + id);
			return new FlaggedComment(flag);
		} finally {
			closeSession(session);
		}
	}
	
	public static void clearFlaggedComment(long id) throws DBException {
		Session session = openSession(); 
		try {
			DBFlaggedComment flag = session.get(DBFlaggedComment.class, id);
			if (flag == null) throw new DBException("Flag not found: " + id);
			DBComment comment = flag.getComment();
			DBUser user = flag.getUser();
			try {
				session.beginTransaction();
				session.delete(flag);
				session.merge(comment);
				session.merge(user);
			session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static List<FlaggedComment> getFlaggedComments() {
		Session session = openSession();
		try {
			return session.createQuery("from DBFlaggedComment mod order by mod.date desc", DBFlaggedComment.class).stream().map(x->new FlaggedComment(x)).collect(Collectors.toList());
		} finally {
			closeSession(session);
		}
	}
	
	public static List<ModEpisode> getMods() {
		Session session = openSession();
		try {
			return session.createQuery("from DBModEpisode mod order by mod.date desc", DBModEpisode.class).stream().map(x->new ModEpisode(x)).collect(Collectors.toList());
		} finally {
			closeSession(session);
		}
	}
	
	public static ModEpisode getMod(long id) throws DBException {
		Session session = openSession();
		try {
			DBModEpisode flag = session.get(DBModEpisode.class, id);
			if (flag == null) throw new DBException("Flag not found: " + id);
			return new ModEpisode(flag);
		}finally {
			closeSession(session);
		}
	}
	
	/**
	 * Remove a DBFlaggedEpisode from the database
	 * @param id
	 * @throws DBException
	 */
	public static void clearFlag(long id) throws DBException {
		Session session = openSession(); 
		try {
			DBFlaggedEpisode flag = session.get(DBFlaggedEpisode.class, id);
			if (flag == null) throw new DBException("Flag not found: " + id);
			DBEpisode ep = flag.getEpisode();
			DBUser user = flag.getUser();
			try {
				session.beginTransaction();
				session.delete(flag);
				session.merge(ep);
				session.merge(user);
			session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	
	public static void clearMod(long id, boolean accepted) throws DBException {
		Session session = openSession();
		try {
			DBModEpisode mod = session.get(DBModEpisode.class, id);
			if (mod == null) throw new DBException("Flag not found: " + id);
			DBEpisode ep = mod.getEpisode();
			ep.setMod(null);
			try {
				session.beginTransaction();
				if (accepted) DB.modifyEp(session, ep.getMap(), mod.getLink(), mod.getTitle(), mod.getBody(), ep.getAuthor().getId());
				session.delete(mod);
				session.merge(ep);
			session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				Strings.log(String.format("Database error modifying: %s", id));
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
		
	}
	
	public static void pruneQueues() {
		Session session = openSession();
		try {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR_OF_DAY, -24);
			Date yesterday = cal.getTime();
			List<DBEmailChange> ecList;
			List<DBPotentialUser> puList;
			List<DBPasswordReset> prList;
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<DBEmailChange> query = cb.createQuery(DBEmailChange.class);
				Root<DBEmailChange> root = query.from(DBEmailChange.class);
				query.select(root).where(cb.lessThan(root.get("date"), yesterday));
				ecList = session.createQuery(query).list();
			}
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<DBPotentialUser> query = cb.createQuery(DBPotentialUser.class);
				Root<DBPotentialUser> root = query.from(DBPotentialUser.class);
				query.select(root).where(cb.lessThan(root.get("date"), yesterday));
				puList = session.createQuery(query).list();
			}
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<DBPasswordReset> query = cb.createQuery(DBPasswordReset.class);
				Root<DBPasswordReset> root = query.from(DBPasswordReset.class);
				query.select(root).where(cb.lessThan(root.get("date"), yesterday));
				prList = session.createQuery(query).list();
			}
			
			try {
				session.beginTransaction();
				for (DBEmailChange ec : ecList) {
					DBUser user = ec.getUser();
					user.setEmailChange(null);
					ec.setUser(null);
					session.delete(ec);
					session.merge(user);
				}
				for (DBPasswordReset pr : prList) {
					DBUser user = pr.getUser();
					user.setPasswordReset(null);
					pr.setUser(null);
					session.delete(pr);
					session.merge(user);
				}
				for (DBPotentialUser ec : puList) session.delete(ec);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
			}
			
		} finally {
			closeSession(session);
		}
	}
	
	public static String addEmailChange(String userId, String newEmail) throws DBException {
		Session session = openSession();
		synchronized (ecLock) { try {
			DBUser user = DB.getUserById(session, userId);
			if (user == null) throw new DBException("Not found: " + userId);
			DBEmailChange oldEc = user.getEmailChange();
			DBEmailChange ec = new DBEmailChange();
			ec.setDate(new Date());
			ec.setNewEmail(newEmail);
			ec.setUser(user);
			String token;
			{
				DBEmailChange asdf;
				do {
					token = newToken();
					asdf = session.get(DBEmailChange.class, token);
				} while (asdf != null);
			}
			ec.setToken(token);
			
			user.setEmailChange(ec);
			try {
				session.beginTransaction();
				if (oldEc != null) {
					oldEc.setUser(null);
					session.delete(oldEc);
				}
				session.save(ec);
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("database error");
			}
			return token;
		} finally {
			closeSession(session);
		} }
	}
	
	/**
	 * Verify a user's change email address using their token
	 * @param token
	 * @throws DBException if token not valid
	 */
	public static void changeEmail(String token) throws DBException {
		Session session = openSession();
		try {
			DBEmailChange ec = session.get(DBEmailChange.class, token);
			if (ec == null) throw new DBException("Confirmation link is expired, invalid, or has already been used");
			DBUser user = ec.getUser();
			
			if (emailInUse(ec.getNewEmail())) throw new DBException("New email " + ec.getNewEmail() + " already in use");

			user.setEmail(ec.getNewEmail());
			user.setEmailChange(null);
			ec.setUser(null);
			try {
				session.beginTransaction();
				session.merge(user);
				session.delete(ec);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Adds a new user to the database
	 * @param email
	 * @param password HASHED password (NOT PLAINTEXT!)
	 * @param author
	 * @return token for new potential user
	 */
	public static String addPotentialUser(String username, String email, String passwordHash, String author) {
		Session session = openSession();
		try {
			DBPotentialUser user = new DBPotentialUser();
			user.setAuthor(author);
			user.setEmail(email);
			user.setPasswordHash(passwordHash);
			user.setUsername(username);
			user.setDate(new Date());
			String token;
			synchronized (puLock) {
				DBPotentialUser dbpu;
				do {
					token = newToken();
					dbpu = session.get(DBPotentialUser.class, token);
				} while (dbpu != null);
			}
			user.setToken(token);
			try {
				session.beginTransaction();
				session.save(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
			}
			return token;
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param token
	 * @return username
	 * @throws DBException if username already exists
	 */
	public static String addUser(String token) throws DBException {
		Session session = openSession();
		synchronized (userLock) {try {
			DBPotentialUser pu = session.get(DBPotentialUser.class, token);
			if (pu == null) throw new DBException("Confirmation link is expired, invalid, or has already been used");
			DBUser user = DB.getUserById(session, pu.getUsername());
			if (user != null) throw new DBException("Username " + pu.getUsername() + " already exists");
			user = DB.getUserByEmail(session, pu.getEmail());
			if (user != null) throw new DBException("Email address has already been verified with another account");
			user = new DBUser();
			user.setLevel((byte) 1);
			user.setAuthor(pu.getAuthor());
			user.setEmail(pu.getEmail());
			user.setBio("");
			user.setPassword(pu.getPasswordHash());
			user.setId(pu.getUsername());
			user.setDate(new Date());
			DBTheme defaultTheme; 
			boolean addDefaultTheme = false;
			{
				defaultTheme = session.get(DBTheme.class, Theme.DEFAULT_NAME);
				if (defaultTheme == null) {
					defaultTheme = new DBTheme();
					defaultTheme.setName(Theme.DEFAULT_NAME);
					defaultTheme.setCss(Theme.DEFAULT_CSS);
					addDefaultTheme = true;
				}
				user.setTheme(defaultTheme);
			}
			try {
				session.beginTransaction();
				if (addDefaultTheme) session.save(defaultTheme);
				session.save(user);
				session.delete(pu);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException(e);
			}
			return user.getId();
		} finally {
			closeSession(session);
		}}
	}
	
	public static void updateAllAcountDates() {
		HashSet<String> users = new HashSet<>();
		Session session = openSession();
		try {
			int nullCount;
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<Long> query = cb.createQuery(Long.class);
				Root<DBUser> root = query.from(DBUser.class);
				query.select(cb.count(root)).where(cb.isNull(root.get("date")));
				nullCount = session.createQuery(query).uniqueResult().intValue();
				System.out.println("****** Null dates: " + nullCount);
			}
			{
				CriteriaBuilder cb = session.getCriteriaBuilder();
				CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class);
				Root<DBUser> root = query.from(DBUser.class);
				query.select(root).where(cb.isNull(root.get("date")));
				for (int i=0; i<nullCount; i+=STREAM_SIZE) {
					Stream<DBUser> stream = session.createQuery(query).stream();
					stream.forEach(user->{
						users.add(user.getId());
					});
				}
			}
			{
				int count = ((Long)session.createQuery("select count(*) from DBEpisode ep where ep.author.date is not null and ep.date<ep.author.date").uniqueResult()).intValue();
				System.out.println("****** Early dates: " + count);

				for (int i=0; i<count; i+=STREAM_SIZE) {
					Stream<DBEpisode> stream = session.createQuery("from DBEpisode ep where ep.author.date is not null and ep.date<ep.author.date", DBEpisode.class).stream();
					stream.forEach(ep->{
						users.add(ep.getAuthor().getId());
					});
					System.out.println("***** " + i);
				}
			}
			System.out.println("********* Found " + users.size() + " users");
			
			for (String username : users)  {
				try {
					DB.updateAccountDate(session, username);
					Strings.log("Updated date for user: " + username);
				} catch (DBException e) {
					Strings.log(e);
				} 
			}
			
			Strings.log("Finished updating dates");
		} finally {
			closeSession(session);
		}
	}
	public static void updateAccountDate(Session session, String username) throws DBException {
		DBUser user = DB.getUserById(session, username);
		if (user == null) throw new DBException("Not found: " + username);
		DBEpisode oldest = null;
		if (user.getDate() == null) oldest = (DBEpisode) session.createQuery("from DBEpisode ep where ep.author.id='" + username + "' order by ep.date asc").setMaxResults(1).uniqueResult();
		else oldest = (DBEpisode) session.createQuery("from DBEpisode ep where ep.author.id='" + username + "' and ep.date<ep.author.date order by ep.date asc").setMaxResults(1).uniqueResult();
		if (oldest != null) try {
			session.beginTransaction();
			user.setDate(oldest.getDate());
			session.merge(user);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			throw new DBException(e);
		}
	}
	
	public static void mergeAccounts(String userA, String userB) throws DBException {
		Session session = openSession();
		try {
			DBUser a = DB.getUserById(session, userA);
			DBUser b = DB.getUserById(session, userB);
			{
				ArrayList<String> notFound = new ArrayList<>(2);
				if (a==null) notFound.add(userA);
				if (b==null) notFound.add(userB);
				if (notFound.size() > 0) throw new DBException("Not found: " + notFound);
			}
			System.out.println("Found both users");
			userA = userA.toLowerCase();
			userB = userB.toLowerCase();
			try {
				session.beginTransaction();
				System.out.println("Result " + session.createQuery("update DBEpisode ep set ep.author=(from DBUser as user where id='" + userA + "') where ep.author.id='" + userB + "'").executeUpdate());
				System.out.println("Result " + session.createQuery("update DBEpisode ep set ep.editor=(from DBUser as user where id='" + userA + "') where ep.editor.id='" + userB + "'").executeUpdate());
				System.out.println("Result " + session.createQuery("update DBFlaggedEpisode ep set ep.user=(from DBUser as user where id='" + userA + "') where ep.user.id='" + userB + "'").executeUpdate());
				session.createQuery("delete DBEpisodeView ev where ev.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBUpvote uv where uv.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBAnnouncementView uv where uv.viewer.id='" + userB + "'").executeUpdate();
				
				session.createQuery("delete DBFlaggedComment uv where uv.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBComment uv where uv.user.id='" + userB + "'").executeUpdate();
				
				session.createQuery("delete DBNotifiction note where note.user.id='" + userB + "'").executeUpdate();

				session.delete(b);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				e.printStackTrace();
			}
			DB.updateAccountDate(session, userA);
		} finally {
			closeSession(session);
		}
	}
	
	public static boolean passwordResetTokenExists(String token) {
		Session session = openSession();
		try {
			return session.get(DBPasswordReset.class, token) != null;
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Creates a new password reset, returns its token
	 * @param username
	 * @return {token, email}
	 * @throws DBException if username not found
	 * @throws PasswordResetException if pr already exists
	 */
	public static String[] newPasswordResetUsername(String username) throws DBException, PasswordResetException {
		Session session = openSession();
		try {
			return DB.newPasswordReset(session,DB.getUserById(session, username));
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param email
	 * @return {token, email}
	 * @throws DBException if email not found
	 * @throws PasswordResetException if pr already exists
	 */
	public static String[] newPasswordResetEmail(String email) throws DBException, PasswordResetException {
		Session session = openSession();
		try {
			return DB.newPasswordReset(session,DB.getUserByEmail(session, email));
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param session
	 * @param user 
	 * @return {token, email}
	 * @throws DBException if username/email not found
	 * @throws PasswordResetException if pr already exists
	 */
	private static String[] newPasswordReset(Session session, DBUser user) throws DBException, PasswordResetException {
		if (user == null) throw new DBException("User not found");
		else if (user.getPasswordReset() != null) throw new PasswordResetException("Already reset");
		else if (user.getId().equals(DB.ROOT_ID)) throw new DBException("This account may not be modified");
		synchronized (prLock) {
			DBPasswordReset pr = new DBPasswordReset();
			String token;
			do {
				token = newToken();
			} while (session.get(DBPasswordReset.class, token) != null);
			pr.setDate(new Date());
			pr.setToken(token);
			pr.setUser(user);
			user.setPasswordReset(pr);
			try {
				session.beginTransaction();
				session.save(pr);
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error " + e.getMessage());
			}
			return new String[] {token, user.getEmail()};
		}
	}
	
	public static void resetPassword(String token, String password) throws DBException {
		Session session = openSession();
		try {
			DBPasswordReset pr = session.get(DBPasswordReset.class, token);
			if (pr == null) throw new DBException("Not found: " + token);
			DBUser user = pr.getUser();
			pr.setUser(null);
			user.setPasswordReset(null);
			user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
			try {
				session.beginTransaction();
				session.delete(pr);
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
		} finally {
			closeSession(session);
		}
	}
	
	public static void createArchiveToken(String comment) {
		synchronized (archiveTokenLock) {
			Session session = openSession();
			try {
				DBArchiveToken token = new DBArchiveToken();
				token.setToken(newToken());
				token.setComment(comment);
				token.setDate(new Date());
				try {
					session.beginTransaction();
					session.save(token);
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
					createArchiveToken(comment);
				}
			} finally {
				closeSession(session);
			}
		}
	}
	
	public static void deleteArchiveToken(long id) throws DBException {
		Session session = openSession();
		try {
			DBArchiveToken token = session.get(DBArchiveToken.class, id);
			try {
				session.beginTransaction();
				session.delete(token);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Not found: " + id);
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static List<ArchiveToken> getArchiveTokens() {
		Session session = openSession();
		try {
			return session.createQuery("from DBArchiveToken a order by a.date asc", DBArchiveToken.class).stream().map(at -> new ArchiveToken(at)).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	public static boolean isValidArchiveToken(String token) {
		Session session = openSession();
		try {
			return session.createQuery("from DBArchiveToken a where a.token='" + token + "'", DBArchiveToken.class).list().size() > 0;
		} finally {
			closeSession(session);
		}
	}
	
	public static enum PopularEpisode{
		HITS ("order by hits desc, upvotes desc, views desc"),
		VIEWS ("order by views desc, upvotes desc, hits desc"),
		UPVOTES ("order by upvotes desc, views desc, hits desc");
		public final String ORDER_BY;
		private PopularEpisode(String orderBy) {
			this.ORDER_BY=orderBy;
		}
	}
	
	public static enum PopularUser{
		HITS ("order by hits desc, upvotes desc, views desc, episodes desc"),
		VIEWS ("order by views desc, upvotes desc, hits desc, episodes desc"),
		UPVOTES ("order by upvotes desc, views desc, hits desc, episodes desc"),
		EPISODES ("order by episodes desc, upvotes desc, views desc, hits desc");
		public final String ORDER_BY;
		private PopularUser(String orderBy) {
			this.ORDER_BY=orderBy;
		}
	}
	
	public static enum PopularUserTime{
		ALL (""),
		WEEK ("and fbepisodes.date > (now() - interval '7 days')"),
		MONTH("and fbepisodes.date > (now() - interval '30 days')");
		public final String TIMELIMIT;
		private PopularUserTime(String timeLimit) {
			this.TIMELIMIT = timeLimit;
		}
	}
	
	private static final String POPULAR_QUERY = "select generatedid,id,link,title,date,max(childcount), max(hitscount) as hits,max(viewscount) as views, max(upvotescount) as upvotes\n" + 
			"from (\n" + 
			"    (select fbepisodes.generatedid,fbepisodes.id,fbepisodes.link,fbepisodes.title,fbepisodes.date,childcount, fbepisodes.viewcount as hitscount, count(*) as viewscount, 0 as upvotescount\n" + 
			"        from fbepisodes, fbepisodeviews\n" + 
			"        where fbepisodes.generatedid=fbepisodeviews.episode_generatedid\n" + 
			"        group by fbepisodes.generatedid)\n" + 
			"    union\n" + 
			"    (select fbepisodes.generatedid,fbepisodes.id,fbepisodes.link,fbepisodes.title,fbepisodes.date,childcount, fbepisodes.viewcount as hitscount, 0 as viewscount, count(*) as upvotescount\n" + 
			"        from fbepisodes,fbupvotes\n" + 
			"        where fbepisodes.generatedid=fbupvotes.episode_generatedid\n" + 
			"        group by fbepisodes.generatedid)\n" + 
			"    ) as countstuff\n" + 
			"group by generatedid,id,link,title,date \n";
	
	private static final String POPULAR_USERS_QUERY = "select username, author, date, max(episodescount) as episodes, max(hitscount) as hits, max(viewscount) as views, max(upvotescount) as upvotes from (\n" + 
			"(select fbusers.id as username, author,fbusers.date as date,count(*) as episodescount, 0 as hitscount, 0 as upvotescount, 0 as viewscount\n" + 
			"    from fbusers, fbepisodes\n" + 
			"    where fbusers.id=fbepisodes.author_id $TIMELIMIT \n" + 
			"    group by username, author)\n" + 
			"union\n" + 
			"(select fbusers.id as username, author,fbusers.date as date,0 as episodescount, sum(fbepisodes.viewcount) as hitscount, 0 as upvotescount, 0 as viewscount\n" + 
			"    from fbusers, fbepisodes\n" + 
			"    where fbusers.id=fbepisodes.author_id $TIMELIMIT \n" + 
			"    group by username, author)\n" + 
			"union\n" + 
			"(select fbusers.id as username, author,fbusers.date as date,0 as episodescount, 0 as hitscount, 0 as upvotescount, count(*) as viewscount\n" + 
			"    from fbusers, fbepisodes, fbepisodeviews\n" + 
			"    where fbusers.id=fbepisodes.author_id and fbepisodes.generatedid=fbepisodeviews.episode_generatedid $TIMELIMIT \n" + 
			"    group by username, author)\n" + 
			"union\n" + 
			"(select fbusers.id as username, author,fbusers.date as date,0 as episodescount, 0 as hitscount, count(*) as upvotescount, 0 as viewscount\n" + 
			"    from fbusers, fbepisodes, fbupvotes\n" + 
			"    where fbusers.id=fbepisodes.author_id and fbepisodes.generatedid=fbupvotes.episode_generatedid $TIMELIMIT \n" + 
			"    group by username, author)\n" + 
			") as countstuff group by username, author, date \n";
	
	@SuppressWarnings("unchecked")
	private static List<Episode> popularEpisodesReal(PopularEpisode pop) {
		Session session = openSession();
		try {
			
			return (ArrayList<Episode>)session.createNativeQuery(POPULAR_QUERY + pop.ORDER_BY + " \nlimit 100;").stream()
			.map(o->{
				Object[] x = (Object[])o;
				String id = idToMap((String)x[1]);
				String link = (String)x[2];
				String title = (String)x[3];
				Date date = (Date)x[4];
				int childCount = (Integer) x[5];
				long hits = ((BigInteger)x[6]).longValue();
				long views = ((BigInteger)x[7]).longValue();
				long upvotes = ((BigInteger)x[8]).longValue();
				return (new Episode(id,link,title,date,childCount,hits,views,upvotes, null, null /*TODO*/));
			}).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	private static Map<PopularEpisode, PopularEpisodeContainer> popularEpisodesMap = Collections.synchronizedMap(new EnumMap<PopularEpisode,PopularEpisodeContainer>(PopularEpisode.class)); 
	
	private static class PopularEpisodeContainer {
		public final List<Episode> episodes;
		public final long timestamp;
		public PopularEpisodeContainer(List<Episode> episodes) {
			this.episodes = episodes;
			timestamp = System.currentTimeMillis();
		}
	}
	
	public static List<Episode> popularEpisodes(PopularEpisode pop) {
		PopularEpisodeContainer puc = popularEpisodesMap.get(pop);
		if (puc == null || (System.currentTimeMillis()-puc.timestamp) > (1000*60*5)) { // if it has never been requested, or was last requested more than 5 minutes ago, reload it
			puc = new PopularEpisodeContainer(popularEpisodesReal(pop));
			popularEpisodesMap.put(pop,puc);
		}
		return puc.episodes;
	}
	
	@SuppressWarnings("unchecked")
	private static List<User> popularUsersReal(PopularUser pop, PopularUserTime time) {
		Session session = openSession();
		try {
			
			return (List<User>)session.createNativeQuery(POPULAR_USERS_QUERY.replace("$TIMELIMIT", time.TIMELIMIT) + pop.ORDER_BY + " \nlimit 100;").stream()
			.map(o->{
				Object[] x = (Object[])o;
				String username = (String)x[0];
				String author = (String)x[1];
				Date date = (Date)x[2];
				long episodes = ((BigInteger)x[3]).longValue();
				long hits = ((BigDecimal)x[4]).longValue();
				long views = ((BigInteger)x[5]).longValue();
				long upvotes = ((BigInteger)x[6]).longValue();
				return (new User(username,author,date,episodes,hits,views,upvotes));
			}).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	private static Map<PopularUserEnumContainer, PopularUserContainer> popularUsersMap = Collections.synchronizedMap(new HashMap<PopularUserEnumContainer,PopularUserContainer>()); 
	
	private static class PopularUserEnumContainer {
		public final PopularUser pop;
		public final PopularUserTime time;
		public PopularUserEnumContainer(PopularUser pop, PopularUserTime time) {
			this.pop = pop;
			this.time = time;
		}
		@Override
		public int hashCode() {
			java.util.Objects.hash(pop, time);
			final int prime = 31;
			int result = 1;
			result = prime * result + ((pop == null) ? 0 : pop.hashCode());
			result = prime * result + ((time == null) ? 0 : time.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (!(obj instanceof PopularUserEnumContainer)) return false;
			PopularUserEnumContainer other = (PopularUserEnumContainer) obj;
			return (pop == other.pop) && (time != other.time);
		}
	}
	
	private static class PopularUserContainer {
		public final List<User> users;
		public final long timestamp;
		public PopularUserContainer(List<User> users) {
			this.users = users;
			timestamp = System.currentTimeMillis();
		}
	}
	
	public static List<User> popularUsers(PopularUser pop, PopularUserTime time) {
		PopularUserEnumContainer puec = new PopularUserEnumContainer(pop, time);
		PopularUserContainer puc = popularUsersMap.get(puec);
		if (puc == null || (System.currentTimeMillis()-puc.timestamp) > (1000*60*5)) { // if it has never been requested, or was last requested more than 5 minutes ago, reload it
			puc = new PopularUserContainer(popularUsersReal(pop, time));
			popularUsersMap.put(puec,puc);
		}
		return puc.users;
	}
	
	private static final String ANNOUNCE_REPLACER = "AKJHFGAKJHEFGAF";
	private static final String GET_ANNOUNCEMENTS = 
			"SELECT fbannouncements.id,fbannouncements.date,fbannouncements.body,fbannouncements.author_id,fbusers.author, "
				+ "(SELECT COUNT(*) FROM fbannouncementviews WHERE fbannouncementviews.viewer_id='"+ANNOUNCE_REPLACER+"' AND fbannouncementviews.announcement_id=fbannouncements.id) as viewed "
			+ "FROM fbannouncements,fbusers "
			+ "WHERE fbusers.id=fbannouncements.author_id "
			+ "ORDER BY fbannouncements.date DESC;";
	private static final String GET_ANNOUNCEMENTS_ANON = 
			"SELECT fbannouncements.id,fbannouncements.date,fbannouncements.body,fbannouncements.author_id,fbusers.author, 0 "
			+ "FROM fbannouncements,fbusers "
			+ "WHERE fbusers.id=fbannouncements.author_id "
			+ "ORDER BY fbannouncements.date DESC;";
	
	/**
	 * Get all announcements
	 * @param username null if not logged in
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static List<Announcement> getAnnouncements(String username) {
		Session session = openSession();
		try {
			return (List<Announcement>)session.createNativeQuery((username==null)?GET_ANNOUNCEMENTS_ANON:GET_ANNOUNCEMENTS.replace(ANNOUNCE_REPLACER, username)).stream()
			.map(o->{
				Object[] x = (Object[])o;
				long id = ((BigInteger)x[0]).longValue();
				Date date = (Date)x[1];
				String body = (String)x[2];
				String authorId = (String)x[3];
				String author = (String)x[4];
				boolean read = true;
				if (username != null) read = ((BigInteger)x[5]).compareTo(BigInteger.ZERO) > 0;
				return new Announcement(id, date, body, authorId, author, read);
			}).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Add an announcement. Checks that the user is logged in and admin, and that body is not empty
	 * @param username
	 * @param body
	 * @throws DBException if body is empty, or user is not logged in or not admin
	 */
	public static void addAnnouncement(String username, String body) throws DBException {
		if (body.trim().length() == 0) throw new DBException("Announcement cannot be empty");
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			if (user.getLevel() < 100) throw new DBException("You are not authorized to do that");
			
			DBAnnouncement a = new DBAnnouncement();
			a.setAuthor(user);
			a.setBody(body);
			a.setDate(new Date());
			
			try {
				session.beginTransaction();
				session.save(a);
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Delete an announcement. Checks that the user is logged in and admin, and that the announcement exists
	 * @param username
	 * @param body
	 * @throws DBException if user is not logged in or not admin, or announcement does not exist
	 */
	public static void deleteAnnouncement(String username, long announcement) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			if (user.getLevel() < 100) throw new DBException("You are not authorized to do that");
			
			DBAnnouncement a = session.get(DBAnnouncement.class, announcement);
			if (a == null) throw new DBException("Announcement not found");
			
			try {
				session.beginTransaction();
				session.createNativeQuery("DELETE FROM fbannouncementviews WHERE announcement_id=" + a.getId() + ";").executeUpdate();
				session.createNativeQuery("DELETE FROM fbannouncements WHERE id=" + a.getId() + ";").executeUpdate();
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Marks an announcement as viewed. Checks that announcement and user are valid
	 * @param username 
	 * @param announcement
	 * @throws DBException if user is not logged in or announcement does not exist
	 */
	public static void markAnnouncementViewed(String username, long announcement) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			
			DBAnnouncement a = session.get(DBAnnouncement.class, announcement);
			if (a == null) throw new DBException("Announcement not found");
			
			DBAnnouncementView view = new DBAnnouncementView();
			view.setAnnouncement(a);
			view.setViewer(user);
			
			try {
				session.beginTransaction();
				session.save(view);
				session.merge(user);
				session.merge(a);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Marks all notification for the given user as viewed. Checks that user is valid
	 * @param username 
	 * @param announcement
	 * @param page first page is 1
	 * @throws DBException if user is not logged in
	 */
	public static List<Notification> getNotificationsForUser(String username, boolean all, int page) throws DBException {
		--page;
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			
			StringBuilder q = new StringBuilder();
			q.append("from DBNotification note where note.user.id='" + username + "' ");
			if (!all) q.append("and note.read=false ");
			q.append("order by note.date desc");
			
			List<Notification> result = session.createQuery(q.toString(), DBNotification.class).setMaxResults(100).setFirstResult(page*100).stream().map(Notification::new).collect(Collectors.toList());
			
			try {
				session.beginTransaction();
				session.createQuery("update DBNotification set read=true where user.id='" + username + "'").executeUpdate();
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
			return result;
			
		} finally {
			closeSession(session);
		}
	}
		
	public static void createNotification(String username, String body) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Username not found: " + username);
			
			DBNotification not = new DBNotification();
			not.setDate(new Date());
			not.setRead(false);
			not.setUser(user);
			not.setBody(body);
			
			try {
				session.beginTransaction();
				session.save(not);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static void updateUserNotificationSettings(String username, boolean commentSite, boolean commentMail, boolean childSite, boolean childMail) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Username not found: " + username);
			
			boolean change = false;
			
			if (user.isCommentSite() != commentSite) {
				user.setCommentSite(commentSite);
				change = true;
			}
			
			if (user.isCommentMail() != commentMail) {
				user.setCommentMail(commentMail);
				change = true;
			}
			
			if (user.isChildSite() != childSite) {
				user.setChildSite(childSite);
				change = true;
			}
			
			if (user.isChildMail() != childMail) {
				user.setChildMail(childMail);
				change = true;
			}
			
			if (change) try {
				session.beginTransaction();
				session.merge(user);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
		
	
	/**
	 * 
	 * @param username
	 * @return
	 * @throws DBException if username does not exist
	 */
	public static int unreadAnnouncements(String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			
			return ((BigInteger)(session.createNativeQuery(
					"SELECT COUNT(*) FROM fbannouncements "
					+ "WHERE fbannouncements.id NOT IN "
					+ "(SELECT announcement_id FROM fbannouncementviews WHERE fbannouncementviews.viewer_id='"+username+"' AND fbannouncementviews.announcement_id=fbannouncements.id);"
				).list().get(0))).intValue();
						
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param username
	 * @return
	 * @throws DBException if username does not exist
	 */
	public static int unreadNotifications(String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			
			int count = session.createQuery("select count(*) from DBNotification note where note.user.id='"+username+"' and note.read=false", Long.class).uniqueResult().intValue();

			return count;
						
		} finally {
			closeSession(session);
		}
	}
	
	public static void changeDonateButton(String username, String newHTML) throws DBException {
		Session session = openSession();
		try {
			
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("You must be logged in to do that");
			if (user.getLevel() < 100) throw new DBException("You are not authorized to do that");
			
			try {
				session.beginTransaction();
				DBSiteSetting button = session.get(DBSiteSetting.class, "donate_button");
				if (button == null) {
					button = new DBSiteSetting();
					button.setKey("donate_button");
					button.setValue(newHTML);
					session.save(button);
				} else {
					button.setValue(newHTML);
					session.merge(button);
				}
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Turns a branch into a new story root
	 * @param id id of top episode in branch, to become new root episode
	 * @return new id of new root episode
	 * @throws DBException if ep does not exist, ep is already a root, or
	 */
	public static String moveEpisodeToRoot(String oldMapId) throws DBException {
		Session session = openSession();
		try {
			
			DBEpisode ep = DB.getEpById(session, oldMapId);
			if (ep == null) throw new DBException("not found: " + oldMapId);
			if (ep.getDepth() == 1) return ep.getMap();
			
			synchronized (epLock) {
				final int oldParentDepth = ep.getDepth()-1;
				
				final int newRootId = DB.getRoots(session).stream()
						.map(root->DB.keyToArr(root.getMap())[0])
						.reduce((a,b)->a>b?a:b).get()+1;
				
				final String oldId = ep.getId();
				final String newId = "A" + newRootId;
				
				final int branchSize = ep.getChildCount();
				
				session.beginTransaction();
				try {
					session.createNativeQuery(
							"UPDATE fbepisodes\n" + 
							"SET parent_generatedid = null\n" + 
							"WHERE id='"+oldId+"';").executeUpdate();
					session.createNativeQuery("UPDATE fbepisodes\n" + 
							"SET id = replace(id,'"+oldId+"','"+newId+"'), depth = depth - "+oldParentDepth+"\n" + 
							"WHERE id LIKE '"+oldId+"%';").executeUpdate();
					int[] arr = DB.keyToArr(DB.idToMap(oldId));
					ArrayList<String> path = new ArrayList<>();
					StringBuilder sb = new StringBuilder();
					sb.append("A" + arr[0]);
					for (int i=1; i<arr.length; ++i) {
						path.add(sb.toString());
						sb.append("B" + arr[i]);
					}
					String where = path.stream().map(s->"id='" + s + "'").collect(Collectors.joining(" OR "));
					session.createNativeQuery("UPDATE fbepisodes\n" + 
							"SET childcount = childcount - "+branchSize+"\n" + 
							"WHERE " + where + ";").executeUpdate();
					session.getTransaction().commit();
				} catch (Exception e) {
					e.printStackTrace();
					session.getTransaction().rollback();
					throw new DBException("rollback");
				}
				return Integer.toString(newRootId);
			}
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Gets a list of names (primary keys) of all themes
	 * @return
	 */
	public static ArrayList<String> getThemeNames() {
		Session session = openSession();
		try {
			return session.createQuery("from DBTheme", DBTheme.class).stream().map(t->t.getName()).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
 	private static String newToken() {
		StringBuilder token = new StringBuilder();
		for (int i=0; i<32; ++i) token.append((char)('a'+Strings.r.nextInt(26)));
		return token.toString();
	}
 	
 	/**
 	 * Converts an map id ("1-2-3") to an int[] ({1,2,3})
 	 * @param s
 	 * @return
 	 * @throws NumberFormatException
 	 */
 	public static int[] keyToArr(String s) throws NumberFormatException {
		String[] arr = s.split("-");
		int[] ret = new int[arr.length];
		for (int i=0; i<arr.length; ++i) ret[i] = Integer.parseInt(arr[i]);
		return ret;
	}

	/**
	 * Correctly sorts key strings. 1-10 will come after 1-9 instead of after 1-1
	 */
	public static Comparator<String> keyStringComparator = new Comparator<String>() {
		@Override
		public int compare(String A, String B) {
			int[] a, b;
			try {
				a = keyToArr(A);
			} catch (NumberFormatException e) {
				throw new RuntimeException("Illegal keystring: " + A);
			}	
			try {
				b = keyToArr(B);
			} catch (NumberFormatException e) {
				throw new RuntimeException("Illegal keystring: " + B);
			}	
			for (int i=0; i<a.length && i<b.length; ++i) {
				Integer x = a[i];
				Integer y = b[i];
				int comp = x.compareTo(y);
				if (comp != 0) return comp;
			}
			return Integer.compare(a.length, b.length);
		}
	};
}
