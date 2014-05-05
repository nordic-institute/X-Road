var oSecurityCategories, oClients, oAuthCerts, oServerManagementRequests;

function hideTables() {
    $("#securityserver_security_categories_wrapper").hide();
    $("#securityserver_clients_wrapper").hide();
    $("#securityserver_auth_certs_wrapper").hide();
    $("#securityserver_management_requests_wrapper").hide();

    $("#toggle_securityserver_security_categories").text("+");
    $("#toggle_securityserver_clients").text("+");
    $("#toggle_securityserver_auth_certs").text("+");
    $("#toggle_securityserver_management_requests").text("+");
}

function showTables() {
    $('[id^=toggle_securityserver_]').each(function(i, element) {
        $(element).text('-');
        window[$(element).data('refresh')](getEditableServerId());
    });
};

function getEditableServerId() {
    return {
        serverCode: $("#securityserver_details_server_code").val(),
        ownerClass: $("#securityserver_details_owner_class").val(),
        ownerCode: $("#securityserver_details_owner_code").val(),
    };
}

function getEditableServerData() {
    var editableServerData = getEditableServerId();
    editableServerData.ownerName = $("#securityserver_details_owner_name").val();
    editableServerData.address = $("#securityserver_details_address").val();
    editableServerData.identifier = $("#securityserver_details_identifier").text();

    return editableServerData;
}

function fillSecurityServerDetails(securityServer) {
    $("#securityserver_details_owner_name").val(securityServer.owner_name);
    $("#securityserver_details_owner_class").val(securityServer.owner_class);
    $("#securityserver_details_owner_code").val(securityServer.owner_code);
    $("#securityserver_details_server_code").val(securityServer.server_code);
    $("#securityserver_details_registered").val(securityServer.registered);
    $("#securityserver_details_address").val(securityServer.address);
    $("#securityserver_details_identifier").text(securityServer.identifier);
}

function refreshSecurityServerDataTables(params) {
    $("#securityserver_authcert_delete").disable();

    refreshSecurityServerCategories(params);
    refreshClients(params);
    refreshAuthCerts(params);
    refreshServerManagementRequests(params);
}

function refreshSecurityServerCategories(params, refreshCallback) {
    $.get("securityservers/server_security_categories", params, function(response) {
        oSecurityCategories.fnClearTable();
        oSecurityCategories.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function refreshClients(params, refreshCallback) {
    $.get("securityservers/clients", params, function(response) {
        oClients.fnClearTable();
        oClients.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function refreshAuthCerts(params, refreshCallback) {
    $.get("securityservers/auth_certs", params, function(response) {
        oAuthCerts.fnClearTable();
        oAuthCerts.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function refreshServerManagementRequests(params, refreshCallback) {
    $.get("securityservers/management_requests", params, function(response) {
        oServerManagementRequests.fnClearTable();
        oServerManagementRequests.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function openSecurityServerDetails(securityServerData) {
    openDetailsIfAllowed("securityservers/can_see_details", function() {
        fillSecurityServerDetails(securityServerData);
        $("#securityserver_details_dialog").dialog("open");
        $("#securityserver_authcert_delete").disable();
        showTables();
    });
}

function fillSecurityCategoriesEditTable(securityCategories) {
    var tableBody = $("#security_categories_edit_table > tbody");
    tableBody.empty();

    securityCategories.forEach(function(category) {
        var newRow = $("<tr></tr>");

        var checkboxColumn = $("<td></td>");
        var belongsToServerCheckbox = $('<input type="checkbox"/>');

        if(category.belongs_to_server){
            belongsToServerCheckbox.attr("checked", "checked");
        }

        checkboxColumn.append(belongsToServerCheckbox);
        newRow.append(checkboxColumn);
        newRow.append('<td>' + category.description + '</td>');

        var codeColumn = $('<td>' + category.code + '</td>');
        codeColumn.hide();
        newRow.append(codeColumn);

        tableBody.append(newRow);
    });
}

function getCheckedSecurityCategories() {
    checkedSecurityCategories = [];

    $("#security_categories_edit_table tr").each(function(index){
        var tdCheckbox = $(this).find('td:eq(0) input');
        var checked = tdCheckbox.attr("checked") == "checked";

        if(checked){
            var code = $(this).find('td:eq(2)').text().trim();
            checkedSecurityCategories.push(code);
        }

    });

    return checkedSecurityCategories;
}

function clearAuthCertAddData(){
    $("#auth_cert_add_submit").disable();
    $("#add_auth_cert_temp_id").text("");

    $("#add_auth_cert_csp").val("");
    $("#add_auth_cert_serial_number").val("");
    $("#add_auth_cert_subject").val("");
    $("#add_auth_cert_expires").val("");
}

function fillAuthCertServerData() {
    var serverData = getEditableServerData();
    $("#add_auth_cert_owner_name, #delete_auth_cert_owner_name")
            .val(serverData.ownerName);
    $("#add_auth_cert_owner_class, #delete_auth_cert_owner_class")
            .val(serverData.ownerClass);
    $("#add_auth_cert_owner_code, #delete_auth_cert_owner_code")
            .val(serverData.ownerCode);
    $("#add_auth_cert_servercode, #delete_auth_cert_servercode")
            .val(serverData.serverCode);
}

function fillAddableAuthCertData(authCertData) {
    $("#server_auth_cert_file").val("");
    $("#add_auth_cert_csp").val(authCertData.csp);
    $("#add_auth_cert_serial_number").val(authCertData.serial_number);
    $("#add_auth_cert_subject").val(authCertData.subject);
    $("#add_auth_cert_expires").val(authCertData.expires);
    $("#add_auth_cert_temp_id").text(authCertData.temp_cert_id);
}

function fillDeletableAuthCertData() {
    var authCertData = oAuthCerts.getFocusData();

    $("#delete_auth_cert_csp").val(authCertData.csp);
    $("#delete_auth_cert_serial_number").val(authCertData.serial_number);
    $("#delete_auth_cert_subject").val(authCertData.subject);
    $("#delete_auth_cert_expires").val(authCertData.expires);
}

function openAuthCertDetailsById(authCertId) {
    var params = {certId: authCertId};

    $.post("securityservers/get_cert_details_by_id", params, function(response) {
        certData = response.data;
        $("#cert_details_dump").val(certData.cert_dump);
        $("#cert_details_hash").text(certData.cert_hash);
        openCertDetailsDialog();
    }, "json");
}

function uploadCallbackAuthCert(response) {
    var submitButton = $("#auth_cert_add_submit");
    if (response.success) {
        fillAddableAuthCertData(response.data);

        submitButton.enable();
        $(".auth_cert_details").show();
    } else {
        clearAuthCertAddData();

        submitButton.disable();
        $(".auth_cert_details").hide();
    }

    showMessages(response.messages);
}

function getThisServerTempCertId() {
    return $("#add_auth_cert_temp_id").text();
}

function createSecurityCategoriesTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "description" }
    ];

    oSecurityCategories = 
            $("#securityserver_security_categories").dataTable(opts);
}

function createClientsTable() {
    var opts = defaultOpts(onDraw, 1);
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "name" },
        { "mData": "member_class" },
        { "mData": "member_code" },
        { "mData": "subsystem_code" }
    ];
    opts.fnRowCallback = function(nRow, client) {
        var clientLink = getDetailsLink(client.member_code)

        clientLink.click(function(){
            openMemberDetailsById(client.id);
        });

        var clientColumn = $(nRow).find("td:eq(2)");
        clientColumn.empty().append(clientLink);
    };

    oClients = $('#securityserver_clients').dataTable(opts);
}
function createAuthCertsTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "csp" },
        { "mData": "serial_number" },
        { "mData": "subject" },
        { "mData": "expires" }
    ];
    opts.fnRowCallback = function(nRow, authCert) {
        var authCertLink = getDetailsLink(authCert.serial_number)

        authCertLink.click(function(){
            openAuthCertDetailsById(authCert.id);
        });

        var authCertColumn = $(nRow).find("td:eq(1)");
        authCertColumn.empty().append(authCertLink);
    };

    oAuthCerts = $("#securityserver_auth_certs").dataTable(opts);
}
function createServerManagementRequestsTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "id" },
        { "mData": "type" },
        { "mData": "received" },
        { "mData": "status" }
    ];
    opts.fnRowCallback = function(nRow, managementRequest) {
        var managementRequestColumn = $(nRow).find("td:first");
        var managementRequestLink = getDetailsLink(managementRequest.id);

        managementRequestLink.click(function(){
            openManagementRequestDetails(managementRequest);
        });

        managementRequestColumn.empty().append(managementRequestLink);
    };
    oServerManagementRequests = 
        $("#securityserver_management_requests").dataTable(opts);
}

$(document).ready(function() {

    createSecurityCategoriesTable();
    createClientsTable();
    createAuthCertsTable();
    createServerManagementRequestsTable();


    $("#securityserver_auth_certs tbody td[class!=dataTables_empty]")
    .live("click",function(ev) {
        if (oAuthCerts.setFocus(0, ev.target.parentNode)) {
            $("#securityserver_authcert_delete").enable();
        }
    });

    $("#securityserver_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: "95%",
        buttons: [
          { text: "Close",
              click: function() {
                  $(this).dialog("close");
              }
          }
        ]
    });

    $("#securityserver_address_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 230,
        width: 360,
        buttons: [
            { text: "OK",
              click: function() {
                var dialog = this;
                var newAddress = $("#securityserver_edit_address_new").val();
                $("#securityserver_details_address").val(newAddress);
                serverData = getEditableServerData();

                $.post("securityservers/address_edit", serverData, function() {
                    try {
                        updateSecurityServersTable();
                    } catch (e){
                        // Do nothing as updateSecurityServersTable may not be
                        // present everywhere.
                    }
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

    $("#securityserver_details_change_address").live("click", function() {
        var currentAddress = $("#securityserver_details_address").val();
        $("#securityserver_edit_address_new").val(currentAddress);

        $("#securityserver_address_edit_dialog").dialog("open");

        // XXX: is there more elegant way to prevent submitting the form?
        return false;
    });

    $("#security_categories_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 400,
        width: 400,
        buttons: [
            { text: "OK",
              click: function() {
                var dialog = this;
                var serverData = getEditableServerId();
                var checkedCategories = getCheckedSecurityCategories();

                var params = {
                        serverData: serverData,
                        categories: checkedCategories
                };

                $.post("securityservers/edit_security_categories", params,
                        function(response) {
                    oSecurityCategories.fnClearTable();
                    oSecurityCategories.fnAddData(response.data);
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
    $("#security_categories_edit").live("click", function() {
        var params = getEditableServerData();
        $.post("securityservers/all_security_categories", params,
                function(response) {
            fillSecurityCategoriesEditTable(response.data);
        }, "json");

        var identifier = params.identifier;
        $("#security_categories_edit_legend").text(identifier);
        $("#security_categories_edit_dialog").dialog("open");
    });

    $("#auth_cert_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 500,
        width: 500,
        buttons: [
            { text: _("submit"),
                id: "auth_cert_add_submit",
                disabled: "disabled",
                click: function() {
                    var dialog = this;
                    var params = getEditableServerId();
                    params["tempCertId"] = getThisServerTempCertId();

                    $.post("securityservers/auth_cert_adding_request", params,
                            function(response) {
                        // TODO: display appropriate success message
                        clearAuthCertAddData();
                        refreshSecurityServerDataTables(params);
                        $(dialog).dialog("close");
                    }, "script");
                }
            },
            { text: _("cancel"),
                click: function() {
                    var dialog = this;
                    var params = {tempCertId: getThisServerTempCertId()};

                    $.post("securityservers/cancel_new_auth_cert_request", params,
                            function(response) {
                        $(dialog).dialog("close");
                    }, "script");
                }
            }
        ]
    });

    $("#securityserver_authcert_add").live("click", function() {
        clearAuthCertAddData();

        $(".auth_cert_details").hide();
        $("#auth_cert_add_dialog").dialog("open");

        fillAuthCertServerData();
    });

    $("#add_auth_cert_upload").live("click", function() {
        $("#server_auth_cert_upload").submit();
        // function uploadCallback manages post-submission activities on UI part
    });

    $("#auth_cert_delete_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 500,
        width: 500,
        buttons: [
            { text: _("submit"),
                click: function() {
                    var dialog = this;
                    var params = getEditableServerId();
                    var certId = oAuthCerts.getFocusData().id;
                    params["certId"] = certId;

                    $.post("securityservers/auth_cert_deletion_request", params,
                            function(response) {
                        // TODO: display appropriate success message
                        refreshSecurityServerDataTables(params);
                        $(dialog).dialog("close");
                    }, "script");
                }
            },
            { text: _("cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }
        ]
    });

    $("#securityserver_authcert_delete").live("click", function() {
        $("#auth_cert_delete_dialog").dialog("open");

        fillAuthCertServerData();
        fillDeletableAuthCertData();
    });

    $("#toggle_securityserver_security_categories").live("click", function() {
        var toggleButton = $(this);
        var tableId = "securityserver_security_categories";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshSecurityServerCategories(getEditableServerId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_securityserver_clients").live("click", function() {
        var toggleButton = $(this);
        var tableId = "securityserver_clients";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshClients(getEditableServerId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_securityserver_auth_certs").live("click", function() {
        console.log("Toggling auth certs clicked: ");
        var toggleButton = $(this);
        var tableId = "securityserver_auth_certs";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshAuthCerts(getEditableServerId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_securityserver_management_requests").live("click", function() {
        var toggleButton = $(this);
        var tableId = "securityserver_management_requests";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshServerManagementRequests(getEditableServerId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });
});