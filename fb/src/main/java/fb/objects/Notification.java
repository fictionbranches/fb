package fb.objects;

import java.util.Date;

import fb.db.DBNotification;

public class Notification {
	public final long id;
	public final Date date;
	public final FlatUser user;
	public final boolean read;
	public final String type;
	
	public final FlatEpisode episode;
	public final FlatEpisode parentEpisode;
	public final Comment comment;
	public final String body;
	public final FlatUser sender;
	public final Boolean approved;
	
	public Notification(DBNotification note) {
		//this(note.getId(), note.getDate(), note.getBody(), note.getUser().getId(), note.isRead());
		this.id = note.getId();
		this.date = note.getDate();
		this.user = new FlatUser(note.getUser());
		this.read = note.isRead();
		this.type = note.getType()==null?DBNotification.LEGACY_NOTE:note.getType();
		
		switch (this.type) {
		case DBNotification.LEGACY_NOTE:
			this.body = note.getBody();
			this.episode = null;
			this.parentEpisode = null;
			this.comment = null;
			this.sender = null;
			this.approved = null;
			break;
		case DBNotification.NEW_CHILD_EPISODE: 
			this.episode = note.getEpisode()==null?null:new FlatEpisode(note.getEpisode());
			this.parentEpisode = note.getEpisode()==null?null:new FlatEpisode(note.getEpisode().getParent());
			this.comment = null;
			this.body = null;
			this.sender = null;
			this.approved = null;
			break;
		case DBNotification.NEW_COMMENT_ON_OWN_EPISODE:
		case DBNotification.NEW_COMMENT_ON_SUBBED_EPISODE:
			this.comment = note.getComment()==null?null:new Comment(note.getComment());
			this.episode = null;
			this.parentEpisode = null;
			this.body = null;
			this.sender = null;
			this.approved = null;
			break;
		case DBNotification.MODIFICATION_RESPONSE:
			this.comment = null;
			this.episode = new FlatEpisode(note.getEpisode());
			this.parentEpisode = null;
			this.body = null;
			this.sender = new FlatUser(note.getSender());
			this.approved = note.getApproved();
			break; 
		case DBNotification.NEW_AUTHOR_SUBSCRIPTION_EPISODE:
			this.comment = null;
			this.episode = new FlatEpisode(note.getEpisode());
			this.parentEpisode = null;
			this.body = null;
			this.sender = null;
			this.approved = null;
			break;
		default:
			this.episode = null;
			this.parentEpisode = null;
			this.comment = null;
			this.body = null;
			this.sender = null;
			this.approved = null;
		}
	}
}
