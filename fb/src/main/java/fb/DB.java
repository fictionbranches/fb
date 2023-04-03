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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.automaton.RegExp;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.MustJunction;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;

import fb.db.DBAnnouncement;
import fb.db.DBAnnouncementView;
import fb.db.DBArchiveToken;
import fb.db.DBComment;
import fb.db.DBCommentSub;
import fb.db.DBEmailChange;
import fb.db.DBEpisode;
import fb.db.DBEpisodeTag;
import fb.db.DBEpisodeView;
import fb.db.DBFavEp;
import fb.db.DBFlaggedComment;
import fb.db.DBFlaggedEpisode;
import fb.db.DBModEpisode;
import fb.db.DBNotification;
import fb.db.DBPasswordReset;
import fb.db.DBPotentialUser;
import fb.db.DBSiteSetting;
import fb.db.DBTag;
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
import fb.objects.FlatEpisodeWithTags;
import fb.objects.FlatUser;
import fb.objects.ModEpisode;
import fb.objects.Notification;
import fb.objects.Tag;
import fb.objects.Theme;
import fb.objects.User;
import fb.util.Discord;
import fb.util.Strings;
import fb.util.Text;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.StreamingOutput;

public class DB {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	public static final String ROOT_ID = "fbadministrator1";
	public static final String MESSENGER_ID = "fictionbranches";
	
	static SessionFactory mySessionFactory;
	private static Object userLock = new Object(); // exists because multiple users could submit for the same username simultaneously 
	private static Object ecLock = new Object(); // exists because token is generated in java
	private static Object puLock = new Object(); // exists because token is generated in java
	private static Object prLock = new Object(); // exists because token is generated in java
	private static Object archiveTokenLock = new Object(); // exists because token is generated in java
	private static ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock(); // used to ensure no sessions are open when sessionfactory is closed
	private static final char EP_PREFIX = 'A';
	private static final char EP_INFIX = 'B';
	static {
		synchronized (userLock) { synchronized (ecLock) { synchronized (puLock) { synchronized (prLock) {
			mySessionFactory = newSessionFactory();
			LOGGER.info("Database success");
		} } } }
	}
	
	private static SessionFactory newSessionFactory() {
		Configuration configuration = new Configuration();
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
				LOGGER.error(e.getMessage(), e);
				System.exit(1);
				throw new RuntimeException(e);
			}
		} else {
			RuntimeException e = new RuntimeException(dbSettingsFilename + " not found");
			LOGGER.error(dbSettingsFilename + " not found", e);
			System.exit(1);
			throw e;
		}
		
		configuration.setProperty("hibernate.search.default.indexBase", InitWebsite.BASE_DIR + "/search-indexes");
		configuration.setProperty("hibernate.search.default.directory_provider", "filesystem");
		configuration.setProperty("hibernate.search.lucene.analysis_definition_provider", "fb.db.SearchDefinitionProvider");

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
		configuration.addAnnotatedClass(DBFavEp.class);
		configuration.addAnnotatedClass(DBCommentSub.class);
		configuration.addAnnotatedClass(DBTag.class);
		configuration.addAnnotatedClass(DBEpisodeTag.class);
		
		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties());
		try {
			return configuration.buildSessionFactory(builder.build());
		} catch (Exception e) {
			LOGGER.error("Error configuring db", e);
			System.exit(1);
			throw new RuntimeException(e);
		}
	}
	
	public static void closeSessionFactory() {
		try {
			LOGGER.warn("Trying to close Session factory");
			sessionLock.writeLock().lock();
			LOGGER.warn("Closing Session factory");
			mySessionFactory.close();
		} finally {
			sessionLock.writeLock().unlock();
			LOGGER.warn("Session closed");
		}
	}
	
	public static void restartSessionFactory() {
		try {
			LOGGER.warn("Trying to restart Session factory");
			sessionLock.writeLock().lock();
			LOGGER.warn("Closing Session factory");
			mySessionFactory.close();
			LOGGER.warn("Opening Session factory");
			mySessionFactory = newSessionFactory();
		} finally {
			sessionLock.writeLock().unlock();
			LOGGER.warn("Session restarted");
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
		
		public DBException(String message, Exception e) {
			super(message, e);
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
	 * Returns null if id does not exist
	 * @param id
	 * @return
	 */
	public static DBUser getUserById(Session session, String id) {
		if (id==null) return null;
		return session.get(DBUser.class, id.toLowerCase());
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
		return session.createQuery(query).uniqueResult();
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
	public static long addEp(long parentId, String link, String title, String body, String authorId, Date date) throws DBException {
		Session session = openSession();
		try {
			DBEpisode parent = session.get(DBEpisode.class, parentId);
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
			
			child.setNewMap(parent.getNewMap() + DB.EP_INFIX + childId);
			
			String updateCounts = "update DBEpisode ep set ep.childCount=(ep.childCount+1) where " + 
					DB.newMapToIdList(parent.getNewMap())
						.map(pathId -> "ep.generatedId=" + pathId)
						.collect(Collectors.joining(" or "));
			
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
					note.setType(DBNotification.NEW_CHILD_EPISODE);
					note.setDate(new Date());
					note.setRead(false);
					note.setUser(parent.getAuthor());
					note.setEpisode(child);
					session.save(note);
				}
				if (sendMailNotification && !InitWebsite.DEV_MODE) new Thread(()->
					Accounts.sendEmail(parent.getAuthor().getEmail(), "Someone added a new child to your episode", "<a href=\"https://"+Strings.getDOMAIN()+"/fb/user/" + child.getAuthor().getId() + "\">" + Text.escape(child.getAuthor().getAuthor()) + "</a> wrote a <a href=\"https://"+Strings.getDOMAIN()+"/fb/story/" + child.getGeneratedId() + "\">new child episode</a> of <a href=https://"+Strings.getDOMAIN()+"/fb/story/" + parent.getGeneratedId() +">" + Text.escape(parent.getTitle()) + "</a>")
				).start();
				
				session.getTransaction().commit();
				
				if (Strings.getDISCORD_NEW_EPISODE_HOOK() != null && Strings.getDISCORD_NEW_EPISODE_HOOK().length() > 0) {
					DBEpisode root = session.get(DBEpisode.class, DB.newMapToIdList(child.getNewMap()).findFirst().get());				
					final FlatEpisode flatEp = new FlatEpisode(child);
					final FlatEpisode flatRoot = new FlatEpisode(root);
					
					new Thread(()->Discord.notifyNewEpisode(flatEp, flatRoot)).start();
				}
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error("addEp rollback", e);
				throw new DBException("Database error", e);
			}
			return childId;
		} finally {
			closeSession(session);
		}
	}
	
	public static long addArchiveEp(long parentId, String link, String title, String body, String authorName, Date date) throws DBException {
		Session session = openSession();
		try {
			DBEpisode parent = session.get(DBEpisode.class, parentId);
			DBUser author = session.createQuery("from DBUser u where id like 'archive%' and author='" + authorName + "'",DBUser.class).setMaxResults(1).uniqueResult();
			
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
			
			Long childId;
			try {
				session.beginTransaction();
				if (newAuthor) session.save(author);
				else session.merge(author);
				childId = (Long) session.save(child);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error");
			}
			
			child.setNewMap(parent.getNewMap() + EP_INFIX + "" + childId);
			
			String updateCounts = "update DBEpisode ep set ep.childCount=(ep.childCount+1) where " + 
				DB.newMapToIdList(parent.getNewMap())
					.map(pathId -> "ep.generatedId=" + pathId)
					.collect(Collectors.joining(" or "));
			
			try {
				session.beginTransaction();
				session.save(child);
				
				session.createQuery(updateCounts).executeUpdate();
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error("addArchiveEp rollback",e);
				throw new DBException("Database error",e);
			}
			return childId;
		} finally {
			closeSession(session);
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
			Session session = openSession();
			try {				
				DBEpisode ep = session.get(DBEpisode.class, generatedId);
				if (ep == null) throw new DBException("Not found: " + generatedId);
				if (session.createQuery("select count(*) from DBEpisode ep where ep.parent.generatedId=" + ep.getGeneratedId(), Long.class).uniqueResult() > 0l) throw new DBException("Episode " + generatedId + " has children");
								
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
					
					session.createQuery("delete DBFlaggedEpisode flag where flag.episode.generatedId=" + generatedId).executeUpdate();
					session.createQuery("delete DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBFavEp fe where fe.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBNotification nt where nt.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBCommentSub x where x.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					session.createQuery("delete DBModEpisode x where x.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					
					session.createNativeQuery(
							"""
							delete from fbflaggedcomments 
							using fbcomments, fbepisodes 
							where fbflaggedcomments.comment_id=fbcomments.id and fbcomments.episode_generatedid=fbepisodes.generatedid and 
							fbepisodes.generatedid=
							""" + ep.getGeneratedId() + ";").executeUpdate();

					session.createNativeQuery(
							"""
							delete from fbnotifications 
							using fbcomments, fbepisodes 
							where fbnotifications.comment_id=fbcomments.id and fbcomments.episode_generatedid=fbepisodes.generatedid and 
							fbepisodes.generatedid=""" + ep.getGeneratedId() + ";").executeUpdate();

					
					session.createQuery("delete DBComment co where co.episode.generatedId=" + ep.getGeneratedId()).executeUpdate();
					
					for (DBUser user : mergeUsers) session.merge(user);
					for (DBFlaggedEpisode flag : deleteFlags) session.delete(flag);
					session.delete(ep);
										
					String updateCounts = "update DBEpisode ep set ep.childCount=(ep.childCount-1) where " + 
						DB.newMapToIdList(parentMap)
							.map(pathId -> "ep.generatedId=" + pathId)
							.collect(Collectors.joining(" or "));
					
					session.createQuery(updateCounts).executeUpdate();
					
					session.getTransaction().commit();
					
				} catch (Exception e) {
					session.getTransaction().rollback();
					LOGGER.error("deleteEp rollback", e);
					throw new DBException("Database error: " + e.getMessage(), e);
				}
			} finally {
				closeSession(session);
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
		Session session = openSession();
		try {
			DBUser author = getUserById(session, authorId);
			if (author == null) throw new DBException("Author does not exist");

			DBEpisode child = new DBEpisode();
			
			
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
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error("addRootEp rollback", e);
				throw new DBException("Database error");
			}
			return childId;
		} finally {
			closeSession(session);
			Story.updateRootEpisodesCache();
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
			
			return session.createQuery(query).stream().map(FlatUser::new).toList();
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Modifies an episode of the story
	 * @param id id of episode
	 * @param title new title of new episode
	 * @param body new body of new episode
	 * @param formParams 
	 * @param author new author of new episode
	 * @throws DBException if id not found
	 */
	public static void modifyEp(long generatedId, String link, String title, String body, String editorId, MultivaluedMap<String, String> formParams) throws DBException {
		Session session = openSession();
		try {
			session.beginTransaction();
			DB.modifyEp(session, generatedId, link, title, body, editorId);
			DB.updateTags(generatedId, editorId, formParams);
			session.getTransaction().commit();
		} catch (Exception e) {
			session.getTransaction().rollback();
			LOGGER.error(String.format("Database error modifying: %s", generatedId+""), e);
			throw new DBException("Database error", e);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Transaction must already be open when calling this method
	 * @param session
	 * @param generatedId
	 * @param link
	 * @param title
	 * @param body
	 * @param editorId
	 * @throws DBException
	 */
	private static void modifyEp(Session session, long generatedId, String link, String title, String body, String editorId) throws DBException {
		DBEpisode ep = session.get(DBEpisode.class, generatedId);
		if (ep == null) throw new DBException("Not found: " + generatedId);
		DBUser editor = getUserById(session, editorId);
		if (editor == null) throw new DBException("Editor not found: " + editorId);
		ep.setTitle(title);
		ep.setLink(link);
		ep.setBody(body);
		ep.setEditDate(new Date());
		ep.setEditor(editor);

		session.merge(ep);
		session.merge(editor);
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
			try {
				session.beginTransaction();
				session.save(newMod);
				session.merge(ep);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error(String.format("Database error submitting new modify: %s", generatedId+""), e);
				throw new DBException("Database error", e);
			}
		} finally {
			closeSession(session);
		}
	}
		
	/**
	 * If specified episode already has a mod request submitted, return 2.
	 * Else if a child episode exists which is not owned by owner of the specified episode, return 1.
	 * Else return 0
	 * @param id id of episode
	 * @return 0 if episode can be modified, 1 if child episodes not owned by owner exist, 2 if mod already exists
	 * @throws DBException if episode does not exist
	 */
	public static int checkIfEpisodeCanBeModified(long generatedId) throws DBException {
		Session session = openSession();
		try {
			DBEpisode episode = session.get(DBEpisode.class, generatedId);
			if (episode == null) throw new DBException("Not found: " + generatedId);
			
			DBModEpisode mod = session.createQuery("from DBModEpisode e where e.episode.generatedId=" + episode.getGeneratedId(), DBModEpisode.class).uniqueResult();
			if (mod != null) return 2;
			
			String q = "from DBEpisode ep where ep.author.id != '" + episode.getAuthor().getId() + "' and newMap like '" + episode.getNewMap() + "%'";
			
			return session.createQuery(q, DBEpisode.class).stream().findAny().isPresent()?1:0;
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
	public static EpisodeWithChildren getFullEp(long generatedId, String username) throws DBException {
		boolean canUpvote = false;
		boolean isFavorite = false;
		Session session = openSession();
		try {
			DBUser user = null;
			final DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			
			final long visitorCount = (Long)session.createQuery("select count(*) from DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId()).uniqueResult();
			final long upvotes = (Long)session.createQuery("select count(*) from DBUpvote uv where uv.episode.generatedId=" + ep.getGeneratedId()).uniqueResult();
			
			ArrayList<Comment> comments = session.createQuery("from DBComment c where c.episode.generatedId=" + ep.getGeneratedId() + " order by c.date", DBComment.class)
					.setMaxResults(PAGE_SIZE)
					.stream()
					.map(Comment::new)
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
						canUpvote = session
								.createQuery("from DBUpvote vote where vote.episode.generatedId=" + ep.getGeneratedId() + " and vote.user.id='" + user.getId() + "'")
								.uniqueResultOptional()
								.isEmpty();
						isFavorite = session
								.createQuery("from DBFavEp fav where fav.episode.generatedId=" + ep.getGeneratedId() + " and fav.user.id='" + user.getId() + "'")
								.uniqueResultOptional()
								.isPresent();
						if (session.createQuery("from DBEpisodeView ev where ev.episode.generatedId=" + ep.getGeneratedId() + " and ev.user.id='" + username + "'").uniqueResultOptional().isEmpty()) {
							try {
							session.beginTransaction();
							DBEpisodeView ev = new DBEpisodeView();
							ev.setEpisode(ep);
							ev.setUser(user);
							ev.setDate(new Date());
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
			
			final Map<String, Tag> allTags = DB.getAllTags().stream().collect(Collectors.toMap(tag -> tag.shortName, tag -> tag));
			
			final String query = CHILD_QUERY + ep.getGeneratedId() + CHILD_QUERY_POST;
			
			@SuppressWarnings("unchecked")
			final Stream<Object> stream = session.createNativeQuery(query).stream(); // TODO this is extremely slow, multiple seconds sometimes
			
			final ArrayList<Episode> children = stream.map(x -> {
				final Object[] arr = (Object[])x;
				final long childGeneratedId = ((BigInteger)arr[1]).longValue();
				final String newMap = (String)arr[2];
				final String link = (String)arr[3];
				final String title = (String)arr[4];
				final Date date = (Date)arr[5];
				final int childcount = (int) arr[6];
				final long hits = ((BigInteger)arr[7]).longValue();
				final long views = ((BigInteger)arr[8]).longValue();
				final long childUpvotes = ((BigInteger)arr[9]).longValue();
				final String authorId = (String)arr[10];
				final String authorName = (String)arr[11];
				final String tagNamesStr = (String) arr[12];
				
				final Set<Tag> tags;
				if (tagNamesStr.length() == 0) tags = null;
				else {
					tags = new HashSet<>();
					for (String name : tagNamesStr.split(",")) tags.add(allTags.get(name));
				}
				
				return new Episode(childGeneratedId,newMap,link,title,date,childcount,hits,views,childUpvotes,authorId,authorName, tags);
			}).collect(Collectors.toCollection(ArrayList::new));
						
			final String pathQuery;
			{
				final ArrayList<Long> list = new ArrayList<>(DB.newMapToIdList(ep.getNewMap()).toList());
				
				Collections.reverse(list);
				
				pathQuery = "from DBEpisode ep where " + 
					list.subList(0, Integer.min(20, list.size()))
						.stream()
						.map(pathId->"ep.generatedId="+pathId)
						.collect(Collectors.joining(" or ")) + 
					" order by ep.id desc";
			}
			
			final List<FlatEpisode> pathbox = session
					.createQuery(pathQuery,DBEpisode.class)
					.stream().map(FlatEpisode::new)
					.toList();
			
			boolean userIsSubscribedToComments;
			if (user == null) userIsSubscribedToComments = false;
			else userIsSubscribedToComments = session.createQuery("from DBCommentSub ev where ev.episode.generatedId=" + ep.getGeneratedId() + " and ev.user.id='" + username + "'").uniqueResultOptional().isPresent();

			final Set<Tag> tags = DB.getTagsForEp(session, ep);
			return new EpisodeWithChildren(ep, visitorCount, upvotes, user, canUpvote, isFavorite, children, comments, pathbox, userIsSubscribedToComments, tags);
		} finally {
			closeSession(session);
		}
	}
	
	private static Set<Tag> getTagsForEp(Session sesh, DBEpisode ep) {
		return Objects.requireNonNullElseGet(DB.getTagsForEpisodes(sesh, Set.of(ep.getGeneratedId())).get(ep.getGeneratedId()), Set::of);
	}
	
	private static final String CHILD_QUERY = """
			select parent_generatedid,generatedid,newmap,link,title,episodedate,childcount, max(hitscount) as hits,max(viewscount) as views, max(upvotescount) as upvotes, author_id, fbusers.author as author_name,array_to_string(array_agg(distinct fbtags.shortname),',')
			from (

			    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.newmap as newmap,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, count(*) as viewscount, 0 as upvotescount
			        from fbepisodes, fbepisodeviews
			        where fbepisodes.generatedid=fbepisodeviews.episode_generatedid
			        group by fbepisodes.generatedid)

			    union

			    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.newmap as newmap,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, 0 as viewscount, count(*) as upvotescount
			        from fbepisodes,fbupvotes
			        where fbepisodes.generatedid=fbupvotes.episode_generatedid
			        group by fbepisodes.generatedid)

			    union

			    (select fbepisodes.parent_generatedid,fbepisodes.generatedid,fbepisodes.newmap as newmap,fbepisodes.author_id,fbepisodes.link,fbepisodes.title,fbepisodes.date as episodedate, fbepisodes.childcount, fbepisodes.viewcount as hitscount, 0 as viewscount, 0 as upvotescount
			    from fbepisodes)

			    ) as countstuff
			LEFT JOIN fbusers ON fbusers.id=countstuff.author_id
			LEFT JOIN fbepisodetags ON countstuff.generatedid=fbepisodetags.episode_generatedid
			LEFT JOIN fbtags ON fbtags.id=fbepisodetags.tag_id
			where countstuff.parent_generatedid=""";
	
	private static final String CHILD_QUERY_POST = " group by parent_generatedid,generatedid,newmap,link,title,episodedate,childcount,author_id,author_name";
	
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
				upvote.setDate(new Date());
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
	
	public static void downvote(long generatedId, String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Not found: " + username);
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException ("Not found: " + generatedId);
			
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
	
	public static void favoriteEp(long generatedId, String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Not found: " + username);
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException ("Not found: " + generatedId);
			
			try {
				session.beginTransaction();
				DBFavEp favorite = new DBFavEp();
				favorite.setEpisode(ep);
				favorite.setUser(user);
				favorite.setDate(new Date());
				session.save(favorite);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error " + e.getMessage());
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static void unfavoriteEp(long generatedId, String username) throws DBException {
		Session session = openSession();
		try {
			DBUser user = DB.getUserById(session, username);
			if (user == null) throw new DBException("Not found: " + username);
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException ("Not found: " + generatedId);
			
			DBFavEp favorite = session.createQuery("from DBFavEp uv where uv.episode.generatedId=" + ep.getGeneratedId() + " and uv.user.id='" + user.getId() + "'", DBFavEp.class).uniqueResult();
			
			if (favorite != null)try {
				session.beginTransaction();
				favorite.setEpisode(null);
				favorite.setUser(null);
				session.delete(favorite);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				throw new DBException("Database error " + e.getMessage());
			}
		} finally {
			closeSession(session);
		}
	}
	
	public static FlatEpisode getFlatEp(long generatedId) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			return new FlatEpisode(ep);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param generatedId
	 * @param search
	 * @param page
	 * @param sort
	 * @param tagsShortNames these are NOT checked against the DB. Ensure they are valid before passing them in
	 * @return
	 * @throws DBException
	 */
	public static SearchResultList search(long generatedId, String search, int page, String sort, Set<String> tagsShortNames) throws DBException {
		Session session = openSession();
		page-=1;
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			FullTextSession sesh = Search.getFullTextSession(session);
			QueryBuilder qb = sesh.getSearchFactory().buildQueryBuilder().forEntity(DBEpisode.class).get();
						
			RegexpQuery idQuery = new RegexpQuery(new Term("newMap", (ep.getNewMap()+EP_INFIX).toLowerCase()+".*"), RegExp.NONE);
			Query searchQuery = qb.simpleQueryString().onFields("title","link","body").matching(search).createQuery();
						
			MustJunction combinedQueryBuilder = qb.bool()
					.must(searchQuery)
					.must(idQuery);
			
			for (String shortName : tagsShortNames) {
				Query tagQuery = qb.keyword().onField("lazytags.tag.shortName").matching(shortName).createQuery();
				combinedQueryBuilder = combinedQueryBuilder.must(tagQuery);
			}
			
			try {
				
				Sort sorter = switch (sort) {
					case "newest" -> new Sort(new SortField("date", SortField.Type.LONG, true));
					case "oldest" -> new Sort(new SortField("date", SortField.Type.LONG, false));
					default -> null;
				};
				
				FullTextQuery q = sesh.createFullTextQuery(combinedQueryBuilder.createQuery(), DBEpisode.class);
				if (sorter != null) q.setSort(sorter);
				q.setFirstResult(PAGE_SIZE*page);
				q.setMaxResults(PAGE_SIZE+1);
				
				@SuppressWarnings("unchecked")
				List<FlatEpisode> list = ((Stream<Object>)(q.stream()))
					.map(e -> (DBEpisode)e)
					.map(e->new FlatEpisode(e))
					.collect(Collectors.toCollection(ArrayList::new));	
								
				final boolean hasNext = list.size() > PAGE_SIZE;
				if (hasNext) {
					list = list.subList(0, PAGE_SIZE);
				}
				
				Map<Long, Set<Tag>> tags = DB.getTagsForEpisodes(sesh, list.stream().map(e -> e.generatedId).collect(Collectors.toSet()));
				
				Map<FlatEpisode, Set<Tag>> map = new LinkedHashMap<>();
				
				for (FlatEpisode e : list) {
					map.put(e, tags.get(e.generatedId));
				}
				
				return new SearchResultList(map, hasNext);
			} catch (Exception e) {
				throw new RuntimeException("Search exception on id " + generatedId + " with search query \"" + search + "\" -- "  + e + " -- " + e.getMessage(), e);
			}
		} finally {
			closeSession(session);
		}
	}
		
	public static Map<Long, Set<Tag>> getTagsForEpisodes(Session sesh, Set<Long> generatedIds) {
		
		if (generatedIds.isEmpty()) return Map.of();
		
		String query = """
				SELECT fbtags.id, fbtags.shortname, fbtags.longname, fbtags.description,
				fbusers.id AS userid, fbusers.author, fbtags.createddate, fbepisodetags.episode_generatedid
				FROM fbtags
				INNER JOIN fbepisodetags ON fbepisodetags.tag_id=fbtags.id
				LEFT JOIN fbusers ON fbtags.createdby_id=fbusers.id
				WHERE 
				""" + 
				generatedIds.stream()
					.map(generatedId -> "episode_generatedid=" + generatedId)
					.collect(Collectors.joining(" OR "));
		
		Map<Long, Set<Tag>> ret = new HashMap<>();
		
		for (Object x : sesh.createNativeQuery(query).list()) {
			Object[] arr = (Object[])x;
			int i = 0;
			long id = ((BigInteger)arr[i++]).longValue();
			String shortname = (String)arr[i++];
			String longname = (String)arr[i++];
			String description = (String)arr[i++];
			String createdbyid = (String)arr[i++];
			String createdbyauthor = (String)arr[i++];
			long createddate = ((BigInteger)arr[i++]).longValue();
			long generatedId = ((BigInteger)arr[i++]).longValue();
			Tag tag = new Tag(id, shortname, longname, description, createdbyid, createdbyauthor, createddate, null);
						
			ret.computeIfAbsent(generatedId, g -> new HashSet<Tag>())
			.add(tag);
			
		}
		
		return ret;

	}

	public static Set<Tag> getTagsFromShortNames(Session sesh, Stream<String> shortNames) {
		String query = """
				SELECT fbtags.id, fbtags.shortname, fbtags.longname, fbtags.description,
				fbusers.id AS userid, fbusers.author, fbtags.createddate
				FROM fbtags
				LEFT JOIN fbusers ON fbtags.createdby_id=fbusers.id
				WHERE 
				""" + 
				shortNames
					.map(generatedId -> "episode_generatedid=" + generatedId)
					.collect(Collectors.joining(" OR "));
		
		Set<Tag> ret = new HashSet<>();
		
		for (Object x : sesh.createNativeQuery(query).list()) {
			Object[] arr = (Object[])x;
			int i = 0;
			long id = ((BigInteger)arr[i++]).longValue();
			String shortname = (String)arr[i++];
			String longname = (String)arr[i++];
			String description = (String)arr[i++];
			String createdbyid = (String)arr[i++];
			String createdbyauthor = (String)arr[i++];
			long createddate = ((BigInteger)arr[i++]).longValue();
			ret.add(new Tag(id, shortname, longname, description, createdbyid, createdbyauthor, createddate, null));
		}
		
		return ret;

	}
	
	/**
	 * List of FlatEpisodes, along with a FlatUser and boolean.
	 * Used by DB.search()
	 */
	public static class SearchResultList {
		public final Map<FlatEpisode, Set<Tag>> episodes;
		public final boolean morePages;
		public SearchResultList(Map<FlatEpisode, Set<Tag>> episodes, boolean morePages) {
			this.episodes = episodes;
			this.morePages = morePages;
		}
	}

	
	public static AuthorSearchResult searchUser(String search, int page) {
		Session session = openSession();
		page-=1;
		try {
			FullTextSession sesh = Search.getFullTextSession(session);
			QueryBuilder qb = sesh.getSearchFactory().buildQueryBuilder().forEntity(DBUser.class).get();
			
			Query searchQuery = qb.simpleQueryString().onFields("author","id").matching(search).createQuery();
			
			@SuppressWarnings("unchecked")
			Stream<Object> stream = sesh.createFullTextQuery(searchQuery, DBUser.class)
					.setFirstResult(PAGE_SIZE*page)
					.setMaxResults(PAGE_SIZE+1)
					.stream();
			
			List<FlatUser> list = stream.map(e->new FlatUser((DBUser)e))
					.collect(Collectors.toCollection(ArrayList::new));
			
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
			LOGGER.warn("Beginning indexing");
			sesh.createIndexer().startAndWait();
			LOGGER.warn("Done indexing " + ((System.nanoTime()-start)/1000000000.0));
		} catch (InterruptedException e) {
			LOGGER.error("Interrupted search index", e);
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
	public static FlatEpisode getEpByLegacyId(String oldId) throws DBException {	
		Session session = openSession();
		try {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);

			query.select(root).where(cb.equal(root.get("legacyId"), oldId));

			Optional<DBEpisode> result = session.createQuery(query).uniqueResultOptional();

			if (result.isEmpty()) throw new DBException("Not found: " + oldId);
			else return new FlatEpisode(result.get());
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param oldMap like 1-2-3
	 * @return
	 * @throws DBException
	 */
	public static FlatEpisode getEpByOldMap(String oldMap) throws DBException {	
		Session session = openSession();
		try {
			CriteriaBuilder cb = session.getCriteriaBuilder();
			CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
			Root<DBEpisode> root = query.from(DBEpisode.class);

			query.select(root).where(cb.equal(root.get("oldMap"), EP_PREFIX + oldMap.replace('-', EP_INFIX)));
			
			Optional<DBEpisode> result = session.createQuery(query).uniqueResultOptional();
			
			if (result.isEmpty()) throw new DBException("Not found: " + oldMap);
			else return new FlatEpisode(result.get());
		} finally {
			closeSession(session);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static Map<FlatEpisode, Set<Tag>> getRecentsPage(Session session, long generatedId, int page, boolean reverse, String tagFilter) {
				
		List<String> whereClausesToAnd = new ArrayList<>();
		if (generatedId != 0) {
			final String rootClause = "newmap='" + EP_PREFIX + generatedId + "' OR newmap LIKE '" + EP_PREFIX + generatedId + EP_INFIX + "%'";
			whereClausesToAnd.add(rootClause);
		}
		if (tagFilter != null && tagFilter.length() > 0) {
			whereClausesToAnd.add("EXISTS (SELECT fbepisodetags.id FROM fbepisodetags INNER JOIN fbtags ON fbtags.id=fbepisodetags.tag_id WHERE episode_generatedid=fbepisodes.generatedid AND fbtags.shortname='"+tagFilter+"')");
		}
		
		final String whereClause = whereClausesToAnd.isEmpty() ? "" : ("WHERE " + whereClausesToAnd.stream().collect(Collectors.joining(") AND (", "(", ")")));
		
		String query = """
				SELECT
				    fbepisodes.generatedid,
				    fbepisodes.oldmap,
				    fbepisodes.newmap,
				    fbepisodes.title,
				    fbepisodes.link,
				    fbusers.id AS userid,
				    fbusers.author,
				    fbusers.avatar,
				    fbepisodes.body,
				    fbepisodes.date,
				    fbepisodes.childcount,
				    fbepisodes.parent_generatedid,
				    fbepisodes.viewcount,
				    array_to_string(array_agg(distinct fbtags.shortname),',')
				FROM fbepisodes
				LEFT JOIN fbusers ON fbusers.id=fbepisodes.author_id
				LEFT JOIN fbepisodetags ON fbepisodetags.episode_generatedid=fbepisodes.generatedid
				LEFT JOIN fbtags ON fbtags.id=fbepisodetags.tag_id
				$WHERECLAUSE
				GROUP BY generatedid,oldmap,newmap,title,link,userid,author,avatar,body,fbepisodes.date,childcount,parent_generatedid,viewcount
				ORDER BY date $ORDER
				OFFSET $PAGE_OFFSET
				LIMIT $PAGE_SIZE
				"""
				.replace("$WHERECLAUSE", whereClause)
				.replace("$ORDER", reverse?"ASC":"DESC")
				.replace("$PAGE_OFFSET", String.valueOf(PAGE_SIZE*page))
				.replace("$PAGE_SIZE", String.valueOf(PAGE_SIZE))
				;
												
		Map<String, Tag> allTags = DB.getAllTags(session).map(Tag::new).collect(Collectors.toMap(tag -> tag.shortName, tag -> tag));
		
		return ((Stream<Object>)session.createNativeQuery(query).stream()).map(x -> {
			Object[] arr = (Object[])x;
			
			long epid = ((BigInteger)arr[0]).longValue();
			String oldMap = (String)arr[1];
			String newMap = (String)arr[2];
			String title = (String)arr[3];
			String link = (String)arr[4];
			String authorId = (String)arr[5];
			String authorName = (String)arr[6];
			String authorAvatar = (String)arr[7];
			String body = (String)arr[8];
			Date date = (Date)arr[9];
			Date editDate = null;
			String editorId = null;
			String editorName = null;
			int childCount = (int)arr[10];
			Long parentId = arr[11]==null ? null : ((BigInteger)arr[11]).longValue();
			long hits = ((BigInteger)arr[12]).longValue();
			
			String tagNamesStr = (String)arr[13];
			final Set<Tag> tags;
			if (tagNamesStr.length() == 0) tags = Set.of();
			else {
				tags = new HashSet<>();
				for (String name : tagNamesStr.split(",")) tags.add(allTags.get(name));
			}
			
			return new FlatEpisodeWithTags(epid, oldMap, newMap, title, link, authorId, authorName, authorAvatar, 
					body, date, editDate, editorId, editorName, childCount, parentId, hits, tags);
		}).collect(Collectors.toMap(fewt -> (FlatEpisode)fewt, fewt -> fewt.tags));
		
	}

	public static Map<FlatEpisode, Set<Tag>> getRecentsPage(long generatedId, int page, boolean reverse, String tagFilter) throws DBException {
		Session session = openSession();
		page -= 1;
		try {
			if (generatedId != 0) { // make sure we're using a root episode
				DBEpisode ep = session.get(DBEpisode.class, generatedId);
				if (ep == null) throw new DBException("Not found: " + generatedId);
				generatedId = DB.newMapToIdList(ep.getNewMap()).findFirst().get();
			}
			return Collections.unmodifiableMap(getRecentsPage(session, generatedId, page, reverse, tagFilter));
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * List of FlatEpisodes, along with a FlatUser and boolean.
	 * Serves multiple purposes
	 */
	public static class RecentsResultList {
		public final FlatUser user;
		public final Map<FlatEpisode, Set<Tag>> episodes;
		public final int numPages;
		public RecentsResultList(FlatUser user, Map<FlatEpisode, Set<Tag>> episodes, int numPages) {
			this.user = user;
			this.episodes = episodes;
			this.numPages = numPages;
		}
	}
	
	/**
	 * Get 100 (PAGE_SIZE) most recent episodes of a particular story, or of all stories
	 * @param story root id for story, or 0 to get all stories
	 * @param num number of episodes to get
	 * @return
	 * @throws DBException
	 */
	public static RecentsResultList getRecents(long generatedId, int page, boolean reverse, String tagFilter) throws DBException {
		Session session = openSession();
		page-=1;
		try {
			if (generatedId != 0l) {
				DBEpisode ep = session.get(DBEpisode.class, generatedId);
				if (ep == null) throw new DBException("Not found: " + generatedId);
				generatedId = DB.newMapToIdList(ep.getNewMap()).findFirst().get();
			}
			
			String sql = "SELECT COUNT(*) FROM fbepisodes ";
			
			if (tagFilter != null) {
				sql += "INNER JOIN fbepisodetags ON fbepisodetags.episode_generatedid=fbepisodes.generatedid ";
				sql += "INNER JOIN fbtags ON fbtags.id=fbepisodetags.tag_id ";
				sql += "WHERE fbtags.shortname='" + tagFilter + "' ";
				if (generatedId != 0) sql += "AND (newmap='" + EP_PREFIX+Long.toString(generatedId) + "' OR newmap LIKE '" + EP_PREFIX + Long.toString(generatedId) + EP_INFIX + "%" + "')";				
			} else {
				if (generatedId != 0) sql += "WHERE newmap='" + EP_PREFIX+Long.toString(generatedId) + "' OR newmap LIKE '" + EP_PREFIX + Long.toString(generatedId) + EP_INFIX + "%" + "'";		
			}
						
			int totalCount = ((BigInteger)(session.createNativeQuery(sql).uniqueResult())).intValue();
			
			Map<FlatEpisode, Set<Tag>> alist = getRecentsPage(session, generatedId, page, reverse, tagFilter);
									
			return new RecentsResultList(null, Collections.unmodifiableMap(alist), totalCount/PAGE_SIZE+1);
		}finally {
			closeSession(session);
		}
	}
	
	/**
	 * 
	 * @param username requestor
	 * @param page most recent page is 1
	 * @return
	 * @throws DBException
	 */
	public static CommentResultList getRecentComments(int page) {
		Session session = openSession();
		page-=1;
		try {
			
			String sql = "SELECT COUNT(*) FROM fbcomments";
			int totalCount = ((BigInteger)(session.createNativeQuery(sql).uniqueResult())).intValue();
			
			List<Comment> list = session.createNativeQuery(
					"SELECT * FROM fbcomments " + 
					" ORDER BY date DESC" + 
					" OFFSET " + (COMMENT_PAGE_SIZE*page) +
					" LIMIT " + COMMENT_PAGE_SIZE +
					"",
				DBComment.class)
					.stream()
					.map(Comment::new)
					.toList();
												
			return new CommentResultList(list, totalCount/COMMENT_PAGE_SIZE+1);
		}finally {
			closeSession(session);
		}
	}
	private static final int COMMENT_PAGE_SIZE = 50;
	
	public static StreamingOutput getOutlinePage(Cookie token, long generatedId, int pageNum) {
		final int page = pageNum - 1;
		final int OUTLINE_PAGE_SIZE = 300;
		return new StreamingOutput() {
			public void write(OutputStream os) throws IOException {
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
					DBEpisode ep = session.get(DBEpisode.class, generatedId);
					if (ep == null) {
						writer.write(Strings.getFile("generic.html", user).replace("$EXTRA", "Not found: " + generatedId));
						writer.flush();
						return;
					}
					int minDepth = ep.episodeDepthFromNewMap();
					
					writer.write("\n<!-- BEGIN PAGE " + page + "-->\n");
					
					String query = "" 
							+ "select generatedId, link, array_length(CAST(string_to_array(replace(replace(fbepisodes.newmap,'"+EP_INFIX+"','-'),'"+EP_PREFIX+"',''),'-') AS bigint[]),1) as depth, fbusers.id, fbusers.author "
							+ "from fbepisodes,fbusers where fbepisodes.newmap like '" + ep.getNewMap() + EP_INFIX + "%' and fbepisodes.author_id=fbusers.id "
							+ "order by (CAST(string_to_array(replace(replace(fbepisodes.newmap,'"+EP_INFIX+"','-'),'"+EP_PREFIX+"',''),'-') AS bigint[])) asc LIMIT " + OUTLINE_PAGE_SIZE + " OFFSET " + (page*OUTLINE_PAGE_SIZE) + "";
						try {							
							final ReturnedSomething returnedSomething = new ReturnedSomething();

							@SuppressWarnings("unchecked")
							Stream<Object[]> stream = session.createNativeQuery(query).stream();
							stream.forEach(x -> {
								long id = ((BigInteger) x[0]).longValue();
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

							if (returnedSomething.get()) writer.write("<div class=\"next\"><a href=\"/fb/outline/" + generatedId + "?page=" + (page + 2) + "\">next</a></div>");

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
	}
	
	private static class ReturnedSomething {
		private boolean value = false;
		public void set() { value = true; }
		public boolean get() { return value; }
	}
	
	public static List<FlatEpisode> getPath(long generatedId) throws DBException {		
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
									
			String query = "select * from fbepisodes where " + 
				DB.newMapToIdList(ep.getNewMap()).map(id->"generatedid="+id).collect(Collectors.joining(" or ")) + 
				" order by array_length(CAST(string_to_array(replace(replace(fbepisodes.newmap,'"+EP_INFIX+"','-'),'"+EP_PREFIX+"',''),'-') AS bigint[]),1) asc";
			return session.createNativeQuery(query,DBEpisode.class).stream()
				.map(FlatEpisode::new)
				.toList();
			
		} finally {
			closeSession(session);
		}
	}
	
	public static List<FlatEpisode> getFullStory(long generatedId) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("Not found: " + generatedId);
			
			List<Long> ids = DB.newMapToIdList(ep.getNewMap()).toList();
			if (ids.size() > 30) ids = ids.subList(ids.size()-30, ids.size());
			
			String query = "select * from fbepisodes where " + 
				ids.stream().map(id->"generatedid="+id).collect(Collectors.joining(" or ")) + 
				" order by array_length(CAST(string_to_array(replace(replace(fbepisodes.newmap,'"+EP_INFIX+"','-'),'"+EP_PREFIX+"',''),'-') AS bigint[]),1) asc";
			return session.createNativeQuery(query,DBEpisode.class).stream()
				.map(FlatEpisode::new)
				.toList();

		} finally {
			closeSession(session);
		}
	}
	
	public static Stream<Long> newMapToIdList(String newMap) {
		String[] arr = newMap.substring(1,newMap.length()).split(""+EP_INFIX);
		return Arrays.stream(arr).map(Long::parseLong);
	}
	
	public static FlatEpisode[] getRoots() {
		Session session = openSession();
		try {
			return getRoots(session).map(FlatEpisode::new).toArray(size->new FlatEpisode[size]);
		} finally {
			closeSession(session);
		}
	}
	
	public static Stream<DBEpisode> getRoots(Session session) {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBEpisode> query = cb.createQuery(DBEpisode.class);
		Root<DBEpisode> root = query.from(DBEpisode.class);			
					
		query.select(root).where(cb.isNull(root.get("parent"))).orderBy(cb.asc(root.get("date")));
		
		return session.createQuery(query).stream();
}
	
	/**
	 * Change a user's author name
	 * @param id id of user
	 * @param newAuthor new author name
	 * @throws DBException if id not found
	 */
	public static void changeBodyTextWidth(String id, int newBodyTextWidth) throws DBException {
		Session session = openSession();
		try {
			DBUser user = getUserById(session, id);
			if (user == null) throw new DBException("User id does not exist");
			user.setBodyTextWidth(newBodyTextWidth);
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
			DBTheme theme = session.get(DBTheme.class, newTheme);
			if (theme == null) throw new DBException("Theme " + newTheme + " does not exist");
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
			if (user == null) throw new DBException("User id does not exist");
			return new FlatUser(user);
		} finally {
			closeSession(session);
		}
	}
	
	private static final int PAGE_SIZE = 100;
	public static AuthorProfileResult getUserProfile(String userId, int page) throws DBException {
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
			
			List<FlatEpisode> list = session.createQuery(query)
					.setFirstResult(PAGE_SIZE*page)
					.setMaxResults(PAGE_SIZE+1).stream()
					.map(FlatEpisode::new)
					.collect(Collectors.toCollection(ArrayList::new));
			boolean hasNext = list.size() > PAGE_SIZE;
			return new AuthorProfileResult(new FlatUser(user), hasNext?list.subList(0, PAGE_SIZE):list, hasNext);
		} finally {
			closeSession(session);
		}
	}
	
	public static class AuthorProfileResult {
		public final FlatUser user;
		public final List<FlatEpisode> episodes;
		public final boolean morePages;
		public AuthorProfileResult(FlatUser user, List<FlatEpisode> episodes, boolean morePages) {
			this.user = user;
			this.episodes = episodes;
			this.morePages = morePages;
		}
	}

	public static class CommentResultList {
		public final List<Comment> comments;
		public final int numPages;
		public CommentResultList(List<Comment> comments, int numPages) {
			this.comments = comments;
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
			return checkPasswordImpl(hashedPassword, password);
		} finally {
			closeSession(session);
		}
	}
	
	/**
	 * Given a FlatUser and possible plaintext password, check if the possible password is valid for the user
	 * @param user FlatUser instance with user.password equal to hashed password
	 * @param password plaintext possible password
	 * @return true if password matches
	 */
	public static boolean checkPassword(FlatUser user, String password) {
		if (user == null) return false;
		return checkPasswordImpl(user.hashedPassword, password);
	}
	
	/**
	 * Given a hashed password and possible plaintext password, check if the possible password is valid for the user
	 * @param hashedPassword hashed password
	 * @param password plaintext possible password
	 * @return true if password matches
	 */
	private static boolean checkPasswordImpl(String hashedPassword, String password) {
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
	 * @param authorId id of user doing the commenting
	 * @param commentText
	 * @return id of newly created comment
	 * @throws DBException
	 */
	public static long addComment(long generatedId, String authorId, String commentText) throws DBException {
		Date commentDate = new Date();
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			DBUser commenter = getUserById(session, authorId);

			if (ep == null) throw new DBException("Episode not found: " + generatedId);
			if (commenter == null) throw new DBException("Author does not exist");

			DBComment comment = new DBComment();
			
			comment.setText(commentText);
			comment.setDate(commentDate);
			comment.setEditDate(commentDate);
			comment.setEpisode(ep);
			comment.setUser(commenter);
			comment.setEditor(commenter);
			
			boolean sendSiteNotification = false;
			boolean sendMailNotification = false;
			
			if (!comment.getEpisode().getAuthor().getId().equals(commenter.getId())) { // don't sent notification to episode author if they wrote the comment
				sendSiteNotification = comment.getEpisode().getAuthor().isCommentSite();
				sendMailNotification = comment.getEpisode().getAuthor().isCommentMail();
			}
			
			long commentID;
						
			try {
				session.beginTransaction();
				session.save(comment);
				session.merge(ep);
				session.merge(commenter);
				
				commentID = comment.getId();
				
				if (sendSiteNotification) {
					DBNotification note = new DBNotification();
					note.setType(DBNotification.NEW_COMMENT_ON_OWN_EPISODE);
					note.setDate(new Date());
					note.setRead(false);
					note.setUser(comment.getEpisode().getAuthor());
					note.setComment(comment);
					session.save(note);
				}
				
				if (sendMailNotification && !InitWebsite.DEV_MODE) {
					final String email = comment.getEpisode().getAuthor().getEmail();
					final String authorid = commenter.getId();
					final String authorauthor = commenter.getAuthor();
					final String epTitle = comment.getEpisode().getTitle();
					final long cid = comment.getId();
					final long gid = generatedId;
					new Thread(()-> // send the email
						Accounts.sendEmail(email, "Someone commented on your episode", 
								"<a href=\"https://"+Strings.getDOMAIN()+"/fb/user/" + authorid + "\">" + Text.escape(authorauthor) + "</a> left a <a href=\"https://"+Strings.getDOMAIN()+"/fb/story/" + gid + "#comment" + cid + "\">comment</a> on " + Text.escape(epTitle))
					).start();
				}
				
				boolean commenterIsSubscribed = false;
				
				List<DBCommentSub> list = session.createQuery("from DBCommentSub cs where cs.episode.generatedId=" + ep.getGeneratedId() + " and cs.user.id!='" + ep.getAuthor().getId() + "'", DBCommentSub.class).list();
				for (DBCommentSub cs : list) {
					
					// if the subber is the comment author, skip 
					// if the subber is the episode author, skip // handled in hql query above
					
					if (commenter.getId().equals(cs.getUser().getId())) {
						// subber is comment auther
						// therefore commenter is already subscribed
						commenterIsSubscribed = true;
					}
					
					DBNotification note = new DBNotification();
					note.setType(DBNotification.NEW_COMMENT_ON_SUBBED_EPISODE);
					note.setDate(new Date());
					note.setRead(false);
					note.setUser(cs.getUser());
					note.setComment(comment);
					session.save(note);
				}
				
				if (!commenter.getId().equals(ep.getAuthor().getId()) && !commenterIsSubscribed) { 
					DBCommentSub dcs = new DBCommentSub();
					dcs.setDate(new Date());
					dcs.setEpisode(ep);
					dcs.setUser(commenter);
					session.save(dcs);
				}
				
				session.getTransaction().commit();
				
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error(e.getMessage());
				LOGGER.error(e.toString());
				e.printStackTrace();
				throw new DBException("Database error " + e.getMessage());
			}
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
				session.createQuery("delete DBNotification n where n.comment.id=" + commentId).executeUpdate();
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
	public static void flagEp(long generatedId, String authorId, String flagText) throws DBException {
		Session session = openSession();
		try {
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			DBUser author = getUserById(session, authorId);

			if (ep == null) throw new DBException("Episode not found: " + generatedId);
			if (author == null) throw new DBException("Author does not exist");

			DBFlaggedEpisode flag = new DBFlaggedEpisode();
			
			flag.setText(flagText);
			flag.setDate(new Date());
			flag.setEpisode(ep);
			flag.setUser(author);
						
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
	}
	
	public static List<FlaggedEpisode> getFlags() {
		Session session = openSession();
		try {
			return session.createQuery("from DBFlaggedEpisode flag order by flag.date desc", DBFlaggedEpisode.class).stream().map(FlaggedEpisode::new).toList();
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
			return session.createQuery("from DBFlaggedComment mod order by mod.date desc", DBFlaggedComment.class).stream().map(FlaggedComment::new).toList();
		} finally {
			closeSession(session);
		}
	}
	
	public static List<ModEpisode> getMods() {
		Session session = openSession();
		try {
			return session.createQuery("from DBModEpisode mod order by mod.date desc", DBModEpisode.class).stream().map(ModEpisode::new).toList();
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
	
	/**
	 * new_child_episode:
	 *   id
	 *   date
	 *   user
	 *   read
	 *   type
	 *   
	 *   episode
	 *   sender
	 *   approved
	 */
	public static void clearMod(long id, boolean accepted, String modUsername) throws DBException {
		Session session = openSession();
		try {
			DBModEpisode mod = session.get(DBModEpisode.class, id);
			if (mod == null) throw new DBException("Flag not found: " + id);
			DBEpisode ep = mod.getEpisode();
			try {
				session.beginTransaction();
				if (accepted) DB.modifyEp(session, ep.getGeneratedId(), mod.getLink(), mod.getTitle(), mod.getBody(), ep.getAuthor().getId());
				
				DBNotification note = new DBNotification();
				note.setDate(new Date());
				note.setUser(ep.getAuthor());
				note.setRead(false);
				note.setType(DBNotification.MODIFICATION_RESPONSE);
				note.setEpisode(ep);
				note.setSender(DB.getUserById(session, modUsername));
				note.setApproved(accepted);
				
				session.delete(mod);
				session.merge(ep);
				session.save(note);
			session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error(String.format("Database error modifying: %s", id), e);
				throw new DBException("Database error", e);
			}
		} finally {
			closeSession(session);
		}
		
	}
	
	private static Stream<DBEmailChange> getPruneEmailQueue(Session session, Date yesterday) {		
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBEmailChange> query = cb.createQuery(DBEmailChange.class);
		Root<DBEmailChange> root = query.from(DBEmailChange.class);
		query.select(root).where(cb.lessThan(root.get("date"), yesterday));
		return session.createQuery(query).stream();
	}
	
	private static Stream<DBPotentialUser> getPrunePuQueue(Session session, Date yesterday) {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBPotentialUser> query = cb.createQuery(DBPotentialUser.class);
		Root<DBPotentialUser> root = query.from(DBPotentialUser.class);
		query.select(root).where(cb.lessThan(root.get("date"), yesterday));
		return session.createQuery(query).stream();
	}
	
	private static Stream<DBPasswordReset> getPrunePrQueue(Session session, Date yesterday) {
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<DBPasswordReset> query = cb.createQuery(DBPasswordReset.class);
		Root<DBPasswordReset> root = query.from(DBPasswordReset.class);
		query.select(root).where(cb.lessThan(root.get("date"), yesterday));
		return session.createQuery(query).stream();
	}
	
	public static void pruneQueues() {
		Session session = openSession();
		try {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.HOUR_OF_DAY, -24);
			Date yesterday = cal.getTime();
			Stream<DBEmailChange> ecList = getPruneEmailQueue(session, yesterday);
			Stream<DBPotentialUser> puList = getPrunePuQueue(session, yesterday);
			Stream<DBPasswordReset> prList = getPrunePrQueue(session, yesterday);
			
			try {
				session.beginTransaction();
				ecList.forEach(ec->{
					DBUser user = ec.getUser();
					user.setEmailChange(null);
					ec.setUser(null);
					session.delete(ec);
					session.merge(user);
				});
				prList.forEach(pr->{
					DBUser user = pr.getUser();
					user.setPasswordReset(null);
					pr.setUser(null);
					session.delete(pr);
					session.merge(user);
				});
				puList.forEach(session::delete);
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
			
				DBEmailChange asdf;
				do {
					token = newToken();
					asdf = session.get(DBEmailChange.class, token);
				} while (asdf != null);
			
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
			
				defaultTheme = session.get(DBTheme.class, Theme.DEFAULT_NAME);
				if (defaultTheme == null) {
					defaultTheme = new DBTheme();
					defaultTheme.setName(Theme.DEFAULT_NAME);
					defaultTheme.setCss(Theme.DEFAULT_CSS);
					addDefaultTheme = true;
				}
				user.setTheme(defaultTheme);
			
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
	
	/**
	 * Sets the account creation date of a user to be the older of the current creation date and the oldest episode creation date
	 * @param session
	 * @param username
	 * @throws DBException
	 */
	private static void updateAccountDate(Session session, String username) throws DBException {
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
			
				ArrayList<String> notFound = new ArrayList<>(2);
				if (a==null) notFound.add(userA);
				if (b==null) notFound.add(userB);
				if (!notFound.isEmpty()) throw new DBException("Not found: " + notFound);
			
			userA = userA.toLowerCase();
			userB = userB.toLowerCase();
			try {
				session.beginTransaction();
				
				session.createQuery("update DBEpisode ep set ep.author=(from DBUser as user where id='" + userA + "') where ep.author.id='" + userB + "'").executeUpdate();
				session.createQuery("update DBEpisode ep set ep.editor=(from DBUser as user where id='" + userA + "') where ep.editor.id='" + userB + "'").executeUpdate();
				session.createQuery("update DBFlaggedEpisode ep set ep.user=(from DBUser as user where id='" + userA + "') where ep.user.id='" + userB + "'").executeUpdate();
				
				session.createQuery("delete DBEpisodeView ev where ev.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBUpvote uv where uv.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBFavEp uv where uv.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBAnnouncementView uv where uv.viewer.id='" + userB + "'").executeUpdate();
				
				session.createQuery("delete DBFlaggedComment uv where uv.user.id='" + userB + "'").executeUpdate();
				session.createQuery("delete DBComment uv where uv.user.id='" + userB + "'").executeUpdate();
				
				session.createQuery("delete DBNotification note where note.user.id='" + userB + "'").executeUpdate();

				session.delete(b);
				session.getTransaction().commit();
			} catch (Exception e) {
				session.getTransaction().rollback();
				LOGGER.error("mergeAccounts() rollback", e);
				throw new DBException("rollback", e);
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
			return session.createQuery("from DBArchiveToken a order by a.date asc", DBArchiveToken.class).stream().map(ArchiveToken::new).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	public static boolean isValidArchiveToken(String token) {
		Session session = openSession();
		try {
			return session.createQuery("from DBArchiveToken a where a.token='" + token + "'", DBArchiveToken.class).uniqueResultOptional().isPresent();
		} finally {
			closeSession(session);
		}
	}
	
	public enum PopularEpisode{
		HITS ("order by hits desc, upvotes desc, views desc"),
		VIEWS ("order by views desc, upvotes desc, hits desc"),
		UPVOTES ("order by upvotes desc, views desc, hits desc");
		public final String ORDER_BY;
		private PopularEpisode(String orderBy) {
			this.ORDER_BY=orderBy;
		}
	}
	
	private static final String POPULAR_QUERY = """
			SELECT
			    generatedid,
			    newmap,
			    link,
			    title,
			    date,
			    MAX(childcount),
			    MAX(hitscount) AS hits,
			    MAX(viewscount) AS views,
			    MAX(upvotescount) AS upvotes,
			    fbtags.id,
			    fbtags.shortname,
			    fbtags.longname,
			    fbtags.description
			FROM (
			    (SELECT
			        fbepisodes.generatedid,
			        fbepisodes.newmap,
			        fbepisodes.link,
			        fbepisodes.title,
			        fbepisodes.date,
			        childcount,
			        fbepisodes.viewcount AS hitscount,
			        COUNT(*) AS viewscount,
			        0 AS upvotescount
			      FROM fbepisodes, fbepisodeviews
			      WHERE fbepisodes.generatedid=fbepisodeviews.episode_generatedid
			    GROUP BY fbepisodes.generatedid)
			    UNION
			    (SELECT
			        fbepisodes.generatedid,
			        fbepisodes.newmap,
			        fbepisodes.link,
			        fbepisodes.title,
			        fbepisodes.date,
			        childcount,
			        fbepisodes.viewcount AS hitscount,
			        0 AS viewscount,
			        COUNT(*) AS upvotescount
			      FROM fbepisodes,fbupvotes
			      WHERE fbepisodes.generatedid=fbupvotes.episode_generatedid
			    GROUP BY fbepisodes.generatedid)
			) AS countstuff
			LEFT JOIN fbepisodetags ON fbepisodetags.episode_generatedid=countstuff.generatedid
			LEFT JOIN fbtags ON fbtags.id=fbepisodetags.tag_id
			GROUP BY generatedid,newmap,link,title,date,fbtags.id,fbtags.shortname,fbtags.longname,fbtags.description
			""";
		
	@SuppressWarnings("unchecked")
	private static List<Episode> popularEpisodesReal(PopularEpisode pop) {
		Session session = openSession();
		try {
			
			Stream<Object> stream = session.createNativeQuery(POPULAR_QUERY + pop.ORDER_BY + " \nlimit 100;").stream();
			
			Map<Long, Episode> map = new LinkedHashMap<>();
			
			for (Object o : stream.toList()) {
				Object[] x = (Object[])o;
				long generatedId = ((BigInteger)x[0]).longValue();
				
				Episode ep = map.get(generatedId);
				if (ep == null) {
					String newMap = (String)x[1];
					String link = (String)x[2];
					String title = (String)x[3];
					Date date = (Date)x[4];
					int childCount = (Integer) x[5];
					long hits = ((BigInteger)x[6]).longValue();
					long views = ((BigInteger)x[7]).longValue();
					long upvotes = ((BigInteger)x[8]).longValue();
					
					Long tagid = x[9]==null ? null : ((BigInteger)x[9]).longValue();
					String shortname = (String)x[10];
					String longname = (String)x[11];
					String description = (String)x[12];
					
					ep = new Episode(generatedId,newMap,link,title,date,childCount,hits,views,upvotes, null, null, new HashSet<>(tagid==null ? Set.of() : Set.of(new Tag(tagid, shortname, longname, description, null, null, 0l, null))));
					map.put(generatedId, ep);
				} else {
					Long tagid = x[9]==null ? null : ((BigInteger)x[9]).longValue();
					String shortname = (String)x[10];
					String longname = (String)x[11];
					String description = (String)x[12];
					Tag tag = new Tag(tagid, shortname, longname, description, null, null, 0l, null);
					if (tagid != null) {
						ep.tags.add(tag);
					}
				}
			}
			
			return map.values().stream().toList();
			
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
		final Session session = openSession();
		try {
			return ((Stream<Object>)(session.createNativeQuery(
					POPULAR_USERS_QUERY.replace("$TIMELIMIT", time.TIMELIMIT) + pop.ORDER_BY + " \nlimit 100;"
				).stream())).map(o->{
				final Object[] x = (Object[])o;
				final String username = (String)x[0];
				final String author = (String)x[1];
				final Date date = (Date)x[2];
				final long episodes = ((BigInteger)x[3]).longValue();
				final long hits = ((BigDecimal)x[4]).longValue();
				final long views = ((BigInteger)x[5]).longValue();
				final long upvotes = ((BigInteger)x[6]).longValue();
				return (new User(username,author,date,episodes,hits,views,upvotes));
			}).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
	private static final String POPULAR_USERS_QUERY = """
			SELECT username, author, date, MAX(episodescount) AS episodes, MAX(hitscount) AS hits, MAX(viewscount) AS views, MAX(upvotescount) AS upvotes FROM (
			(SELECT fbusers.id AS username, author,fbusers.date AS date,COUNT(*) AS episodescount, 0 AS hitscount, 0 AS upvotescount, 0 AS viewscount
			    FROM fbepisodes
			    LEFT JOIN fbusers ON fbusers.id=fbepisodes.author_id
			    WHERE fbepisodes.date > ($TIMELIMIT)
			    GROUP BY username, author)
			UNION
			(SELECT fbusers.id AS username, author,fbusers.date AS date,0 AS episodescount, SUM(fbepisodes.viewcount) AS hitscount, 0 AS upvotescount, 0 AS viewscount
			    FROM fbepisodes
			    LEFT JOIN fbusers ON fbusers.id=fbepisodes.author_id
			    WHERE fbepisodes.date > ($TIMELIMIT)
			    GROUP BY username, author)
			UNION
			(SELECT fbusers.id AS username, author,fbusers.date AS date,0 AS episodescount, 0 AS hitscount, 0 AS upvotescount, COUNT(*) AS viewscount
			    FROM fbepisodeviews
			    LEFT JOIN fbepisodes ON fbepisodes.generatedid=fbepisodeviews.episode_generatedid
			    LEFT JOIN fbusers ON fbusers.id=fbepisodes.author_id
			    WHERE fbepisodeviews.date > ($TIMELIMIT)
			    GROUP BY username, author)
			UNION
			(SELECT fbusers.id AS username, author,fbusers.date AS date,0 AS episodescount, 0 AS hitscount, COUNT(*) AS upvotescount, 0 AS viewscount
			    FROM fbupvotes
			    LEFT JOIN fbepisodes ON fbepisodes.generatedid=fbupvotes.episode_generatedid
			    LEFT JOIN fbusers ON fbusers.id=fbepisodes.author_id
			    WHERE fbupvotes.date > ($TIMELIMIT)
			    GROUP BY username, author)
			) AS countstuff GROUP BY username, author, date 
			""";
	
	public enum PopularUser{
		HITS ("ORDER BY hits DESC, upvotes DESC, views DESC, episodes DESC"),
		VIEWS ("ORDER BY views DESC, upvotes DESC, hits DESC, episodes DESC"),
		UPVOTES ("ORDER BY upvotes DESC, views DESC, hits DESC, episodes DESC"),
		EPISODES ("ORDER BY episodes DESC, upvotes DESC, views DESC, hits DESC");
		public final String ORDER_BY;
		private PopularUser(String orderBy) {
			this.ORDER_BY=orderBy;
		}
	}
	
	public enum PopularUserTime{
		ALL ("TO_DATE('1970-01-01', 'YYYY-MM-DD')"),
		WEEK ("NOW() - INTERVAL '7 days'"),
		MONTH ("NOW() - INTERVAL '30 days'");
		public final String TIMELIMIT;
		private PopularUserTime(String timeLimit) {
			this.TIMELIMIT = timeLimit;
		}
	}
	
	private static ConcurrentHashMap<PopularUserEnumContainer, PopularUserContainer> popularUsersMap = new ConcurrentHashMap<>(); 
	
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
			
			Stream<Object> stream = session.createNativeQuery((username==null)?GET_ANNOUNCEMENTS_ANON:GET_ANNOUNCEMENTS.replace(ANNOUNCE_REPLACER, username)).stream();
			
			return stream.map(o->{
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
			
			List<Notification> result = session.createQuery(q.toString(), DBNotification.class).setMaxResults(100).setFirstResult(page*100).stream().map(Notification::new).toList();
			
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
				).uniqueResult())).intValue();
						
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
			
			return session
					.createQuery("select count(*) from DBNotification note where note.user.id='"+username+"' and note.read=false", Long.class)
					.uniqueResult()
					.intValue();

						
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
	 * Turns a branch into a new story root. Ep will have the same URL and generatedId
	 * @param id id of top episode in branch, to become new root episode
	 * @throws DBException if ep does not exist, ep is already a root, or
	 */
	public static void moveEpisodeToRoot(long generatedId) throws DBException {
		Session session = openSession();
		try {
			
			DBEpisode ep = session.get(DBEpisode.class, generatedId);
			if (ep == null) throw new DBException("not found: " + generatedId);
			if (ep.episodeDepthFromNewMap() == 1) return; // already a root episode
			
			{
				final int branchSize = ep.getChildCount();
				
				final String oldNewMap = ep.getNewMap();
				
				session.beginTransaction();
				try {
					session.createNativeQuery( // set parentId of new root episode to null
							"UPDATE fbepisodes\n" + 
							"SET parent_generatedid = null\n" + 
							"WHERE generatedid="+generatedId+";").executeUpdate();
					session.createNativeQuery( // set newMap of all episodes in branch (including root)
							"UPDATE fbepisodes\n" + 
							"SET newmap = replace(newmap,'"+oldNewMap+"','"+EP_PREFIX+generatedId+"')\n" + 
							"WHERE newmap='"+oldNewMap+"' OR newmap LIKE '"+oldNewMap+""+DB.EP_INFIX+"%';").executeUpdate();
					
					String where = DB.newMapToIdList(oldNewMap).filter(gid->gid!=generatedId).map(gid->"generatedId=" + gid).collect(Collectors.joining(" OR "));
					session.createNativeQuery("UPDATE fbepisodes\n" + 
							"SET childcount = childcount - "+branchSize+"\n" + 
							"WHERE " + where + ";").executeUpdate();
					session.getTransaction().commit();
				} catch (Exception e) {
					LOGGER.error("moveEpisodeToRoot() rollback", e);
					session.getTransaction().rollback();
					throw new DBException("rollback", e);
				}
			}
		} finally {
			closeSession(session);
			Story.updateRootEpisodesCache();
		}
	}
	
	/**
	 * Gets a list of names (primary keys) of all themes
	 * @return
	 */
	public static List<String> getThemeNames() {
		Session session = openSession();
		try {
			return session.createQuery("from DBTheme", DBTheme.class).stream().map(DBTheme::getName).collect(Collectors.toCollection(ArrayList::new));
		} finally {
			closeSession(session);
		}
	}
	
 	private static String newToken() {
		StringBuilder token = new StringBuilder();
		for (int i=0; i<32; ++i) token.append((char)('a'+Strings.r.nextInt(26)));
		return token.toString();
	}
 	
 	public static final Comparator<String> newMapComparator = (a,b)->{
 		List<Long> aList = DB.newMapToIdList(a).toList();
		List<Long> bList = DB.newMapToIdList(b).toList();
		for (int i=0; i<aList.size() && i<bList.size(); ++i) {
			Long x = aList.get(i);
			Long y = bList.get(i);
			int comp = x.compareTo(y);
			if (comp != 0) return comp;
		}
		return Integer.compare(aList.size(), bList.size());
 	};
 	
 	public static Set<Tag> getAllTags() {
		Session sesh = DB.openSession();
		try {
			return getAllTags(sesh).map(Tag::new).sorted(Comparator.comparing(tag -> tag.shortName.toLowerCase())).collect(Collectors.toCollection(LinkedHashSet::new));
		} finally {
			DB.closeSession(sesh);
		}
 	}
 	
 	/**
 	 * Get a list of all tags in sorted order, along with how many episodes currently have each tag assigned
 	 * @return
 	 */
 	public static List<Tag> getAllTagsWithCounts() {
		Session sesh = DB.openSession();
		try {
			
			final String query = 
					"""
					SELECT fbtags.id,shortname,longname,description,createdby_id,createddate,fbusers.author,
					COUNT(fbepisodetags.episode_generatedid) AS ct 
					FROM fbtags 
					LEFT JOIN fbepisodetags ON fbepisodetags.tag_id=fbtags.id 
					LEFT JOIN fbusers ON fbusers.id=fbtags.createdby_id 
					GROUP BY fbtags.id,shortname,longname,description,createdby_id,createddate,editedby_id,editeddate,fbusers.author
					ORDER BY shortname ASC, longname ASC, createddate ASC
					;		
					""";
			
			@SuppressWarnings("unchecked")
			Stream<Object> stream = sesh.createNativeQuery(query).stream();
			
			return stream.map(x -> {
				Object[] arr = (Object[])x;
				
				long id = ((BigInteger)arr[0]).longValue();
				String shortname = (String)arr[1];
				String longname = (String)arr[2];
				String description = (String)arr[3];
				String createdby_id = (String)arr[4];
				long createddate = ((BigInteger)arr[5]).longValue();
				String author = (String)arr[6];
				long count = ((BigInteger)arr[7]).longValue();
								
				return new Tag(id, shortname, longname, description, createdby_id, author, createddate, count);
		 	}).toList();
			
		} finally {
			DB.closeSession(sesh);
		}
 	}
 	
 	/**
 	 * Get all tags in sorted order
 	 * @param sesh
 	 * @return
 	 */
 	public static Stream<DBTag> getAllTags(Session sesh) {
		return sesh.createQuery("from DBTag tag order by tag.shortName", DBTag.class).stream();
 	}
 	 	
 	/**
	 * Get a list of all available tags in sorted order, with each tag mapped to a
	 * boolean value, true meaning the supplied episode has that tag
	 * 
	 * @param generatedId
	 * @return
	 * @throws DBException
	 */
 	public static Map<Tag, Boolean> getAllTagsForEpisode(long generatedId) throws DBException {
 		Session sesh = DB.openSession();
 		try {
 			DBEpisode ep = sesh.get(DBEpisode.class, generatedId);
 			if (ep == null) throw new DBException("Not found: " + generatedId);
 			Set<Tag> epTags = DB.getTagsForEp(sesh, ep);
 			List<Tag> allTags = getAllTags(sesh).map(Tag::new).toList();
 			return allTags.stream().collect(Collectors.toMap(tag -> tag, epTags::contains, (a, b) -> a||b, LinkedHashMap::new));
 		} finally {
 			DB.closeSession(sesh);
 		}
 	}
 	
 	/**
 	 * Updates the tags on an episode according to the formParams
 	 * @param generatedId
 	 * @param taggerUsername
 	 * @param formParams
 	 * @throws DBException
 	 */
 	public static void updateTags(long generatedId, String taggerUsername, MultivaluedMap<String, String> formParams) throws DBException {
 		Session sesh = DB.openSession();
 		try {
 			DB.updateTags(sesh, generatedId, taggerUsername, formParams); 			
 		} finally {
 			DB.closeSession(sesh);
 		}

 	}
 	
 	/**
 	 * Updates the tags on an episode according to the formParams
 	 * @param generatedId
 	 * @param taggerUsername
 	 * @param formParams
 	 * @throws DBException
 	 */
 	public static void updateTags(Session sesh, long generatedId, String taggerUsername, MultivaluedMap<String, String> formParams) throws DBException {
		final DBEpisode ep = sesh.get(DBEpisode.class, generatedId);
		if (ep == null) throw new DBException("Not found: " + generatedId);
		final DBUser tagger = sesh.get(DBUser.class, taggerUsername);
		if (tagger == null) throw new DBException("Not found: " + taggerUsername);
		
		if (tagger.getLevel()<10 && !ep.getAuthor().getId().equals(tagger.getId())) throw new DBException("You are not allowed to do that");
		
		
		final Set<String> currentTagsShortNames = DB.getTagsForEp(sesh, ep)
				.stream()
				.map(tag -> tag.shortName)
				.collect(Collectors.toCollection(HashSet::new));
		
		final Set<DBTag> allTags = getAllTags(sesh).collect(Collectors.toCollection(HashSet::new));
		
		final Set<DBTag> addTags = allTags.stream().filter(tag -> formParams.containsKey(tag.getShortName()) && !currentTagsShortNames.contains(tag.getShortName())).collect(Collectors.toCollection(HashSet::new));
		
		final Set<DBTag> delTags = allTags.stream()
			.filter(tag -> !formParams.containsKey(tag.getShortName()) && currentTagsShortNames.contains(tag.getShortName()))
			.collect(Collectors.toCollection(HashSet::new));
				
		if (!addTags.isEmpty() || !delTags.isEmpty()) {
			sesh.beginTransaction();
			try {
				for (DBTag tag : addTags) {
					sesh.save(new DBEpisodeTag(ep, tag, tagger));
				}
				if (!delTags.isEmpty()) {
					String delete = """
							DELETE FROM fbepisodetags
							WHERE episode_generatedid=
							""" + ep.getGeneratedId() + " AND (" + delTags.stream().map(tag -> "tag_id=" + tag.getId()).collect(Collectors.joining(" OR ")) + ")";
					sesh.createNativeQuery(delete).executeUpdate();
				}
				ep.setTagDate(new Date());
				sesh.merge(ep);
				sesh.getTransaction().commit();
			} catch (Exception e) {
				sesh.getTransaction().rollback();
				throw new DBException(e);
			}
		}
 	}
 	
 	private DB() {}
}
