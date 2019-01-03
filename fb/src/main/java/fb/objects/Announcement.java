package fb.objects;

import java.util.Date;

public class Announcement {
	public final long id;
	public final Date date;
	public final String body;
	public final String authorId;
	public final String authorName;
	public final boolean read;
	
	public Announcement(long id, Date date, String body, String authorId, String authorName, boolean read) {
		this.id = id;
		this.date = date;
		this.body = body;
		this.authorId = authorId;
		this.authorName = authorName;
		this.read = read;
	}
	
}
