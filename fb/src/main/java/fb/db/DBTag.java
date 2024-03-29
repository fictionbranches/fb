package fb.db;

import static org.hibernate.search.annotations.Index.YES;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

@Entity
@Table(
	name="fbtags", 
	indexes = {
		@Index(columnList = "shortname"),
	}
)
@Indexed
public class DBTag {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long id;	
	
	@Column(columnDefinition = "text", unique = true, nullable = false)
	@Field(index=YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String shortName;
	
	@Column(columnDefinition = "text")
	@Field(index=YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String longName;
	
	@Column(columnDefinition = "text")
	@Field(index=YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String description;
	
	@ManyToOne
	private DBUser createdBy;
	
	private Long createdDate;

	@ManyToOne
	private DBUser editedBy;
	
	private long editedDate;
	
	public long getId() {
		return id;
	}
	
	public void setCreatedDate(Long createdDate) {
		this.createdDate = createdDate;
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

	public DBUser getEditedBy() {
		return editedBy;
	}

	public void setEditedBy(DBUser editedBy) {
		this.editedBy = editedBy;
	}

	public long getEditedDate() {
		return editedDate;
	}

	public void setEditedDate(long editedDate) {
		this.editedDate = editedDate;
	}

	@Override
	public int hashCode() {
		return Objects.hash(createdBy, createdDate, description, editedBy, editedDate, id, longName, shortName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof DBTag)) return false;
		DBTag other = (DBTag) obj;
		return Objects.equals(createdBy, other.createdBy) && Objects.equals(createdDate, other.createdDate) && Objects.equals(description, other.description) && Objects.equals(editedBy, other.editedBy) && editedDate == other.editedDate
				&& id == other.id && Objects.equals(longName, other.longName) && Objects.equals(shortName, other.shortName);
	}
}
