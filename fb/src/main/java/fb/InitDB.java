package fb;

import java.util.Date;
import java.util.Scanner;

import org.hibernate.Session;
import org.springframework.security.crypto.bcrypt.BCrypt;

import fb.DB.DBException;
import fb.db.DBEpisode;
import fb.db.DBUser;

/**
 * Run this class's main() (as a regular Java Application, not on tomcat) to
 * initialize the database
 */
public class InitDB {

//	private static final boolean PRINT_EPISODES_ADDED = true; // enable to print each episode's newid to stdout when it is added to the db
//
//	private static Random r = new Random();
//	
//	private static final String EMPTY_LEGACY_USER_ID = "legacy00000000";
//	
//	public static void countDB()  {
//		Session session = DB.openSession();
//		try {
//			/***** Count episodes in DB ******/
//			long stop, start = System.nanoTime();
//
//			int c = count(session, "4");
//			stop = System.nanoTime();
//			System.out.println("finished tfog: " + c + " " + (((double) (stop - start)) / 1000000000.0));
//			start = System.nanoTime();
//
//			c = count(session, "3");
//			stop = System.nanoTime();
//			System.out.println("finished af: " + c + " " + (((double) (stop - start)) / 1000000000.0));
//			start = System.nanoTime();
//
//			c = count(session, "1");
//			stop = System.nanoTime();
//			System.out.println("finished forum: " + c + " " + (((double) (stop - start)) / 1000000000.0));
//			start = System.nanoTime();
//
//			c = count(session, "2");
//			stop = System.nanoTime();
//			System.out.println("finished yawyw: " + c + " " + (((double) (stop - start)) / 1000000000.0));
//			start = System.nanoTime();
//		} finally {
//			DB.closeSession(session);
//		}
//	}
//	
	public static void main(String[] args) {
		cleanStart();
	}
	
	public static void cleanStart()  {
		try (Scanner in = new Scanner(System.in)) {
			
			System.out.println("enter root password:");
			String rootpw = in.nextLine();
			System.out.println("Root account username is: " + DB.ROOT_ID);
			System.out.println("Root account email is: admin@fictionbranches.net");
			
			Session session = DB.openSession();
			try {
			session.beginTransaction();

			DBUser user = new DBUser();
			user.setEmail("admin@fictionbranches.net");
			user.setId(DB.ROOT_ID);
			user.setLevel((byte)100);
			user.setAuthor("FB Admin");
			user.setPassword(BCrypt.hashpw(rootpw, BCrypt.gensalt(10)));
			user.setBio("Fiction Branches Admin Account");
			session.save(user);
			session.getTransaction().commit();
			} finally {
				DB.closeSession(session);
			}
			
			String phoenixID = "phoenix";
			boolean success = false;
			do {
				
				System.out.println("Enter your email address:");
				String email = in.nextLine();
				
				System.out.println("Enter your username (alphanumerics and -._ only):");
				String username = in.nextLine();
				
				System.out.println("Enter your author name:");
				String author = in.nextLine();
				
				
				System.out.println("enter your password:");
				String password = in.nextLine();
				String createToken = DB.addPotentialUser(username, email, BCrypt.hashpw(password, BCrypt.gensalt(10)), author);
				try {
					System.out.println("Adding your account");
					
					phoenixID = DB.addUser(createToken);
					System.out.println("Your account has been added");
					success = true;
				} catch (DBException e) {
					System.out.println("Uh oh, DBException");
					System.out.println(e.getMessage());
					success = false;
				}
			} while (!success);
			
			try {
				System.out.println("Giving you adminship");
				DB.changeUserLevel(phoenixID, (byte)100);
				System.out.println("Done");
			} catch (DBException e) {
				e.printStackTrace();
				System.out.println("*** THIS SHOULD NEVER HAPPEN ***");
				DB.closeSessionFactory();
				System.exit(24);
			}
			
			session = DB.openSession();
			try {
				System.out.println("Adding first episode");
				Date date = new Date();
				DBUser author = DB.getUserById(session, phoenixID);
				if (author == null) throw new DBException("Author does not exist");

				DBEpisode child = new DBEpisode();
				
				//String childId = "1";

				//child.setMap(childId);
				child.setDepth(1);
				
				child.setTitle("Your First Story Title");
				child.setLink("Your First Story");
				child.setBody("## This is your first story");
				child.setAuthor(author);
				child.setParent(null);
				child.setDate(date);
				child.setChildCount(1);
				child.setEditDate(date);
				child.setEditor(author);

			
				try {
					session.beginTransaction();
					session.save(child);
					session.merge(author);
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
					throw new DBException("Database error");
				}
				try {
					session.beginTransaction();
					child.setNewMap(""+child.getGeneratedId());
					session.merge(child);
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
					throw new DBException("Database error");
				}
				System.out.println("First episode added!");
			} catch (DBException e) {
				e.printStackTrace();
				System.out.println("*** THIS SHOULD NEVER HAPPEN ***");
				DB.closeSessionFactory();
				System.exit(24);
			} finally {
				DB.closeSession(session);
			}
		} finally {
			DB.closeSessionFactory();
			System.exit(0);
		}
	}
//
//	public static void doImport() throws DBException  {
//		
//		try (Scanner in = new Scanner(System.in)) {
//			
//			System.out.println("enter root password:");
//			String rootpw = in.nextLine();
//			
//			Session session = DB.openSession();
//			try {
//			session.beginTransaction();
//
//			DBUser user = new DBUser();
//			user.setEmail("admin@fictionbranches.net");
//			user.setId(DB.ROOT_ID);
//			user.setLevel((byte)100);
//			user.setAuthor("FB Admin");
//			user.setPassword(BCrypt.hashpw(rootpw, BCrypt.gensalt(10)));
//			user.setBio("Fiction Branches Admin Account");
//			session.save(user);
//			session.getTransaction().commit();
//			} finally {
//				DB.closeSession(session);
//			}
//									
//			System.out.println("Enter your email address:");
//			String email = in.nextLine();
//			
//			System.out.println("Enter your username (alphanumerics and -._ only):");
//			String username = in.nextLine();
//			
//			System.out.println("Enter your author name:");
//			String author = in.nextLine();
//			
//			
//			System.out.println("enter your password:");
//			String password = in.nextLine();
//			
//			String phoenixID = InitDB.addUser(session, username, email, BCrypt.hashpw(password, BCrypt.gensalt(10)), author);
//			DB.changeUserLevel(phoenixID, (byte)100);
//		}
//				
//		Strings.log("Starting import");
//		long stop, start=System.nanoTime();
//		
//		readStory("tfog", "4");
//		stop = System.nanoTime();
//		Strings.log("finished tfog: " + (((double)(stop-start))/1000000000.0));
//		start = System.nanoTime();
//		
//		readStory("af", "3");
//		stop = System.nanoTime();
//		Strings.log("finished af: " + (((double)(stop-start))/1000000000.0));
//		start = System.nanoTime();
//		
//		readStory("forum", "1");
//		stop = System.nanoTime();
//		Strings.log("finished forum: " + (((double)(stop-start))/1000000000.0));
//		start = System.nanoTime();
//		
//		readStory("yawyw", "2");
//		stop = System.nanoTime();
//		Strings.log("finished yawyw: " + (((double)(stop-start))/1000000000.0));
//		
//		generateChildCounts();
//		
//		
//		DB.closeSessionFactory();
//		Strings.log("Fin");
//		System.exit(0);
//		
//	}
//	
//	private static String addUser(Session session, String username, String email, String hashedPassword, String author) throws DBException {
//		DBUser user = DB.getUserById(session, username);
//		if (user != null) throw new DBException("");
//		user = DB.getUserByEmail(session, email);
//		if (user != null) throw new DBException("");
//		try {
//			session.beginTransaction();
//			user = new DBUser();
//			user.setAuthor(author);
//			user.setAvatar("");
//			user.setBio("");
//			user.setDate(new Date());
//			user.setEmail(email);
//			user.setId(username);
//			user.setLevel((byte) 1);
//			user.setPassword(hashedPassword);
//			user.setTheme("");
//			session.getTransaction().commit();
//		} catch (Exception e) {
//			session.getTransaction().rollback();
//		}
//		return username;
//	}
//		
//	public static void generateChildCounts() throws DBException {
//		Session session = DB.openSession();
//		try {
//			FlatEpisode[] roots = DB.getRoots();
//			for (FlatEpisode ep : roots) {
//				String rootId = ep.id;
//				session.beginTransaction();
//				long start = System.nanoTime();
//				generateChildCounts(session, rootId);
//				long stop = System.nanoTime();
//				Strings.log("Generated child counts: " + ((((double)(stop-start))/1000000000.0)) + " " + rootId);
//				start = System.nanoTime();
//				session.getTransaction().commit();
//				stop = System.nanoTime();
//				Strings.log("Persisted child counts: " + ((((double)(stop-start))/1000000000.0)) + " " + rootId);
//			}
//		} finally {
//			DB.closeSession(session);
//		}
//	}
//		
//	/**
//	 * Count episodes in tree
//	 * @param id of root of tree
//	 * @return number of episodes (including root) in tree
//	 * @throws IOException 
//	 */
//	static int count(Session session, String id) {
//		DBEpisode ep = DB.getEpById(session, id);
//
//		if (ep == null) System.err.println("null");
//		int sum = 1; // count this episode
//		List<DBEpisode> children = session.createQuery("from DBEpisode ep where ep.parent.generatedId=" + ep.getGeneratedId(), DBEpisode.class).list();
//		for (DBEpisode child : children) sum+=count(session, child.getMap());
//		return sum;
//		
//	}
//	
//	private static int generateChildCounts(Session session, String id) {
//		
//		DBEpisode ep = DB.getEpById(session, id);
//		if (ep == null) System.err.println("null");
//		int sum = 1; // count this episode
//		List<DBEpisode> children = session.createQuery("from DBEpisode ep where ep.parent.generatedId=" + ep.getGeneratedId(), DBEpisode.class).list();
//		for (DBEpisode child : children) {
//			int x = generateChildCounts(session, child.getMap());
//			sum+=x;
//		}
//		ep.setChildCount(sum);
//		session.merge(ep);
//		if (PRINT_EPISODES_ADDED) System.out.println("Generated child counts: " + id);
//		return sum;
//	}
//	
//	private static void readStory(String story, String rootId) {
//		Strings.log("Importing " + story);
//		String dirPath = System.getProperty("user.home") + "/fbscrape/" + story + "/";
//		
//		Session session = DB.openSession();
//		try {
//		session.beginTransaction();
//		
//
//		Strings.log("Loading root of " + story);
//		LegacyEpisodeContainer rootCont = readEpisode(new File(dirPath+"/root"));
//		DBEpisode rootEp = rootCont.ep;
//		
//		boolean isNewUser = false;
//		DBUser newUser = getUserByAuthor(session, rootCont.author);
//		if (newUser == null) {
//			newUser = new DBUser();
//			newUser.setEmail(null);
//			newUser.setLevel((byte)1);
//			
//			String id = genLegacyID();
//			while (session.get(DBUser.class, id) != null) id = genLegacyID();
//			newUser.setId(id);
//			
//			isNewUser = true;
//		}
//		
//		rootEp.setParent(null);
//		rootEp.setMap(rootId);
//		rootEp.setDepth(keyToArr(rootId).length);
//		rootEp.setAuthor(newUser);
//		rootEp.setEditor(newUser);
//		newUser.setAuthor(rootCont.author);
//		newUser.setPassword("disabled");
//		newUser.setBio("");
//				
//		session.save(rootEp);
//		if (isNewUser) session.save(newUser);
//		else session.merge(newUser);
//		session.getTransaction().commit();
//		} finally {
//			DB.closeSession(session);
//		}
//		
//		Strings.log("Loading index of " + story);
//		TreeMap<String, String> map = new TreeMap<>(Comparators.keyStringComparator); // <"1-2-3","01234someguy">
//		Scanner index;
//		try {
//			index = new Scanner(new File(dirPath + "index.txt"));
//		} catch (FileNotFoundException e) {
//			Strings.log("index.txt  not found for " + story + " " + dirPath + "index.txt");
//			return;
//		}
//		boolean passed = true;
//		while (index.hasNext()) {
//			String oldId = index.next();
//			String newId = index.next();
//			if (oldId.equals("root") || keyToArr(newId).length == 1) continue;
//			if (map.containsKey(newId)) {
//				Strings.log("Duplicate newId: " + newId + " " + oldId + " " + map.get(newId));
//				passed = false;
//			} else map.put(newId, oldId);
//		}
//		index.close();
//		if (!passed) System.exit(1);
//		
//		Strings.log("Finding missing eps for " + story); 
//		
//		HashSet<String> missingEpisodes = new HashSet<>();
//		for (String id : map.keySet()) {
//			String parentId = getParentId(id);
//			String olderSiblingId = getOlderSiblingId(id);
//			if (keyToArr(parentId).length > 1 && !map.containsKey(parentId)) {
//				missingEpisodes.add(parentId);
//			}
//			if (olderSiblingId != null) if (!map.containsKey(olderSiblingId)) {
//				missingEpisodes.add(olderSiblingId);
//			}
//		}
//		
//		boolean noneMissing = false;
//		while (noneMissing == false) {
//			noneMissing = true;
//			HashSet<String> newMissingEpisodess = new HashSet<>();
//			for (String id : missingEpisodes) {
//				String parentId = getParentId(id);
//				String olderSiblingId = getOlderSiblingId(id);
//				if (keyToArr(parentId).length > 1 && !missingEpisodes.contains(parentId) && !map.containsKey(parentId)) {
//					newMissingEpisodess.add(parentId);
//					noneMissing = false;
//				}
//				if (olderSiblingId != null) if (!missingEpisodes.contains(olderSiblingId) && !map.containsKey(olderSiblingId)) {
//					newMissingEpisodess.add(olderSiblingId);
//					noneMissing = false;
//				}
//			}
//			missingEpisodes.addAll(newMissingEpisodess);
//		}
//		
//		Strings.log("Done finding missing eps for " + story); 
//
//		for (String id : missingEpisodes) {
//			if (map.put(id, null) != null) {
//				throw new RuntimeException(id + " was marked missing, but exists in map");
//			}
//		}
//		
//		
//		Strings.log("Persisting episodes for " + story); 
//		for (String newId : map.keySet()) {
//			if (missingEpisodes.contains(newId)) { // episode needs to exist but doesn't, so make it from scratch
//				session = DB.openSession();
//				try {
//				session.beginTransaction();
//				String childId = newId;
//				String parentId = getParentId(childId);
//				DBEpisode child = new DBEpisode();
//				boolean isNewUser = false;
//				DBUser user = session.get(DBUser.class, EMPTY_LEGACY_USER_ID);
//				if (user == null) {
//					user = new DBUser();
//					String id = genLegacyID();
//					while (session.get(DBUser.class, id) != null) id = genLegacyID();
//					user.setId(id);
//					
//					user.setAuthor("(Empty)");
//					user.setEmail(null);
//					user.setLevel((byte)1);
//					user.setPassword("disabled");
//					user.setBio("");
//					isNewUser = true;
//				}
//
//				child.setTitle("(Empty)");
//				child.setLink("(Empty)");
//				child.setAuthor(user);
//				child.setBody("(Empty)");
//				child.setDate(badDate);
//				child.setEditor(user);
//				child.setEditDate(badDate);
//
//				Strings.log("ID must exist, but doesn't: " + childId);
//				DBEpisode parent = DB.getEpById(session, parentId);
//
//				child.setMap(childId);
//				child.setParent(parent);
//				
//				child.setDepth(keyToArr(child.getMap()).length);
//				
//				if (isNewUser) session.save(user);
//				else session.merge(user);
//				session.save(child);
//				session.merge(parent);
//				session.getTransaction().commit();
//				if (PRINT_EPISODES_ADDED) System.out.println("Added episode " + child.getMap());
//				} finally {
//					DB.closeSession(session);
//				}	
//			} else { // otherwise, load the episode from file
//				File f = new File(dirPath + map.get(newId));
//				session = DB.openSession();
//				try {
//				session.beginTransaction();
//				String childId = newId;
//				String parentId = getParentId(childId);
//				LegacyEpisodeContainer epCont = readEpisode(f);
//				DBEpisode child = epCont.ep;
//								
//				DBEpisode parent = DB.getEpById(session, parentId);
//				boolean isNewUser = false;
//				DBUser user = getUserByAuthor(session, epCont.author);
//				if (user == null) {
//					user = new DBUser();
//					String id = genLegacyID();
//					while (session.get(DBUser.class, id) != null) id = genLegacyID();
//					user.setId(id);
//					user.setAuthor(epCont.author);
//					user.setEmail(null);
//					user.setLevel((byte)1);
//					user.setPassword("disabled");
//					user.setBio("");
//					isNewUser = true;
//				}
//								
//				child.setMap(childId);
//				child.setLegacyId(map.get(childId));
//				child.setDepth(keyToArr(childId).length);
//				child.setParent(parent);
//				child.setAuthor(user);
//				child.setEditor(user);
//
//				if (isNewUser) session.save(user);
//				else session.merge(user);
//				session.save(child);
//				session.merge(parent);
//				session.getTransaction().commit();
//				if (PRINT_EPISODES_ADDED) System.out.println("Added episode " + child.getMap());
//				} finally {
//					DB.closeSession(session);
//				}
//			}
//		}
//	}
//	
//	private static final Date badDate;
//	static {
//		Calendar c = Calendar.getInstance();
//		c.set(Calendar.YEAR, 1999);
//		c.set(Calendar.MONTH, 12);
//		c.set(Calendar.DAY_OF_MONTH, 31);
//		c.set(Calendar.HOUR, 12);
//		c.set(Calendar.MINUTE, 0);
//		c.set(Calendar.SECOND, 0);
//		badDate = c.getTime();
//		System.out.println(badDate);
//	}
//	
//	private static class LegacyEpisodeContainer {
//		public final DBEpisode ep;
//		public final String author;
//		public LegacyEpisodeContainer(DBEpisode ep, String author) {
//			this.ep = ep;
//			this.author = author;
//		}
//	}
//	
//	public static int[] keyToArr(String s) {
//		return DB.keyToArr(s);
//	}
//	
//	public static String arrToKey(int[] arr) {
//		StringBuilder sb = new StringBuilder();
//		for (int x : arr) sb.append(x + "-");
//		return sb.substring(0, sb.length()-1);
//	}
//	
//	public static String getParentId(String s) {
//		String[] arr = s.split("-");
//		StringBuilder ret = new StringBuilder();
//		for (int i=0; i<arr.length-1; ++i) ret.append(arr[i] + "-");
//		return ret.substring(0, ret.length()-1);
//	}
//	
//	public static String getOlderSiblingId(String s) {
//		int[] arr = keyToArr(s);
//		arr[arr.length-1]--;
//		return (arr[arr.length-1] >= 1)?(arrToKey(arr)):(null);
//	}
//	
//	private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
//	/**
//	 * Sets title, link, author*, body, and date
//	 * Does not set id or parent
//	 * *author is in separate field in return object
//	 * @param f
//	 * @return
//	 */
//	private static LegacyEpisodeContainer readEpisode(File f) {
//		try {
//			Scanner in = new Scanner(f);
//			DBEpisode ep = new DBEpisode();
//			String author = null;
//			try {
//				in.nextLine(); // Don't need oldId now
//				String id = in.nextLine(); // Only used for logging here
//				ep.setLink(trimTo(in.nextLine(), 254).replace("%3f", "?"));
//				ep.setTitle(trimTo(in.nextLine(), 254));
//				author = trimTo(in.nextLine(), 254);
//				String dateString = in.nextLine();
//				try {
//					Date date = df.parse(dateString);
//					ep.setDate(date);
//					ep.setEditDate(date);
//				} catch (ParseException e) {
//					ep.setDate(badDate);
//					ep.setEditDate(badDate);
//					Strings.log("Bad header: " + id + " " + f.getAbsolutePath());
//				}
//			} catch (NoSuchElementException e) {
//				in.close();
//				if (ep.getTitle() == null) ep.setTitle("(Empty)");
//				if (ep.getLink() == null) ep.setLink("(Empty)");
//				if (author == null) author = "(Empty)";
//				if (ep.getBody() == null) ep.setBody("");
//				if (ep.getDate() == null) {
//					ep.setDate(badDate);
//					ep.setEditDate(badDate);
//				}
//				Strings.log("(partially) empty episode: " + f.getName());
//				return new LegacyEpisodeContainer(ep, author);
//			}
//			String line = in.nextLine();
//			while (in.hasNext() && line.trim().length() == 0) line = in.nextLine();
//			StringBuilder body = new StringBuilder();
//			body.append(line + "\n");
//			while (in.hasNext()) body.append(in.nextLine() + "\n");
//			ep.setBody(body.toString().replace('`', '\''));
//			in.close();
//			return new LegacyEpisodeContainer(ep, author);
//		} catch (FileNotFoundException e) {
//			Strings.log("Error: file not found " + f.getAbsolutePath());
//			throw new RuntimeException(e);
//		} 
//	}
//	
//	private static DBUser getUserByAuthor(Session session, String author) {
//		CriteriaBuilder cb = session.getCriteriaBuilder();
//		CriteriaQuery<DBUser> query = cb.createQuery(DBUser.class);
//		Root<DBUser> root = query.from(DBUser.class);
//				
//		query.select(root).where(cb.equal(root.get("author"), author));
//		
//		return session.createQuery(query).uniqueResult();
//	}
//	
//	private static String trimTo(String s, int l) {
//		if (s.length() <= l) return s;
//		else return s.substring(0, l);
//	}
//	
//	private static ArrayList<Character> idChars = new ArrayList<>();
//	static {
//		for (char c='a'; c<='z'; ++c) idChars.add(c);
//		for (char c='0'; c<='9'; ++c) idChars.add(c);
//	}
//	private static String genLegacyID() {
//		StringBuilder sb = new StringBuilder();
//		for (int i=0; i<8; ++i) sb.append(idChars.get(r.nextInt(idChars.size())));
//		return "legacy" + sb.toString();
//	}
}
