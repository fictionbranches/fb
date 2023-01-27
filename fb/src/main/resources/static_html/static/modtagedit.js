function deleteTag(shortName) {
	
	const container = document.getElementById('container_' + shortName);
	const deleteButton = document.getElementById('delete_btn_' + shortName);
	const tableRow = document.getElementById('row_' + shortName);
	const infoSpan = document.getElementById('span_' + shortName);
	
	infoSpan.innerHTML = "";
	
	const deleteOldStyle = deleteButton.style.display;
	deleteButton.style.display = "none";
	
	const confirmQuery = document.createElement('span');
	confirmQuery.innerHTML = "Are you sure?<br/>";
	
	const yesButton = document.createElement('button');
	yesButton.innerHTML = "Yes";
	
	const noButton = document.createElement('button');
	noButton.innerHTML = "No";
	
	const newDiv = document.createElement('div');
	newDiv.append(confirmQuery);
	newDiv.append(yesButton);
	newDiv.append(noButton);
	container.append(newDiv);
	
	noButton.onclick = function() {
		newDiv.remove();
		deleteButton.style.display = deleteOldStyle;
	};

	yesButton.onclick = function() {
		const req = new XMLHttpRequest();
		req.open("POST", "/fb/modtagdelete", true);
		req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		req.onload = function() {
			const resultString = req.responseText
			if (resultString === "done") {
				tableRow.parentNode.removeChild(tableRow);
			} else {
				infoSpan.innerHTML = " " + resultString;
				newDiv.remove();
				deleteButton.style.display = deleteOldStyle;
			}
		}
		req.send("shortname="+encodeURIComponent(shortName)); 
	};
	
}

$(document).ready(function() {
    const deleteButtons = document.getElementsByClassName('delete_btn');
    for (let i=0; i<deleteButtons.length; ++i) {
		const btn = deleteButtons.item(i);
		const name = btn.id.substring("delete_btn_".length);
		btn.onclick = () => deleteTag(name);
	}
});