var XROAD_SECURITYSERVER_EDIT = function() {
    var oSecurityCategories, oClients, oAuthCerts, oManagementRequests,
            oAddableClients;

    var openedFromList = false;

    var openingAuthCertDetails = false;
    var openingClientDetails = false;

    /* -- PUBLIC - START -- */

    function open(securityServerData, fromList) {
        XROAD_CENTERUI_COMMON.openDetailsIfAllowed(
                "securityservers/can_see_details", function() {
            openedFromList = fromList == true ? true : false;

            fillServerDetails(securityServerData);

            disableAuthCertDeletion();
            disableClientDeletion();

            $("#securityserver_edit_dialog").dialog("open");
            $("#securityserver_edit_tabs").tabs("option", "active", 0);
        });
    }

    function refreshManagementRequests() {
        if (typeof(oManagementRequests) == "undefined") {
            return;
        }

        oManagementRequests.fnReloadAjax();
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

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function initView() {
        initDialogs();
        initInputHandlers();
    }

    function fillServerDetails(securityServer) {
        $("#securityserver_edit_owner_name").text(securityServer.owner_name);
        $("#securityserver_edit_owner_class").text(securityServer.owner_class);
        $("#securityserver_edit_owner_code").text(securityServer.owner_code);
        $("#securityserver_edit_server_code").text(securityServer.server_code);
        $("#securityserver_edit_registered").text(securityServer.registered);
        $("#securityserver_edit_address").text(securityServer.address);
        $("#securityserver_edit_identifier").text(securityServer.identifier);
    }

    function fillClientRegRequestServerDetails() {
        var serverData = getEditableServerData();

        var ownerNameInput = $("#securityserver_client_owner_name");
        var ownerClassInput = $("#securityserver_client_owner_class");
        var ownerCodeInput = $("#securityserver_client_owner_code");
        var serverCodeInput = $("#securityserver_client_server_code");

        ownerNameInput.disable();
        ownerClassInput.disable();
        ownerCodeInput.disable();
        serverCodeInput.disable();

        ownerNameInput.text(serverData.ownerName);
        ownerClassInput.text(serverData.ownerClass);
        ownerCodeInput.text(serverData.ownerCode);
        serverCodeInput.text(serverData.serverCode);
    }

    var tabRefreshers = {
        "#server_details_tab": function() {},
        "#server_clients_tab": refreshClients,
        "#server_auth_certs_tab": refreshAuthCerts,
        "#server_management_requests_tab": refreshManagementRequests
    };

    function refreshTab(tab, params) {
        tabRefreshers[tab].call(this, params);
    }

    function refreshServerDataTables(params) {
        refreshClients(params);
        refreshAuthCerts(params);
        refreshManagementRequests(params);
    }

    // TODO (RM #2770): probably usable when security categories implemented
    function refreshSecurityCategories(params, refreshCallback) {
        $.get("securityservers/server_security_categories", params,
                function(response) {
            oSecurityCategories.fnClearTable();
            oSecurityCategories.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshClients(params, refreshCallback) {
        disableClientDeletion();

        $.get("securityservers/clients", params, function(response) {
            oClients.fnClearTable();
            oClients.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshAuthCerts(params, refreshCallback) {
        disableAuthCertDeletion();

        $.get("securityservers/auth_certs", params, function(response) {
            oAuthCerts.fnClearTable();
            oAuthCerts.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
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

    function fillAuthCertServerData() {
        var serverData = getEditableServerData();
        $("#add_auth_cert_owner_name, #delete_auth_cert_owner_name")
                .text(serverData.ownerName);
        $("#add_auth_cert_owner_class, #delete_auth_cert_owner_class")
                .text(serverData.ownerClass);
        $("#add_auth_cert_owner_code, #delete_auth_cert_owner_code")
                .text(serverData.ownerCode);
        $("#add_auth_cert_servercode, #delete_auth_cert_servercode")
                .text(serverData.serverCode);
    }

    function fillAddableAuthCertData(authCertData) {
        $("#securityserver_auth_cert_upload").val("");
        $("#add_auth_cert_ca").text(authCertData.csp);
        $("#add_auth_cert_serial_number").text(authCertData.serial_number);
        $("#add_auth_cert_subject").text(authCertData.subject);
        $("#add_auth_cert_expires").text(authCertData.expires);
        $("#add_auth_cert_temp_id").text(authCertData.temp_cert_id);
    }

    function fillDeletableAuthCertData() {
        var authCertData = oAuthCerts.getFocusData();

        $("#delete_auth_cert_ca").text(authCertData.csp);
        $("#delete_auth_cert_serial_number").text(authCertData.serial_number);
        $("#delete_auth_cert_subject").text(authCertData.subject);
        $("#delete_auth_cert_expires").text(authCertData.expires);
    }

    function selectNewServerClient(newClient) {
        fillNewClientFields(newClient);
        updateNewClientSubmitButton();
        focusClientSubsystemCodeInput();
    }

    function fillNewClientFields(newClient) {
        $("#securityserver_client_name").text(newClient.name);
        $("#securityserver_client_class").text(newClient.member_class);
        $("#securityserver_client_code").text(newClient.member_code);
        $("#securityserver_client_subsystem_code").val(
            newClient.subsystem_code);
    }

    function initNewServerClientSubsystemAutocomplete(newClient) {
        var subsystemCodeInput = $("#securityserver_client_subsystem_code");
        var params = {
            memberClass: newClient.member_class,
            memberCode: newClient.member_code
        };

        $.get("members/subsystem_codes", params, function(response) {
            destroySubsystemCodeInputAutocompleteQuietly();
            subsystemCodeInput.autocomplete({source: response.data});
        }, "json");
    }

    function destroySubsystemCodeInputAutocompleteQuietly() {
        try {
            $("#securityserver_client_subsystem_code").autocomplete("destroy");
        } catch (err) {
            // Do nothing, as destroyable autocomplete may not be present.
        }
    }

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

    function getEditableServerId() {
        return {
            serverCode: $("#securityserver_edit_server_code").text(),
            ownerClass: $("#securityserver_edit_owner_class").text(),
            ownerCode: $("#securityserver_edit_owner_code").text(),
        };
    }

    function getServerClientRegRequestParams() {
        return {
            memberClass: $("#securityserver_client_class").text(),
            memberCode: $("#securityserver_client_code").text(),
            subsystemCode: $("#securityserver_client_subsystem_code").val(),
            ownerClass: $("#securityserver_client_owner_class").text(),
            ownerCode: $("#securityserver_client_owner_code").text(),
            serverCode: $("#securityserver_client_server_code").text()
        };
    }

    function getEditableServerData() {
        var editableServerData = getEditableServerId();
        editableServerData.ownerName =
            $("#securityserver_edit_owner_name").text();
        editableServerData.address =
            $("#securityserver_edit_address").text();
        editableServerData.identifier =
            $("#securityserver_edit_identifier").text();

        return editableServerData;
    }

    function getClientDeletionRequestData() {
        var client = oClients.getFocusData();
        var server = getEditableServerData();

        return {
            client: {
                name: client.name,
                memberClass: client.member_class,
                memberCode: client.member_code,
                subsystemCode: client.subsystem_code
            },
            server: {
                ownerName: server.ownerName,
                ownerClass: server.ownerClass,
                ownerCode: server.ownerCode,
                serverCode: server.serverCode
            }
        };
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

    function getThisServerTempCertId() {
        return $("#add_auth_cert_temp_id").text();
    }

    function openAuthCertDetailsById(authCertId) {
        var params = {certId: authCertId};

        $.get("securityservers/get_cert_details_by_id", params, function(response) {
            XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
        }, "json");
    }

    /* -- GET DATA - END -- */

    /* -- CLEAR FIELDS - START -- */

    function clearAuthCertAddData(){
        $("#auth_cert_add_submit").disable();
        $("#add_auth_cert_temp_id").text("");

        $("#add_auth_cert_ca").text("");
        $("#add_auth_cert_serial_number").text("");
        $("#add_auth_cert_subject").text("");
        $("#add_auth_cert_expires").text("");
    }

    function clearServerClientAddData() {
        $("#securityserver_client_name").text("");
        $("#securityserver_client_class").text("");
        $("#securityserver_client_code").text("");
        $("#securityserver_client_subsystem_code").val("");

        $("#securityserver_client_owner_name").text("");
        $("#securityserver_client_owner_class").text("");
        $("#securityserver_client_owner_code").text("");
        $("#securityserver_client_server_code").text("");
    }

    /* -- CLEAR FIELDS - END -- */

    /* -- POST REQUESTS - START -- */

    function editAddress(dialog) {
        var newAddress = $("#securityserver_edit_address_new").val();
        $("#securityserver_edit_address").text(newAddress);
        serverData = getEditableServerData();

        $.post("securityservers/address_edit", serverData,
                function() {
            updateSecurityserversTableIfExists();
            $(dialog).dialog("close");
        }, "json");
    }

    function editSecurityCategories(dialog) {
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

    function addAuthCert(dialog) {
        var params = getEditableServerId();
        params["tempCertId"] = getThisServerTempCertId();

        $.post("securityservers/auth_cert_adding_request", params,
                function(response) {
            clearAuthCertAddData();
            refreshServerDataTables(params);
            $(dialog).dialog("close");
        }, "json");
    }

    function deleteAuthCert(dialog) {
        var params = getEditableServerId();
        var certId = oAuthCerts.getFocusData().id;
        params["certId"] = certId;

        $.post("securityservers/auth_cert_deletion_request", params,
                function(response) {
            refreshServerDataTables(params);
            $(dialog).dialog("close");
        }, "json");
    }

    function addNewServerClientRequest(dialog) {
        var params = getServerClientRegRequestParams();

        $.post("members/add_new_server_client_request", params,
                function(){
            clearServerClientAddData();
            refreshServerDataTables(getEditableServerId());
            $(dialog).dialog("close");
        }, "json");
    }

    /* -- POST REQUESTS - END -- */

    /* -- HANDLERS - START -- */

    function initInputHandlers() {
        $("#securityserver_edit_change_address").live("click", function() {
            return startAddressChange();
        });

        // TODO (RM #2770): probably usable when security categories implemented
        $("#security_categories_edit").live("click", function() {
            startSecurityCategoryEditing()
        });

        $("#securityserver_authcert_add").live("click", function() {
            startAuthCertAdding();
        });

        $("#securityserver_auth_cert_upload").live("change", function() {
            $("#server_auth_cert_upload").submit();
            /* function uploadCallback manages post-submission activities on
            UI part */
        });

        $("#securityserver_authcert_delete").live("click", function() {
            startAuthCertDeletion();
        });

        $("#securityserver_client_add").live("click", function() {
            startAddingClient();
        });

        $("#securityserver_client_client_search").live("click", function() {
            var securityServerCode = getEditableServerId().serverCode;

            MEMBER_SEARCH_DIALOG.open(securityServerCode, function(newClient) {
                selectNewServerClient(newClient);
                initNewServerClientSubsystemAutocomplete(newClient);
            }, true);
        });

        $("#securityserver_delete").live("click", function() {
            var requestParams = getEditableServerId();
            var confirmParams = {
                server: requestParams.serverCode,
                ownerCode: requestParams.ownerCode,
                ownerClass: requestParams.ownerClass
            };

            confirm("securityservers.remove_confirm", confirmParams,
                    function() {
                $.post("securityservers/delete", requestParams, function() {
                    $("#securityserver_edit_dialog").dialog("close");
                    updateSecurityserversTableIfExists();
                    refreshMemberDataTablesIfExist();
                }, "json");
            });
        });
    }

    function startAddressChange() {
        var currentAddress = $("#securityserver_edit_address").text();
        $("#securityserver_edit_address_new").val(currentAddress);

        $("#securityserver_address_edit_dialog").dialog("open");

        return false;
    }

    function startAuthCertAdding() {
        clearAuthCertAddData();

        $(".auth_cert_details").hide();
        $("#auth_cert_add_dialog").dialog("open");

        fillAuthCertServerData();
    }

    function startAuthCertDeletion() {
        $("#auth_cert_delete_dialog").dialog("open");

        fillAuthCertServerData();
        fillDeletableAuthCertData();
    }

    function startAddingClient() {
        clearServerClientAddData();
        fillClientRegRequestServerDetails();
        $("#securityserver_client_register_dialog").dialog("open");
    }

    function startSecurityCategoryEditing() {
        var params = getEditableServerData();
        $.get("securityservers/all_security_categories", params,
                function(response) {
            fillSecurityCategoriesEditTable(response.data);
        }, "json");

        var identifier = params.identifier;
        $("#security_categories_edit_legend").text(identifier);
        $("#security_categories_edit_dialog").dialog("open");
    }

    function updateNewClientSubmitButton() {
        var newClientSubmitButton = $("#securityserver_client_register_submit");

        if (isReadonlyInputFilled($("#securityserver_client_class")) &&
                isReadonlyInputFilled($("#securityserver_client_code"))) {
            newClientSubmitButton.enable();
        } else {
            newClientSubmitButton.disable();
        }
    }

    /* -- HANDLERS - END -- */

    /* -- DATA TABLES - START -- */

    function createDataTables() {
        createSecurityCategoriesTable();
        createClientsTable();
        createAuthCertsTable();
        createManagementRequestsTable();
    }

    function destroyDataTables() {
        // Non-serverside tables seem to destroy themselves.
        oManagementRequests.fnDestroy();
    }

    function createSecurityCategoriesTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            { "mData": "description", mRender : util.escape }
        ];

        oSecurityCategories =
                $("#securityserver_security_categories").dataTable(opts);
    }

    function createClientsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bDestroy = true;
        opts.aoColumns = [
            { "mData": "name", mRender : util.escape },
            { "mData": "member_class", mRender : util.escape },
            { "mData": "member_code", mRender : util.escape },
            { "mData": "subsystem_code", mRender : util.escape }
        ];
        opts.fnRowCallback = function(nRow, client) {
            var clientLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(client.member_code);

            clientLink.click(function(){
                openingClientDetails = true;
                XROAD_MEMBER_EDIT.openById(client.id);
            });

            var clientColumn = $(nRow).find("td:eq(2)");
            clientColumn.empty().append(clientLink);
        };

        oClients = $('#securityserver_clients').dataTable(opts);

        $("#securityserver_clients tbody td[class!=dataTables_empty]")
                .live("click", function(ev) {
            if (openingClientDetails) {
                oClients.removeFocus();
                disableClientDeletion();
                openingClientDetails = false;
                return;
            }

            enableClientDeletion(ev);
        });
    }

    function createAuthCertsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            { "mData": "csp", mRender : util.escape },
            { "mData": "serial_number", mRender : util.escape },
            { "mData": "subject", mRender : util.escape },
            { "mData": "expires", mRender : util.escape }
        ];
        opts.fnRowCallback = function(nRow, authCert) {
            var authCertLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(authCert.serial_number);

            authCertLink.click(function(){
                openingAuthCertDetails = true;
                openAuthCertDetailsById(authCert.id);
            });

            var authCertColumn = $(nRow).find("td:eq(1)");
            authCertColumn.empty().append(authCertLink);
        };

        oAuthCerts = $("#securityserver_auth_certs").dataTable(opts);

        $("#securityserver_auth_certs tbody td[class!=dataTables_empty]")
                .live("click", function(ev) {
            if (openingAuthCertDetails) {
                oAuthCerts.removeFocus();
                disableAuthCertDeletion();
                openingAuthCertDetails = false;
                return;
            }

            enableAuthCertDeletion(ev);
        });
    }

    function createManagementRequestsTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = 300;
        opts.sDom = "tp";
        opts.aoColumns = [
            { "mData": "id" },
            { "mData": "type" },
            { "mData": "received" },
            { "mData": "status" }
        ];

        opts.aaSorting = [ [2,'desc'] ];

        opts.fnRowCallback = function(nRow, managementRequest) {
            var managementRequestColumn = $(nRow).find("td:first");
            var managementRequestLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(managementRequest.id);
            var updateTablesCallback = function() {
                refreshManagementRequests();
                // TODO: Add callback for members if needed!
            }

            managementRequestLink.click(function(){
                XROAD_REQUEST_EDIT.open(managementRequest, updateTablesCallback);
            });

            managementRequestColumn.empty().append(managementRequestLink);
        };

        opts.sAjaxSource = "securityservers/management_requests";

        opts.fnServerParams = function(aoData) {
            var serverId = getEditableServerId();

            aoData.push({
                "name": "ownerClass",
                "value": serverId.ownerClass
            });
            aoData.push({
                "name": "ownerCode",
                "value": serverId.ownerCode
            });
            aoData.push({
                "name": "serverCode",
                "value": serverId.serverCode
            });
        };

        oManagementRequests =
            $("#securityserver_management_requests").dataTable(opts);
    }

    /* -- DATA TABLES - END -- */

    /* -- DIALOGS - START -- */

    function initDialogs() {
        initDetailsDialog();
        initAddressEditDialog();
        initSecurityCategoriesEditDialog();
        initAuthCertAddDialog();
        initAuthCertDeleteDialog();
        initSecurityServerClientRegisterDialog();

        initDynamicDialogHandlers();
    }

    function initDetailsDialog() {
        $("#securityserver_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 1000,
            height: 600,
            buttons: [ {
                text: _("common.close"),
                click: function() {
                    $(this).dialog("close");
                }
            } ],
            open: function() {
                createDataTables();
            },
            close: function() {
                destroyDataTables();
            }
        });

        $("#securityserver_edit_tabs").initTabs({
            activate: function(event, ui) {
                refreshTab($("a", ui.newTab).attr("href"),
                           getEditableServerId());
            }
        });
    }

    function initAddressEditDialog() {
        $("#securityserver_address_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 230,
            width: 360,
            buttons: [
                { text: "OK",
                  click: function() {
                      editAddress(this);
                  }
                },
                { text: "Cancel",
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    }

    // TODO (RM #2770): probably usable when security categories implemented
    function initSecurityCategoriesEditDialog() {
        $("#security_categories_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 400,
            width: 400,
            buttons: [
                { text: "OK",
                  click: function() {
                    editSecurityCategories(this);
                  }
                },
                { text: "Cancel",
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    }

    function initAuthCertAddDialog() {
        $("#auth_cert_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 500,
            buttons: [
                { text: _("common.submit"),
                    id: "auth_cert_add_submit",
                    disabled: "disabled",
                    click: function() {
                        addAuthCert(this);
                    }
                },
                { text: _("common.cancel"),
                    click: function() {
                        $(this).dialog("close");
                    }
                }
            ],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogMaxHeight($(this));
            }

        });
    }

    function initAuthCertDeleteDialog() {
        $("#auth_cert_delete_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 500,
            buttons: [
                { text: _("common.submit"),
                    click: function() {
                        deleteAuthCert(this);
                    }
                },
                { text: _("common.cancel"),
                    click: function() {
                        $(this).dialog("close");
                    }
                }
            ],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogMaxHeight($(this));
            }

        });
    }

    function initSecurityServerClientRegisterDialog() {
        $("#securityserver_client_register_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 560,
            buttons: [
                { text: _("common.submit"),
                    disabled: "disabled",
                    id: "securityserver_client_register_submit",
                    click: function() {
                        addNewServerClientRequest(this);
                    }
                },
                { text: _("common.cancel"),
                    click: function() {
                        $(this).dialog("close");
                    }
                }
            ],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogMaxHeight($(this));
            }
        });
    }

    function initDynamicDialogHandlers() {
        $("#securityserver_client_delete").live("click", function(){
            XROAD_REQUEST_CLIENT_DELETION.fillRemovableClientData(
                    getClientDeletionRequestData());

            XROAD_REQUEST_CLIENT_DELETION.openDialog(function() {
                refreshServerDataTables(getEditableServerId());
            });
        });
    }

    /* -- DIALOGS - END -- */

    function updateSecurityserversTableIfExists() {
        if (openedFromList == false) {
            return;
        }

        XROAD_SECURITYSERVERS.updateTable();
    }

    $(document).ready(function() {
        initView();
    });

    function refreshMemberDataTablesIfExist() {
        if (openedFromList == true) {
            return;
        }

        // TODO: what exactly needs to be refreshed here?
        // var memberId = XROAD_MEMBER_EDIT.getEditableMemberId()
        // XROAD_MEMBER_EDIT.refreshMemberDataTables(memberId);
    }

    function enableAuthCertDeletion(event) {
        if (oAuthCerts.setFocus(0, event.target.parentNode)) {
            $("#securityserver_authcert_delete").enable();
        }
    }

    function disableAuthCertDeletion() {
        $("#securityserver_authcert_delete").disable();
    }

    function enableClientDeletion(event) {
        if (oClients.setFocus(0, event.target.parentNode)) {
            $("#securityserver_client_delete").enable();
        }
    }

    function disableClientDeletion() {
        $("#securityserver_client_delete").disable();
    }

    function focusClientSubsystemCodeInput() {
        $("#securityserver_client_subsystem_code").focus();
    }

    return {
        open: open,

        refreshManagementRequests: refreshManagementRequests,

        uploadCallbackAuthCert: uploadCallbackAuthCert
    };
}();
