package fb.objects;

import java.util.Date;

import fb.db.DBArchiveToken;

public class ArchiveToken {
	public final long id;
	public final String token;
	public final String comment;
	public final Date date;
	public ArchiveToken(DBArchiveToken token) {
		this.id = token.getId();
		this.token = token.getToken();
		this.comment = token.getComment();
		this.date = token.getDate();
	}
}
