package fb.db;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Scanner;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.Session;

import fb.DB;
import fb.util.Strings;

@Entity
@Table(name="fbthemes")
public class DBTheme {
	
	public DBTheme() {}
	
	@Id
	private String name;
	
	@Column(columnDefinition = "text") 
	private String css;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCss() {
		return css;
	}

	public void setCss(String css) {
		this.css = css;
	}
	
	public static void main(String[] args) throws Exception {
		HashMap<String,String> css = new HashMap<>(); // <Name, css code>
		try (Scanner scan = new Scanner(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("styles.txt")))) { 
			Strings.log("Updating styles");
			while (scan.hasNext()) {
				String themeName = scan.nextLine();
				String fileName = scan.nextLine() + ".css";
				StringBuilder sb = new StringBuilder();
				try (Scanner cssScan = new Scanner(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("static_html/static/css/" + fileName)))) { 
					while (cssScan.hasNext()) sb.append(cssScan.nextLine() + "\n");
				}
				css.put(themeName, sb.toString());
			}
		}
		for (Entry<String,String> e : css.entrySet()) System.out.println(e);
		Session session = DB.openSession();
		try {
			session.beginTransaction();
			for (Entry<String,String> e : css.entrySet()) {
				DBTheme theme = new DBTheme();
				theme.setName(e.getKey());
				theme.setCss(e.getValue());
				session.save(theme);
			}
			session.getTransaction().commit();
		} catch (Exception e){
			session.getTransaction().rollback();
			throw new RuntimeException(e);
		} finally {
			DB.closeSession(session);
		}
	}
}
