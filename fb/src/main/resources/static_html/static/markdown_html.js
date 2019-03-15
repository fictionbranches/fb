$(document).ready(function(event) {
	Array.prototype.forEach.call(document.getElementsByClassName("fbrawmarkdown"),x=>x.innerHTML = markdownToHTML(x.innerHTML));
});
