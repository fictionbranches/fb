package fb.objects;

import fb.db.DBTheme;

public class Theme {
	
	public final String name;
	public final String css;
	
	public Theme(DBTheme theme) {
		this.name = theme.getName();
		this.css = theme.getCss();
	}
	
	public Theme(String name, String css) {
		this.name = name;
		this.css = css;
	}
}
