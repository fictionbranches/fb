package fb.db;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

import fb.DB;


@Entity
@Table(name="fbepisodes")
@Indexed
public class DBEpisode {
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private long generatedId;
	
	@Column(columnDefinition = "text", unique=true)
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String id;
			
	private String legacyId = null;
	
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String title;
	
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String link;
	
	private int depth;
	
	private int childCount;
	
	private long viewCount;
		
	@ManyToOne
	private DBUser author;
	
	private Date date;
	
	@ManyToOne
	private DBUser editor;
	
	private Date editDate;
	
	@ManyToOne
	private DBEpisode parent;
	
	@OneToOne(mappedBy = "episode")
	private DBModEpisode mod;
	
	@Column(columnDefinition = "text")
	@Field(index=Index.YES, store=Store.NO, analyze=Analyze.YES)
	private String body;
	
	// The following constructor, getters, and setters are required for JPA persistence
	public DBEpisode() {} 
	
	public long getGeneratedId() {
		return generatedId;
	}

	public void setGeneratedId(long generatedId) {
		this.generatedId = generatedId;
	}
	
	/**
	 * DO NOT USE - for Hibernate's use only
	 * @return
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * DO NOT USE - for Hibernate's use only
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Get an episode map, which is it's ID without the prefixed '$'
	 * @return
	 */
	public String getMap() {
		return DB.idToMap(id);
		//return id.substring(1, id.length());
	}
	
	/**
	 * Set an episode map, which sets this.id='$'+id
	 * @param id
	 */
	public void setMap(String id) {
		//this.id = "$" + id;
		this.id = DB.mapToId(id);
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
	public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
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
	
	public DBModEpisode getMod() {
		return mod;
	}

	public void setMod(DBModEpisode mod) {
		this.mod = mod;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(id + ": " + title);
		sb.append(" [" + ((parent==null)?"root":parent.getTitle()) + "]");
		return sb.toString();
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof DBEpisode)) return false;
		DBEpisode that = (DBEpisode)o;
		return this.id.equals(that.id);
	}
	
	public int hashCode() {
		return id.hashCode();
	}
}
