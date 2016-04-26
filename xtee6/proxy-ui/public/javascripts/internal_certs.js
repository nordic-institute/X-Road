(function(INTERNAL_CERTS, $) {
    var oInternalCerts, oProxyInternalCert;

    function enableInternalCertsActions() {
        if (oInternalCerts.getFocus()) {
            $("#internal_cert_details, #internal_cert_delete").enable();
        } else {
            $("#internal_cert_details, #internal_cert_delete").disable();
        }
    }

    function initInternalCertsTables() {
        var opts = scrollableTableOpts(200);
        opts.bPaginate = false;
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "hash", "sWidth": "100%" }
        ];
        opts.oLanguage = {
            "sZeroRecords": _("common.zero_records_none")
        };

        oInternalCerts = $("#internal_certs").dataTable(opts);

        oProxyInternalCert = $("#proxy_internal_cert").dataTable(opts);
    }

    function initInternalCertsDialogs() {
        $("#cert_details_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 730,
            height: 580,
            open: function() {
                var dialog = this;
                var params = {
                    client_id: CLIENTS.getClientId(),
                    hash: oInternalCerts.getFocusData().hash
                };
                $.get(action("internal_cert_details"), params, function(response) {
                    $("#dump", dialog).text(response.data.dump).scrollTop(0);
                    $("#hash", dialog).text(response.data.hash);
                });
            },
            buttons: [
                {
                    text: _("common.ok"),
                    click: function() {
                        $(this).dialog('close');
                    }
                }
            ]
        });
    }

    function initInternalCertsActions() {
        $("#internal_certs tr").live('click', function() {
            if (oInternalCerts.setFocus(0, this)) {
                enableInternalCertsActions();
            }
        });

        $("#internal_connection_type_edit").live('click', function() {
            var params = {
                client_id: CLIENTS.getClientId(),
                connection_type: $('#connection_type').val()
            };

            $.post(action("internal_connection_type_edit"), params, function(response) {
                if (response.data.connection_type != null) {
                    $('#connection_type').val(response.data.connection_type);
                }
            }, "json");
        });

        if ($("#internal_connection_type_edit").length == 0) {
            $("#connection_type").disable();
        }

        $("#internal_cert_add").click(function() {
            openFileUploadDialog(
                action("internal_cert_add"),
                _("clients.client_internal_certs_tab.import_certificate"),
                { client_id: CLIENTS.getClientId() });
        });

        $("#internal_cert_delete").click(function() {
            var params = {
                client_id: CLIENTS.getClientId(),
                hash: oInternalCerts.getFocusData().hash
            };
            confirm("clients.client_internal_certs_tab.delete_internal_cert_confirm",
                    { hash: params.hash }, function() {
                $.post(action("internal_cert_delete"), params, function(response) {
                    oInternalCerts.fnReplaceData(response.data);
                    enableInternalCertsActions();
                });
            });
        });

        $("#internal_cert_details").live('click', function() {
            $('#cert_details_dialog').dialog('open');
        });

        $("#proxy_internal_cert_export").live('click', function() {
            location.href = action("proxy_internal_cert_export");
        });
    }

    INTERNAL_CERTS.init = function() {
        oInternalCerts.fnClearTable();

        enableInternalCertsActions();

        var params = {
            client_id: CLIENTS.getClientId()
        };

        $.get(action("internal_connection_type"), params, function(response) {
            if (response.data.connection_type != null) {
                $('#connection_type').val(response.data.connection_type);
            }
        }, "json");

        $.get(action("client_internal_certs"), params, function(response) {
            oInternalCerts.fnAddData(response.data);
        }, "json");

        oProxyInternalCert.fnClearTable();

        $.get(action("proxy_internal_cert"), function(response) {
            if (response.data.hash) {
                oProxyInternalCert.fnAddData(response.data);
                $("#proxy_internal_cert_export").enable();
            } else {
                $("#proxy_internal_cert_export").disable();
            }
        }, "json");
    };

    INTERNAL_CERTS.uploadCallback = function(response) {
        if (response.success) {
            oInternalCerts.fnReplaceData(response.data);
            enableInternalCertsActions();
            closeFileUploadDialog();
        }

        showMessages(response.messages);
    };
    
    function initTestability() {
        // add data-name attributes to improve testability
        $("#cert_details_dialog").parent().attr("data-name", "cert_details_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
    }

    $(document).ready(function() {
        initInternalCertsTables();
        initInternalCertsDialogs();
        initInternalCertsActions();

        enableInternalCertsActions();
        initTestability();
    });

})(window.INTERNAL_CERTS = window.INTERNAL_CERTS || {}, jQuery);
