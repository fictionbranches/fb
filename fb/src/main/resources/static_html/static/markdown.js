var parser = new commonmark.Parser();
var renderer = new commonmark.HtmlRenderer({
	safe : true,
	softbreak : '<br/>'
});
function markdownToHTML(body) {
	var parsed = parser.parse(body);
	var walker = parsed.walker();
	var event, node;
	var inEmph = false;

	while ((event = walker.next())) {
		  node = event.node;
		  if (node.type != 'paragraph' && node.type != 'text' && node.type != 'document') print(node.type);
		  if (node.type === 'code_block') {
		    if (event.entering) {
		      inEmph = true;
		    } else {
		      inEmph = false;
		      // add Emph node's children as siblings
		      while (node.firstChild) {
		        node.insertBefore(node.firstChild);
		      }
		      // remove the empty Emph node
		      node.unlink()
		    }
		  } else if (inEmph && node.type === 'text') {
		      node.literal = node.literal;
		  }
		}

	return renderer.render(parsed);
}
