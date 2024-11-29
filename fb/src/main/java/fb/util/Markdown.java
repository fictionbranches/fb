package fb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fb.DB;
import fb.db.DBFileCache;

public class Markdown {
	
	private static final List<String> MARKDOWN_IT_URLs = List.of(
			"https://cdnjs.cloudflare.com/ajax/libs/markdown-it/13.0.1/markdown-it.min.js",
			"https://unpkg.com/markdown-it-center-text@1.0.4/dist/markdown-it-center-text.min.js"
		);
	
	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	private static final Object lock = new Object();
	private static final Markdown m = new Markdown();
	
	public static String formatBody(String body) {
		body = Text.escape(body);
		synchronized (lock) {
			try {
				return (String) ((Invocable) m.engine).invokeFunction("markdownToHTML", body);
			} catch (NoSuchMethodException | ScriptException e) {
				LOGGER.error(e + " " + e.getMessage(), e);
				return body;
			}
		}
	}
	
	public static String formatBodyNoImage(String body) {
		body = Text.escape(body);
		synchronized (lock) {
			try {
				return (String) ((Invocable) m.engine).invokeFunction("markdownToHTMLNoImage", body);
			} catch (NoSuchMethodException | ScriptException e) {
				LOGGER.error(e + " " + e.getMessage(), e);
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
				for (String url : MARKDOWN_IT_URLs) {
					engine.eval(getJavascript(url));
				}
				engine.eval(Text.readFileFromJar("static_html/static/markdown.js"));
				LOGGER.info("js engine init in " + (((double) (System.nanoTime() - start)) / 1000000000.0));
			} catch (ScriptException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private String getJavascript(String url) {
		final Session session = DB.openSession();
		try {
			if (url == null || url.length() == 0) return "";
			DBFileCache cache = session.get(DBFileCache.class, url);
			if (cache == null) {
				cache = new DBFileCache();
				cache.setKey(url);
				try {
					session.beginTransaction();
					session.save(cache);
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
					LOGGER.warn("Unable to download " + url, e);
					return "";
				}
			}
			
			if (cache.getValue() == null || cache.getValue().length() == 0) {
				cache.setKey(url);
				cache.setValue(getJavascriptImpl(url));
				
				try {
					session.beginTransaction();
					session.merge(cache);
					session.getTransaction().commit();
				} catch (Exception e) {
					session.getTransaction().rollback();
					LOGGER.warn("Unable to download " + url, e);
					return "";
				}				
			}
			
			return cache.getValue();
			
		} catch (Exception e) {
			LOGGER.warn("Unable to download " + url, e);
			return "";
		} finally {
			DB.closeSession(session);
		}
	}

	private String getJavascriptImpl(String url) {
		try (final BufferedReader scan = new BufferedReader(new InputStreamReader(URI.create(url).toURL().openStream()))) {
			return scan.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			throw new RuntimeException("Unable to download markdown-it.js", e);
		}
	}
}
