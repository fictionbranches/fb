window.onload = function() {
    if (parent) {
        let oHead = document.getElementsByTagName("head")[0];
        let arrStyleSheets = parent.document.getElementsByTagName("link");
        for (let i = 0; i < arrStyleSheets.length; i++) {
            oHead.appendChild(arrStyleSheets[i].cloneNode(true));
        }
        arrStyleSheets = parent.document.getElementsByTagName("style");
        for (let i = 0; i < arrStyleSheets.length; i++) {
            oHead.appendChild(arrStyleSheets[i].cloneNode(true));
        }
    }
};
