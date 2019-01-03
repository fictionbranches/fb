package fb.objects;

import java.util.Date;

import fb.db.DBFlaggedComment;

public class FlaggedComment {
	public final long id;
	public final String text;
	public final Date date;
	public final FlatUser user;
	public final Comment comment;
	
	public FlaggedComment(DBFlaggedComment c) {
		this.id = c.getId();
		this.text = c.getText();
		this.date = c.getDate();
		this.user = new FlatUser(c.getUser());
		this.comment = new Comment(c.getComment());
	}
}
