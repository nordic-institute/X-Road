var oOwnedServers, oGlobalGroupMembership, oSubsystems, oUsedServers, 
        oMemberManagementRequests, oAllServers;

var remainingGlobalGroups = {};

function openMemberDetails(memberData) {
    openDetailsIfAllowed("members/can_see_details", function(){
        fillMemberDetails(memberData);
        $("#member_edit_dialog").dialog("open");
        disableUsedServerDeletionButton();
        showMemberDetailsTables();
    });
}

function showMemberDetailsTables() {
    $('[id^=toggle_member_]').each(function(i, element) {
        $(element).text('-');
        window[$(element).data('refresh')](getEditableMemberId());
    });
};

function hideMemberDetailsTables() {
    $("#member_owned_servers_wrapper").hide();
    $("#member_global_group_membership_wrapper").hide();
    $("#member_subsystems_wrapper").hide();
    $("#member_used_servers_wrapper").hide();
    $("#member_management_requests_wrapper").hide();

    $("#toggle_member_owned_servers").text("+");
    $("#toggle_member_global_group_membership").text("+");
    $("#toggle_member_subsystems").text("+");
    $("#toggle_member_used_servers").text("+");
    $("#toggle_member_management_requests").text("+");

    disableGroupMemberDeletionButton();
    disableUsedServerDeletionButton();
}

function disableUsedServerDeletionButton() {
    $("#remove_securityserver_client").disable();
}


function getEditableMemberId() {
    return {
        memberClass: $("#member_edit_class").val(),
        memberCode: $("#member_edit_code").val()
    };
}

function getEditableMemberData() {
    var editableMemberData = getEditableMemberId();
    editableMemberData.memberName = getMemberName();
    editableMemberData.adminContact = $("#member_edit_admin_contact").val();

    return editableMemberData;
}

function getMemberName() {
    return $("#member_edit_name").val();
}

function fillMemberDetails(member) {
    $("#member_edit_name").val(member.name);
    $("#member_edit_class").val(member.member_class);
    $("#member_edit_code").val(member.member_code);
    $("#member_edit_admin_contact").val(member.admin_contact);
}

function refreshMemberDataTables(params) {
    refreshOwnedServers(params);
    refreshGlobalGroupMembership(params);
    refreshSubsystems(params);
    refreshUsedServers(params);
    refreshMemberManagementRequests(params);
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
    $.get("members/global_groups", params, function(response) {
        updateGlobalGroupMembershipTable(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}


function refreshSubsystems(params, refreshCallback) {
    $.get("members/subsystems", params, function(response) {
        updateSubsystemsTable(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function refreshUsedServers(params, refreshCallback) {
    $.get("members/used_servers", params, function(response) {
        oUsedServers.fnClearTable();
        oUsedServers.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
    });
}

function refreshMemberManagementRequests(params, refreshCallback) {
    $.get("members/management_requests", params, function(response) {
        oMemberManagementRequests.fnClearTable();
        oMemberManagementRequests.fnAddData(response.data);

        if (refreshCallback != null) {
            refreshCallback();
        }
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

function deleteSubsystem(subsystemCode) {
    var member = getEditableMemberId();

    var requestParams = {
         memberCode: member.memberCode,
         memberClass: member.memberClass,
         subsystemCode: subsystemCode
    };

    var confirmParams = [requestParams["subsystemCode"],
                         requestParams["memberCode"],
                         requestParams["memberClass"]];

    confirm("members.remove.subsystem.confirm", confirmParams,
            function() {
        $.post("members/delete_subsystem", requestParams, function(response) {
            updateSubsystemsTable(response.data);
            enableActions();
        }, "json");
    });
}

function disableGroupMemberDeletionButton() {
    $("#delete_global_group_membership").disable();
}

function deleteMemberFromGlobalGroup() {
    var member = getEditableMemberId();
    var group = oGlobalGroupMembership.getFocusData();

    var requestParams = {
        memberClass: member.memberClass,
        memberCode: member.memberCode,
        groupCode: group.group_code,
        subsystemCode: group.subsystem
    };

    var confirmParams = [group.identifier, requestParams["groupCode"]];

    confirm("members.remove.global_group.confirm", confirmParams, function() {
        $.post("members/delete_member_from_global_group", requestParams,
                function(response) {
            updateGlobalGroupMembershipTable(response.data);
        }, "json");
    });
}

function uploadCallbackOwnedServerAuthCert(response) {
    var submitButton = $("#add_owned_server_submit");
    if (response.success) {
        fillOwnedServerAuthCertData(response.data);
        refreshMemberDataTables(getEditableMemberId());

        submitButton.enable();
        $(".auth_cert_details").show();
    } else {
        clearOwnedServerAddData();

        submitButton.disable();
        $(".auth_cert_details").hide();
    }

    showMessages(response.messages);

    return true;
}

function fillOwnedServerAuthCertData(authCertData) {
    $("#auth_cert_file").val("");
    $("#owned_server_authcert_csp").val(authCertData.csp);
    $("#owned_server_authcert_serial_number").val(authCertData.serial_number);
    $("#owned_server_authcert_subject").val(authCertData.subject);
    $("#owned_server_authcert_expires").val(authCertData.expires);
    $("#owned_server_temp_cert_id").text(authCertData.temp_cert_id);
}

function getNewOwnedServerData() {
    return {
        ownerClass: $("#owned_server_owner_class").val(),
        ownerCode: $("#owned_server_owner_code").val(),
        serverCode: $("#owned_server_add_servercode").val(),
        tempCertId: getOwnedServerTempCertId()
    };
}

function getOwnedServerTempCertId() {
    return $("#owned_server_temp_cert_id").text();
}

function clearOwnedServerAddData() {
    $("#add_owned_server_submit").disable();
    $("#owned_server_temp_cert_id").text("");
    $("#owned_server_add_servercode").val("");

    $("#owned_server_authcert_csp").val("");
    $("#owned_server_authcert_serial_number").val("");
    $("#owned_server_authcert_subject").val("");
    $("#owned_server_authcert_expires").val("");
}

/**
 * Fills server details for security server client registration request.
 */
function fillServerDetails() {
    var serverData = oAllServers.getFocusData();

    $("#used_server_owner_name").val(serverData.owner_name);
    $("#used_server_owner_class").val(serverData.owner_class);
    $("#used_server_owner_code").val(serverData.owner_code);
    $("#used_server_server_code").val(serverData.server_code);
}

function getClientRegistrationRequestParams() {
    return {
        memberClass: $("#used_server_class").val(),
        memberCode: $("#used_server_code").val(),
        subsystemCode: $("#used_server_subsystem_code").val(),
        ownerClass: $("#used_server_owner_class").val(),
        ownerCode: $("#used_server_owner_code").val(),
        serverCode: $("#used_server_server_code").val()
    };
}

function clearServerClientAddData() {
    $("#used_server_name").val(""),
    $("#used_server_class").val(""),
    $("#used_server_code").val(""),
    $("#used_server_subsystem_code").val(""),
    $("#used_server_owner_name").val(""),
    $("#used_server_owner_class").val(""),
    $("#used_server_owner_code").val(""),
    $("#used_server_server_code").val("")
}

function fillRemovableClientData() {
    var memberData = getEditableMemberData();

    $("#used_server_remove_name").val(memberData.memberName);
    $("#used_server_remove_class").val(memberData.memberClass);
    $("#used_server_remove_code").val(memberData.memberCode);

    var serverData = oUsedServers.getFocusData();

    $("#used_server_remove_subsystem_code")
            .val(serverData.client_subsystem_code);
    $("#used_server_remove_owner_name").val(serverData.owner_name);
    $("#used_server_remove_owner_class").val(serverData.owner_class);
    $("#used_server_remove_owner_code").val(serverData.owner_code);
    $("#used_server_remove_server_code").val(serverData.server);
}

function getClientRemovingRequestParams() {
    return {
        memberClass: $("#used_server_remove_class").val(),
        memberCode: $("#used_server_remove_code").val(),
        subsystemCode: $("#used_server_remove_subsystem_code").val(),
        ownerClass: $("#used_server_remove_owner_class").val(),
        ownerCode: $("#used_server_remove_owner_code").val(),
        serverCode: $("#used_server_remove_server_code").val()
    };
}

function clearServerClientRemoveData() {
    $("#used_server_remove_name").val(""),
    $("#used_server_remove_class").val(""),
    $("#used_server_remove_code").val(""),
    $("#used_server_remove_subsystem_code").val(""),
    $("#used_server_remove_owner_name").val(""),
    $("#used_server_remove_owner_class").val(""),
    $("#used_server_remove_owner_code").val(""),
    $("#used_server_remove_server_code").val("")
}

function fillServerOwnerData() {
    var memberData = getEditableMemberData();
    $("#used_server_name").val(memberData.memberName);
    $("#used_server_class").val(memberData.memberClass);
    $("#used_server_code").val(memberData.memberCode);
}

function openServerDetailsById(serverId) {
    var params = {serverId: serverId};

    $.post("securityservers/get_server_by_id", params, function(response) {
        openSecurityServerDetails(response.data);
        hideMemberDetailsTables();
    }, "json");
}

function createOwnedServersTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "server" }
    ];
    opts.fnRowCallback = function(nRow, ownedServer) {
        var ownedServerLink = getDetailsLink(ownedServer.server);

        ownedServerLink.click(function(){
            openServerDetailsById(ownedServer.id);
        });

        var serverCodeColumn = $(nRow).find("td:first");
        serverCodeColumn.empty().append(ownedServerLink);
    };

    oOwnedServers = $("#member_owned_servers").dataTable(opts);
}

function createGlobalGroupMembershipsTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "group_code" },
        { "mData": "subsystem" },
        { "mData": "added_to_group" }
    ];

    opts.fnRowCallback = function(nRow, globalGroup) {
        var globalGroupLink = getDetailsLink(globalGroup.group_code);
 
        globalGroupLink.click(function(){
            openGlobalGroupDetailsById(globalGroup.group_id);
        });

        var globalGroupColumn = $(nRow).find("td:first");
        globalGroupColumn.empty().append(globalGroupLink);
    };

    oGlobalGroupMembership =
        $("#member_global_group_membership").dataTable(opts);
}

function createSubsystemsTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "subsystem_code" },
        { "mData": "used_servers" }
    ];
    opts.fnRowCallback = function(nRow, subsystem) {
        usedServers = subsystem.used_servers;
        if (usedServers.length === 0) {
            var codeColumn = $(nRow).find("td:first");
            codeColumn.addClass("no_used_servers");

            var deleteButton = $('<button/>', {
                text: _('members.subsystem.remove'),
                click: function() {
                    deleteSubsystem(subsystem.subsystem_code);
                }
            });
            deleteButton.addClass("subsystem_delete");
            codeColumn.append(deleteButton);
        } else {
            var usedServersColumn = $(nRow).find("td:eq(1)");
            usedServersColumn.empty();

            $.each(usedServers, function(index, each) {
                if (index > 0) {
                    usedServersColumn.append(", ");
                }

                var usedServerLink = getDetailsLink(each.server_code);

                usedServerLink.click(function(){
                    openServerDetailsById(each.id);
                });

                usedServersColumn.append(usedServerLink);
            });
        }
    };

    oSubsystems = $('#member_subsystems').dataTable(opts);
}

function createUsedServersTable() {
    var opts = defaultOpts(onDraw, 1);
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "server" },
        { "mData": "client_subsystem_code" },
        { "mData": "owner_name" }
    ];
    opts.fnRowCallback = function(nRow, usedServer) {
        var serverCodeColumn = $(nRow).find("td:first");
        var serverCodeLink = getDetailsLink(usedServer.server);

        serverCodeLink.click(function(){
            openServerDetailsById(usedServer.id);
        });

        serverCodeColumn.empty().append(serverCodeLink);

        var serverOwnerColumn = $(nRow).find("td:eq(2)");
        var serverOwnerLink = getDetailsLink(usedServer.owner_name);

        serverOwnerLink.click(function(){
            openMemberDetailsById(usedServer.owner_id);
        });

        serverOwnerColumn.empty().append(serverOwnerLink);
    };

    oUsedServers = $("#member_used_servers").dataTable(opts);
}

function createMemberManagementRequestsTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "t";
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

    oMemberManagementRequests = $("#member_management_requests").dataTable(opts);
}

function createAllServersTable() {
    var opts = defaultOpts(null, 1);
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.bFilter = false;
    opts.bPaginate = false;
    opts.aoColumns = [
        { "mData": "owner_name" },
        { "mData": "owner_class" },
        { "mData": "owner_code" },
        { "mData": "server_code" }
    ];

    oAllServers = $("#used_server_search_all").dataTable(opts);
}

function addMemberToGlobalGroup() {
    var params = getEditableMemberId();
    params.subsystemCode = $("#member_to_group_add_select_subsystem").val();
    params.groupCode = $("#member_to_group_add_select_group").val();

    $.post("members/add_member_to_global_group", params, function(response){
        updateGlobalGroupMembershipTable(response.data);
    });
}

function getMemberToGlobalGroupAddDialog() {
    return $("#member_to_group_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        title: _("members.add_member_to_group.title", [getMemberName()]),
        height: 300,
        width: 300,
        buttons: [
            { text: _("ok"),
              click: function() {
                  addMemberToGlobalGroup();
                  $(this).dialog("close");
              }
            },
            { text: _("cancel"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });
}

function fillMemberSubsystemSelect() {
    $.post("members/subsystem_codes", getEditableMemberId(), function(response){
        fillSelectWithEmptyOption("member_to_group_add_select_subsystem",
                response.data);
    });
}

function fillGlobalGroupSelect() {
    var select = $("#member_to_group_add_select_group");
    select.find('option').remove();

    $.post("members/remaining_global_groups", getEditableMemberId(),
            function(response){
        $.each(response.data, function(index, each){
            var code = each.code;
            select.append('<option value="' + code + '">' + code + '</option>');
            remainingGlobalGroups[code] = each.description;
        });

        $("#member_to_group_add_description")
                .val(remainingGlobalGroups[select.val()]);
    });
}

$(document).ready(function() {

    createOwnedServersTable();
    createGlobalGroupMembershipsTable();
    createSubsystemsTable();
    createUsedServersTable();
    createMemberManagementRequestsTable();
    createAllServersTable();


    var all_servers_table_row =
        $("#used_server_search_all tbody td[class!=dataTables_empty]");

    all_servers_table_row.live("click",function(ev) {
        if(oAllServers.setFocus(0, ev.target.parentNode)) {
            $("#member_securityserver_search_select").enable();
        }
    });

    all_servers_table_row.live("dblclick",function() {
        fillServerDetails();
        $("#securityserver_search_dialog").dialog("close");
    });

    $("#member_global_group_membership tbody td").live("click",
            function(ev) {
        if(oGlobalGroupMembership.setFocus(0, ev.target.parentNode)) {
            $("#delete_global_group_membership").enable();
        }
    });

    $("#member_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: "95%",
        buttons: [
            { text: _("close"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#member_name_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 230,
        width: 360,
        buttons: [
            { text: _("ok"),
              click: function() {
                var dialog = this;

                var newName = $("#member_edit_name_new").val();
                memberData = getEditableMemberData();
                memberData.memberName = newName;

                $.post("members/member_edit", memberData, function() {
                    $("#member_edit_name").val(newName);

                    redrawMembersTable();

                    $(dialog).dialog("close");
                }, "json");
              }
            },
            { text: _("cancel"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#member_edit_change_name").live("click", function() {
        var currentName = $("#member_edit_name").val();
        $("#member_edit_name_new").val(currentName);

        $("#member_name_edit_dialog").dialog("open");
        
        // XXX: is there more elegant way to prevent submitting the form?
        return false;
    });

    $("#member_contact_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 230,
        width: 360,
        buttons: [
            { text: _("ok"),
                click: function() {
                    var dialog = this;
                    var newContact = $("#member_edit_contact_new").val();
                    memberData = getEditableMemberData();
                    memberData.adminContact = newContact;

                    $.post("members/member_edit", memberData, function() {
                        $("#member_edit_admin_contact").val(newContact);

                        redrawMembersTable();

                        $(dialog).dialog("close");
                  }, "json");
              }
            },
            { text: _("cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }
        ]
    });

    $("#member_edit_change_contact").live("click", function() {
        var currentContact = $("#member_edit_admin_contact").val();
        $("#member_edit_contact_new").val(currentContact);

        $("#member_contact_edit_dialog").dialog("open");

        // XXX: is there more elegant way to prevent submitting the form?
        return false;
    });

    $("#member_edit_delete").live("click", function() {
        deleteMember(getEditableMemberId());
    });

    $("#delete_global_group_membership").live("click", function() {
        deleteMemberFromGlobalGroup();
    });

    $("#owned_server_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 500,
        width: 500,
        buttons: [
            { text: _("submit"),
                id: "add_owned_server_submit",
                disabled: "disabled",
                click: function() {
                    var dialog = this;
                    var params = getNewOwnedServerData();

                    $.post("members/add_new_owned_server_request", params,
                            function(response) {
                        // TODO: display appropriate success message
                        clearOwnedServerAddData();
                        refreshMemberDataTables(getEditableMemberId());
                        $(dialog).dialog("close");
                    }, "script");
                }
            },
            { text: _("cancel"),
                click: function() {
                    var dialog = this;
                    var params = {tempCertId: getOwnedServerTempCertId()};

                    $.post("members/cancel_new_owned_server_request", params,
                            function(response) {
                        $(dialog).dialog("close");
                    }, "script");
                }
            }
        ]
    });

    $("#add_owned_server").live("click", function() {
        clearOwnedServerAddData();

        $(".auth_cert_details").hide();
        $("#owned_server_add_dialog").dialog("open");

        var memberData = getEditableMemberData();
        $("#owned_server_owner_name").val(memberData.memberName);
        $("#owned_server_owner_class").val(memberData.memberClass);
        $("#owned_server_owner_code").val(memberData.memberCode);
    });

    $("#add_global_group_membership").live("click", function() {
        fillMemberSubsystemSelect();
        fillGlobalGroupSelect();
        getMemberToGlobalGroupAddDialog().dialog("open");
    });

    $("#owned_server_cert_upload").live("click", function() {
        $("#auth_cert_upload").submit();
        // function uploadCallbackOwnedServerAuthCert manages post-submission
        // activities on UI part
    });

    $("#securityserver_client_register_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 630,
        width: 560,
        buttons: [
            { text: _("submit"),
                click: function() {
                    var dialog = this;
                    var params = getClientRegistrationRequestParams();

                    $.post("members/add_new_server_client_request", params,
                            function() {
                        // TODO: display appropriate success message
                        clearServerClientAddData();
                        refreshMemberDataTables(getEditableMemberId());
                        $(dialog).dialog("close");
                    }, "script");

                    $(dialog).dialog("close");
                }
            },
            { text: _("cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }
        ]
    });

    $("#register_securityserver_client").live("click", function() {
        clearServerClientAddData();
        $("#securityserver_client_register_dialog").dialog("open");

        fillServerOwnerData();
    });

    $("#securityserver_search_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 430,
        width: 860,
        buttons: [
            { text: _("select"),
              disabled: "disabled",
              id: "member_securityserver_search_select",
              click: function() {
                  var dialog = this;

                  fillServerDetails();

                  $(dialog).dialog("close");
              }
            },
            { text: _("cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }
        ]
    });

    $("#used_server_server_search").live("click", function() {
        $("#securityserver_search_dialog").dialog("open");

        $.post("members/get_all_securityservers", null, function(response) {
            oAllServers.fnClearTable();
            oAllServers.fnAddData(response.data);
            enableActions();
        }, "json");
    });

    $("#member_used_servers tbody tr").live("click", function(ev) {
        if(oUsedServers.setFocus(0, ev.target.parentNode)){
            $("#remove_securityserver_client").enable();
        }
    });

    $("#securityserver_client_remove_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 430,
        width: 460,
        buttons: [
            { text: _("submit"),
              click: function() {
                  var dialog = this;

                  var params = getClientRemovingRequestParams();

                  $.post("members/delete_server_client_request", params,
                          function() {
                      // TODO: display appropriate success message
                      clearServerClientRemoveData();
                      refreshMemberDataTables(getEditableMemberId());
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

    $("#remove_securityserver_client").live("click", function() {
        $("#securityserver_client_remove_dialog").dialog("open");
        
        fillRemovableClientData();
    });

    $("#toggle_member_owned_servers").live("click", function() {
        var toggleButton = $(this);
        var tableId = "member_owned_servers";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshOwnedServers(getEditableMemberId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_member_global_group_membership").live("click", function() {
        var toggleButton = $(this);
        var tableId = "member_global_group_membership";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
            disableGroupMemberDeletionButton();
        } else {
            refreshGlobalGroupMembership(getEditableMemberId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_member_subsystems").live("click", function() {
        var toggleButton = $(this);
        var tableId = "member_subsystems";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshSubsystems(getEditableMemberId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_member_used_servers").live("click", function() {
        var toggleButton = $(this);
        var tableId = "member_used_servers";

        if (isTableVisible(tableId)) {
            disableUsedServerDeletionButton();
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshUsedServers(getEditableMemberId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#toggle_member_management_requests").live("click", function() {
        var toggleButton = $(this);
        var tableId = "member_management_requests";

        if (isTableVisible(tableId)) {
            toggleTableVisibility(toggleButton, tableId);
        } else {
            refreshMemberManagementRequests(getEditableMemberId(), function() {
                toggleTableVisibility(toggleButton, tableId);
            });
        }
    });

    $("#member_to_group_add_select_group").live("change", function() {
        $("#member_to_group_add_description")
                .val(remainingGlobalGroups[this.value]);
    });
});