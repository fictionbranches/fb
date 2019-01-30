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
	
});