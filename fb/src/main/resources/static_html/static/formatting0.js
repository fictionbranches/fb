//window.onload = function() {
$(document).ready(function() {
        var button = document.getElementById("formatButton");
        if (button) {
//                button.addEventListener("click", toggleFormatting);
                button.onclick = function(){
                        var x = document.getElementById("formattingDiv");
                        var frame = document.getElementById("formattingFrame");
                        var button = document.getElementById("formatButton");
                        if (x.style.display === "none") {
                                x.style.display = "block";
                                button.value = "Hide Formatting Help";
                        } else {
                                x.style.display = "none";
                                button.value = "Show Formatting Help";
                        }
                        frame.style.height = frame.contentWindow.document.body.scrollHeight + 'px';
                }
        }

        var sortSelect = document.getElementById("fbtableorder");
        if (sortSelect) {
                sortSelect.onchange = function(){
                        location = sortSelect.value;
                };
        }
//};
});
