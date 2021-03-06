package fb.objects;

import java.util.Date;

import fb.db.DBEpisode;
import fb.db.DBModEpisode;
import fb.db.DBUser;

public class ModEpisode {

	public final long modId;
	public final long episodeGeneratedId;
	public final String newMap;
	public final String oldMap;
	public final String body;
	public final Date date;
	public final String link;
	public final String title;
	public final String userId;
	public final String author;
	public final String oldLink;
	public final FlatEpisode currentEpisode;

	public ModEpisode(DBModEpisode ep) {
		modId = ep.getId();
		episodeGeneratedId = ep.getEpisode().getGeneratedId();
		body = ep.getBody();
		date = ep.getDate();
		link = ep.getLink();
		title = ep.getTitle();
		DBEpisode episode = ep.getEpisode();
		newMap = episode.getNewMap();
		oldMap = episode.getOldMap();
		oldLink = episode.getLink();
		DBUser user = episode.getAuthor();
		userId = user.getId();
		author = user.getAuthor();
		currentEpisode = new FlatEpisode(episode);
	}
}
