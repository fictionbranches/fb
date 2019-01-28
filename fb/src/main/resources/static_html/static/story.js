$(document).ready(function(event) {
		
	let button = $('#fbcommentbutton');
	let text = $('#fbcommenttext');
	let form = $('#fbcommentform');
	
	button.prop("disabled",true);
	
	text.bind('input propertychange', function() {
		button.prop("disabled",true);
		if (text.val().length > 0) button.prop("disabled",false);
		else button.prop("disabled",true);
	});
	
});