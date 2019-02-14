$(document).ready(function(event) {
		
	let button = $('#fbcommentbutton');
	let text = $('#fbcommenttext');
	let form = $('#fbcommentform');
	let extra = $('#fbcommentformextra');
	
	button.prop("disabled",true);
	
	text.bind('input propertychange', function() {
		button.prop("disabled",true);
		let l = text.val().length;
		let empty = l<=0;
		let full = l>5000;
		if (empty || full) button.prop("disabled",true);
		else button.prop("disabled",false);
		
		extra.html("(" + l + "/5000)");
	});
	
	let oldEmphasizedComment = null;
	function emphasizeComment() {
		let a = window.location.hash.substr(1);
		console.log('emphasizing comment ' + a);
		if (a) {
			if (a.length > 7) {
				if (a.startsWith('comment')) {
					let div = $('#'+a);
					if (div) {
						console.log('Old border: ' + div.border);
						console.log('Old div: ' + div.html());
						
						//div.border = '5px solid';
						div.css('border', '5px solid');
						console.log('comment emphasized');
						if (oldEmphasizedComment) {
							//oldEmphasizedComment.border = '1px solid';
							oldEmphasizedComment.css('border', '1px solid');
						}
						oldEmphasizedComment = div;
					}
				}
			}
		}
	}
	
	$(window).bind('hashchange', function() {
		emphasizeComment();
	});
	
	emphasizeComment();
});