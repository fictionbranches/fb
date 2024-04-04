package fb.objects;

import fb.db.DBAuthorSubscription;

public class AuthorSubscription {

	public final long id;
	public final long date;
//	public final FlatUser subscriber;
	public final FlatUser subscribedTo;
	public AuthorSubscription(DBAuthorSubscription sub) {
		this.id = sub.getId();
		this.date = sub.getCreatedDate();
//		this.subscriber = new FlatUser(sub.getSubscriber());
		this.subscribedTo = new FlatUser(sub.getSubscribedTo());
	}
}
