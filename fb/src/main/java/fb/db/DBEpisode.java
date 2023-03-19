package fb.db;

import java.util.Date;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.DateBridge;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Resolution;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;

import fb.DB;

@Entity
@Table(name="fbepisodes", indexes = {
		@javax.persistence.Index(columnList = "date", name = "ep_date_index")
})
@Indexed
public class DBEpisode {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long generatedId;
	
	@Column(columnDefinition = "text", unique=true)
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String oldMap;
	
	@Column(columnDefinition = "text", unique=true)
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String newMap;
			
	private String legacyId = null;
	
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String title;
	
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String link;
	
	private int childCount;
	
	private long viewCount;
		
	@ManyToOne
	private DBUser author;
	
	@Field
	@SortableField
	@DateBridge(resolution = Resolution.SECOND)
	private Date date;
	
	@ManyToOne
	private DBUser editor;
	
	private Date editDate;
	
	@ManyToOne
	private DBEpisode parent;
		
	@Column(columnDefinition = "text")
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES, analyzer=@Analyzer(definition = "fbAnalyzer"))
	private String body;
	
	@OneToMany(mappedBy = "episode", fetch = FetchType.LAZY)
	@IndexedEmbedded
	private Set<DBEpisodeTag> lazytags;
	
	@Field
	@SortableField
	@DateBridge(resolution = Resolution.SECOND)
	private Date tagDate;
	
	public int episodeDepthFromNewMap() {
		return DBEpisode.episodeDepthFromNewMap(this.newMap);
	}
	
	public long getGeneratedId() {
		return generatedId;
	}

	public void setGeneratedId(long generatedId) {
		this.generatedId = generatedId;
	}

	public String getOldMap() {
		return oldMap;
	}

	public void setOldMap(String oldMap) {
		this.oldMap = oldMap;
	}

	public String getNewMap() {
		return newMap;
	}

	public void setNewMap(String newMap) {
		this.newMap = newMap;
	}

	public String getLegacyId() {
		return legacyId;
	}

	public void setLegacyId(String legacyId) {
		this.legacyId = legacyId;
	}

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getLink() {
		return link;
	}
	public void setLink(String link) {
		this.link = link;
	}

	public int getChildCount() {
		return childCount;
	}

	public void setChildCount(int childCount) {
		this.childCount = childCount;
	}
	public long getViewCount() {
		return viewCount;
	}

	public void setViewCount(long viewCount) {
		this.viewCount = viewCount;
	}

	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	public DBUser getAuthor() {
		return author;
	}
	public void setAuthor(DBUser author) {
		this.author = author;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public DBUser getEditor() {
		return editor;
	}

	public void setEditor(DBUser editor) {
		this.editor = editor;
	}

	public Date getEditDate() {
		return editDate;
	}

	public void setEditDate(Date editDate) {
		this.editDate = editDate;
	}

	public DBEpisode getParent() {
		return parent;
	}
	public void setParent(DBEpisode parent) {
		this.parent = parent;
	}
	
	public Set<DBEpisodeTag> getLazytags() {
		return lazytags;
	}

	public void setLazytags(Set<DBEpisodeTag> lazytags) {
		this.lazytags = lazytags;
	}

	public Date getTagDate() {
		return tagDate;
	}

	public void setTagDate(Date tagDate) {
		this.tagDate = tagDate;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(generatedId + ": " + title);
		sb.append(" [" + ((parent==null)?"root":parent.getTitle()) + "]");
		return sb.toString();
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBEpisode)) return false;
		DBEpisode that = (DBEpisode)o;
		return this.generatedId == that.generatedId;
	}
	
	public int hashCode() {
		return Long.hashCode(generatedId);
	}
	
	public static int episodeDepthFromNewMap(String newMap) {
		return (int)DB.newMapToIdList(newMap).count();
	}

}
