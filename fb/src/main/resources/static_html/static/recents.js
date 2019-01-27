$(document).ready(function(event) {

	function getUrlParameter(sURL, sParam) {
		let sURLVariables = sURL.substring(1).split('&');
		for (let i = 0; i < sURLVariables.length; i++) {
			let sParameterName = sURLVariables[i].split('=');
			if (sParameterName[0] === sParam) {
				return sParameterName[1] === undefined ? true : decodeURIComponent(sParameterName[1]);
			}
		}
		return false;
	}

	function getUrlParameterFromCurrent(sParam) {
		let sURL = window.location.search;
		return getUrlParameter(sURL, sParam);
	}
		
	$("#recentcontainer").on('click', 'a', function(e) {
		e.preventDefault();
		
		$("#recentsdiv").html('<p>...loading...</p>');
		
		var url = $(this).attr('href'); 
		
		console.log(url);
		
		let story = getUrlParameter(url,'story');
		let page = getUrlParameter(url,'page');
		let reverse = getUrlParameter(url,'reverse');
		
		let req = new XMLHttpRequest();
		req.open( 'GET', '/fb/recentpage?story=' + encodeURIComponent(story) + '&page=' + encodeURIComponent(page) + (reverse?'&reverse':''), true );
		req.setRequestHeader("Content-type", "application/json");
		req.onload = function() {
			$("#recentsdiv").html(req.responseText);
		}
		req.send();
		
		window.history.pushState({'tag':'recents', 'url':url, 'original':false}, "", url);
		
	});
	
	history.replaceState({'tag':'recents', 'url':window.location.href, "original":true}, "", window.location.href);
});