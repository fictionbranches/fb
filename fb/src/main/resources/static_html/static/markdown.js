var mdit = markdownit('commonmark',{breaks: true});

mdit.disable(['code','fence','html_block','html_inline']);
mdit.enable(['table']);
function markdownToHTML(body) {
	return mdit.render(body);
}