package fb.objects;

import java.util.Date;

import fb.db.DBRecentUserBlock;

public class RecentUserBlock {

	public final long id;
	public final Date date;
//	public final FlatUser blockingUser;
	public final FlatUser blockedUser;
	public RecentUserBlock(DBRecentUserBlock block) {
		this.id = block.getId();
		this.date = block.getDate();
//		this.blockingUser = new FlatUser(block.getBlockingUser());
		this.blockedUser = new FlatUser(block.getBlockedUser());
	}
}
