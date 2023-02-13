package fb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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
			try (BufferedReader s = new BufferedReader(new StringReader(sw.getBuffer().toString()))) {
				return s.lines().toList();
			}
		} catch (IOException ioe) {
			LOGGER.error("Trouble logging previous exception's stack trace: ", ioe);
			return new ArrayList<>();
		}
	}
	
	public static String readFileFromJar(String filepath) {
		try (BufferedReader scan = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath)))) {
			return scan.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			LOGGER.warn("Not found: " + filepath, e);
			throw new RuntimeException(e);
		}
	}
	
	public static String readTextFile(String path) {
		return readTextFile(new File(path));
	}
	
	public static String readTextFile(File file) {
		try (BufferedReader scan = new BufferedReader(new FileReader(file))) {
			return scan.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			LOGGER.warn("Not found: " + file, e);
			throw new RuntimeException(e);
		}
	}
	
	public static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
	
	private Text() { }
}
