$(document).ready(function(event) {
	
	if (document.cookie.indexOf("fbjs=") < 0) document.cookie = "fbjs=true; max-age=" + (60 * 60 * 24 * 365 * 100);
	
	$("#logoutButton").submit(function( event ) {
		var req = new XMLHttpRequest();
		req.open( "GET", "/fb/logout", true );
		req.onload = function () {
			location.reload();
		}
		req.send ();
		event.preventDefault();
	});
	$("#loginForm").submit(function( event ) {
		var emailString = $("#loginEmail").val();
		var passwordString = $("#loginPassword").val();
		var req = new XMLHttpRequest();
		req.open( "POST", "/fb/loginpost2", true );
		req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		req.onload = function() {
			var resultString = req.responseText
			if (resultString === "loggedin") {
				location.reload();
			} else {
				$("#loginResultDiv").html("<br/>" + resultString);
			}
		}
		req.send("email="+encodeURIComponent(emailString)+"&password="+encodeURIComponent(passwordString)); 
		event.preventDefault();
	});
});
