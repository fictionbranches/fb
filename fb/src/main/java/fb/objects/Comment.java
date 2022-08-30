package fb.objects;

import java.util.Date;

import fb.db.DBComment;

public class Comment {
	public final long id;
	public final Date date;
	public final FlatUser user;
	public final FlatEpisode episode;
	public final String text;
	public final boolean modVoice;
	
	public Comment(DBComment c) {
		id = c.getId();
		date = c.getDate();
		user = new FlatUser(c.getUser());
		episode = new FlatEpisode(c.getEpisode());
		text = c.getText();
		modVoice = c.isModVoice();
	}
}
