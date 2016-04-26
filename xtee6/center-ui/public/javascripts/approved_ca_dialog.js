var XROAD_APPROVED_CA_DIALOG = function() {
    var ocspResponders;
    var intermediateCas;

    var caId;
    var tempCaCertId;

    function initCACertUploadDialog() {
        $("#ca_cert_upload_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 200,
            width: 600,
            buttons: [
                { id: "ca_cert_submit",
                  text: _("common.ok"),
                  click: function() {
                      $("#ca_cert").closest("form").submit();
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $(document).on("change", "#ca_cert", function() {
            $("#ca_cert_file").val($("#ca_cert").val());
            $("#ca_cert_submit").enable();
        });
    }

    function initCASettingsDialog() {
        $("#ca_settings_dialog").initDialog({
            title: _("approved_cas.ca_settings"),
            autoOpen: false,
            modal: true,
            height: 350,
            width: 800,
            buttons: [
                { id: "ca_settings_submit",
                  text: _("common.ok"),
                  click: function() {
                      var params = $("#ca_settings_dialog").find("input, select")
                          .serializeObject();
                      params.temp_cert_id = tempCaCertId;

                      $.post(action("add_top_ca"), params, function(response) {
                          XROAD_CAS.refreshCas();

                          $("#ca_settings_dialog").dialog("close");
                          openEditDialog(response.data);
                      }, "json");
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

    function initApprovedCADialog() {
        $("#approved_ca_dialog").initDialog({
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

    function initApprovedCATabs() {
        $("#approved_ca_tabs").initTabs({
            activate: function(event, ui) {
                var tabRefreshers = {
                    "#ocsp_responders_tab": refreshOCSPResponders,
                    "#intermediate_cas_tab": refreshIntermediateCAs
                };

                var tab = $("a", ui.newTab).attr("href");
                if (tabRefreshers[tab]) {
                    tabRefreshers[tab].call(this);
                }
            }
        }).tabs("option", "active", 0);
    }

    function initTopCACertTab() {
        $("#top_ca_cert_view").click(function() {
            var params = {
                ca_id: caId
            };

            $.get(action("ca_cert"), params, function(response) {
                XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
            }, "json");

            return false;
        });
    }

    function initCASettingsTab() {
        $(".name_extractor_disabled").click(function(){
            var checked = $(this).is(":checked");
            $(this).closest("table")
                .find(".name_extractor_member_class, .name_extractor_method")
                .val("").enable(!checked);
            $("#ca_settings_submit, #ca_settings_save").enable(checked);
        });

        $(".name_extractor_method").keyup(function() {
            $("#ca_settings_submit, #ca_settings_save")
                .enable($(this).val().length > 0);
        });

        $("#ca_settings_save").click(function() {
            var params = $("#ca_settings_tab").find("input, select")
                .serializeObject();
            params.ca_id = caId;

            $.post(action("edit_ca_settings"), params, function() {
                XROAD_CAS.refreshCas();
            }, "json");
        });
    }

    function initOCSPRespondersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "url", mRender: util.escape },
            { "mData": null,
              "sWidth": "13em",
              "mRender": function(data, type, full) {
                  return !full.has_cert ? "" :
                      "<button class='right'>" + _("common.cert_view") + "</button>";
              }
            }
        ];
        opts.asRowId = ["id"];

        ocspResponders = $("#ocsp_responders").dataTable(opts);

        $("#ocsp_responders").on("click", "tbody tr", function() {
            $("#ocsp_responder_edit, #ocsp_responder_delete")
                .enable(ocspResponders.setFocus(0, this));
        });

        $("#ocsp_responders").on("click", "tbody button", function() {
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

    function initOCSPResponderDialog() {
        XROAD_URL_AND_CERT_DIALOG.initForPrefix("ocsp_responder",
                function(params) { // onAdd
            $.post(action("add_ocsp_responder"), params, function(response) {
                if (XROAD_INTERMEDIATE_CA_DIALOG.isOpen()) {
                    XROAD_INTERMEDIATE_CA_DIALOG.refreshOCSPResponders();
                } else {
                    refreshOCSPResponders();
                }
                XROAD_URL_AND_CERT_DIALOG.closeDialog("ocsp_responder");
            }, "json");

        }, function(params) { // onEdit
            $.post(action("edit_ocsp_responder"), params, function(response) {
                if (XROAD_INTERMEDIATE_CA_DIALOG.isOpen()) {
                    XROAD_INTERMEDIATE_CA_DIALOG.refreshOCSPResponders();
                } else {
                    refreshOCSPResponders();
                }
                XROAD_URL_AND_CERT_DIALOG.closeDialog("ocsp_responder");
            }, "json");

        }, function(params) { // onCertView
            $.get(action("ocsp_responder_cert"), params, function(response) {
                XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
            }, "json");
        });
    }

    function initOCSPRespondersTab() {
        $("#ocsp_responder_edit").click(function() {
            var selected = ocspResponders.getFocusData();
            var params = {
                ocsp_responder_id: selected.id
            };

            XROAD_URL_AND_CERT_DIALOG.openEditDialog(
                "ocsp_responder", _("approved_cas.edit_ocsp_responder"),
                true, selected.url, selected.has_cert, params);
        });

        $("#ocsp_responder_add").click(function() {
            var params = {
                ca_id: caId
            };
            XROAD_URL_AND_CERT_DIALOG.openAddDialog("ocsp_responder",
                _("approved_cas.add_ocsp_responder"), true, params);
        });

        $("#ocsp_responder_delete").click(function() {
            var selected = ocspResponders.getFocusData();
            var params = {
                ocsp_responder_id: selected.id
            };

            $.post(action("delete_ocsp_responder"), params, refreshOCSPResponders);
        });
    }

    function initIntermediateCAsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "valid_from", "sWidth": "14em" },
            { "mData": "valid_to", "sWidth": "14em" }
        ];
        opts.asRowId = ["id"];

        intermediateCas = $("#intermediate_cas").dataTable(opts);

        $("#intermediate_cas").on("click", "tbody tr", function() {
            $("#intermediate_ca_edit, #intermediate_ca_delete")
                .enable(intermediateCas.setFocus(0, this));
        });
    }

    function initIntermediateCAsTab() {
        $("#intermediate_ca_edit").click(function() {
            var selected = intermediateCas.getFocusData();
            XROAD_INTERMEDIATE_CA_DIALOG.openEditDialog(caId, selected);
        });

        $("#intermediate_ca_add").click(function() {
            XROAD_INTERMEDIATE_CA_DIALOG.openAddDialog(caId);
        });

        $("#intermediate_ca_delete").click(function() {
            var params = {
                intermediate_ca_id: intermediateCas.getFocusData().id
            };
            $.post(action("delete_intermediate_ca"), params,
                refreshIntermediateCAs);
        });
    }

    function refreshOCSPResponders() {
        var params = {
            ca_id: caId
        };

        $.get(action("ocsp_responders"), params, function(response) {
            ocspResponders.fnReplaceData(response.data);

            $("#ocsp_responder_edit, #ocsp_responder_delete")
                .enable(!!ocspResponders.getFocus());
        });
    }

    function refreshIntermediateCAs() {
        var params = {
            ca_id: caId
        };

        $.get(action("intermediate_cas"), params, function(response) {
            intermediateCas.fnReplaceData(response.data);

            $("#intermediate_ca_edit, #intermediate_ca_delete")
                .enable(!!intermediateCas.getFocus());
        });
    }

    function certUploadCallback(response) {
        if (response.success) {
            tempCaCertId = response.data.temp_cert_id;

            $(".name_extractor_disabled", "#ca_settings_dialog")
                .prop("checked", false);
            $(".name_extractor_member_class, .name_extractor_method",
              "#ca_settings_dialog").val("").enable();
            $("#ca_settings_submit").disable();

            $("#ca_cert_upload_dialog").dialog("close");
            $("#ca_settings_dialog").dialog("open");
        }

        showMessages(response.messages);
    }

    function openAddDialog() {
        tempCaCertId = null;

        $("#ca_cert_upload_dialog").dialog(
            "option", "title", _("approved_cas.upload_ca_cert"));
        $("#ca_cert_upload_dialog form")
            .attr("action", action("upload_top_ca_cert"));
        $("#ca_cert_upload_dialog form #ca_id").disable();

        $("#ca_cert_file").val("");
        $("#ca_cert_submit").text(_("common.next")).disable();
        $("#ca_cert_upload_dialog").dialog("open");
    }

    function openEditDialog(selectedCa) {
        caId = selectedCa.id;
        ocspResponders.fnClearTable();
        intermediateCas.fnClearTable();

        $("#top_ca_cert_subject_dn").text(selectedCa.subject);
        $("#top_ca_cert_issuer_dn").text(selectedCa.issuer);
        $("#top_ca_cert_valid_from").text(selectedCa.valid_from);
        $("#top_ca_cert_valid_to").text(selectedCa.valid_to);

        $("#ca_settings_save").enable();
        $(".name_extractor_disabled", "#ca_settings_tab").prop(
            "checked", selectedCa.name_extractor_disabled);

        if (selectedCa.name_extractor_disabled) {
            $(".name_extractor_member_class, .name_extractor_method",
              "#ca_settings_tab").val("").disable();
        } else {
            $(".name_extractor_member_class", "#ca_settings_tab").val(
                selectedCa.name_extractor_member_class).enable();
            $(".name_extractor_method", "#ca_settings_tab").val(
                selectedCa.name_extractor_method).enable();
        }

        $("#approved_ca_tabs").tabs("option", "active", 0);

        $("#approved_ca_dialog").dialog(
            "option", "title", _("approved_cas.approved_ca_details"));
        $("#approved_ca_dialog").dialog("open");
    }
    
    function initTestability() {
        // add data-name attributes to improve testability
        $("#ca_cert_upload_dialog").parent().attr("data-name", "ca_cert_upload_dialog");
        $("#ca_settings_dialog").parent().attr("data-name", "ca_settings_dialog");
        $("#approved_ca_dialog").parent().attr("data-name", "approved_ca_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initCACertUploadDialog();
        initCASettingsDialog();

        initApprovedCADialog();
        initApprovedCATabs();

        initTopCACertTab();
        initCASettingsTab();
        initOCSPRespondersTable();
        initOCSPResponderDialog();
        initOCSPRespondersTab();
        initIntermediateCAsTable();
        initIntermediateCAsTab();
        initTestability();
    });

    return {
        openAddDialog: openAddDialog,
        openEditDialog: openEditDialog,
        certUploadCallback: certUploadCallback,
        refreshIntermediateCAs: refreshIntermediateCAs
    };
}();
