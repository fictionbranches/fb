package fb.util;

import java.io.InputStreamReader;
import java.io.Reader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Markdown {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
	
	public static String formatBody(String body) {
		synchronized (engine) {
			try {
				return (String) ((Invocable) engine).invokeFunction("markdownToHTML", escape(body));
			} catch (NoSuchMethodException | ScriptException e) {
				System.err.println(e + " " + e.getMessage());
				return body;
			}
		}
	}
	
	private final static ScriptEngine engine;
	static { 
		long start = System.nanoTime();
		engine  = new ScriptEngineManager().getEngineByName("javascript");
		if (engine == null) {
			LOGGER.error("No javascript engine");
			System.exit(99);
			throw new RuntimeException("No javascript engine");
		}
		try {
			LOGGER.info("Got js engine in " + (((double)(System.nanoTime()-start))/1000000000.0));
			engine.eval(getJsReaderFromJar("static_html/static/markdown-it.js"));
			engine.eval(getJsReaderFromJar("static_html/static/markdown.js"));
			LOGGER.info("js engine init in " + (((double)(System.nanoTime()-start))/1000000000.0));
		} catch (ScriptException e) {
			throw new RuntimeException(e);
		} 
	}
	
	/**
	 * Escape body text and convert markdown/formatting to HTML
	 * @param body unformatted markdown body
	 * @return HTML formatted body
	 */
	/*public static String formatBodyOld(String body) {
		return renderer.render(parser.parse((escape(body))));
	}
	
	private static final Parser parser;
	private static final HtmlRenderer renderer;
	
	static {
		MutableDataSet options = new MutableDataSet();
		options.set(Parser.EXTENSIONS,Stream.of(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create(), SuperscriptExtension.create()).collect(Collectors.toList()));
		options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
		options.set(Parser.FENCED_CODE_BLOCK_PARSER, false);
		options.set(Parser.INDENTED_CODE_BLOCK_PARSER, false);
		options.set(Parser.HTML_BLOCK_PARSER, false);
		options.set(Parser.BLOCK_QUOTE_PARSER, false);
				
		parser = Parser.builder(options).build();
		renderer = HtmlRenderer.builder(options).build();
	}*/
	
	public static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
	
	public static Reader getJsReaderFromJar(String filepath) {
		return new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath));
	}
}
