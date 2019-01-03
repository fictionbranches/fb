package fb.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "fbannouncementviews")
public class DBAnnouncementView {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@ManyToOne
	private DBUser viewer;
	
	@ManyToOne
	private DBAnnouncement announcement;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public DBUser getViewer() {
		return viewer;
	}

	public void setViewer(DBUser viewer) {
		this.viewer = viewer;
	}

	public DBAnnouncement getAnnouncement() {
		return announcement;
	}

	public void setAnnouncement(DBAnnouncement announcement) {
		this.announcement = announcement;
	}

}
