package fb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.text.StringEscapeUtils;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.superscript.SuperscriptExtension;
import com.vladsch.flexmark.util.options.MutableDataSet;

public class Markdown {
	/**
	 * Escape body text and convert markdown/formatting to HTML
	 * @param body unformatted markdown body
	 * @return HTML formatted body
	 */
	public static String formatBodyOld(String body) {
		return renderer.render(parser.parse((escape(body))));
	}
	
	public static String formatBody(String body) {
		return formatBody(body,engine);
	}
	
	public static void main(String[] args) {
		new ScriptEngineManager().getEngineFactories().forEach(System.out::println);;
	}
	
	private static final String SCRIPT = 
			getJsFromURL("https://cdnjs.cloudflare.com/ajax/libs/markdown-it/8.4.2/markdown-it.min.js") + 
			" ; \n" + 
			getJsFromJar("static_html/static/markdown.js");

	private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
	static {
		try {
			engine.eval(SCRIPT);
		} catch (ScriptException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String formatBody(String body, ScriptEngine engine) {
		try {
			return (String) ((Invocable) engine).invokeFunction("markdownToHTML", escape(body));
		} catch (NoSuchMethodException | ScriptException e) {
			return body;
		}
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
	}
	
	public static String escape(String string) {
		return StringEscapeUtils.escapeHtml4(string);
	}
	
	private static String getJsFromURL(String url) {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(url).openStream()))){
			while (true) {
				int x = br.read();
				if (x<0) break;
				char c = (char)x;
				sb.append(c);
			}
		} catch (IOException e) { }
		return sb.toString();
	}
	
	public static String getJsFromJar(String filepath) {
		try (Scanner scan = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath))) { 
			StringBuilder sb = new StringBuilder(); 
			while (scan.hasNext()) sb.append(scan.nextLine() + "\n");
			return sb.toString();
		}
	}
}
