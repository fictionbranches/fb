function createMarkdownBase() {
	var md = markdownit('commonmark',{breaks: true}).use(markdownitCentertext);
	md.enable(['table','strikethrough']);
	return md;
}

function createMarkdown() {
	var md = createMarkdownBase();
	md.disable(['code','fence','html_block','html_inline']);
	return md;
}

function createMarkdownNoImage() {
	var md = createMarkdownBase();
	md.disable(['code','fence','html_block','html_inline','image']);
	return md;
}

var mdit = createMarkdown();
function markdownToHTML(body) {
	return mdit.render(body.replace('-&gt;', '->').replace('&lt;-', '<-'));
}

var mdit_noimage = null;
function markdownToHTMLNoImage(body) {
	if (mdit_noimage == null) {
		mdit_noimage = createMarkdownNoImage();
	}
	return mdit_noimage.render(body.replace('-&gt;', '->').replace('&lt;-', '<-'));
}
