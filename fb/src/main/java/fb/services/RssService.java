package fb.services;

import static fb.util.Text.escape;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;

import fb.DB;
import fb.Story;
import fb.DB.DBException;
import fb.objects.Comment;
import fb.objects.FlatEpisode;
import fb.util.Strings;

public class RssService {

	private static final Logger LOGGER = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());

	public static String commentsFeed;
	public static HashMap<Long,String> feeds;
	static {
		updateFeeds();
		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(RssService::updateFeeds, 1, 1, TimeUnit.HOURS);
	}
	
	public static void bump() {}
	
	private static void updateFeeds() {
		HashMap<Long,String> list = new HashMap<>();
		list.put(0l, generate(0l));
		try {
			for (FlatEpisode root : DB.getRoots()) {
				long generatedId = root.generatedId;
				list.put(generatedId, generate(generatedId));
			}
		} finally {
			feeds = list;
			LOGGER.info(String.format("Updated RSS feeds: %s", list.keySet().stream().map(Object::toString).collect(Collectors.joining(" "))));
		}
		commentsFeed = generateComments();
	}
	
	private static String generate(long story) {
		final SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setTitle("Fiction Branches");
		feed.setLink("https://" + Strings.getDOMAIN());
		feed.setDescription("Fiction Branches is an online software engine which allows the production of multi-plotted stories.");
		final ArrayList<SyndEntry> entries = new ArrayList<>();
		Set<FlatEpisode> eps;
		try {
			eps = DB.getRecents(story, 1, false, "", null).episodes.keySet();
		} catch (DBException e) {
			LOGGER.info("Couldn't get recents for RSS");
			return feedToString(feed);
		}
		for (FlatEpisode ep : eps) {
			SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(escape(ep.link));
			entry.setLink("https://" + Strings.getDOMAIN() + "/fb/story/" + ep.generatedId);
			entry.setPublishedDate(ep.date);
			entry.setAuthor(escape(ep.authorName));
			
			SyndContent desc = new SyndContentImpl();
			desc.setType("text/html");
			StringBuilder body = new StringBuilder();
			body.append("<h1>" + escape(ep.title) + "</h1>\n");
			body.append(Story.formatBody(ep.body));
			desc.setValue(body.toString());
			entry.setDescription(desc);
			entries.add(entry);
		}

		feed.setEntries(entries);
		
		return feedToString(feed);
		
	}
	
	private static String generateComments() {
		final SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setTitle("Fiction Branches");
		feed.setLink("https://" + Strings.getDOMAIN());
		feed.setDescription("Fiction Branches is an online software engine which allows the production of multi-plotted stories.");
		final ArrayList<SyndEntry> entries = new ArrayList<>();
		List<Comment> coms = DB.getRecentComments(1).comments;
		for (Comment com : coms) {
			SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(escape("Comment on " + com.episode.link));
			entry.setLink("https://" + Strings.getDOMAIN() + "/fb/story/" + com.episode.generatedId + "#comment" + com.id);
			entry.setPublishedDate(com.date);
			entry.setAuthor(escape(com.user.author));
			
			SyndContent desc = new SyndContentImpl();
			desc.setType("text/html");
			StringBuilder body = new StringBuilder();
			body.append("<h1>" + escape("New comment on '" + com.episode.title + "'") + "</h1>\n");
			body.append(Story.formatBody(com.text));
			desc.setValue(body.toString());
			entry.setDescription(desc);
			entries.add(entry);
		}

		feed.setEntries(entries);
		
		return feedToString(feed);
		
	}

	
//	private static final String emptyFeed;
//	static {
//		final SyndFeed feed = new SyndFeedImpl();
//		feed.setFeedType("rss_2.0");
//		feed.setTitle("Fiction Branches");
//		feed.setLink("https://" + Strings.getDOMAIN());
//		feed.setDescription("Fiction Branches is an online software engine which allows the production of multi-plotted stories.");
//		final ArrayList<SyndEntry> entries = new ArrayList<>();
//		feed.setEntries(entries);
//		emptyFeed = feedToString(feed);
//	}
	
	public static String emptyFeed() {
		final SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setTitle("Fiction Branches");
		feed.setLink("https://" + Strings.getDOMAIN());
		feed.setDescription("Fiction Branches is an online software engine which allows the production of multi-plotted stories.");
		final ArrayList<SyndEntry> entries = new ArrayList<>();
		feed.setEntries(entries);
		return feedToString(feed);
	}
	
	private static String feedToString(SyndFeed feed) {
		Writer writer = new StringWriter();
		try {
			new SyndFeedOutput().output(feed, writer);
		} catch (IOException e) {
			LOGGER.error("RSS: there was some problem writing to the Writer", e);
		} catch (FeedException e) {
			LOGGER.error("RSS: the XML representation for the feed could not be created", e);
		}
		return writer.toString();
	}
}