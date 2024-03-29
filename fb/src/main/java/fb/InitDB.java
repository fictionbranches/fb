package fb;

import java.util.Date;
import java.util.Scanner;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;

import fb.DB.DBException;
import fb.db.DBUser;

/**
 * Run this class's main() (as a regular Java Application, not on tomcat) to
 * initialize the database
 */
public class InitDB {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	public static void main(String[] args) {
		cleanStart();
	}
	
	@SuppressWarnings("squid:S106")
	public static void cleanStart()  {
		try (Scanner in = new Scanner(System.in)) {
			
			System.out.println("enter root password:");//NOSONAR
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
			
			String phoenixID = "ChangeMe";
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
				LOGGER.error("*** THIS SHOULD NEVER HAPPEN ***", e);
				DB.closeSessionFactory();
				System.exit(24);
				throw new RuntimeException(e);
			}
			
			session = DB.openSession();
			try {
				System.out.println("Adding first episode");
				DBUser author = DB.getUserById(session, phoenixID);
				if (author == null) throw new DBException("Author does not exist");				
				
				final String title = "Your First Story Title";
				final String link = "Your First Story";
				final String body = "## This is your first story";
				
				DB.addRootEp(link, title, body, author.getId(), new Date());
				
				System.out.println("First episode added!");
			} catch (DBException e) {
				LOGGER.error("*** THIS SHOULD NEVER HAPPEN ***", e);
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
}
