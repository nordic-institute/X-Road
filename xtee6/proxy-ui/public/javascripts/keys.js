var oKeys;

function escapeSelector(str) {
    return !str ? ""
        : str.replace(/([;&,\.\+\*\~':"\!\^#$%@\[\]\(\)=>\|])/g, '_');
}

function enableActions() {
    $("#unregister").hide();
    $("#register").disable();
    $("#details, #generate_key, #generate_csr, #delete, #activate, #disable").disable();
    $("#refresh, #import_cert").enable();

    if ($(".token.row_selected").length > 0) {
        $("#details, #generate_key").enable();
    }
    if ($(".key.row_selected").length > 0) {
        $("#details, #delete").enable();
    }
    if ($(".key.row_selected:not(.key-unavailable)").length > 0) {
        $("#generate_csr").enable();
    }
    if ($(".cert-active.row_selected:not(.unsaved)").length > 0) {
        $("#activate").hide();
        $("#disable").show();
        $("#details, #delete, #disable").enable();
    }
    if ($(".cert-inactive.row_selected:not(.unsaved)").length > 0) {
        $("#disable").hide();
        $("#activate").show();
        $("#details, #delete, #activate").enable();
    }
    if ($(".cert-request.row_selected").length > 0) {
        $("#activate, #disable").disable();
        $("#delete").enable();
    }

    $(".token-available .activate_token").enable();
    $(".token-unavailable .activate_token").disable();

    if (oKeys.getFocus() && oKeys.getFocusData()) {
        var cert = oKeys.getFocusData();

        if (cert.register_enabled) {
            $("#register").enable();
        } else {
            $("#register").disable();
        }

        if (cert.unregister_enabled) {
            $("#unregister").show();
            $("#register").hide();
            $("#delete").disable();
        } else {
            $("#unregister").hide();
            $("#register").show();
        }
    }
}

function initDialogs() {
    $("#activate_token_dialog").initDialog({
        autoOpen: false,
        modal: true,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = $("#activate_token_form").serialize();

                  $.post(action("activate_token"), params, function(response) {
                      oKeys.fnClearTable(false);
                      oKeys.fnAddData(response.data);
                      enableActions();
                      $(dialog).dialog("close");
                      $("#token_id, #pin", dialog).val("");
                  }, "json");
              }
            },
            { text: "Cancel",
              click: function() {
                  $("#token_id, #pin", this).val("");
                  $(this).dialog("close");
              }
            }
        ]
    });

    $(document).on('click', ".activate_token", function() {
        $("#activate_token_form #token_id").val(
            $(this).closest(".token").attr("data-id"));
        $("#activate_token_dialog").dialog("open");
    });

    $("#generate_csr_dialog").initDialog({
        autoOpen: false,
        modal: true,
        width: 550,
        buttons: [
            { text: "OK",
              click: function() {
                  $("#key_usage", this).val(
                      $("#key_usage_select", this).val());

                  var dialog = this;
                  var params = $("form", this).serializeObject();

                  $.post(action("generate_csr"), params, function(response) {
                      location.href = action("download_csr")
                          + "?csr=" + response.data.redirect;

                      $(dialog).dialog("close");
                      $("input[type!=hidden], select", dialog).val("");
                      $("#key_usage_select").enable();
                      $("#refresh").click();
                  });
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
                  $("input[type!=hidden], select", this).val("");
                  $("#key_usage_select").enable();
              }
            }
        ]
    });

    $("#generate_csr").click(function() {
        var row = oKeys.getFocus();
        var keyId = row.dataset.id;
        var keyUsage = null;

        $.each(oKeys.fnGetData(), function(idx, val) {
            if (val.key_id == keyId) {
                keyUsage = val.key_usage;
                return false;
            }
        });

        if (keyUsage) {
            $("#generate_csr_dialog form #key_usage_select")
                .val(keyUsage).disable();
        }

        if (keyUsage == "auth") {
            $("#generate_csr_dialog form #member_id")
                .disable().hide().closest("div").hide();
        } else {
            $("#generate_csr_dialog form #member_id")
                .enable().show().closest("div").show();
        }

        $("#generate_csr_dialog form #token_id").val(row.dataset.parentId);
        $("#generate_csr_dialog form #key_id").val(row.dataset.id);
        $("#generate_csr_dialog").dialog("open");
    });

    $("#import_cert_dialog").initDialog({
        autoOpen: false,
        modal: true,
        buttons: [
            { text: "OK",
              click: function() {
                  $("#import_cert_form").submit();
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#import_cert").click(function() {
        $("#import_cert_dialog input[type!=hidden]").val("");
        $("#import_cert_dialog").dialog("open");
    });

    $("#token_details_dialog, #key_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 300,
        width: 600,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = $("form", this).serialize();

                  $.post(action("friendly_name"), params, function(response) {
                      oKeys.fnClearTable(false);
                      oKeys.fnAddData(response.data);
                      enableActions();

                      $(dialog).dialog("close");
                      $("input[type!=hidden]", dialog).val("");
                  }, "json");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#cert_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 600,
        width: 700,
        buttons: [
            { text: "OK",
              click: function() {
                  $(this).dialog("close");
                  $(".dialog-body", this).html("");
              }
            }
        ]
    });

    $(document).on("dblclick", ".token", function() {
        var row = $(this).closest("tr").get(0);
        var params = {
            token_id: row.dataset.id
        };

        $.get(action("token_details"), params, function(data) {
            $("#token_details_dialog .dialog-body").html(data);
            $("#token_details_dialog").dialog("open");
        }, "html");
    });

    $(document).on("dblclick", ".key", function() {
        var row = $(this).closest("tr").get(0);
        var params = {
            token_id: row.dataset.parentId,
            key_id: row.dataset.id
        };

        $.get(action("key_details"), params, function(data) {
            $("#key_details_dialog .dialog-body").html(data);
            $("#key_details_dialog").dialog("open");
        }, "html");
    });

    $(document).on("dblclick", ".cert-active , .cert-inactive", function() {
        var row = $(this).closest("tr").get(0);
        var data = oKeys.fnGetData(row);

        var params = {
            token_id: data.token_id,
            key_id: data.key_id,
            cert_id: data.cert_id
        };

        $.get(action("cert_details"), params, function(data) {
            $("#cert_details_dialog .dialog-body").html(data);
            $("#cert_details_dialog").dialog("open");
        }, "html");
    });

    $("#register_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 200,
        width: 500,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = $("form", this).serializeObject();
                  var focusData = oKeys.getFocusData();

                  params.token_id = focusData.token_id;
                  params.key_id = focusData.key_id;
                  params.cert_id = focusData.cert_id;

                  $.post(action("register"), params, function(response) {
                      oKeys.fnClearTable();
                      oKeys.fnAddData(response.data);
                      enableActions();

                      $(dialog).dialog("close");
                  }, "json");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#register").click(function() {
        $("#register_dialog #address").val("");
        $("#register_dialog").dialog("open");
    });
}

function uploadCallback(response) {
    if (response.success) {
        $("#import_cert_dialog").dialog("close");
        $("#refresh").click();
    }

    showMessages(response.messages);
}

$(document).ready(function() {
    $("#addkey_form, #loadcert_form").hide();

    oKeys = $("#keys").dataTable({
        "bSort": true,
        "bFilter": true,
        "sScrollY": "400px",
        "bScrollCollapse": true,
        "bPaginate": false,
        "bAutoWidth": false,
        "sDom": "t<'dataTables_footer'fp<'clearer'>>",
        "oLanguage": {
            "sZeroRecords": _("keys.no_keys")
        },
        "aoColumns": [
            { "mData": "token_id",
              "bVisible": false },
            { "mData": "token_friendly_name",
              "bVisible": false },
            { "mData": "token_available",
              "bVisible": false },
            { "mData": "token_active",
              "bVisible": false },
            { "mData": "key_id",
              "bVisible": false },
            { "mData": "key_friendly_name",
              "bVisible": false },
            { "mData": "key_usage",
              "bVisible": false },
            { "mData": "cert_id",
              "bVisible": false },
            { "mData": "cert_friendly_name",
              "sClass": "cert-friendly-name" },
            { "mData": "cert_member_code" },
            { "mData": "cert_ocsp_response",
              "sClass": "cert-ocsp-response align-center" },
            { "mData": "cert_expires",
              "sClass": "cert-expires align-center" },
            { "mData": "cert_status",
              "sClass": "align-center" },
            { "mData": "cert_saved_to_conf",
              "bVisible": false },
            { "mData": "cert_request",
              "bVisible": false },
            { "mData": "buttons",
              "sClass": "buttons",
              "mRender": function(data, type, full) {
                  if (!full.cert_saved_to_conf && full.cert_importable) {
                      return "<button class='import'>Import</button>";
                  } else {
                      return "";
                  }
              }}
        ],
        "fnRowCallback": function(nRow, oData) {
            $(nRow).addClass(
                oData.token_available ?
                    "token-available" : "token-unavailable");

            if (!oData.key_available) {
                $(nRow).addClass("key-unavailable");
            }

            $(nRow).addClass(
                oData.token_active ? "token-active" : "token-inactive");

            if (oData.cert_id) {
                // let's make it easy to determine which tokens and
                // keys are not saved to configuration
                $(nRow).addClass(
                    !oData.cert_saved_to_conf ? "unsaved"
                        : escapeSelector(oData.token_id) + "-token-saved "
                        + escapeSelector(oData.key_id) + "-key-saved");

                if (oData.cert_request) {
                    $(nRow).addClass("cert-request");
                } else {
                    $(nRow).addClass(oData.cert_active ?
                        "cert-active" : "cert-inactive");
                }

                if (oData.cert_ocsp_response) {
                    $(nRow).addClass(
                        "ocsp-response-" + oData.cert_ocsp_response.toLowerCase());
                }
                if (oData.cert_expires_in < 0) {
                    $(nRow).addClass("expired");
                } else if (oData.cert_expires_in < 10) {
                    $(nRow).addClass("expiring");
                }
            } else {
                // mark the rows used to create empty groups
                $(nRow).addClass("empty");
            }

            return nRow;
        },
        "fnDrawCallback": function(oSettings) {
            // hide rows used to create empty groups
            this.$("tr.empty", {"filter": "applied"}).hide();
        }
    });

    oKeys.rowGrouping({
        "asGroupingColumnName": [ "token_id", "key_id" ],
        "aiGroupingColumnIndex": [ 0, 4 ],
        "afnGroupLabelFormat": [
            function(oData, sLabel) {
                var friendlyName = oData.token_friendly_name
                    ? oData.token_friendly_name : oData.token_id;

                var locked = " <span class='locked'>BLOCKED</span>";

                var buttons = "<div class='right'>"
                    + "<button class='activate_token'>Enter PIN</button>"
                    + "<button class='deactivate_token'>Logout</button>"
                    + "</div>";

                return "<div class='left'>Token: " + friendlyName +
                    (oData.token_locked ? locked : "") + "</div>" +
                    (oData.token_activatable ? buttons : "");
            },
            function(oData, sLabel) {
                var friendlyName = oData.key_friendly_name
                    ? oData.key_friendly_name : oData.key_id;
                var keyUsage = oData.key_usage
                    ? oData.key_usage : "?";

                return "Key: " + friendlyName
                    + " (<span class='key-usage'>" + keyUsage + "</span>)";
            }
        ],
        "afnGroupClass": [
            function (oData) {
                var classes = "token";

                classes += oData.token_available ?
                    " token-available" : " token-unavailable";
                classes += oData.token_active ?
                    " token-active" : " token-inactive";

                var tokenId = escapeSelector(oData.token_id);
                if ($("." + tokenId + "-token-saved").length == 0) {
                    classes += " unsaved";
                }

                return classes;
            },
            function (oData) {
                var classes = "key";

                classes += oData.token_available ?
                    " token-available" : " token-unavailable";
                classes += oData.token_active ?
                    " token-active" : " token-inactive";

                var keyId = escapeSelector(oData.key_id);
                if ($("." + keyId + "-key-saved").length == 0) {
                    classes += " unsaved";
                }

                return classes;
            }
        ]
    });

    initDialogs();

    $("#refresh").click(function() {
        $.get(action("refresh"), null, function(response) {
            oKeys.fnClearTable(false);
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    }).click();

    focusInput();

    $("#keys tbody tr:not(.token.token-inactive, .key.token-inactive)")
        .live("click", function() {
            oKeys.setFocus(0, this);
            enableActions();
        });

    $(document).on('click', ".deactivate_token", function() {
        var params = {
            token_id: $(this).closest(".token").attr("data-id")
        };
        $.post(action("deactivate_token"), params, function(response) {
            oKeys.fnClearTable(false);
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#generate_key").click(function() {
        clearMessages();

        var params = {
            token_id: oKeys.getFocus().dataset.id
        };

        $.post(action("generate_key"), params, function(response) {
            oKeys.fnAddData(response.data);
            oKeys.setFocus(0, $(".key[data-id='" + response.data[1].key_id + "']"));
            enableActions();
        }, "json");
    });

    $("#key_usage_select").change(function() {
        if ($(this).val() == "auth") {
            $("#generate_csr_dialog form #member_id")
                .disable().hide().closest("div").hide();
        } else {
            $("#generate_csr_dialog form #member_id")
                .enable().show().closest("div").show();
        }
    });

    $(document).on("click", ".import", function() {
        var row = $(this).closest("tr").get(0);
        var data = oKeys.fnGetData(row);
        var params = {
            token_id: data.token_id,
            key_id: data.key_id,
            cert_id: data.cert_id
        };
        $.post(action("import"), params, function(response) {
            oKeys.fnClearTable(false);
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#delete").click(function() {
        var row = oKeys.getFocus();

        if ($(row).hasClass("key")) {
            var keyId = row.dataset.id;
            var keyFriendlyName = keyId;

            $.each(oKeys.fnGetData(), function(idx, val) {
                if (val.key_id == keyId) {
                    if (val.key_friendly_name) {
                        keyFriendlyName = val.key_friendly_name;
                    }
                    return false;
                }
            });

            var certCount = $(row).nextUntil(".token, .key", ":visible").length;

            confirm("keys.delete.key.confirm", [keyFriendlyName, certCount],
                function() {
                    var params = {
                        key_id: keyId
                    };

                    $.post(action("delete_key"), params, function(response) {
                        oKeys.fnClearTable(true);
                        oKeys.fnAddData(response.data);
                        enableActions();
                    }, "json");
                });            
        } else {
            var data = oKeys.getFocusData();
            var params = {
                cert_id: data.cert_id
            };

            var url = data.cert_request ?
                action("delete_cert_request") : action("delete_cert");

            confirm("keys.delete.cert.confirm", [data.cert_friendly_name],
                function() {
                    $.post(url, params, function(response) {
                        oKeys.fnClearTable(true);
                        oKeys.fnAddData(response.data);
                        enableActions();
                    }, "json");
                });
        }
    });

    $("#activate").click(function() {
        var params = {
            cert_id: oKeys.getFocusData().cert_id
        };

        $.post(action("activate_cert"), params, function(response) {
            oKeys.fnClearTable(false);
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#disable").click(function() {
        var params = {
            cert_id: oKeys.getFocusData().cert_id
        };

        $.post(action("deactivate_cert"), params, function(response) {
            oKeys.fnClearTable();
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#unregister").click(function() {
        var focusData = oKeys.getFocusData();
        var params = {
            token_id: focusData.token_id,
            key_id: focusData.key_id,
            cert_id: focusData.cert_id
        };

        $.post(action("unregister"), params, function(response) {
            oKeys.fnClearTable();
            oKeys.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#details").click(function() {
        $(oKeys.getFocus()).dblclick();
    });
});
