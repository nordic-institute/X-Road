(function(SDSB_CERT_DETAILS_DIALOG, $, undefined) {
    SDSB_CERT_DETAILS_DIALOG.openDialog = function(dump) {
        $("#cert_details_dump").val(dump);

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
}(window.SDSB_CERT_DETAILS_DIALOG = window.SDSB_CERT_DETAILS_DIALOG || {}, jQuery));
