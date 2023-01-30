function deleteTag(tagid) {

	const container = $('#container_' + tagid);
	const deleteButton = $('#delete_btn_' + tagid);
	const editButton = $('#edit_btn_' + tagid);
	const tableRow = $('#row_' + tagid);
	const infoSpan = $('#span_' + tagid);

	infoSpan.html("");

	deleteButton.hide();
	editButton.hide();

	const confirmQuery = document.createElement('span');
	confirmQuery.innerHTML = "Are you sure?";

	const yesButton = document.createElement('button');
	yesButton.innerHTML = "Yes";

	const noButton = document.createElement('button');
	noButton.innerHTML = "No";

	const newDiv = document.createElement('div');
	newDiv.append(confirmQuery);
	newDiv.append(document.createElement('br'));
	newDiv.append(yesButton);
	newDiv.append(noButton);
	container.append(newDiv);

	noButton.onclick = function() {
		newDiv.remove();
		deleteButton.show();
		editButton.show();
	};

	yesButton.onclick = function() {

		yesButton.disabled = true;
		noButton.disabled = true;
		confirmQuery.innerHTML = "Loading...";

		const req = new XMLHttpRequest();
		req.open("POST", "/fb/modtagedit_delete", true);
		req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		req.onload = function() {
			const resultString = req.responseText
			if (resultString === "done") {
				tableRow.remove();
			} else {
				infoSpan.html("<br/>" + resultString);
				newDiv.remove();
				deleteButton.show();
				editButton.show();
			}
		}
		req.send("tagid=" + tagid);
	};
}

function editTag(tagid) {
	const container = $('#container_' + tagid);
	const deleteButton = $('#delete_btn_' + tagid);
	const editButton = $('#edit_btn_' + tagid);
	const tableRow = $('#row_' + tagid);
	const infoSpan = $('#span_' + tagid);

	infoSpan.html("");

	deleteButton.hide();
	editButton.hide();

	const yesButton = document.createElement('button');
	yesButton.innerHTML = "Save";

	const noButton = document.createElement('button');
	noButton.innerHTML = "Cancel";

	const newDiv = document.createElement('div');
	newDiv.append(document.createElement('br'));
	newDiv.append(yesButton);
	newDiv.append(noButton);
	container.append(newDiv);

	noButton.onclick = function() {
		newDiv.remove();
		deleteButton.show();
		editButton.show();

		$('#span_short_' + tagid).show();
		$('#span_long_' + tagid).show();
		$('#span_desc_' + tagid).show();

		$('#input_short_' + tagid).remove();
		$('#input_long_' + tagid).remove();
		$('#input_desc_' + tagid).remove();
	};

	const oldShort = $('#span_short_' + tagid).text();
	const oldLong = $('#span_long_' + tagid).text();
	const oldDesc = $('#span_desc_' + tagid).text();

	$('#span_short_' + tagid).hide();
	$('#span_long_' + tagid).hide();
	$('#span_desc_' + tagid).hide();

	$('#td_short_' + tagid).append(`<input type="text" id="input_short_${tagid}" value="${oldShort}"></input>`);
	$('#td_long_' + tagid).append(`<input type="text" id="input_long_${tagid}" value="${oldLong}"></input>`);
	$('#td_desc_' + tagid).append(`<input type="text" id="input_desc_${tagid}" value="${oldDesc}"></input>`);

	yesButton.onclick = function() {
		
		const newShort = $('#input_short_' + tagid).val();
		const newLong = $('#input_long_' + tagid).val();
		const newDesc = $('#input_desc_' + tagid).val();
		
		if (newShort === oldShort && newLong == oldLong && newDesc == oldDesc) {
			noButton.onclick();
			return;
		}

		if (newShort.length == 0 || newLong.length == 0 || newDesc.length == 0) {
			infoSpan.html("Short name, long name, and description cannot be empty.");
			return;
		}


		yesButton.disabled = true;
		noButton.disabled = true;
		infoSpan.html("Loading...");

		const req = new XMLHttpRequest();
		req.open("POST", "/fb/modtagedit_edit", true);
		req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		req.onload = function() {
			if (req.responseText === "done") {
				location.reload();
			} else {
				infoSpan.html(req.responseText + '<br/>');
				yesButton.disabled = false;
				noButton.disabled = false;
			}
		}
		req.send(
			"tagid=" + tagid +
			"&shortname=" + encodeURIComponent(newShort) +
			"&longname=" + encodeURIComponent(newLong) +
			"&description=" + encodeURIComponent(newDesc)
		);
	};
}

function addTag() {
	const short = $('#shortname').val();
	const long = $('#longname').val();
	const desc = $('#description').val();

	if (short.length == 0 || long.length == 0 || desc.length == 0) {
		$('#add_span').html("Short name, long name, and description cannot be empty.<br/>");
		return;
	}
	
	const req = new XMLHttpRequest();
	req.open("POST", "/fb/modtagedit_add", true);
	req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
	req.onload = function() {
		if (req.responseText === "done") {
			location.reload();
		} else {
			$('#add_span').html(req.responseText + '<br/>');
		}
	}
	req.send(
		"shortname=" + encodeURIComponent(short) +
		"&longname=" + encodeURIComponent(long) +
		"&description=" + encodeURIComponent(desc)
	);

}

$(document).ready(function() {

	$('#add_button').click(addTag);

	$('.delete_btn').map((i, btn) => {
		const tagid = btn.id.substring("delete_btn_".length);
		btn.onclick = () => deleteTag(tagid);
	});

	$('.edit_btn').map((i, btn) => {
		btn.onclick = () => editTag(btn.id.substring("edit_btn_".length));
	});

});

