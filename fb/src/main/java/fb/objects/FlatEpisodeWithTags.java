package fb.objects;

import java.util.Set;
import java.util.stream.Collectors;

import fb.db.DBEpisode;

public class FlatEpisodeWithTags extends FlatEpisode {

	public final Set<Tag> tags;

	public FlatEpisodeWithTags(DBEpisode ep) {
		super(ep);
		this.tags = ep.getLazytags().stream().map(tag -> tag.getTag()).map(Tag::new).collect(Collectors.toSet());
	}

}
