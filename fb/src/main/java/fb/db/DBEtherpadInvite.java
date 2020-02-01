package fb.db;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name="fbetherpadinvites")
public class DBEtherpadInvite {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;
		
	@ManyToOne
	private DBUser invitee;
	
	@ManyToOne
	private DBEtherpad pad;
	
	private Boolean accepted = null;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public DBUser getInvitee() {
		return invitee;
	}

	public void setInvitee(DBUser invitee) {
		this.invitee = invitee;
	}

	public DBEtherpad getPad() {
		return pad;
	}

	public void setPad(DBEtherpad pad) {
		this.pad = pad;
	}

	public Boolean getAccepted() {
		return accepted;
	}

	public void setAccepted(Boolean accepted) {
		this.accepted = accepted;
	}
	
}
