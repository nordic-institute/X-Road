var oKeys;

var SOFTTOKEN_ID = "0";
var KEY_USAGE_AUTH = "auth";
var KEY_USAGE_SIGN = "sign";

function enableActions() {
    $("#unregister").hide();
    $("#register").disable();
    $("#details, #generate_key, #generate_csr, #delete, #activate, #disable").disable();
    $("#import_cert").enable();

    if ($(".token.row_selected").length > 0) {
        $("#details").enable();
    }

    if ($(".token.row_selected.token-active").length > 0) {
        $("#generate_key").enable();
    }

    if ($(".key.row_selected").length > 0) {
        $("#details").enable();

        var selectedKey = $(".key.row_selected");

        if (!selectedKey.is(".not-supported")
            && getKeyData(oKeys.getFocus()).key_deletable
            && (!selectedKey.hasClass("unsaved")
                || selectedKey.hasClass("token-active"))) {

            $("#delete").enable();
        }
    }

    if ($(".key.row_selected:not(.key-unavailable, .token-inactive,"
          + " .not-supported)").length > 0) {
        var keyUsage = getKeyData(oKeys.getFocus()).key_usage;

        // only enable if keyUsage is not determined yet or user can
        // generate csr-s of this keyUsage type
        if (!keyUsage || $("#key_usage_select option[value="
                          + keyUsage + "]").length > 0) {
            $("#generate_csr").enable();
        }
    }

    if ($(".cert-active.row_selected:not(.unsaved)").length > 0) {
        $("#activate").hide();
        $("#disable").show();
    }

    if ($(".cert-inactive.row_selected:not(.unsaved)").length > 0) {
        $("#disable").hide();
        $("#activate").show();
    }

    if ($(".cert-request.row_selected").length > 0) {
        $("#activate, #disable").disable();
    }

    $(".token-available .activate_token").enable();
    $(".token-unavailable .activate_token").disable();

    if (oKeys.getFocus() && oKeys.getFocusData()) {
        var cert = oKeys.getFocusData();

        if (!cert.cert_request) {
            $("#details").enable();
        }

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

            if (cert.cert_deletable &&
                (cert.cert_saved_to_conf || cert.token_active)) {
                $("#delete").enable();
            }
        }

        if (cert.cert_activatable) {
            $("#activate:visible, #disable:visible").enable();
        }
    }
}

function refreshTokens() {
    $.get(action("refresh"), null, function(response) {
        oKeys.fnReplaceData(response.data);
        enableActions();
    }, "json");
}

function initDialogs() {
    $(document).on('click', ".activate_token", function() {
        var tokenId = $(this).closest(".token").attr("data-id");

        activateToken(tokenId, function() {
            refreshTokens();
            if (tokenId == SOFTTOKEN_ID) {
                PERIODIC_JOBS.refreshAlerts();
            }
        }, function() {
            $.ajax({
                url: action("refresh"),
                global: false,
                success: function(response) {
                    oKeys.fnReplaceData(response.data);
                    enableActions();
                },
                dataType: "json"
            });
        });
    });

    $("#generate_csr_dialog").initDialog({
        autoOpen: false,
        modal: true,
        width: 550,
        close: function() {
            $("input[type!=hidden], select", this).val("");
            $("#key_usage_select").enable();
        },
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  $("#key_usage", this).val(
                      $("#key_usage_select", this).val());

                  var dialog = this;
                  var params = $("form", this).serializeObject();

                  $.post(action("generate_csr"), params, function(response) {
                      oKeys.fnReplaceData(response.data.tokens);
                      enableActions();

                      location.href = action("download_csr")
                          + "?csr=" + response.data.redirect
                          + "&key_usage=" + params.key_usage;

                      $(dialog).dialog("close");
                      $("input[type!=hidden], select", dialog).val("");
                      $("#key_usage_select").enable();
                  });
              }
            },
            { text: _("common.cancel"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#generate_csr").click(function() {
        var keyRow = oKeys.getFocus();
        var keyData = getKeyData(oKeys.getFocus());
        var keyUsage = keyData.key_usage;

        // only softToken with id 0 is allowed to have auth keys
        if (!keyUsage && keyData.token_id != SOFTTOKEN_ID) {
            keyUsage = KEY_USAGE_SIGN;
        }

        if (keyUsage) {
            $("#generate_csr_dialog form #key_usage_select")
                .val(keyUsage).disable();
        }

        if (keyUsage == KEY_USAGE_AUTH) {
            $("#generate_csr_dialog form #member_id")
                .disable().hide().closest("div").hide();
        } else {
            $("#generate_csr_dialog form #member_id")
                .enable().show().closest("div").show();
        }

        $("#generate_csr_dialog form #token_id").val(keyRow.dataset.parentId);
        $("#generate_csr_dialog form #key_id").val(keyRow.dataset.id);
        $("#generate_csr_dialog").dialog("open");
    });

    $("#import_cert").click(function() {
        openFileUploadDialog(
            action("import_cert"), _("keys.index.import_certificate"));
    });

    $("#token_details_dialog, #key_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 400,
        width: 700,
        open: function() {
            var textarea = $("textarea", this);
            if (textarea.length > 0) {
                textarea.css("height", "1px");
                textarea.css("height", (25 + textarea[0].scrollHeight) + "px");
            }
        },
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  if ($("input[name=friendly_name]", this).is(":disabled")) {
                      $(this).dialog("close");
                      return;
                  }

                  var dialog = this;
                  var params = $("form", this).serialize();

                  $.post(action("friendly_name"), params, function(response) {
                      oKeys.fnReplaceData(response.data);

                      enableActions();

                      $(dialog).dialog("close");
                      $("input[type!=hidden]", dialog).val("");
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

    $("#cert_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 600,
        width: 700,
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  $(this).dialog("close");
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

        $.get(action("cert_details"), params, function(response) {
            $("#cert_details_dialog #dump").text(response.data.dump);
            $("#cert_details_dialog #hash").text(response.data.hash);
            $("#cert_details_dialog").dialog("open");
            $("#dump").scrollTop(0);
        }, "json");
    });

    $("#register_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 200,
        width: 500,
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  var dialog = this;
                  var params = $("form", this).serializeObject();
                  var focusData = oKeys.getFocusData();

                  params.token_id = focusData.token_id;
                  params.key_id = focusData.key_id;
                  params.cert_id = focusData.cert_id;

                  $.post(action("register"), params, function(response) {
                      oKeys.fnReplaceData(response.data);
                      enableActions();

                      $(dialog).dialog("close");
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

    $("#register").click(function() {
        $("#register_dialog #address").val("");
        $("#register_dialog").dialog("open");
    });
}

function uploadCallback(response) {
    if (response.success) {
        closeFileUploadDialog();
        refreshTokens();
    }

    showMessages(response.messages);
}

// Key rows are added by ui and do not have their own data, so we find
// a row that has data for this key.
function getKeyData(keyRow) {
    var keyId = keyRow.dataset.id;
    var keyData = null;

    $.each(oKeys.fnGetData(), function(idx, val) {
        if (val.key_id == keyId) {
            keyData = val;
            return false;
        }
    });

    return keyData;
}

function deleteKey(row) {
    var keyId = row.dataset.id;
    var keyData = null;
    var unregister_certs = false;

    $.each(oKeys.fnGetData(), function(idx, val) {
        if (val.key_id == keyId) {
            if (!keyData) {
                keyData = val;
            }

            if (val.unregister_enabled) {
                unregister_certs = true;
                return false;
            }
        }
    });

    var keyFriendlyName = keyData.key_id;
    var tokenFriendlyName = keyData.token_id;

    if (keyData.key_friendly_name) {
        keyFriendlyName = keyData.key_friendly_name;
    }

    if (keyData.token_friendly_name) {
        tokenFriendlyName = keyData.token_friendly_name;
    }

    var params = {
        token_id: keyData.token_id,
        key_id: keyData.key_id
    };

    var confirmText = $(row).hasClass("unsaved")
        ? "keys.index.delete_key_from_token_confirm"
        : "keys.index.delete_key_confirm";

    var confirmTextParams = {
        token: tokenFriendlyName,
        key: keyFriendlyName
    };

    if (unregister_certs) {
        confirmText = "keys.index.delete_key_and_unregister_certs_confirm";
    }

    confirm(confirmText, confirmTextParams, function() {
        $.post(action("delete_key"), params, function(response) {
            oKeys.fnReplaceData(response.data);

            enableActions();
        }, "json");
    });
}

function deleteCert(row) {
    var focusData = oKeys.getFocusData();
    var params = {
        token_id: focusData.token_id,
        key_id: focusData.key_id,
        cert_id: focusData.cert_id
    };

    var url = focusData.cert_request ?
        action("delete_cert_request") : action("delete_cert");

    confirm("keys.index.delete_cert_confirm",
            { hash: focusData.cert_friendly_name },
            function() {
                $.post(url, params, function(response) {
                    oKeys.fnReplaceData(response.data);

                    enableActions();
                }, "json");
            });
}

$(document).ready(function() {
    $("#addkey_form, #loadcert_form").hide();

    var opts = scrollableTableOpts(400);
    opts.asRowId = ["token_id", "key_id", "cert_id"];
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.oLanguage.sZeroRecords = _("keys.index.no_keys");
    opts.asStripeClasses = [];
    opts.aoColumns = [
        { "mData": "token_id",
          "bVisible": false },
        { "mData": "token_friendly_name",
          "bVisible": false, mRender: util.escape },
        { "mData": "token_available",
          "bVisible": false },
        { "mData": "token_active",
          "bVisible": false },
        { "mData": "key_id",
          "bVisible": false },
        { "mData": "key_friendly_name",
          "bVisible": false, mRender: util.escape },
        { "mData": "key_usage",
          "bVisible": false },
        { "mData": "cert_id",
          "bVisible": false },
        { "mData": "cert_friendly_name",
          "sClass": "cert-friendly-name", mRender: util.escape },
        { "mData": "cert_member_code", mRender: util.escape },
        { "mData": "cert_ocsp_response",
          "sClass": "cert-ocsp-response align-center",
          "sWidth": "6.5em" },
        { "mData": "cert_expires",
          "sClass": "cert-expires align-center",
          "sWidth": "7em" },
        { "mData": "cert_status",
          "sClass": "align-center",
          "sWidth": "8em" },
        { "mData": "cert_saved_to_conf",
          "bVisible": false },
        { "mData": "cert_request",
          "bVisible": false },
        { "mData": "buttons",
          "sClass": "buttons",
          "sWidth": "7em",
          "mRender": function(data, type, full) {
              if (!full.cert_saved_to_conf && full.cert_importable) {
                  return "<button class='import right'>" + _("common.import") + "</button>";
              } else {
                  return "";
              }
          }}
    ];
    opts.fnRowCallback = function(nRow, oData) {
        if (oData.cert_id) {
            if (!oData.cert_saved_to_conf) {
                $(nRow).addClass("unsaved");
            }

            if (!oData.key_available) {
                $(nRow).addClass("key-unavailable");
            }

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
    };
    opts.fnDrawCallback = function(oSettings) {
        // hide rows used to create empty groups
        this.$("tr.empty", {"filter": "applied"}).hide();
        this.fnAdjustColumnSizing(false);
    };

    oKeys = $("#keys").dataTable(opts);

    oKeys.rowGrouping({
        "asGroupingColumnName": [ "token_id", "key_id" ],
        "aiGroupingColumnIndex": [ 0, 4 ],
        "afnGroupLabelFormat": [
            function(oData, sLabel) {
                var friendlyName = oData.token_friendly_name
                    ? oData.token_friendly_name : oData.token_id;

                var locked = " <span class='locked'>" + _("keys.index.locked") + "</span>";

                var buttons = "<div class='right'>"
                    + "<button class='activate_token'>" + _("common.enter_pin") + "</button>"
                    + "<button class='deactivate_token'>" + _("keys.index.logout") + "</button>"
                    + "</div>";

                return "<div class='left token-name'>" + _("keys.index.token") + util.escape(friendlyName) +
                    (oData.token_locked ? locked : "") + "</div>" +
                    (oData.token_activatable ? buttons : "");
            },
            function(oData, sLabel) {
                var friendlyName = oData.key_friendly_name
                    ? oData.key_friendly_name : oData.key_id;
                var keyUsage = oData.key_usage
                    ? oData.key_usage : "?";
                var keyNotSupported =
                    (oData.token_id != SOFTTOKEN_ID
                     && oData.key_usage == KEY_USAGE_AUTH) ? ", not supported" : "";

                return _("keys.index.key") + util.escape(friendlyName)
                    + " (<span class='key-usage'>" + keyUsage + keyNotSupported + "</span>)";
            }
        ],
        "afnGroupClass": [
            function (oData) {
                var classes = "token";

                classes += oData.token_available ?
                    " token-available" : " token-unavailable";
                classes += oData.token_active ?
                    " token-active" : " token-inactive";

                if (!oData.token_saved_to_conf) {
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

                if (!oData.key_available) {
                    classes += " key-unavailable";
                }

                if (oData.token_id != SOFTTOKEN_ID
                    && oData.key_usage == KEY_USAGE_AUTH) {
                    classes += " not-supported";
                }

                if (!oData.key_saved_to_conf) {
                    classes += " unsaved";
                }

                return classes;
            }
        ]
    });

    initDialogs();
    refreshTokens();
    focusInput();

    $("#keys tbody tr").live("click", function() {
        oKeys.setFocus(0, this);
        enableActions();
    });

    $(document).on('click', ".deactivate_token", function() {
        var tokenId = $(this).closest(".token").attr("data-id");

        deactivateToken(tokenId, function() {
            refreshTokens();
            PERIODIC_JOBS.refreshAlerts();
        });
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
        if ($(this).val() == KEY_USAGE_AUTH) {
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
            oKeys.fnReplaceData(response.data);
            enableActions();
        }, "json");
    });

    $("#delete").click(function() {
        var row = oKeys.getFocus();

        if ($(row).hasClass("key")) {
            deleteKey(row);
        } else {
            deleteCert(row);
        }
    });

    $("#activate").click(function() {
        var focusData = oKeys.getFocusData();
        var params = {
            token_id: focusData.token_id,
            key_id: focusData.key_id,
            cert_id: focusData.cert_id
        };

        $.post(action("activate_cert"), params, function(response) {
            oKeys.fnReplaceData(response.data);
            enableActions();
        }, "json");
    });

    $("#disable").click(function() {
        var focusData = oKeys.getFocusData();
        var params = {
            token_id: focusData.token_id,
            key_id: focusData.key_id,
            cert_id: focusData.cert_id
        };

        $.post(action("deactivate_cert"), params, function(response) {
            oKeys.fnReplaceData(response.data);
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

        confirm("keys.index.unregister_cert_confirm",
                { hash: focusData.cert_friendly_name }, function() {
            $.post(action("unregister"), params, function(response) {
                oKeys.fnReplaceData(response.data);
                enableActions();
            }, "json");
        });
    });

    $("#details").click(function() {
        $(oKeys.getFocus()).dblclick();
    });
});
