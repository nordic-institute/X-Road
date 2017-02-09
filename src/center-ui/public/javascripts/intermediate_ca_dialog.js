var XROAD_INTERMEDIATE_CA_DIALOG = function() {
    var ocspResponders;
    var caId, intermediateCaId;

    function initDialog() {
        $("#intermediate_ca_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 450,
            width: 950,
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    }

    function initTabs() {
        $("#intermediate_ca_tabs").initTabs({
            activate: function(event, ui) {
                var tabRefreshers = {
                    "#intermediate_ca_ocsp_responders_tab": refreshOCSPResponders
                };

                var tab = $("a", ui.newTab).attr("href");
                if (tabRefreshers[tab]) {
                    tabRefreshers[tab].call(this);
                }
            }
        }).tabs("option", "active", 0);
    }

    function initIntermediateCATab() {
        $("#intermediate_ca_cert_view").click(function() {
            var params = {
                ca_id: caId,
                intermediate_ca_id: intermediateCaId
            };

            $.get(action("ca_cert"), params, function(response) {
                XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
            }, "json");

            return false;
        });
    }

    function initOCSPRespondersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "url" },
            { "mData": null,
              "sWidth": "13em",
              "mRender": function(data, type, full) {
                  return !full.has_cert ? "" :
                      "<button class='right'>" + _("common.cert_view") + "</button>";
              }
            }
        ];
        opts.asRowId = ["id"];

        ocspResponders = $("#intermediate_ca_ocsp_responders").dataTable(opts);

        $("#intermediate_ca_ocsp_responders").on("click", "tbody tr", function() {
            $("#intermediate_ca_ocsp_responder_edit, " +
              "#intermediate_ca_ocsp_responder_delete")
                .enable(ocspResponders.setFocus(0, this));
        });

        $("#intermediate_ca_ocsp_responders").on("click", "tbody button", function() {
            var selected = ocspResponders.fnGetData(
                $(this).closest("tr").get(0));
            var params = {
                ocsp_responder_id: selected.id
            };

            $.get(action("ocsp_responder_cert"), params, function(response) {
                XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
            }, "json");
        });
    }

    function initOCSPRespondersTab() {
        $("#intermediate_ca_ocsp_responder_edit").click(function() {
            var selected = ocspResponders.getFocusData();
            var params = {
                ocsp_responder_id: selected.id
            };

            XROAD_URL_AND_CERT_DIALOG.openEditDialog(
                "ocsp_responder", _("approved_cas.edit_ocsp_responder"),
                true, selected.url, selected.has_cert, params);
        });

        $("#intermediate_ca_ocsp_responder_add").click(function() {
            var params = {
                intermediate_ca_id: intermediateCaId
            };
            XROAD_URL_AND_CERT_DIALOG.openAddDialog("ocsp_responder",
                _("approved_cas.add_ocsp_responder"), true, params);
        });

        $("#intermediate_ca_ocsp_responder_delete").click(function() {
            var selected = ocspResponders.getFocusData();
            var params = {
                ocsp_responder_id: selected.id
            };

            $.post(action("delete_ocsp_responder"), params, refreshOCSPResponders);
        });
    }

    function refreshOCSPResponders() {
        var params = {
            ca_id: caId,
            intermediate_ca_id: intermediateCaId
        };

        $.get(action("ocsp_responders"), params, function(response) {
            ocspResponders.fnReplaceData(response.data);

            $("#intermediate_ca_ocsp_responder_edit, " +
              "#intermediate_ca_ocsp_responder_delete")
                .enable(!!ocspResponders.getFocus());
        }, "json");
    }

    function certUploadCallback(response) {
        if (response.success) {
            XROAD_APPROVED_CA_DIALOG.refreshIntermediateCAs();

            $("#ca_cert_upload_dialog").dialog("close");
            openEditDialog(caId, response.data);
        }

        showMessages(response.messages);
    }

    function openAddDialog(_caId) {
        caId = _caId;
        intermediateCaId = null;

        $("#ca_cert_upload_dialog").dialog(
            "option", "title", _("approved_cas.upload_intermediate_ca_cert"));
        $("#ca_cert_upload_dialog form")
            .attr("action", action("upload_intermediate_ca_cert"));
        $("#ca_cert_upload_dialog form #ca_id").enable().val(caId);

        $("#ca_cert_file").val("");
        $("#ca_cert_submit").text(_("common.ok")).disable();
        $("#ca_cert_upload_dialog").dialog("open");
    }

    function openEditDialog(_caId, selected) {
        caId = _caId;
        intermediateCaId = selected.id;
        ocspResponders.fnClearTable();

        $("#intermediate_ca_cert_subject_dn").text(selected.subject);
        $("#intermediate_ca_cert_issuer_dn").text(selected.issuer);
        $("#intermediate_ca_cert_valid_from").text(selected.valid_from);
        $("#intermediate_ca_cert_valid_to").text(selected.valid_to);

        $("#intermediate_ca_tabs").tabs("option", "active", 0);

        $("#intermediate_ca_dialog").dialog({
            title: _("approved_cas.intermediate_ca_details"),
            position: {
                my: "left top",
                at: "left top",
                of: $("#approved_ca_dialog")
            }
        });

        $("#intermediate_ca_dialog").dialog("open");
    }

    function isOpen() {
        return $("#intermediate_ca_dialog").dialog("isOpen");
    }
    function initTestability() {
        // add data-name attributes to improve testability
        $("#intermediate_ca_dialog").parent().attr("data-name", "intermediate_ca_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initDialog();
        initTabs();

        initIntermediateCATab();

        initOCSPRespondersTable();
        initOCSPRespondersTab();
        initTestability();
    });

    return {
        isOpen: isOpen,
        openAddDialog: openAddDialog,
        openEditDialog: openEditDialog,
        refreshOCSPResponders: refreshOCSPResponders,
        certUploadCallback: certUploadCallback
    };
}();
