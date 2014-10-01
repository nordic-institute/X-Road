var SDSB_SECURITYSERVER_EDIT = function() {
    var oSecurityCategories, oClients, oAuthCerts, oManagementRequests,
            oAddableClients;

    var openedFromList = false;

    var openingAuthCertDetails = false;
    var openingClientDetails = false;

    /* -- PUBLIC - START -- */

    function open(securityServerData, fromList) {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed(
                "securityservers/can_see_details", function() {
            openedFromList = fromList == true ? true : false;

            fillServerDetails(securityServerData);

            disableAuthCertDeletion();
            disableClientDeletion();

            $("#securityserver_edit_dialog").dialog("open");
            $("#securityserver_edit_tabs").tabs("option", "active", 0);
        });
    }

    function refreshManagementRequests(params, refreshCallback) {
        if (params == null) {
            params = getEditableServerId();
        }

        $.get("securityservers/management_requests", params,
                function(response) {
            oManagementRequests.fnClearTable();
            oManagementRequests.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function uploadCallbackAuthCert(response) {
        var submitButton = $("#auth_cert_add_submit");
        if (response.success) {
            fillAddableAuthCertData(response.data);

            submitButton.enable();
            $("#add_auth_cert_upload").disable();
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
        createDataTables();
        initInputHandlers();
    }

    function fillServerDetails(securityServer) {
        $("#securityserver_edit_owner_name").val(securityServer.owner_name);
        $("#securityserver_edit_owner_class").val(securityServer.owner_class);
        $("#securityserver_edit_owner_code").val(securityServer.owner_code);
        $("#securityserver_edit_server_code").val(securityServer.server_code);
        $("#securityserver_edit_registered").val(securityServer.registered);
        $("#securityserver_edit_address").val(securityServer.address);
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

        ownerNameInput.val(serverData.ownerName);
        ownerClassInput.val(serverData.ownerClass);
        ownerCodeInput.val(serverData.ownerCode);
        serverCodeInput.val(serverData.serverCode);
    }

    var tabRefreshers = {
        "#details_tab": function() {},
        "#clients_tab": refreshClients,
        "#auth_certs_tab": refreshAuthCerts,
        "#management_requests_tab": refreshManagementRequests
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
        $("#add_auth_cert_ca").val(authCertData.csp);
        $("#add_auth_cert_serial_number").val(authCertData.serial_number);
        $("#add_auth_cert_subject").val(authCertData.subject);
        $("#add_auth_cert_expires").val(authCertData.expires);
        $("#add_auth_cert_temp_id").text(authCertData.temp_cert_id);
    }

    function fillDeletableAuthCertData() {
        var authCertData = oAuthCerts.getFocusData();

        $("#delete_auth_cert_ca").val(authCertData.csp);
        $("#delete_auth_cert_serial_number").val(authCertData.serial_number);
        $("#delete_auth_cert_subject").val(authCertData.subject);
        $("#delete_auth_cert_expires").val(authCertData.expires);
    }

    function fillAddableClientData() {
        var addableClient = oAddableClients.getFocusData();

        $("#securityserver_client_name").val(addableClient.name);
        $("#securityserver_client_class").val(addableClient.member_class);
        $("#securityserver_client_code").val(addableClient.member_code);
        $("#securityserver_client_subsystem_code").val(
                addableClient.subsystem_code);

        $("#securityserver_client_search_dialog").dialog("close");
    }

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

    function getEditableServerId() {
        return {
            serverCode: $("#securityserver_edit_server_code").val(),
            ownerClass: $("#securityserver_edit_owner_class").val(),
            ownerCode: $("#securityserver_edit_owner_code").val(),
        };
    }

    function getServerClientRegRequestParams() {
        return {
            memberClass: $("#securityserver_client_class").val(),
            memberCode: $("#securityserver_client_code").val(),
            subsystemCode: $("#securityserver_client_subsystem_code").val(),
            ownerClass: $("#securityserver_client_owner_class").val(),
            ownerCode: $("#securityserver_client_owner_code").val(),
            serverCode: $("#securityserver_client_server_code").val()
        };
    }

    function getEditableServerData() {
        var editableServerData = getEditableServerId();
        editableServerData.ownerName =
            $("#securityserver_edit_owner_name").val();
        editableServerData.address =
            $("#securityserver_edit_address").val();
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

        $.get("securityservers/get_cert_details_by_id", params, 
                function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
        }, "json");
    }

    /* -- GET DATA - END -- */

    /* -- CLEAR FIELDS - START -- */

    function clearAuthCertAddData(){
        $("#auth_cert_add_submit").disable();
        $("#add_auth_cert_temp_id").text("");

        $("#add_auth_cert_ca").val("");
        $("#add_auth_cert_serial_number").val("");
        $("#add_auth_cert_subject").val("");
        $("#add_auth_cert_expires").val("");
    }

    function clearServerClientAddData() {
        $("#securityserver_client_name").val(""),
        $("#securityserver_client_class").val(""),
        $("#securityserver_client_code").val(""),
        $("#securityserver_client_subsystem_code").val(""),
        $("#securityserver_client_owner_name").val(""),
        $("#securityserver_client_owner_class").val(""),
        $("#securityserver_client_owner_code").val(""),
        $("#securityserver_client_server_code").val("")
    }

    /* -- CLEAR FIELDS - END -- */

    /* -- POST REQUESTS - START -- */

    function editAddress(dialog) {
        var newAddress = $("#securityserver_edit_address_new").val();
        $("#securityserver_edit_address").val(newAddress);
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

        $("#add_auth_cert_upload").live("click", function() {
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
            openServerClientsSearchDialog();
        });

        $("#securityserver_client_name, #securityserver_client_class, " +
        "#securityserver_client_code").live("keyup", function() {
            updateNewClientSubmitButton();
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
        var currentAddress = $("#securityserver_edit_address").val();
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

    function selectAddableClient(event) {
        oAddableClients.setFocus(0, event.target.parentNode)
    }

    function updateNewClientSubmitButton() {
        var newClientSubmitButton = $("#securityserver_client_register_submit");

        if (isInputFilled($("#securityserver_client_name")) &&
                isInputFilled($("#securityserver_client_class")) &&
                isInputFilled($("#securityserver_client_code"))) {
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

    function createSecurityCategoriesTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            { "mData": "description" }
        ];

        oSecurityCategories =
                $("#securityserver_security_categories").dataTable(opts);
    }

    function createClientsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": "member_class" },
            { "mData": "member_code" },
            { "mData": "subsystem_code" }
        ];
        opts.fnRowCallback = function(nRow, client) {
            var clientLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(client.member_code);

            clientLink.click(function(){
                openingClientDetails = true;
                SDSB_MEMBER_EDIT.openById(client.id);
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
        opts.aoColumns = [
            { "mData": "csp" },
            { "mData": "serial_number" },
            { "mData": "subject" },
            { "mData": "expires" }
        ];
        opts.fnRowCallback = function(nRow, authCert) {
            var authCertLink = 
                SDSB_CENTERUI_COMMON.getDetailsLink(authCert.serial_number);

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
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
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
                SDSB_CENTERUI_COMMON.getDetailsLink(managementRequest.id);

            managementRequestLink.click(function(){
                SDSB_REQUEST_EDIT.open(managementRequest);
            });

            managementRequestColumn.empty().append(managementRequestLink);
        };
        oManagementRequests =
            $("#securityserver_management_requests").dataTable(opts);
    }

    function initSearchableClientsTable() {
        var opts = defaultTableOpts();
        opts.bProcessing = true;
        opts.bScrollInfinite = true;
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.sScrollY = "321px";
        opts.sScrollX = "100%";
        opts.sDom = "f<<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": "member_code" },
            { "mData": "member_class" },
            { "mData": "subsystem_code" },
            { "mData": "sdsb_instance" },
            { "mData": "type" },
        ];

        opts.sAjaxSource = "securityservers/addable_clients";

        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "serverCode",
                "value": getEditableServerId().serverCode
            });
        };

        opts.aaSorting = [ [1,'asc'] ];

        oAddableClients =
            $('#securityserver_addable_client_search').dataTable(opts);
        oAddableClients.fnSetFilteringDelay(600);

        $("#securityserver_addable_client_search tbody tr").live("click",
                function(ev) {
            selectAddableClient(ev);
        });

        $("#securityserver_addable_client_search tbody tr").live("dblclick",
                function() {
            fillAddableClientData();
            updateNewClientSubmitButton();
        });
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
            } ]
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
            height: 500,
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
            ]
        });
    }

    function initAuthCertDeleteDialog() {
        $("#auth_cert_delete_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 500,
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
            ]
        });
    }

    function initSecurityServerClientRegisterDialog() {
        $("#securityserver_client_register_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 630,
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
            ]
        });
    }

    function openServerClientsSearchDialog() {
        $("#securityserver_client_search_dialog").initDialog({
            autoOpen: false,
            modal: true,
            title: _("securityservers.clients.add"),
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      fillAddableClientData();
                      updateNewClientSubmitButton();
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ],
            open: function () {
                initSearchableClientsTable();
            },
            close: function () {
                oAddableClients.fnDestroy();
            }
        }).dialog("open");
    }

    function initDynamicDialogHandlers() {
        $("#securityserver_client_delete").live("click", function(){
            SDSB_REQUEST_CLIENT_DELETION.fillRemovableClientData(
                    getClientDeletionRequestData());

            SDSB_REQUEST_CLIENT_DELETION.openDialog(function() {
                refreshServerDataTables(getEditableServerId());
            }); 
        });
    }

    /* -- DIALOGS - END -- */

    function updateSecurityserversTableIfExists() {
        if (openedFromList == false) {
            return;
        }

        SDSB_SECURITYSERVERS.updateTable();
    }

    $(document).ready(function() {
        initView();
    });

    function refreshMemberDataTablesIfExist() {
        if (openedFromList == true) {
            return;
        }

        var memberId = SDSB_MEMBER_EDIT.getEditableMemberId() 
        SDSB_MEMBER_EDIT.refreshMemberDataTables(memberId); 
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

    return {
        open: open,

        refreshManagementRequests: refreshManagementRequests,

        uploadCallbackAuthCert: uploadCallbackAuthCert
    };
}();
