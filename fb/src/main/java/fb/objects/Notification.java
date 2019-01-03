package fb.objects;

import java.util.Date;

import fb.db.DBNotification;

public class Notification {
	public final long id;
	public final Date date;
	public final String body;
	public final String username;
	public final boolean read;
	public Notification(long id, Date date, String body, String username, boolean read) {
		this.id = id;
		this.date = date;
		this.body = body;
		this.username = username;
		this.read = read;
	}
	public Notification(DBNotification note) {
		this(note.getId(), note.getDate(), note.getBody(), note.getUser().getId(), note.isRead());
	}
}
