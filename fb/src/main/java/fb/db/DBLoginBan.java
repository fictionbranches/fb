package fb.db;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "fbloginbans")
public class DBLoginBan {
	@Id
	private String ip;

	private long until;
	
	private int banCount;

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public long getUntil() {
		return until;
	}

	public void setUntil(long until) {
		this.until = until;
	}

	public int getBanCount() {
		return banCount;
	}

	public void setBanCount(int banCount) {
		this.banCount = banCount;
	}

}
