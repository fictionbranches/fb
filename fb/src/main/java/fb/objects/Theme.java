package fb.objects;

import fb.db.DBTheme;

public class Theme {
	
	public final String name;
	public final String css;
	
	public static final String DEFAULT_NAME = "Default";
	public static final String DEFAULT_CSS = "";
	
	public Theme(DBTheme theme) {
		if (theme == null) {
			name = DEFAULT_NAME;
			css = DEFAULT_CSS;
		} else {
			this.name = theme.getName();
			this.css = theme.getCss();
		}
	}
	
	public Theme(String name, String css) {
		this.name = name==null?DEFAULT_NAME:name;
		this.css = css==null?DEFAULT_CSS:css;
	}
}
