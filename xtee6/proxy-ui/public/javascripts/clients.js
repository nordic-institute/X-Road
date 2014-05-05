var oClients, oClientsGlobal, oClientsFile, oCertificates, oInternalCerts;

function enableClientsActions() {
    if (oClients.getFocus()) {
        $("#client_details").enable();
    } else {
        $("#client_details").disable();
    }

    if (oClientsGlobal.getFocus()) {
        $("#client_select_ok").enable();
    } else {
        $("#client_select_ok").disable();
    }

    if (oClients.getFocus()) {
        var client = oClients.getFocusData();

        if (client.register_enabled) {
            $("#client_register").enable();
        } else {
            $("#client_register").disable();
        }
        if (client.unregister_enabled) {
            $("#client_unregister").show();
        } else {
            $("#client_unregister").hide();
        }
        if (client.delete_enabled) {
            $("#client_delete").show();
        } else {
            $("#client_delete").hide();
        }
    }

    if(oInternalCerts.getFocus()) {
        $('#internal_cert_details').enable();
        $('#internal_cert_delete').enable();
    } else {
        $('#internal_cert_details').disable();
        $('#internal_cert_delete').disable();
    }
}

function reqParams(nRow) {
    return {
        identifier: oClients.fnGetData(nRow)[1]
    };
}

function onClientsDraw() {
    if (oClients) {
        enableClientsActions();
    }
}

function certImportCallback(response) {
    $('#import_cert_dialog').dialog('close');
    refreshInternalCerts(response);
};

function refreshInternalCerts(response) {
    if(typeof response == 'undefined')
    {
        var params = {
            client_id: oClients.getFocusData().client_id
        };
        $.post(action('get_internal_certs'), params, function(response) {
            oInternalCerts.fnClearTable();
            oInternalCerts.fnAddData(response.data);
        });
    }
    else
    {
        oInternalCerts.fnClearTable();
        oInternalCerts.fnAddData(response.data);
    }
};

function initClientsDialogs() {
    $("#client_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 400,
        width: 350,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = $("form", this).serializeObject();

                  $.post(action("client_add"), params, function(response) {
                      oClients.fnClearTable();
                      oClients.fnAddData(response.data);

                      $(dialog).dialog("close");

                      var regParams = {
                          member_class: params.add_member_class,
                          member_code: params.add_member_code,
                          subsystem_code: params.add_subsystem_code
                      };

                      confirm("clients.add.regreq.confirm", null, function() {
                          $.post(action("client_regreq"), regParams, function() {
                              refreshClients();
                          });
                      });
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

    $("#client_add").click(function() {
        $("#client_add_dialog form input[type!=hidden]").val("");
        $("#client_add_dialog form select").val("");
        $("#client_add_dialog").dialog("open");
    });

    var namefetch = function() {
        var timer = 0;
        return function(callback, ms) {
            clearTimeout(timer);
            timer = setTimeout(callback, ms);
        };
    }();

    $("#add_member_class").change(function() {
        $("#add_member_code").keyup();
    });

    $("#add_member_code").keyup(function() {
        namefetch(function () {
            var params = $("#add_member_class, #add_member_code").serialize();
            $.get(action("client_name"), params, function(response) {
                $("#add_member_name").val(response.data.name);
            });
        }, 500);        
    });

    $("#client_select_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 630,
        width: 666,
        buttons: [
            { text: "OK",
              id: "client_select_ok",
              click: function() {
                  var client = oClientsGlobal.getFocusData();
                  $("#add_member_name").val(client.member_name);
                  $("#add_member_class").val(client.member_class);
                  $("#add_member_code").val(client.member_code);
                  $("#add_subsystem_code").val(client.subsystem_code);
                  $(this).dialog("close");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#client_select").click(function() {
        oClientsGlobal.fnClearTable();
        enableClientsActions();
        $("#client_select_dialog").dialog("open");
    });

    $("#clients_load_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 500,
        width: 666,
        buttons: [
            { text: "OK",
              click: function() {
                  $(this).dialog("close");
                  $("#client_add_dialog").dialog("close");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#clients_load").click(function() {
        oClientsFile.fnClearTable();
        enableClientsActions();
        $("#clients_load_dialog").dialog("open");
    });

    $("#client_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: 700,
        buttons: [
            { text: "Close",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $('#internal_servers_dialog').initDialog({
        autoOpen: false,
        width: 640,
        height: 500,
        buttons: [
            {
                text: 'OK',
                click: function() {
                    $(this).dialog('close');
                }
            }
        ],
        open: function() {
            getConnectionType();
            refreshInternalCerts();
            getSecurityServerCert();
        }
    });

    $('#import_cert_dialog').initDialog({
        autoOpen: false,
        modal: true,
        open: function()
        {
            $('#client_id').val(oClients.getFocusData().client_id);
        },
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

    $('#cert_details').initDialog({
        autoOpen: false,
        modal: true,
        width: 515,
        height: 600,
        open: function() {
            var params = {
                client_id: oClients.getFocusData().client_id,
                fingerprint: oInternalCerts.getFocusData().fingerprint
            };
            $.post(action('get_internal_cert'), params, function(response) {
                $('#cert_dump').text(response.data.cert_dump);
                $('#cert_hash').text(response.data.cert_hash);
            });
        },
        buttons: [
            {
                text: _('ok'),
                click: function() {
                    $(this).dialog('close');
                }
            }
        ]
    });

    $("#client_details").click(function() {
        openClientDetails(oClients.getFocusData());
    });

    $('#internal_servers').click(function() {
        openInternalServers();
    });
}

function refreshClients() {
    $.get(action("clients_refresh"), null, function(response) {
        oClients.fnClearTable();
        oClients.fnAddData(response.data);
    }, "json");
}

function openClientDetails(client) {
    $("#details_client_id").val(client.client_id);
    $("#details_member_class").val(client.member_class);
    $("#details_member_code").val(client.member_code);
    $("#details_subsystem_code").val(client.subsystem_code);
    $("#details_member_name").val(client.member_name);
    $("#details_contact").val(client.contact);

    var params = {
        client_id: client.client_id
    };
    $.get(action("client_certificates"), params, function(response) {
        oCertificates.fnClearTable();
        oCertificates.fnAddData(response.data);
        $("#client_details_dialog").dialog("open");
    });
}

function openInternalServers() {
    $('#internal_servers_dialog').dialog('open');
};

function getConnectionType() {
    var params = {
        client_id: oClients.getFocusData().client_id
    };
    $.post(action('get_connection_type'), params, function(response) {
        if(response.data.connection_type != null)
            $('#connection_type').val(response.data.connection_type);
    });
};

function getSecurityServerCert() {
    $.get(action('get_ssl_cert'), function(response) {
        $('#security_server_cert').text(response.data.match(/.{1,2}/g).join(":"))
    });
};

function confirmDelete(text) {
    var params = {
        client_id: $("#details_client_id").val(),
    };
    confirm(text, null, function() {
        $.post(action("client_delete"), params, function(response) {
            oClients.fnClearTable();
            oClients.fnAddData(response.data);
            enableClientsActions();

            confirm("clients.delete.certs.confirm", null, function() {
                $.post(action("client_delete_certs"), params);
            });

            $("#client_details_dialog").dialog("close");
        }, "json");
    });
}

$(document).ready(function() {
    var opts = defaultOpts(onClientsDraw, 1);
    opts.sScrollY = "400px";
    opts.bScrollCollapse = true;
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "member_name" },
        { "mData": "member_class" },
        { "mData": "member_code" },
        { "mData": "subsystem_code" },
        { "mData": "state" }
    ];
    opts.fnRowCallback = function(nRow, oData) {
        if (oData.owner) {
            $(nRow).find('td:first').addClass('bold');
        }
    };

    oClients = $("#clients").dataTable(opts);

    $(".clients_actions").prependTo("#clients_wrapper .dataTables_header");

    opts = scrollableTableOpts(null, 1, 200);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "member_name" },
        { "mData": "member_class" },
        { "mData": "member_code" },
        { "mData": "subsystem_code" }
    ];

    oClientsGlobal = $("#clients_global").dataTable(opts);

    opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "member_name" },
        { "mData": "member_class" },
        { "mData": "member_code" },
        { "mData": "subsystem_code" },
        { "mData": "checked" }
    ];

    oClientsFile = $("#clients_file").dataTable(opts);

    opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "csp" },
        { "mData": "serial" },
        { "mData": "state" },
        { "mData": "expires" }
    ];

    oCertificates = $("#certificates").dataTable(opts);

    opts = defaultOpts(null, 1);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "fingerprint", "sWidth": '100%' }
    ];

    opts.fnRowCallback = function(nRow, oData) {
        $(nRow).find('td:first').text(oData.fingerprint.match(/.{1,2}/g).join(":"));
    };

    oInternalCerts = $('#internal_ssl_certs').dataTable(opts);

    initClientsDialogs();
    refreshClients();
    enableClientsActions();

    $("#clients tbody tr").live("click", function() {
        oClients.setFocus(0, this);
        enableClientsActions();
    });

    $("#clients tbody tr").live("dblclick", function() {
        openClientDetails(oClients.fnGetData(this));
    });

    $("#clients_global tbody tr").live("click", function() {
        oClientsGlobal.setFocus(0, this);
        enableClientsActions();
    });

    $('#internal_ssl_certs tr').live('click', function() {
        oInternalCerts.setFocus(0, this);
        enableClientsActions();
    });

    $("#client_select_search").click(function() {
        var params = $("#search_member_name, #search_member_code, " +
                       "#search_member_class, #search_show_subsystems").serialize();
        $.get(action("clients_search"), params, function(response) {
            oClientsGlobal.fnClearTable();
            oClientsGlobal.fnAddData(response.data);
        }, "json");
    });

    $("#client_select_clear").click(function() {
        $("#search_member_code, #search_member_class, #search_member_name").val("");
        $("#search_show_subsystems").removeAttr("checked");
    });

    $("#client_register").click(function() {
        var params = {
            member_class: $("#details_member_class").val(),
            member_code: $("#details_member_code").val(),
            subsystem_code: $("#details_subsystem_code").val()
        };
        confirm("clients.regreq.confirm", null, function() {
            $.post(action("client_regreq"), params, function(response) {
                oClients.fnUpdate(response.data, oClients.getFocus());
                enableClientsActions();
            }, "json");
        });
    });

    $("#client_unregister").click(function() {
        var params = {
            member_class: $("#details_member_class").val(),
            member_code: $("#details_member_code").val(),
            subsystem_code: $("#details_subsystem_code").val()
        };
        confirm("clients.delreq.confirm", null, function() {
            $.post(action("client_delreq"), params, function(response) {
                oClients.fnUpdate(response.data, oClients.getFocus());
                enableClientsActions();

                confirmDelete("clients.delreq.delete.confirm");
            }, "json");
        });
    });

    $("#client_delete").click(function() {
        confirmDelete("clients.delete.confirm");
    });

    $('#edit_connection_type').live('click', function() {
        var params = {
            client_id: oClients.getFocusData().client_id,
            connection_type: $('#connection_type').val()
        };
        $.post(action('edit_connection_type'), params, function(response) {
            if(response.data.connection_type != null)
                $('#connection_type').val(response.data.connection_type);
        });
    });

    $('#internal_cert_add').live('click', function() {
        $('#import_cert_dialog').dialog('open');
    });

    $('#internal_cert_delete').live('click', function() {
        var params = {
            client_id: oClients.getFocusData().client_id,
            fingerprint: oInternalCerts.getFocusData().fingerprint
        };
        confirm('Delete Internal SSL Certificate ' + params.fingerprint + '?', null, function() {
            $.post(action('delete_internal_cert'), params, function(response) {
                refreshInternalCerts(response);
            });
        });
    });

    $('#internal_cert_details').live('click', function() {
        $('#cert_details').dialog('open');
    });
});
