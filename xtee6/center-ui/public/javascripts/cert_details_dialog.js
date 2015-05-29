(function(XROAD_CERT_DETAILS_DIALOG, $, undefined) {
    XROAD_CERT_DETAILS_DIALOG.openDialog = function(dump) {
        $("#cert_details_dump").text(dump);

        $("#cert_details_dialog").initDialog({
            modal: true,
            height: 600,
            width: 800,
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    };
}(window.XROAD_CERT_DETAILS_DIALOG = window.XROAD_CERT_DETAILS_DIALOG || {}, jQuery));
