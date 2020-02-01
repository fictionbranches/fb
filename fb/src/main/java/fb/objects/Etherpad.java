package fb.objects;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import fb.db.DBEtherpad;
import fb.db.DBUser;

public class Etherpad extends FlatEtherpad {
	
	public final List<FlatUser> sharedWith;
	
	public Etherpad(DBEtherpad pad, List<DBUser> sharedWith) {
		super(pad);
		this.sharedWith = Collections.unmodifiableList(sharedWith.stream().map(FlatUser::new).collect(Collectors.toList()));
	}
}
