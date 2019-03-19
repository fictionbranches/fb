package fb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fb.InitWebsite;

public class BadLogger {
	
	private static Object logLock = new Object();
	
	/**
	 * Prepends message with the current date, and writes it to stdout
	 * @param message
	 */
	@Deprecated
	public static void log(String message) {
		synchronized (logLock) {
			Calendar c = Calendar.getInstance();
			int y = c.get(Calendar.YEAR);
			int mo = c.get(Calendar.MONTH);
			int d = c.get(Calendar.DAY_OF_MONTH);
			int h = c.get(Calendar.HOUR_OF_DAY);
			int mi = c.get(Calendar.MINUTE);
			int s = c.get(Calendar.SECOND);
			try (BufferedWriter out = new BufferedWriter(new FileWriter(InitWebsite.BASE_DIR + "/fblog.txt", true))) {
				out.write(String.format("%04d-%02d-%02d %02d:%02d:%02d %s", y, mo, d, h, mi, s, message));
				out.newLine();
			} catch (IOException e) {
				System.err.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, "Could not open log file");//NOSONAR
			} finally {
				System.out.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, message);//NOSONAR
			}
		}
	}

	@Deprecated
	public static void log(Exception e) {
		List<String> lines = traceToLines(e);
		if (!lines.isEmpty()) synchronized (logLock) {
			for (String line : lines) log(line);
		}
	}
	
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
			BadLogger.log("Trouble logging previous exception's stack trace: " + ioe.getMessage());
			return new ArrayList<>();
		}
	}
	
	private BadLogger() {}
}
