$(document).ready(function(event) {
	
	let tag = 'recentepisodesstag';
	
	let numPages = $('#recentsnumpages').val();
	
	let req = null;

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
	
	function rebuildPageSelector(root,page,reverse) {
		prevNext = "<div id=recentcontainer>";
		page-=0;
		if (numPages <= 8) {
			for (let i=1; i<=numPages; ++i) {
				if (i == page) prevNext+= i + " ";
				else prevNext += "<a class=\"monospace\" href=?story=" + root + "&page=" + i + (reverse?"&reverse":"") + ">" + i + "</a> ";
			}
		} else {
			if (page <= 3) { // 1 2 3 4 ... n
				for (let i=1; i<=4; ++i) {
					if (i == page) prevNext += i + " ";
					else prevNext += "<a class=\"monospace\" href=?story=" + root + "&page=" + i + (reverse?"&reverse":"") + ">" + i + "</a> ";
				}
				prevNext += ("... ");
				prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + numPages + (reverse?"&reverse":"") + ">" + numPages + "</a> ");
			} else if (page >= numPages-3) { // 1 ... n-3 n-2 n-1 n
				prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + 1 + (reverse?"&reverse":"") + ">" + 1 + "</a> ");
				prevNext += ("... ");
				for (let i=numPages-3; i<=numPages; ++i) {
					if (i == page) prevNext += (i + " ");
					else prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + i + (reverse?"&reverse":"") + ">" + i + "</a> ");
				}
			} else { // 1 ... x-2 x-1 x x+1 x+2 ... n
				prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + 1 + (reverse?"&reverse":"") + ">" + 1 + "</a> ");
				prevNext += ("... ");
				for (let i=page-2; i<=page+2; ++i) {
					if (i == page) prevNext += (i + " ");
					else prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + i + (reverse?"&reverse":"") + ">" + i + "</a> ");
				}
				prevNext += ("... ");
				prevNext += ("<a class=\"monospace\" href=?story=" + root + "&page=" + numPages + (reverse?"&reverse":"") + ">" + numPages + "</a> ");
			}
		}
		$("#recentcontainer").html(prevNext);
	}
	
	function getPage(story,page,reverse) {
		$("#recentsdiv").html('<p>...loading...</p>');
		
		if (req) {
			req.abort();
			req = null;
		}
		
		req = new XMLHttpRequest();
		req.open( 'GET', '/fb/recentpage?story=' + encodeURIComponent(story) + '&page=' + encodeURIComponent(page) + (reverse?'&reverse':''), true );
		req.onload = function() {
			$("#recentsdiv").html(req.responseText);
		}
		req.send();
	}
		
	$("#recentcontainer").on('click', 'a', function(e) {
		e.preventDefault();
		let url = $(this).attr('href'); 
		
		let story = getUrlParameter(url,'story');
		let page = getUrlParameter(url,'page');
		let reverse = getUrlParameter(url,'reverse');
		
		getPage(story,page,reverse);
		
		rebuildPageSelector(story, page, reverse);
		
		window.history.pushState({'tag':tag, 'url':url, 'original':false}, "", url);
	});
	
	$(window).on('popstate', function(event) {
		if(event.originalEvent.state.tag == tag){
			
			let story = getUrlParameter(event.originalEvent.state.url,'story');
			let page = getUrlParameter(event.originalEvent.state.url,'page');
			let reverse = getUrlParameter(event.originalEvent.state.url,'reverse');
			
			getPage(story,page,reverse);	
			rebuildPageSelector(story, page, reverse);
		}
	});
	
	history.replaceState({'tag':tag, 'url':window.location.href, "original":true}, "", window.location.href);
});