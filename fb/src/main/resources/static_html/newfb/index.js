$(document).ready(function(event) {
	
	function escapeHtml(str) {
		let div = document.createElement('div');
		div.appendChild(document.createTextNode(str));
		return div.innerHTML;
	}
	
	function getUrlParameter(sParam) {
		let sURLVariables = window.location.search.substring(1).split('&');
		for (let i = 0; i < sURLVariables.length; i++) {
			let sParameterName = sURLVariables[i].split('=');
			if (sParameterName[0] === sParam) {
				return sParameterName[1] === undefined ? false : decodeURIComponent(sParameterName[1]);
			}
		}
	}
	
	function getRoots(operator) {
		let req = new XMLHttpRequest();
		req.open( "POST", "/fbapi/getroots", true );
		req.setRequestHeader("Content-type", "application/json");
		req.onload = function() {
			let resultString = req.responseText;
			let result = JSON.parse(resultString);
			operator(result);
		}
		req.send(); 
	}
	
	function getEpisodeById(epid,operator) {
		let req = new XMLHttpRequest();
		req.open( "POST", "/fbapi/getepisode", true );
		req.setRequestHeader("Content-type", "application/json");
		req.onload = function() {
			let resultString = req.responseText;
			let result = JSON.parse(resultString);
			operator(result);
		}
		let obj = {};
		obj.id = epid;
		obj.sendhtml = 'false';
		req.send(JSON.stringify(obj));
	}
	
	function initDisplayEpisode(obj) {
		let ep = obj.episode;
		let html = "";
		html += '<p><h1>' + escapeHtml(ep.link) + '</h1>';
		html += 'By ' + escapeHtml(ep.authorName) + " (" + escapeHtml(ep.authorUsername) + ") at " + ep.date + "</p>";
		if (ep.parentId) {
			html += '<p><a href="?ep=' + ep.parentId + '">Go back</a></p>';
		}
		
		html += "<hr/>";
		html += markdownToHTML(ep.body);
		html += '<br/><hr/><br/>';
		for (let child of ep.children) { 
			html += '<p><a id="link' + child.id + '" href="?ep=' + child.id + '">' + escapeHtml(child.link) + '</a> (' + child.childCount + ')</p>';
		}
		$("#resultDiv").html(html);
		
	}
	
	function initDisplayRoots(roots) {
		var html = "";
		for (let ep of roots.episodes) {
			html += '<p><h1><a href="?ep=' + ep.id + '">' + escapeHtml(ep.link) + '</a> (' + ep.childCount + ')</h1>';
			html += 'By ' + escapeHtml(ep.authorName) + " (" + escapeHtml(ep.authorUsername) + ") at " + ep.date + "</p>";
		}
		$("#resultDiv").html(html);
	}
	
	let epid = getUrlParameter('ep');
	if (epid) getEpisodeById(epid, initDisplayEpisode);
	else getRoots(initDisplayRoots);
});
