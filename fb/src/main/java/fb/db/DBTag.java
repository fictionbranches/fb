package fb.db;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name="fbtags")
public class DBTag {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;	
	
	@Column(columnDefinition = "text", unique = true, nullable = false)
	private String shortName;
	
	@Column(columnDefinition = "text") 
	private String longName;
	
	@Column(columnDefinition = "text") 
	private String description;
	
	@ManyToOne
	private DBUser createdBy;
	
	private long createdDate;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getLongName() {
		return longName;
	}

	public void setLongName(String longName) {
		this.longName = longName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public DBUser getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(DBUser createdBy) {
		this.createdBy = createdBy;
	}

	public long getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(long createdDate) {
		this.createdDate = createdDate;
	}

	@Override
	public int hashCode() {
		return Objects.hash(createdBy, createdDate, description, id, longName, shortName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof DBTag)) return false;
		DBTag other = (DBTag) obj;
		return 
				Objects.equals(createdBy, other.createdBy) && 
				createdDate == other.createdDate && 
				Objects.equals(description, other.description) && 
				id == other.id && 
				Objects.equals(longName, other.longName) && 
				Objects.equals(shortName, other.shortName);
	}
}
