function createMarkdown() {
	var md = markdownit('commonmark',{breaks: true});
	md.disable(['code','fence','html_block','html_inline']);
	md.enable(['table']);
	return md;
}

function createMarkdownNoImage() {
	var md = markdownit('commonmark',{breaks: true});
	md.disable(['code','fence','html_block','html_inline','image']);
	md.enable(['table']);
	return md;
}

var mdit = createMarkdown();
function markdownToHTML(body) {
	return mdit.render(body);
}

var mdit_noimage = null;
function markdownToHTMLNoImage(body) {
	if (mdit_noimage == null) {
		mdit_noimage = createMarkdownNoImage();
	}
	return mdit.render(body);
}
