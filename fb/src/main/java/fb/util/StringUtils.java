package fb.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * For string functions that don't need to hit the database
 */
public class StringUtils {
	
	/**
	 * Encodes a URI Component
	 * @param s
	 * @return
	 */
	public static String encodeURIComponent(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
	
	/**
	 * Read a file from the packed jar
	 * @param filepath
	 * @return
	 */
	public static String readRawFileFromJar(String filepath) {
		try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath))) { 
			StringBuilder sb = new StringBuilder(); 
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
			return sb.toString();
		}
	}
	
	/**
	 * Escape a string for HTML
	 * @param string
	 * @return
	 */
	public static String escape(String string) {
		return Markdown.escape(string);
	}
}
