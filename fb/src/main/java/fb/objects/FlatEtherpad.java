package fb.objects;

import java.util.Date;

import fb.db.DBEtherpad;

public class FlatEtherpad {
	
	public final long id;
	public final String groupID;
	public final FlatUser owner;
	public final Date date;
	public final String name;
	
	public FlatEtherpad(DBEtherpad pad) {
		this.id = pad.getId();
		this.groupID = pad.getGroupID();
		this.name = pad.getName();
		this.date = pad.getDate();
		this.owner = new FlatUser(pad.getOwner());
	}
}
