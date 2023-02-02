package fb.objects;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable episode object. Contains hits/views/upvotes, and minimal fields. Used for special cases. 
 */
public class Episode {
	public final long generatedId;
	public final String newMap;
	public final String title;
	public final String link;
	public final Date date;
	public final int childCount;
	public final long hits;
	public final long views;
	public final long upvotes;
	public final String authorId;
	public final String authorName;
	public final Set<Tag> tags;
	
	@SafeVarargs
	public Episode(long generatedId, String newMap, String link, String title, Date date, int childCount, long hits, long views, long upvotes, String authorId, String authorName, Set<Tag>... tags) {
		this.generatedId = generatedId;
		this.newMap = newMap;
		this.link = link;
		this.title = title;
		this.date = date;
		this.hits = hits;
		this.upvotes = upvotes;
		this.views = views;
		this.childCount = childCount;
		this.authorId = authorId;
		this.authorName = authorName;
		if (tags == null || tags.length == 0) this.tags = Set.of();
		else this.tags = Arrays.stream(tags).flatMap(set -> set==null||set.size()==0 ? Stream.of() : set.stream()).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
