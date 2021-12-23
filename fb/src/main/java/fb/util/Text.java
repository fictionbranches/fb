package fb.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * stuff that would be in Strings.java, but it doesn't need to hit the database
 */
public class Text {

	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	public static List<String> traceToLines(Throwable e) {
		if (e==null) return Stream.of("null").collect(Collectors.toList());
		try (StringWriter sw = new StringWriter()) {
			try (PrintWriter writer = new PrintWriter(sw)) {
				e.printStackTrace(writer);
			}
			ArrayList<String> lines = new ArrayList<>();
			try (Scanner s = new Scanner(sw.getBuffer().toString())) {
				while (s.hasNext()) lines.add(s.nextLine());
			}
			return lines;
		} catch (IOException ioe) {
			LOGGER.error("Trouble logging previous exception's stack trace: ", ioe);
			return new ArrayList<>();
		}
	}
	
	public static String readTextFile(String path) {
		return readTextFile(new File(path));
	}
	
	public static String readTextFile(File file) {
		StringBuilder sb = new StringBuilder();
		try (Scanner scan = new Scanner(file)) {
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
		} catch (FileNotFoundException e) {
			LOGGER.warn("Not found: " + file, e);
		}
		return sb.toString();
	}
	
	public static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
	
	private Text() { }
}
