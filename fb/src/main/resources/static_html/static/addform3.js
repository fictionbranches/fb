let oldBody = '';
let onBodyChange = (e,previewDiv) => {
	let body = e.target.value;
	if(body === '' || body === oldBody) {
		previewDiv.html('');
		return;
	}
	previewDiv.html('<h4>Body preview</h4><div class="fbcomment fbepisodebody"><p>' + markdownToHTML(body) + '</p></div>');
	
	/*let req = new XMLHttpRequest();
	req.open( "POST", "/fbapi/markdowntohtml", true );
	req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
	req.onload = function() {
		let result = req.responseText;
		previewDiv.html('<h4>Body preview</h4><div class="fbcomment"><p>' + result + '</p></div>');
	}
	req.send("body="+encodeURIComponent(body));*/
};
let initReady = () => {
	let bodyText = $("#bodytextarea");
	let previewDiv = $("#bodypreview");
	if (bodyText && previewDiv) {
		bodyText.on('input',e=>onBodyChange(e,previewDiv));
	}
};
$(document).ready(initReady);
