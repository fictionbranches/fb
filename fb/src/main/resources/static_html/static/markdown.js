var mdit = markdownit('commonmark',{breaks: true});

mdit.disable(['code','fence','html_block','html_inline']);
function markdownToHTML(body) {
	return mdit.render(body);
}