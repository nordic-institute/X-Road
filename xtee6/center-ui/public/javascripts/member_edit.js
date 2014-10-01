var SDSB_MEMBER_EDIT = function() {
    var oOwnedServers, oGlobalGroupMembership, oSubsystems, oUsedServers,
            oMemberManagementRequests, oAddableUsedServers;

    var remainingGlobalGroups = {};

    /* -- PUBLIC - START -- */

    function open(memberData) {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed(
                "members/can_see_details", function() {
            fillMemberDetails(memberData);

            $("#member_edit_dialog").dialog("open");
            $("#member_edit_tabs").tabs("option", "active", 0);

            disableUsedServerDeletionButton();
            disableGroupMemberDeletionButton();
            disableSubsystemDeleteButton();
        });
    }

    function openById(memberId) {
        var params = {
            memberId : memberId
        };

        $.get("members/get_member_by_id", params, function(response) {
            open(response.data);
        }, "json");
    }

    function uploadCallbackOwnedServerAuthCert(response) {
        var submitButton = $("#add_owned_server_submit");
        if (response.success) {
            fillOwnedServerAuthCertData(response.data);
            refreshMemberDataTables(getEditableMemberId());

            submitButton.enable();
            $(".auth_cert_details").show();
        } else {
            clearOwnedServerCertData();

            submitButton.disable();
            $(".auth_cert_details").hide();
        }

        showMessages(response.messages);

        return true;
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function fillMemberDetails(member) {
        $("#member_edit_name").text(member.name);
        $("#member_edit_class").text(member.member_class);
        $("#member_edit_code").text(member.member_code);
        $("#member_edit_admin_contact").val(member.admin_contact);
        $("#member_edit_id").text(member.id);
    }

    function refreshMemberDataTables(params) {
        refreshOwnedServers(params);
        refreshGlobalGroupMembership(params);
        refreshSubsystems(params);
        refreshUsedServers();
        refreshManagementRequests(params);
    }

    var tabRefreshers = {
        "#details_tab": function() {},
        "#owned_servers_tab": refreshOwnedServers,
        "#group_membership_tab": refreshGlobalGroupMembership,
        "#subsystems_tab": refreshSubsystems,
        "#used_servers_tab": refreshUsedServers,
        "#management_requests_tab": refreshManagementRequests
    };

    function refreshTab(tab, params) {
        tabRefreshers[tab].call(this, params);
    }

    function refreshOwnedServers(params, refreshCallback) {
        $.get("members/owned_servers", params, function(response) {
            oOwnedServers.fnClearTable();
            oOwnedServers.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshGlobalGroupMembership(params, refreshCallback) {
        disableGroupMemberDeletionButton();

        $.get("members/global_groups", params, function(response) {
            updateGlobalGroupMembershipTable(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshSubsystems(params, refreshCallback) {
        disableSubsystemDeleteButton();

        $.get("members/subsystems", params, function(response) {
            updateSubsystemsTable(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshUsedServers(refreshCallback) {
        var params = {memberId: $("#member_edit_id").text()};

        $.get("members/used_servers", params, function(response) {
            updateUsedServersTable(response.data);

            $("#remove_securityserver_client").disable();
        });
    }

    function refreshManagementRequests(params, refreshCallback) {
        if (params == null) {
            params = getEditableMemberId();
        }

        $.get("members/management_requests", params, function(response) {
            oMemberManagementRequests.fnClearTable();
            oMemberManagementRequests.fnAddData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshAddableUsedServers(params) {
        $.get("securityservers/securityservers_advanced_search",
                getSecurityServersAdvancedSearchParams(), function(response) {
                    oAddableUsedServers.fnClearTable();
                    oAddableUsedServers.fnAddData(response.data);
                });
    }

    function updateGlobalGroupMembershipTable(newData) {
        oGlobalGroupMembership.fnClearTable();
        oGlobalGroupMembership.fnAddData(newData);
    }

    function updateSubsystemsTable(newData) {
        oSubsystems.fnClearTable();
        oSubsystems.fnAddData(newData);
    }

    function updateUsedServersTable(newData) {
        oUsedServers.fnClearTable();
        oUsedServers.fnAddData(newData);
    }

    function fillMemberSubsystemSelect() {
        $.get("members/subsystem_codes", getEditableMemberId(), function(
                response) {
            var options = response.data;
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    "member_to_group_add_select_subsystem", options);

            if (options.length > 0) {
                $("#member_to_group_add_select_subsystem").enable();
            } else {
                $("#member_to_group_add_select_subsystem").disable();
            }
        });
    }

    function fillGlobalGroupSelect() {
        var select = $("#member_to_group_add_select_group");
        select.find('option').remove();

        var params = getEditableMemberId();
        params.subsystemCode = $("#member_to_group_add_select_subsystem").val();

        $.get("members/remaining_global_groups", params,
                function(response) {
            $.each(response.data, function(index, each) {
                var code = each.code;
                select.append('<option value="' + code + '">' + code
                        + '</option>');
                remainingGlobalGroups[code] = each.description;
            });

            $("#member_to_group_add_description").val(
                    remainingGlobalGroups[select.val()]);
        });
    }

    function fillServerOwnerData() {
        var memberData = getEditableMemberData();
        $("#used_server_name").val(memberData.memberName);
        $("#used_server_class").val(memberData.memberClass);
        $("#used_server_code").val(memberData.memberCode);
    }

    /**
     * Fills server details for security server client registration request.
     */
    function fillServerDetails() {
        var serverData = oAddableUsedServers.getFocusData();

        $("#used_server_owner_name").val(serverData.owner_name);
        $("#used_server_owner_class").val(serverData.owner_class);
        $("#used_server_owner_code").val(serverData.owner_code);
        $("#used_server_server_code").val(serverData.server_code);
    }

    function fillOwnedServerAuthCertData(authCertData) {
        $("#auth_cert_file").val("");
        $("#owned_server_authcert_csp").val(authCertData.csp);
        $("#owned_server_authcert_serial_number").val(
                authCertData.serial_number);
        $("#owned_server_authcert_subject").val(authCertData.subject);
        $("#owned_server_authcert_expires").val(authCertData.expires);
        $("#owned_server_temp_cert_id").text(authCertData.temp_cert_id);
    }

    function getClientDeletionRequestData() {
        var memberData = getEditableMemberData();
        var serverData = oUsedServers.getFocusData();

        return {
            client: {
                name: memberData.memberName,
                memberClass: memberData.memberClass,
                memberCode: memberData.memberCode,
                subsystemCode: serverData.client_subsystem_code
            },
            server: {
                ownerName: serverData.owner_name,
                ownerClass: serverData.owner_class,
                ownerCode: serverData.owner_code,
                serverCode: serverData.server
            }
        }
    }

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

    function getEditableMemberId() {
        return {
            memberClass : $("#member_edit_class").text(),
            memberCode : $("#member_edit_code").text()
        };
    }

    function getEditableMemberData() {
        var editableMemberData = getEditableMemberId();
        editableMemberData.memberName = getMemberName();
        editableMemberData.adminContact = $("#member_edit_admin_contact").val();

        return editableMemberData;
    }

    function getMemberName() {
        return $("#member_edit_name").text();
    }

    function getClientRegistrationRequestParams() {
        return {
            memberClass : $("#used_server_class").val(),
            memberCode : $("#used_server_code").val(),
            subsystemCode : $("#used_server_subsystem_code").val(),
            ownerClass : $("#used_server_owner_class").val(),
            ownerCode : $("#used_server_owner_code").val(),
            serverCode : $("#used_server_server_code").val()
        };
    }

    function getNewOwnedServerData() {
        return {
            ownerClass : $("#owned_server_owner_class").val(),
            ownerCode : $("#owned_server_owner_code").val(),
            serverCode : $("#owned_server_add_servercode").val(),
            tempCertId : getOwnedServerTempCertId()
        };
    }

    function getSecurityServersAdvancedSearchParams() {
        var advancedSearchParams = {
            name : $("#used_server_owner_name").val(),
            memberClass : $("#used_server_owner_class").val(),
            memberCode : $("#used_server_owner_code").val(),
            serverCode : $("#used_server_server_code").val()
        };

        return {
            advancedSearchParams : JSON.stringify(advancedSearchParams)
        }
    }

    function getOwnedServerTempCertId() {
        return $("#owned_server_temp_cert_id").text();
    }

    /* -- GET DATA - END -- */

    /* -- CLEAR FIELDS - START -- */

    function clearOwnedServerAddData() {
        $("#owned_server_add_servercode").val("");
        clearOwnedServerCertData();
    }

    function clearOwnedServerCertData() {
        $("#add_owned_server_submit").disable();
        $("#owned_server_temp_cert_id").text("");

        $("#owned_server_authcert_csp").val("");
        $("#owned_server_authcert_serial_number").val("");
        $("#owned_server_authcert_subject").val("");
        $("#owned_server_authcert_expires").val("");
    }

    function clearUsedServerAddData() {
        $("#used_server_name").val(""),
        $("#used_server_class").val(""),
        $("#used_server_code").val(""),
        $("#used_server_subsystem_code").val(""),

        $("#used_server_owner_name").val(""),
        $("#used_server_owner_class").val(""),
        $("#used_server_owner_code").val(""),
        $("#used_server_server_code").val("")
    }

    /* -- CLEAR FIELDS - END -- */

    /* -- POST REQUESTS - START -- */

    function deleteSubsystem(subsystemCode) {
        var member = getEditableMemberId();

        var requestParams = {
            memberCode : member.memberCode,
            memberClass : member.memberClass,
            subsystemCode : subsystemCode
        };

        var confirmParams = {
            subsystem : requestParams["subsystemCode"],
            memberCode : requestParams["memberCode"],
            memberClass : requestParams["memberClass"]
        };

        confirm("members.edit.subsystems.remove_confirm", confirmParams,
                function() {
            $.post("members/delete_subsystem", requestParams, function(
                    response) {
                updateSubsystemsTable(response.data);
            }, "json");
        });
    }

    function deleteMemberFromGlobalGroup() {
        var member = getEditableMemberId();
        var group = oGlobalGroupMembership.getFocusData();

        var requestParams = {
            memberClass : member.memberClass,
            memberCode : member.memberCode,
            groupCode : group.group_code,
            subsystemCode : group.subsystem
        };

        var confirmParams = {
            member : group.identifier,
            group : requestParams["groupCode"]
        };

        confirm("members.edit.global_group_membership.remove_confirm",
                confirmParams, function() {
            $.post("members/delete_member_from_global_group",
                requestParams,
                function(response) {
                    updateGlobalGroupMembershipTable(response.data);
                }, "json");
            });
    }

    function addMemberToGlobalGroup() {
        var params = getEditableMemberId();
        params.subsystemCode = $("#member_to_group_add_select_subsystem").val();
        params.groupCode = $("#member_to_group_add_select_group").val();

        $.post("members/add_member_to_global_group", params,
                function(response) {
            updateGlobalGroupMembershipTable(response.data);
        });
    }

    function addNewServerClientRequest(dialog) {
        var params = getClientRegistrationRequestParams();

        $.post("members/add_new_server_client_request", params,
                function() {
            clearUsedServerAddData();
            refreshMemberDataTables(getEditableMemberId());
            $(dialog).dialog("close");
        }, "json");
    }
    
    function editName(dialog) {
        var newName = $("#member_edit_name_new").val();
        var memberData = getEditableMemberData();
        memberData.memberName = newName;

        $.post("members/member_edit", memberData, function() {
            $("#member_edit_name").text(newName);

            SDSB_MEMBERS.redrawMembersTable();

            $(dialog).dialog("close");
        }, "json");
    }

    function editContact(dialog) {
        var newContact = $("#member_edit_contact_new").val();
        var memberData = getEditableMemberData();
        memberData.adminContact = newContact;

        $.post("members/member_edit", memberData, function() {
            $("#member_edit_admin_contact").val(newContact);

            SDSB_MEMBERS.redrawMembersTable();

            $(dialog).dialog("close");
        }, "json"); 
    }

    function addNewOwnedServerRequest(dialog) {
        var params = getNewOwnedServerData();

        $.post("members/add_new_owned_server_request", params,
                function(response) {
            clearOwnedServerAddData();
            refreshMemberDataTables(getEditableMemberId());
            $(dialog).dialog("close");
        }, "json");
    }

    /* -- POST REQUESTS - END -- */

    /* -- MISC - START -- */

    function openServerDetailsById(serverId) {
        var params = {
            serverId : serverId
        };

        $.get("securityservers/get_server_by_id", params, function(response) {
            SDSB_SECURITYSERVER_EDIT.open(response.data);
        }, "json");
    }

    function handleSubsystemDeleteButtonVisibility(event) {
            if (!oSubsystems.setFocus(0, event.target.parentNode)) {
                return;
            }

            var subsystem = oSubsystems.getFocusData();
            var usedServers = subsystem.used_servers;

            if (usedServers.length === 0) {
                $("#delete_subsystem").enable();
            } else {
                disableSubsystemDeleteButton();
            }
    }

    function disableUsedServerDeletionButton() {
        $("#remove_securityserver_client").disable();
    }

    function disableGroupMemberDeletionButton() {
        $("#delete_global_group_membership").disable();
    }

    function disableSubsystemDeleteButton() {
        $("#delete_subsystem").disable();
    }

    /* -- MISC - END -- */

    /* -- DATA TABLES - START -- */

    function initTables() {
        initOwnedServersTable();
        initGlobalGroupMembershipsTable();
        initSubsystemsTable();
        initUsedServersTable();
        initManagementRequestsTable();
    }

    function initAddableUsedServersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "ft";
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "owner_name"},
            {"mData" : "owner_class"},
            {"mData" : "owner_code"},
            {"mData" : "server_code"} ];

        opts.aaSorting = [ [ 2, 'desc' ] ];

        oAddableUsedServers = $('#used_server_search_all').dataTable(opts);

        var allServersTableRow = $(
                "#used_server_search_all tbody td[class!=dataTables_empty]");

        allServersTableRow.live("click", function(ev) {
            if (oAddableUsedServers.setFocus(0, ev.target.parentNode)) {
                $("#member_securityserver_search_select").enable();
            }
        });

        allServersTableRow.live("dblclick", function() {
            fillServerDetails();
            $("#securityserver_search_dialog").dialog("close");
        });
    }

    function initOwnedServersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            {"mData" : "server"}];

        opts.fnRowCallback = function(nRow, ownedServer) {
            var ownedServerLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(ownedServer.server);

            ownedServerLink.click(function() {
                openServerDetailsById(ownedServer.id);
            });

            var serverCodeColumn = $(nRow).find("td:first");
            serverCodeColumn.empty().append(ownedServerLink);
        };

        $("#member_owned_servers tbody tr, #member_owned_servers tbody tr a")
                .live("click", function(ev) {
            if ($(ev.target).is('a')) {oOwnedServers.setFocus(0,
                        ev.target.parentNode.parentNode);
            } else {
                oOwnedServers.setFocus(0, ev.target.parentNode);
            }
        });

        oOwnedServers = $("#member_owned_servers").dataTable(opts);
    }

    function initGlobalGroupMembershipsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            {"mData" : "group_code"},
            {"mData" : "subsystem"},
            {"mData" : "added_to_group"}];

        opts.fnRowCallback = function(nRow, globalGroup) {
            var globalGroupLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(globalGroup.group_code);

            globalGroupLink.click(function() {
                SDSB_GROUP_EDIT.openById(globalGroup.group_id);
            });

            var globalGroupColumn = $(nRow).find("td:first");
            globalGroupColumn.empty().append(globalGroupLink);
        };

        oGlobalGroupMembership = $("#member_global_group_membership")
                .dataTable(opts);

        $("#member_global_group_membership tbody td").live(
                "click",
                function(ev) {
                    if (oGlobalGroupMembership
                            .setFocus(0, ev.target.parentNode)) {
                        $("#delete_global_group_membership").enable();
                    }
                });

        $("#delete_global_group_membership").live("click", function() {
            deleteMemberFromGlobalGroup();
        });
    }

    function initSubsystemsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            {"mData" : "subsystem_code"},
            {"mData" : "used_servers"} ];

        opts.fnRowCallback = function(nRow, subsystem) {
            usedServers = subsystem.used_servers;

            if (usedServers.length === 0) {
                var nameColumn = $(nRow).find("td:eq(0)");
                nameColumn.addClass("deletable_subsystem");
            } else {
                var usedServersColumn = $(nRow).find("td:eq(1)");
                usedServersColumn.empty();

                $.each(usedServers, function(index, each) {
                    if (index > 0) {
                        usedServersColumn.append(", ");
                    }

                    var usedServerLink =
                        SDSB_CENTERUI_COMMON.getDetailsLink(each.server_code);

                    usedServerLink.click(function() {
                        openServerDetailsById(each.id);
                    });

                    usedServersColumn.append(usedServerLink);
                });
            }
        };

        oSubsystems = $('#member_subsystems').dataTable(opts);

        $("#member_subsystems tbody td").live("click", function(ev) {
            handleSubsystemDeleteButtonVisibility(ev);
        });

        $("#delete_subsystem").live("click", function() {
            var subsystem = oSubsystems.getFocusData();
            deleteSubsystem(subsystem.subsystem_code);
        });
    }

    function initUsedServersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "server"},
            {"mData" : "client_subsystem_code"},
            {"mData" : "owner_name"}];

        opts.fnRowCallback = function(nRow, usedServer) {
            var serverCodeColumn = $(nRow).find("td:first");
            var serverCodeLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(usedServer.server);

            serverCodeLink.click(function() {
                openServerDetailsById(usedServer.id);
            });

            serverCodeColumn.empty().append(serverCodeLink);

            var serverOwnerColumn = $(nRow).find("td:eq(2)");
            var serverOwnerLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(usedServer.owner_name);

            serverOwnerLink.click(function() {
                openById(usedServer.owner_id);
            });

            serverOwnerColumn.empty().append(serverOwnerLink);
        };

        opts.aaSorting = [ [ 1, 'asc' ] ];

        oUsedServers = $('#member_used_servers').dataTable(opts);

        $("#member_used_servers tbody tr").live("click", function(ev) {
            if (oUsedServers.setFocus(0, ev.target.parentNode)) {
                $("#remove_securityserver_client").enable();
            }
        });
    }

    function initManagementRequestsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.aoColumns = [
            {"mData" : "id"},
            {"mData" : "type"},
            {"mData" : "received"},
            {"mData" : "status"} ];

        opts.aaSorting = [ [ 2, 'desc' ] ];

        opts.fnRowCallback = function(nRow, managementRequest) {
            var managementRequestColumn = $(nRow).find("td:first");
            var managementRequestLink =
                SDSB_CENTERUI_COMMON.getDetailsLink(managementRequest.id);

            managementRequestLink.click(function() {
                SDSB_REQUEST_EDIT.open(managementRequest);
            });

            managementRequestColumn.empty().append(managementRequestLink);
        };

        oMemberManagementRequests = $("#member_management_requests").dataTable(
                opts);
    }

    /* -- DATA TABLES - END -- */

    /* -- DIALOGS - START -- */

    function initDialogs() {
        initUsedServersRegisterDialog();
        initMemberEditDialog();
        initMemberNameEditDialog();
        initMemberContactEditDialog();
        initOwnedServerAddDialog();
        initSecurityServerSearchDialog();

        initDynamicDialogHandlers();
    }

    function initUsedServersRegisterDialog() {
        $("#member_used_server_register_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 630,
            width : 560,
            buttons : [
                {
                    text : _("common.submit"),
                    click : function() {
                        addNewServerClientRequest(this);
                    }
                }, {
                    text : _("common.cancel"),
                    click : function() {
                        $(this).dialog("close");
                    }
                } ]
        });

        $("#register_securityserver_client").live("click", function() {
            clearUsedServerAddData();
            fillServerOwnerData();

            $("#member_used_server_register_dialog").dialog("open");
        });
    }

    function initMemberEditDialog() {
        $("#member_edit_dialog").initDialog({
            autoOpen : false,
            modal : true,
            width : 1200,
            height : 500,
            buttons : [ {
                text : _("common.close"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        });

        $("#member_edit_tabs").initTabs({
            activate: function(event, ui) {
                refreshTab($("a", ui.newTab).attr("href"),
                           getEditableMemberId());
            }
        });

        $("#member_edit_delete").live("click", function() {
            SDSB_CENTERUI_COMMON.deleteMember(getEditableMemberId());
        });
    }

    function initMemberNameEditDialog() {
        $("#member_name_edit_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 230,
            width : 360,
            buttons : [ {
                text : _("common.ok"),
                click : function() {
                    editName(this);
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        });

        $("#member_edit_change_name").live("click", function() {
            var currentName = $("#member_edit_name").text();
            $("#member_edit_name_new").val(currentName);

            $("#member_name_edit_dialog").dialog("open");

            return false;
        });
    }

    function initMemberContactEditDialog() {
        $("#member_contact_edit_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 230,
            width : 360,
            buttons : [ {
                text : _("common.ok"),
                click : function() {
                    editContact(this);
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        });
    }

    function getMemberToGlobalGroupAddDialog() {
        return $("#member_to_group_add_dialog").initDialog({
            autoOpen : false,
            modal : true,
            title : _(
                    "members.edit.global_group_membership.add_member_to_group",
                    {
                        member : getMemberName()
                    }),
            width : "auto",
            buttons : [ {
                text : _("common.ok"),
                click : function() {
                    addMemberToGlobalGroup();
                    $(this).dialog("close");
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        });
    }

    function initOwnedServerAddDialog() {
        $("#owned_server_add_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 500,
            width : 500,
            buttons : [
            {
                text : _("common.submit"),
                id : "add_owned_server_submit",
                disabled : "disabled",
                click : function() {
                    addNewOwnedServerRequest(this);
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            }]
        });

        $("#add_owned_server").live("click", function() {
            clearOwnedServerAddData();

            $(".auth_cert_details").hide();

            var memberData = getEditableMemberData();
            $("#owned_server_owner_name").val(memberData.memberName);
            $("#owned_server_owner_class").val(memberData.memberClass);
            $("#owned_server_owner_code").val(memberData.memberCode);

            $("#owned_server_add_dialog").dialog("open");
        });

        $("#owned_server_cert_upload").live("click", function() {
            $("#auth_cert_upload").submit();
            /* function uploadCallbackOwnedServerAuthCert manages
            post-submission activities on UI part */
        });
    }

    function initSecurityServerSearchDialog() {
        $("#securityserver_search_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 430,
            width : 860,
            buttons : [ {
                text : _("common.select"),
                disabled : "disabled",
                id : "member_securityserver_search_select",
                click : function() {
                    var dialog = this;

                    fillServerDetails();

                    $(dialog).dialog("close");
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        });

        $("#used_server_server_search").live("click", function() {
            initAddableUsedServersTable();
            refreshAddableUsedServers();
            $("#securityserver_search_dialog").dialog("open");
        });
    }

    function initDynamicDialogHandlers() {
        $("#add_global_group_membership").live("click", function() {
            fillMemberSubsystemSelect();
            fillGlobalGroupSelect();
            getMemberToGlobalGroupAddDialog().dialog("open");
        });

        $("#remove_securityserver_client").live("click", function() {
            SDSB_REQUEST_CLIENT_DELETION.fillRemovableClientData(
                    getClientDeletionRequestData());

            SDSB_REQUEST_CLIENT_DELETION.openDialog(function() {
                refreshMemberDataTables(getEditableMemberId());
            });
        });

        $("#member_to_group_add_select_group").live("change", function() {
            $("#member_to_group_add_description").val(
                    remainingGlobalGroups[this.value]);
        });

        $("#member_to_group_add_select_subsystem").live("change", function() {
            fillGlobalGroupSelect();
        });
    }

    /* -- DIALOGS - END -- */

    $(document).ready(function() {
        initTables();
        initDialogs();
    });

    return {
        open: open,
        openById: openById,

        getEditableMemberId: getEditableMemberId,

        refreshMemberDataTables: refreshMemberDataTables,
        refreshManagementRequests: refreshManagementRequests,

        uploadCallbackOwnedServerAuthCert: uploadCallbackOwnedServerAuthCert
    };
}();
