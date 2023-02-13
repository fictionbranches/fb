package fb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.stream.Collectors;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Markdown {
	
	private static final String MARKDOWN_IT_URL = "https://cdnjs.cloudflare.com/ajax/libs/markdown-it/13.0.1/markdown-it.min.js";
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	private static final Object lock = new Object();
	public static final Markdown m = new Markdown();
	
	public static String formatBody(String body) {
		body = Text.escape(body);
		synchronized (lock) {
			try {
				return (String) ((Invocable) m.engine).invokeFunction("markdownToHTML", body);
			} catch (NoSuchMethodException | ScriptException e) {
				System.err.println(e + " " + e.getMessage());
				return body;
			}
		}
	}
	
	private final ScriptEngine engine;
	{
		synchronized (lock) {
			long start = System.nanoTime();
			engine = new ScriptEngineManager().getEngineByName("javascript");
			if (engine == null) {
				LOGGER.error("No javascript engine");
				System.exit(99);
				throw new RuntimeException("No javascript engine");
			}
			try {
				LOGGER.info("Got js engine in " + (((double) (System.nanoTime() - start)) / 1000000000.0));
//				engine.eval(getJsReaderFromJar("static_html/static/markdown-it.js"));
				engine.eval(getMarkdownIt());
				engine.eval(getJsReaderFromJar("static_html/static/markdown.js"));
				LOGGER.info("js engine init in " + (((double) (System.nanoTime() - start)) / 1000000000.0));
			} catch (ScriptException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private String getMarkdownIt() {
		try (final BufferedReader scan = new BufferedReader(new InputStreamReader(new URL(MARKDOWN_IT_URL).openStream()))) {
			return scan.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			throw new RuntimeException("Unable to download markdown-it.js", e);
		}
	}
	
	public static String getJsReaderFromJar(String filepath) {
		try (final BufferedReader scan = new BufferedReader(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath)))) {
			return scan.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			throw new RuntimeException("Unable to open file " + filepath, e);
		}
	}
}
