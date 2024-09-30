function createMarkdownBase() {
	var md = markdownit('commonmark',{breaks: true}).use(markdownitCentertext);
	md.enable(['table']);
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
	if (typeof console === 'object') {
		console.log("Rendering body");
		console.log(body);
		console.log("fixed body:");
		console.log(body.replace('-&gt;', '->').replace('&lt;-', '<-'));
	}
	return mdit.render(body.replace('-&gt;', '->').replace('&lt;-', '<-'));
}

var mdit_noimage = null;
function markdownToHTMLNoImage(body) {
	if (mdit_noimage == null) {
		mdit_noimage = createMarkdownNoImage();
	}
	return mdit_noimage.render(body);
}
