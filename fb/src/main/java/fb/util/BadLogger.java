package fb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import fb.InitWebsite;

public class BadLogger {
	
	private static Object logLock = new Object();

	/**
	 * Prepends message with the current date, and writes it to stdout
	 * @param message
	 */
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
				System.err.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, "Could not open log file");
			} finally {
				System.out.printf("%04d-%02d-%02d %02d:%02d:%02d %s%n", y, mo, d, h, mi, s, message);
			}
		}
	}

	public static void log(Exception e) {
		List<String> lines = Strings.traceToLines(e);
		if (!lines.isEmpty()) synchronized (logLock) {
			for (String line : lines) log(line);
		}
	}
	
	private BadLogger() {}
}
