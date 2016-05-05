function openMemberEditDialog(memberRowData) {
    var memberId = {
        memberClass: memberRowData.member_class,
        memberCode: memberRowData.member_code
    };

    var memberName = memberRowData.name;

    var dialog = $("#member_edit_dialog")
        .clone()
        .attr("id", "")
        .addClass("member_edit_dialog")
        .appendTo("body");

    // DataTables
    var oOwnedServers;
    var oGlobalGroupMembership;
    var oSubsystems;
    var oUsedServers;
    var oMemberManagementRequests;

    initDialog();
    initTabs();
    initTables();
    initActions();

    function _$(selector) {
        return dialog.closest(".ui-dialog").find(selector);
    }

    function initDialog() {
        dialog.initDialog({
            autoOpen: true,
            modal: true,
            width: 1000,
            height: 500,
            buttons: [{
                text: _("common.close"),
                click: function() {
                    $(this).dialog("close");
                }
            }],
            close: function() {
                dialog.dialog("destroy").remove();
            }
        });
    }

    function initTabs() {
        _$(".member_edit_tabs").initTabs({
            activate: function(event, ui) {
                var tabRefreshers = {
                    "#member_details_tab": refreshDetails,
                    "#member_owned_servers_tab": refreshOwnedServers,
                    "#member_group_membership_tab": refreshGlobalGroupMembership,
                    "#member_subsystems_tab": refreshSubsystems,
                    "#member_used_servers_tab": refreshUsedServers,
                    "#member_management_requests_tab": refreshManagementRequests
                };

                var tab = $("a", ui.newTab).attr("href");
                tabRefreshers[tab].call(this);
            }
        }).tabs("option", "active", 0);
    }

    function initTables() {
        initOwnedServersTable();
        initGlobalGroupMembershipsTable();
        initSubsystemsTable();
        initUsedServersTable();
        initManagementRequestsTable();
    }

    function initActions() {
        initMemberDetailsActions();
        initOwnedServersActions();
        initGlobalGroupMembershipsActions();
        initUsedServersActions();

        disableUsedServerDeletionButton();
        disableGroupMemberDeletionButton();
        disableSubsystemDeleteButton();
    }

    function disableUsedServerDeletionButton() {
        _$("#remove_securityserver_client").disable();
    }

    function disableGroupMemberDeletionButton() {
        _$("#delete_global_group_membership").disable();
    }

    function disableSubsystemDeleteButton() {
        _$("#delete_subsystem").disable();
    }

    function initOwnedServersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "server"}];

        opts.fnRowCallback = function(nRow, ownedServer) {
            var ownedServerLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(ownedServer.server);

            ownedServerLink.click(function() {
                openServerDetailsById(ownedServer.id);
            });

            var serverCodeColumn = $(nRow).find("td:first");
            serverCodeColumn.empty().append(ownedServerLink);
        };

        oOwnedServers = _$(".member_owned_servers").dataTable(opts);

        oOwnedServers.on("click", "tbody tr, tbody tr a", function(ev) {
            if ($(ev.target).is('a')) {
                oOwnedServers.setFocus(0, ev.target.parentNode.parentNode);
            } else {
                oOwnedServers.setFocus(0, ev.target.parentNode);
            }
        });
    }

    function initGlobalGroupMembershipsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "group_code", mRender: util.escape},
            {"mData" : "subsystem", mRender: util.escape},
            {"mData" : "added_to_group"}];

        opts.fnRowCallback = function(nRow, globalGroup) {
            var globalGroupLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(globalGroup.group_code);

            globalGroupLink.click(function() {
                XROAD_GROUP_EDIT.openById(globalGroup.group_id);
            });

            var globalGroupColumn = $(nRow).find("td:first");
            globalGroupColumn.empty().append(globalGroupLink);
        };

        oGlobalGroupMembership =
            _$(".member_global_group_membership").dataTable(opts);

        oGlobalGroupMembership.on("click", "tbody tr", function(ev) {
            if (oGlobalGroupMembership.setFocus(0, this)) {
                var group = oGlobalGroupMembership.getFocusData();

                if (group.is_readonly == true) {
                    _$("#delete_global_group_membership").disable();
                } else {
                    _$("#delete_global_group_membership").enable();
                }
            }
        });

        _$("#delete_global_group_membership").click(function() {
            var group = oGlobalGroupMembership.getFocusData();

            var requestParams = {
                memberClass : memberId.memberClass,
                memberCode : memberId.memberCode,
                groupCode : group.group_code,
                subsystemCode : group.subsystem
            };

            var confirmParams = {
                member: group.identifier,
                group: requestParams["groupCode"]
            };

            confirm("members.edit.global_group_membership.remove_confirm",
                    confirmParams, function() {
                $.post("members/delete_member_from_global_group", requestParams,
                       function(response) {
                           oGlobalGroupMembership.fnReplaceData(response.data);
                       }, "json");
            });
        });
    }

    function initSubsystemsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "subsystem_code", mRender: util.escape},
            {"mData" : "used_servers", mRender: util.escape} ];

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
                        XROAD_CENTERUI_COMMON.getDetailsLink(each.server_code);

                    usedServerLink.click(function() {
                        openServerDetailsById(each.id);
                    });

                    usedServersColumn.append(usedServerLink);
                });
            }
        };

        oSubsystems = _$(".member_subsystems").dataTable(opts);

        oSubsystems.on("click", "tbody tr", function(ev) {
            if (!oSubsystems.setFocus(0, this)) {
                return;
            }

            var subsystem = oSubsystems.getFocusData();
            var usedServers = subsystem.used_servers;

            if (usedServers.length === 0) {
                _$("#delete_subsystem").enable();
            } else {
                disableSubsystemDeleteButton();
            }
        });

        _$("#delete_subsystem").click(function() {
            var subsystem = oSubsystems.getFocusData();

            var requestParams = {
                memberCode : memberId.memberCode,
                memberClass : memberId.memberClass,
                subsystemCode : subsystem.subsystem_code
            };

            var confirmParams = {
                subsystem : requestParams["subsystemCode"],
                memberCode : requestParams["memberCode"],
                memberClass : requestParams["memberClass"]
            };

            confirm("members.edit.subsystems.remove_confirm", confirmParams,
                    function() {
                $.post("members/delete_subsystem", requestParams,
                       function(response) {
                    oSubsystems.fnReplaceData(response.data);
                }, "json");
            });
        });
    }

    function initUsedServersTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.bDestroy = true;
        opts.aoColumns = [
            {"mData" : "server", mRender: util.escape},
            {"mData" : "client_subsystem_code", mRender: util.escape},
            {"mData" : "owner_name", mRender: util.escape}];

        opts.fnRowCallback = function(nRow, usedServer) {
            var serverCodeColumn = $(nRow).find("td:first");
            var serverCodeLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(usedServer.server);

            serverCodeLink.click(function() {
                openServerDetailsById(usedServer.id);
            });

            serverCodeColumn.empty().append(serverCodeLink);

            var serverOwnerColumn = $(nRow).find("td:eq(2)");
            var serverOwnerLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(usedServer.owner_name);

            serverOwnerLink.click(function() {
                XROAD_MEMBER_EDIT.openById(usedServer.owner_id);
            });

            serverOwnerColumn.empty().append(serverOwnerLink);
        };

        opts.aaSorting = [[ 1, 'asc' ]];

        oUsedServers = _$(".member_used_servers").dataTable(opts);

        oUsedServers.on("click", "tbody tr", function(ev) {
            if (oUsedServers.setFocus(0, this)) {
                _$("#remove_securityserver_client").enable();
            }
        });
    }

    function initManagementRequestsTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = "300px";
        opts.sDom = "t";
        opts.aoColumns = [
            {"mData" : "id"},
            {"mData" : "type"},
            {"mData" : "received"},
            {"mData" : "status"}
        ];

        opts.aaSorting = [[2, 'desc']];

        opts.fnRowCallback = function(nRow, managementRequest) {
            var managementRequestColumn = $(nRow).find("td:first");
            var managementRequestLink =
                XROAD_CENTERUI_COMMON.getDetailsLink(managementRequest.id);
            var updateTablesCallback = function() {
                refreshManagementRequests();
                // TODO: Add callback for members if needed!
            }

            managementRequestLink.click(function() {
                XROAD_REQUEST_EDIT.open(managementRequest, updateTablesCallback);
            });

            managementRequestColumn.empty().append(managementRequestLink);
        };

        opts.sAjaxSource = "members/management_requests";

        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "memberClass",
                "value": memberId.memberClass
            });
            aoData.push({
                "name": "memberCode",
                "value": memberId.memberCode
            });
        };

        oMemberManagementRequests =
            _$(".member_management_requests").dataTable(opts);
    }

    function initUsedServersActions() {
        _$("#register_securityserver_client").click(function() {
            XROAD_MEMBER_EDIT.openUsedServersRegisterDialog(
                memberId, memberName, refreshUsedServers);
        });

        _$("#remove_securityserver_client").click(function() {
            var serverData = oUsedServers.getFocusData();

            var params = {
                client: {
                    name: memberName,
                    memberClass: memberId.memberClass,
                    memberCode: memberId.memberCode,
                    subsystemCode: serverData.client_subsystem_code
                },
                server: {
                    ownerName: serverData.owner_name,
                    ownerClass: serverData.owner_class,
                    ownerCode: serverData.owner_code,
                    serverCode: serverData.server
                }
            };

            XROAD_REQUEST_CLIENT_DELETION.fillRemovableClientData(params);
            XROAD_REQUEST_CLIENT_DELETION.openDialog(function() {
                refreshMemberDataTables();
            });
        });
    }

    function initMemberDetailsActions() {
        _$("#member_edit_delete").click(function() {
            deleteMember(memberId, function() {
                dialog.dialog("close");
            });
        });

        _$("#member_edit_change_name").click(function() {
            XROAD_MEMBER_EDIT.openMemberNameEditDialog(
                memberId, memberName, function(newName) {
                    memberName = newName;
                    _$("#member_edit_name").text(memberName);
                });

            return false;
        });
    }

    function initOwnedServersActions() {
        _$("#add_owned_server").click(function() {
            XROAD_MEMBER_EDIT.openOwnedServersAddDialog(
                memberId, memberName, refreshOwnedServers);
        });
    }

    function initGlobalGroupMembershipsActions() {
        _$("#add_global_group_membership").click(function() {
            XROAD_MEMBER_EDIT.openMemberToGlobalGroupAddDialog(
                memberId, memberName, function(response) {
                    oGlobalGroupMembership.fnReplaceData(response.data);
                });
        });
    }

    function refreshMemberDataTables() {
        refreshOwnedServers();
        refreshGlobalGroupMembership();
        refreshSubsystems();
        refreshUsedServers();
        refreshManagementRequests();
    }

    function refreshDetails() {
        _$("#member_edit_name").text(memberName);
        _$("#member_edit_class").text(memberId.memberClass);
        _$("#member_edit_code").text(memberId.memberCode);
    }

    function refreshOwnedServers(refreshCallback) {
        $.get("members/owned_servers", memberId, function(response) {
            oOwnedServers.fnReplaceData(response.data);

            if (refreshCallback != null) {
                refreshCallback();
            }
        });
    }

    function refreshGlobalGroupMembership() {
        disableGroupMemberDeletionButton();

        $.get("members/global_groups", memberId, function(response) {
            oGlobalGroupMembership.fnReplaceData(response.data);
        });
    }

    function refreshSubsystems() {
        disableSubsystemDeleteButton();

        $.get("members/subsystems", memberId, function(response) {
            oSubsystems.fnReplaceData(response.data);
        });
    }

    function refreshUsedServers() {
        $.get("members/used_servers", memberId, function(response) {
            oUsedServers.fnReplaceData(response.data);
            _$("#remove_securityserver_client").disable();
        });
    }

    function refreshManagementRequests() {
        if (typeof(oMemberManagementRequests) == "undefined") {
            return;
        }

        oMemberManagementRequests.fnReloadAjax();
    }

    function openServerDetailsById(serverId) {
        var params = {
            serverId : serverId
        };

        $.get("securityservers/get_server_by_id", params, function(response) {
            XROAD_SECURITYSERVER_EDIT.open(response.data);
        }, "json");
    }

    function deleteMember(requestParams, callback) {
        refreshOwnedServers(function() {
            var confirmParams = {
                memberCode: requestParams["memberCode"],
                memberClass: requestParams["memberClass"],
                ownedServersMessage: getOwnedServersMessage()
            };

            confirm("members.remove_confirm", confirmParams, function() {
                $.post("members/delete", requestParams, function() {
                    if (typeof callback != "undefined") {
                        callback();
                    }

                    if (typeof XROAD_MEMBERS != "undefined") {
                        XROAD_MEMBERS.redrawMembersTable();
                    }
                }, "json");
            });
        });
    }

    function getOwnedServersMessage() {

        var ownedServers = oOwnedServers.fnGetData();
        var noOfOwnedServers = ownedServers.length;

        if (noOfOwnedServers == 0) {
            return "";
        }

        var serversAsString = ""

        $.each(ownedServers , function(index, each) {
            serversAsString += each.identifier

            if (index < noOfOwnedServers - 1) {
                serversAsString += ", ";
            }
        });

        var translatedMessage = _("members.removable_owned_servers",
            {serversToRemove : serversAsString});

        return translatedMessage ;
    }
}
