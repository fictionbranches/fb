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
	
	function displayEpisode(obj) {
		let ep = obj.episode;
		let html = "";
		html += '<p><h1>' + escapeHtml(ep.link) + '</h1>';
		html += 'By ' + escapeHtml(ep.authorName) + " (" + escapeHtml(ep.authorUsername) + ") at " + ep.date + "</p>";
		if (ep.parentId) {
			html += '<p><a href="?ep=' + ep.parentId + '">Go back</a></p>';
		}
		
		html += "<hr/>";
		html += ep.body;
		html += '<br/><hr/><br/>';
		for (child of ep.children) { 
			html += '<p><a href="?ep=' + child.id + '">' + escapeHtml(child.link) + '</a> (' + child.childCount + ')</p>';
		}
		$("#resultDiv").html(html);
	}
	
	function displayRoots(roots) {
		var html = "";
		for (ep of roots.episodes) {
			html += '<p><h1><a href="?ep=' + ep.id + '">' + escapeHtml(ep.link) + '</a> (' + ep.childCount + ')</h1>';
			html += 'By ' + escapeHtml(ep.authorName) + " (" + escapeHtml(ep.authorUsername) + ") at " + ep.date + "</p>";
		}
		$("#resultDiv").html(html);
	}
	
	let epid = getUrlParameter('ep');
	if (epid) {
		let req = new XMLHttpRequest();
		req.open( "POST", "/fbapi/getepisode", true );
		req.setRequestHeader("Content-type", "application/json");
		
		req.onload = function() {
			let resultString = req.responseText;
			displayEpisode(JSON.parse(resultString));
		}
		let obj = {};
		obj.id = epid;
		obj.sendhtml = 'true';
		req.send(JSON.stringify(obj));
		
	} else {
		let req = new XMLHttpRequest();
		req.open( "POST", "/fbapi/getroots", true );
		req.setRequestHeader("Content-type", "application/json");
		req.onload = function() {
			let resultString = req.responseText;
			displayRoots(JSON.parse(resultString));
		}
		req.send(); 
	}
});