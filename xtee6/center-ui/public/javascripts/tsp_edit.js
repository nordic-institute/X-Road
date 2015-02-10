(function(SDSB_TSP_EDIT, $, undefined) {
    var tspId;

    function enableActions() {
        console.log("tspUrl = " + $("#tsp_url").val());
        console.log("tspId = " + tspId);

        if ($("#tsp_url").val() && (tspId || $("#tsp_temp_cert_id").val())) {
            $("#tsp_edit_submit").enable();
        } else {
            $("#tsp_edit_submit").disable()
        }

        if (tspId || $("#tsp_temp_cert_id").val()) {
            $("#tsp_cert_view").enable();
        } else {
            $("#tsp_cert_view").disable();
        }
    }

    function editTsp() {
        var params = {
            id: tspId,
            url: $("#tsp_url").val()
        };

        $.post("tsps/edit_tsp", params, function() {
            SDSB_TSPS.refreshTable();
            $("#tsp_edit_dialog").dialog("close");
        }, "json");
    }

    function addTsp() {
        var params = {
            url: $("#tsp_url").val(),
            tempCertId: $("#tsp_temp_cert_id").val()
        };

        $.post("tsps/add_tsp", params, function() {
            SDSB_TSPS.refreshTable();
            $("#tsp_edit_dialog").dialog("close");
        }, "json");
    }

    function initTspEditDialog() {
        $("#tsp_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 250,
            width: 800,
            buttons: [
                { text: _("common.ok"),
                  id: "tsp_edit_submit",
                  disabled: "disabled",
                  click: function() {
                      tspId ? editTsp() : addTsp();
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    }

    function initCertDetailsDialog() {
        $("#cert_details_dialog").initDialog({
            autoOpen: false,
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
    }

    function initTspEditActions() {
        $(document).on("change", "#upload_tsp_cert_file", function() {
            $(this).closest("form").submit();
        });

        $(document).on("keyup", "#tsp_url", enableActions);

        $("#tsp_cert_view").click(function() {
            if (tspId) {
                var params = {
                    tspId: tspId
                };
                $.get("tsps/view_tsp_cert", params, function(response) {
                    $("#cert_details_dump").val(response.data.cert_dump);
                    $("#cert_details_dialog").dialog("open");
                }, "json");
            } else {
                $("#cert_details_dialog").dialog("open");
            }

            return false;
        });
    }

    $(document).ready(function() {
        initTspEditDialog();
        initCertDetailsDialog();
        initTspEditActions();
    });

    SDSB_TSP_EDIT.openEditDialog = function(id, url) {
        tspId = id;

        $("#tsp_url").val(url);
        $("#tsp_temp_cert_id").val("");
        $("#tsp_cert_file").text("");

        $("#upload_tsp_cert_file_button").disable();

        $("#tsp_edit_dialog").dialog(
            "option", "title", _("tsps.edit_existing"));

        enableActions();

        $("#tsp_edit_dialog").dialog("open")
    };

    SDSB_TSP_EDIT.openAddDialog = function() {
        tspId = null;
        $("#tsp_url, #tsp_temp_cert_id").val("");
        $("#tsp_cert_file").text("");

        $("#upload_tsp_cert_file_button").enable();

        $("#tsp_edit_dialog").dialog(
            "option", "title", _("tsps.edit_new"));

        enableActions();

        $("#tsp_edit_dialog").dialog("open")
    };

    SDSB_TSP_EDIT.uploadCallbackTspCert = function(response) {
        if (response.success) {
            $("#tsp_temp_cert_id").val(response.data.temp_cert_id);
            $("#tsp_cert_file").text($("#upload_tsp_cert_file").val());
            $("#cert_details_dump").val(response.data.cert_dump);
        } else {
            $("#tsp_temp_cert_id").val("");
            $("#tsp_cert_file").text("");
            $("#cert_details_dump").val("");
        }

        enableActions();
        showMessages(response.messages)
    };

}(window.SDSB_TSP_EDIT = window.SDSB_TSP_EDIT || {}, jQuery));
