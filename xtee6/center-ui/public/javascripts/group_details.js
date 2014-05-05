var oSpecificGroupMembers, oAddableMembers;
var groupId, groupCode;

var skipFillingAddableMembersTable = true;

var existingMembersAdvancedSearch = false;
var existingMembersSimpleSearchSelector = 
        "#group_details_members_filter > label";
var existingMembersSearchLinkSelector = "#group_details_members_filter > a";

var addableMembersAdvancedSearch = false;
var addableMembersSimpleSearchSelector = 
        "#group_addable_members_filter > label";
var addableMembersSearchLinkSelector = "#group_addable_members_filter > a";

var executingExistingMembersAdvancedSearch = false;

var executingAddableMembersAdvancedSearch = false;

// -- Initialization functions - start ---

function openGlobalGroupDetailsById(groupId) {
    var params = {groupId: groupId};
    $.post("groups/find_by_id", params, function(response){
        var groupData = {
           id: response.data.id,
           code: response.data.code,
           description: response.data.description
        };

        openGlobalGroupDetails(groupData)
    }, "json");
}

function openGlobalGroupDetails(groupData) {
    openDetailsIfAllowed("groups/can_see_details", function(){
        groupId = groupData.id;
        groupCode = groupData.code;

        fillDescription(groupData.description);
        initGroupMembersTable();
        addAdvancedSearchLink("group_details_members_filter", function(){
            toggleExistingMembersSearchMode();
        });

        addGroupMembersSelectAllCheckbox();
        openGroupDetailsDialog(groupCode);
        refreshGroupMemberCount();

        $("#group_existing_members_advanced_search_div").hide();
        disableRemoveSelectedMembersButton();
        existingMembersAdvancedSearch = false;
    });
}

function fillDescription(description) {
    $("#group_details_description").val(description);
}

function addGroupMembersSelectAllCheckbox() {
    addSelectAllCheckbox(
            "group_details_members_filter",
            "group_details_members_select_all");
}

function addAddableMembersSelectAllCheckbox() {
    addSelectAllCheckbox(
            "group_addable_members_filter",
            "group_addable_members_select_all");
}

function addSelectAllCheckbox(previousElementId, checkboxId) {
    var previousElement = $("#" + previousElementId);
    var checkboxDiv = $("<div/>");

    var checkbox = $("<input/>", {
        type: "checkbox",
        id: checkboxId,
    });

    checkboxDiv.addClass("right");
    checkboxDiv.text(_("select_all"));

    checkbox.appendTo(checkboxDiv);
    checkboxDiv.insertAfter(previousElement);
}

function addShowGroupMembersInSearchResultsCheckbox() {
    var clearerDiv = $("#group_addable_members_wrapper div.clearer");
    var checkboxDiv = $("<div/>", {class: "upper_spaced"});

    var checkbox = $("<input/>", {
        type: "checkbox",
        id: "show_members_in_search_results",
    });

    checkbox.appendTo(checkboxDiv);
    checkbox.after(_("groups.details.members.add.search.show_members"));
    checkboxDiv.insertAfter(clearerDiv);
}

function initGroupMembersTable() {
    var opts = defaultOpts(onDraw, 100);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.bDestroy = true;
    opts.bScrollCollapse = true;
    opts.bScrollInfinite = true;
    opts.sScrollY = "300px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "name" },
        { "mData": "member_code" },
        { "mData": "member_class" },
        { "mData": "subsystem" },
        { "mData": "sdsb" },
        { "mData": "type" },
        { "mData": "added" }
    ];
    opts.oTableTools = {
            "sRowSelect": "multi"
    }

    opts.sAjaxSource = "groups/group_members";
    opts.fnServerParams = function(aoData) {
        aoData.push({
            "name": "groupId",
            "value": groupId 
        });
        if (executingExistingMembersAdvancedSearch) {
            aoData.push({
                "name": "advancedSearchParams",
                "value": JSON.stringify(getExistingMemberAdvancedSearchParams())
            });
            executingExistingMembersAdvancedSearch = false;
        }
    };

    opts.aaSorting = [ [6,'desc'] ];

    oSpecificGroupMembers = $('#group_details_members').dataTable(opts);
    oSpecificGroupMembers.fnSetFilteringDelay(600);
}

function initAddableMembersTable() {
    var opts = defaultOpts(onDraw, 100);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.bDestroy = true;
    opts.bScrollCollapse = true;
    opts.bScrollInfinite = true;
    opts.sScrollY = "300px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "name" },
        { "mData": "member_code" },
        { "mData": "member_class" },
        { "mData": "subsystem" },
        { "mData": "sdsb" },
        { "mData": "type" }
    ];
    opts.oTableTools = {
            "sRowSelect": "multi"
    }

    opts.oLanguage.sZeroRecords = _("groups.details.members.add.search.zero");

    opts.sAjaxSource = "groups/addable_members";
    opts.fnServerParams = function(aoData) {
        aoData.push({
            "name": "groupId",
            "value": groupId 
        },
        {
            "name": "skipFillTable",
            "value": skipFillingAddableMembersTable
        },
        {
            "name": "showMembersInSearchResult",
            "value": areMembersShownInSearchResult()
        });
        if (executingAddableMembersAdvancedSearch) {
            aoData.push({
                "name": "advancedSearchParams",
                "value": JSON.stringify(getAddableMemberAdvancedSearchParams())
            });
            executingAddableMembersAdvancedSearch = false;
        }
    };

    opts.fnDrawCallback = function() {
        skipFillingAddableMembersTable = false;
    }

    opts.fnInitComplete = function() {
        addAdvancedSearchLink("group_addable_members_filter", function() {
            toggleAddableMembersSearchMode();
        });
        addAddableMembersSelectAllCheckbox();
        addShowGroupMembersInSearchResultsCheckbox();
    }

    opts.fnRowCallback = function (nRow, member) {
        if (member.belongs_to_group == true) {
            $(nRow).addClass("group_member");
        }
    }

    opts.aaSorting = [ [1,'desc'] ];

    oAddableMembers = $('#group_addable_members').dataTable(opts);
    oAddableMembers.fnSetFilteringDelay(600);
}

function openGroupDetailsDialog(groupCode) {
    $("#group_details_dialog").initDialog({
        title: _("groups.details.title", [groupCode]),
        autoOpen: false,
        modal: true,
        height: 620,
        width: "95%",
        buttons: [
            { text: _("close"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    }).dialog("open");
}

function openGroupMembersAddDialog() {
    $("#group_members_add_dialog").initDialog({
        title: _("groups.details.members.add.title", [groupCode]),
        autoOpen: false,
        modal: true,
        height: 500,
        width: "95%",
        buttons: [
            { text: _("groups.add.selected_members"),
                id: "add_selected_members_to_group",
                click: function() {
                    var dialog = this;
                    var selectedMembers = oAddableMembers.getSelectedData();
                    var params = {
                        selectedMembers: selectedMembers,
                        groupId: groupId
                    };

                    $.post("groups/add_members_to_group", params, function(response) {
                        refreshGroupMembersTable();
                        refreshGlobalGroupsList();
                        $(dialog).dialog("close");
                    }, "json");
                }
            },
            { text: _("close"),
                click: function() {
                    $(this).dialog("close");
                }
            },
        ]
    }).dialog("open");
}

// -- Initialization functions - end ---

function enableRemoveSelectedMembersButton() {
    $("#group_details_remove_selected_members").enable();
}

function disableRemoveSelectedMembersButton() {
    $("#group_details_remove_selected_members").disable();
}

function enableAddSelectedMembersButton() {
    $("#add_selected_members_to_group").enable();
}

function disableAddSelectedMembersButton() {
    $("#add_selected_members_to_group").disable();
}

function refreshGroupMemberCount() {
    var params = {groupId: groupId};

    $.post("groups/get_member_count", params, function(response) {
        updateMemberCountElement(response.data.member_count);
    });
}

function updateMemberCountElement(memberCount) {
    var memberCountDiv = $("#group_details_member_count");
    memberCountDiv.empty();
    memberCountDiv.text(" (" + memberCount + ")");
}

// -- Logic related to existing members' advanced search - start ---

/**
 * Toggles between simple and advanced search.
 */
function toggleExistingMembersSearchMode() {
    if (existingMembersAdvancedSearch) {
        turnExistingMembersAdvancedSearchIntoSimpleSearch();
    } else {
        turnExistingMembersSimpleSearchIntoAdvancedSearch();
    }
}

function turnExistingMembersAdvancedSearchIntoSimpleSearch() {
    hideExistingMembersAdvancedSearch();
    showSimpleSearchElement(existingMembersSimpleSearchSelector);
    setSearchLinkText(existingMembersSearchLinkSelector, "advanced_search");
    existingMembersAdvancedSearch = false;
}

function turnExistingMembersSimpleSearchIntoAdvancedSearch() {
    $(existingMembersSimpleSearchSelector).hide();
    fillExistingMemberAdvancedSearchMemberClassSelect();
    showExistingMembersAdvancedSearch();
    setSearchLinkText(existingMembersSearchLinkSelector, "simple_search");
    existingMembersAdvancedSearch = true;
}

function hideExistingMembersAdvancedSearch() {
    $("#group_existing_members_advanced_search_div").hide();
}

function showExistingMembersAdvancedSearch() {
    clearExistingMembersAdvancedSearchData();

    fillSdsbInstanceSelect("group_existing_members_advanced_search_sdsb");
    fillTypeSelect("group_existing_members_advanced_search_type");

    $("#group_existing_members_advanced_search_div").show();
}

function clearExistingMembersAdvancedSearchData() {
    $("#group_existing_members_advanced_search_name").val("");
    $("#group_existing_members_advanced_search_code").val("");
    $("#group_existing_members_advanced_search_class").val("");
    $("#group_existing_members_advanced_search_subsystem").val("");
    $("#group_existing_members_advanced_search_sdsb").val("");
    $("#group_existing_members_advanced_search_type").val("");
}

function getExistingMemberAdvancedSearchParams() {
    return {
        name: $("#group_existing_members_advanced_search_name").val(),
        memberCode: $("#group_existing_members_advanced_search_code").val(),
        memberClass: $("#group_existing_members_advanced_search_class").val(),
        subsystem: $("#group_existing_members_advanced_search_subsystem").val(),
        sdsbInstance: $("#group_existing_members_advanced_search_sdsb").val(),
        objectType: $("#group_existing_members_advanced_search_type").val()
    };
}

function fillExistingMemberAdvancedSearchMemberClassSelect() {
    $.post("application/member_classes", null, function(response){
        fillSelectWithEmptyOption("group_existing_members_advanced_search_class",
                response.data);
    }, "json");
}

// -- Logic related to existing members' advanced search - end ---

// -- Logic related to addable members' advanced search - start ---

function toggleAddableMembersSearchMode() {
    if (addableMembersAdvancedSearch) {
        turnAddableMembersAdvancedSearchIntoSimpleSearch();
    } else {
        turnAddableMembersSimpleSearchIntoAdvancedSearch();
    }
}

function turnAddableMembersAdvancedSearchIntoSimpleSearch() {
    hideAddableMembersAdvancedSearch();
    showSimpleSearchElement(addableMembersSimpleSearchSelector);
    setSearchLinkText(addableMembersSearchLinkSelector, "advanced_search");
    addableMembersAdvancedSearch = false;
}

function turnAddableMembersSimpleSearchIntoAdvancedSearch() {
    $(addableMembersSimpleSearchSelector).hide();
    showAddableMembersAdvancedSearch();
    setSearchLinkText(addableMembersSearchLinkSelector, "simple_search");
    addableMembersAdvancedSearch = true;
}

function hideAddableMembersAdvancedSearch(){
    $("#group_addable_members_advanced_search_fieldset").hide();
}

function showAddableMembersAdvancedSearch() {
    clearAddableMembersAdvancedSearchData();

    fillSdsbInstanceSelect("group_addable_members_advanced_search_sdsb");
    fillTypeSelect("group_addable_members_advanced_search_type");

    $("#group_addable_members_advanced_search_fieldset").show();
}

function clearAddableMembersAdvancedSearchData() {
    $("#group_addable_members_advanced_search_name").val("");
    $("#group_addable_members_advanced_search_code").val("");
    $("#group_addable_members_advanced_search_class").val("");
    $("#group_addable_members_advanced_search_subsystem").val("");
    $("#group_addable_members_advanced_search_sdsb").val("");
    $("#group_addable_members_advanced_search_type").val("");
}

function getAddableMemberAdvancedSearchParams() {
    return {
        name: $("#group_addable_members_advanced_search_name").val(),
        memberCode: $("#group_addable_members_advanced_search_code").val(),
        memberClass: $("#group_addable_members_advanced_search_class").val(),
        subsystem: $("#group_addable_members_advanced_search_subsystem").val(),
        sdsbInstance: $("#group_addable_members_advanced_search_sdsb").val(),
        objectType: $("#group_addable_members_advanced_search_type").val()
    };
}

// -- Logic related to addable members' advanced search - end ---

function refreshGlobalGroupsList() {
    try {
        updateGlobalGroupsTable();
    } catch (e){
        // Do nothing as updateGlobalGroupsTable may not be
        // present everywhere.
    }
}

function refreshGroupMembersTable() {
    refreshGroupMemberCount();
    oSpecificGroupMembers.fnReloadAjax();
}

function deleteMemberGlobalGroup(groupCode) {
    if (typeof oGlobalGroupMembership  === 'undefined') {
        return;
    }

    $.each(oGlobalGroupMembership.fnGetData(), function(index, each) {
        if (each.group_code == groupCode) {
            oGlobalGroupMembership.fnDeleteRow(index);
        }
    });
}

function refreshAddableMembersTable() {
    oAddableMembers.fnReloadAjax();
}

function getRemovableMemberIds() {
    result = [];

    $.each(oSpecificGroupMembers.getSelectedData(), function(index, each) {
        result.push({
            sdsbInstance: each.sdsb,
            memberClass: each.member_class,
            memberCode: each.member_code,
            subsystemCode: each.subsystem
        });
    });

    return result;
}

function areMembersShownInSearchResult() {
    return $("#show_members_in_search_results").attr("checked") ? true : false;
}

function updateSelectAllCheckbox(tableRow, checkboxId) {
    if (!tableRow.hasClass("row_selected")) {
        $("#" + checkboxId).removeAttr("checked");
    }
}

$(document).ready(function(){
    $("#group_description_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 300,
        width: 300,
        buttons: [
            { text: _("ok"),
              click: function() {
                var dialog = this;
                var newDescription = $("#group_description_edit_value").val();

                params = {
                    groupId: groupId,
                    description: newDescription
                };

                $.post("groups/group_edit_description", params, function(response) {
                    $("#group_details_description").val(newDescription);
                    refreshGlobalGroupsList();
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

    $("#group_details_members tbody tr").live("click", function(ev) {
        if (oSpecificGroupMembers.setFocus(0, ev.target.parentNode, true)) {
            if (oSpecificGroupMembers.hasSelectedRows()) {
                enableRemoveSelectedMembersButton();
            } else {
                disableRemoveSelectedMembersButton();
            }
        }

        updateSelectAllCheckbox($(this), "group_details_members_select_all");
    });

    $("#group_addable_members tbody tr:not(.group_member)")
            .live("click", function(ev) {
        if (oAddableMembers.setFocus(0, ev.target.parentNode, true)) {
            if (oAddableMembers.hasSelectedRows()) {
                enableAddSelectedMembersButton();
            } else {
                disableAddSelectedMembersButton();
            }
        }

        updateSelectAllCheckbox($(this), "group_addable_members_select_all");
    });

    $("#group_details_edit_description").live("click", function() {
        var description = $("#group_details_description").val();
        $("#group_description_edit_value").val(description);
        $("#group_description_edit_dialog").dialog("open");
    });

    $("#group_details_delete_group").live("click", function() {
        var requestParams = {groupId: groupId};

        confirm("groups.remove.confirm", [groupCode], function() {
            $.post("groups/delete_group", requestParams, function() {
                $("#group_details_dialog").dialog("close");
                refreshGlobalGroupsList();

                deleteMemberGlobalGroup(groupCode);
                // XXX: This may cause side-effects when opened from other views
                // than groups list.
                enableActions();
            }, "json");
        });
    });

    $("#group_details_remove_selected_members").live("click", function() {
        requestParams = {
            groupId: groupId,
            removableMemberIds: getRemovableMemberIds()
        };

        confirm("groups.remove.selected_members.confirm", [groupCode], function() {
            $.post("groups/remove_selected_members", requestParams, function() {
                refreshGroupMembersTable();
                refreshGlobalGroupsList();
                disableRemoveSelectedMembersButton();
            }, "json");
        });
    });

    $("#group_details_members_select_all").live("change", function() {
        var existingMembersSelector = 
            $("#group_details_members tbody tr:not(.group_member)");
        if ($(this).attr("checked")) {
            existingMembersSelector.addClass("row_selected");
        } else {
            existingMembersSelector.removeClass("row_selected");
        }
    });

    $("#group_details_add_members").live("click", function() {
        skipFillingAddableMembersTable = true;
        hideAddableMembersAdvancedSearch();
        openGroupMembersAddDialog();
        disableAddSelectedMembersButton();
        initAddableMembersTable();
    });

    $("#group_addable_members_select_all").live("change", function() {
        var addableMembersSelector = 
            $("#group_addable_members tbody tr:not(.group_member)");
        if ($(this).attr("checked")) {
            enableAddSelectedMembersButton();
            addableMembersSelector.addClass("row_selected");
        } else {
            disableAddSelectedMembersButton();
            addableMembersSelector.removeClass("row_selected");
        }
    });

    $("#show_members_in_search_results").live("change", function() {
        refreshAddableMembersTable();
    });

    // -- Existing member advanced search buttons - start

    $("#group_existing_members_advanced_search_execute").live("click",
            function() {
        executingExistingMembersAdvancedSearch = true;
        refreshGroupMembersTable();
    });

    $("#group_existing_members_advanced_search_clear").live("click",
            function() {
        clearExistingMembersAdvancedSearchData();
    });

    // -- Existing member advanced search buttons - end

    // -- Addable member advanced search buttons - start

    $("#group_addable_members_advanced_search_execute").live("click",
            function() {
        executingAddableMembersAdvancedSearch = true;
        refreshAddableMembersTable();
    });


    $("#group_addable_members_advanced_search_clear").live("click",
            function() {
        clearAddableMembersAdvancedSearchData();
    });

    // -- Addable member advanced search buttons - end
});
