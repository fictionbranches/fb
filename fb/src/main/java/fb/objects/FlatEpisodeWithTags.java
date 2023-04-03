package fb.objects;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import fb.db.DBEpisode;

public class FlatEpisodeWithTags extends FlatEpisode {

	public final Set<Tag> tags;

	public FlatEpisodeWithTags(DBEpisode ep) {
		super(ep);
		this.tags = ep.getLazytags().stream().map(tag -> tag.getTag()).map(Tag::new).collect(Collectors.toSet());
	}
	
	// used by DB.getRecentsPage
	public FlatEpisodeWithTags(long generatedId, String oldMap, String newMap, String title, String link, String authorId, String authorName, String authorAvatar, String body, Date date, Date editDate, String editorId, String editorName,
			int childCount, Long parentId, long hits, Set<Tag> tags) {
		super(generatedId, oldMap, newMap, title, link, authorId, authorName, authorAvatar, body, date, editDate, editorId, editorName, childCount, parentId, hits);
		this.tags = tags;
	}

}
