$(document).ready(function(event) {
	
	const fbjs = "fbjs";
	document.cookie = `${fbjs}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;`;
	document.cookie = `${fbjs}=true; Path=/; max-age=${60 * 60 * 24 * 7}`;
	
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
		$("#loginButton").attr('disabled', true);
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
				$("#loginButton").attr('disabled', false);
			}
		}
		req.send("email="+encodeURIComponent(emailString)+"&password="+encodeURIComponent(passwordString)); 
		event.preventDefault();
	});
	
	var outputTimestamps = document.getElementsByClassName("output-timestamp");
	var simpleTimestamps = document.getElementsByClassName("simple-timestamp");	
	
	var timestamps = outputTimestamps;
	for(var i = 0; i < timestamps.length; i++) {
		var timestamp = timestamps.item(i);
		var date = new Date(+(timestamp.dataset.unixtimemillis));
		timestamp.innerText = date.toLocaleDateString() + " " + date.toLocaleTimeString() + " " + date.toLocaleTimeString('en-us',{timeZoneName:'short'}).split(' ')[2];
	}
	
	timestamps = simpleTimestamps;
	for(var i = 0; i < timestamps.length; i++) {
		var timestamp = timestamps.item(i);
		timestamp.innerText = new Date(+(timestamp.dataset.unixtimemillis)).toLocaleDateString();
	}
	
	Array.prototype.forEach.call(document.getElementsByClassName("avatarimg"),x=>x.onerror = ()=>x.style.display='none');
	Array.prototype.forEach.call(document.getElementsByClassName("avatarsmall"),x=>x.onerror = ()=>x.style.display='none');
	
});
