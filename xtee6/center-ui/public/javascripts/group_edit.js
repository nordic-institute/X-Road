var SDSB_GROUP_EDIT = function() {
    var oSpecificGroupMembers, oAddableMembers;
    var groupId, groupCode, isReadonly;

    var skipFillingAddableMembersTable = true;

    var existingMembersAdvancedSearch = false;
    var existingMembersSimpleSearchSelector =
            "#group_details_members_filter > label";
    var existingMembersSearchLinkSelector = "#group_details_members_filter > a";

    var addableMembersAdvancedSearch = false;
    var addableMembersSimpleSearchSelector =
            "#group_addable_members_filter > label";
    var addableMembersSearchLinkSelector = "#group_addable_members_filter > a";
    var addableMembersContentSelector =
        "#group_addable_members_wrapper .dataTables_scroll";

    var addableMembersSearchInputSelector =
        "#group_addable_members_filter input";

    var executingExistingMembersAdvancedSearch = false;

    var executingAddableMembersAdvancedSearch = false;
    var performedAddableMembersSearch = false;

    /* -- PUBLIC - START -- */

    function openById(groupId) {
        var params = {groupId: groupId};
        $.get("groups/find_by_id", params, function(response){
            var groupData = {
                    id: response.data.id,
                    code: response.data.code,
                    description: response.data.description,
                    is_readonly: response.data.is_readonly
            };

            open(groupData)
        }, "json");
    }

    function open(groupData) {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed(
                "groups/can_see_details", function(){
            groupId = groupData.id;
            groupCode = groupData.code;
            isReadonly = groupData.is_readonly;

            fillDescription(groupData.description);

            addGroupMembersSelectAllCheckbox();
            openGroupDetailsDialog(groupCode);
            refreshGroupMemberCount();

            $("#group_existing_members_advanced_search_div").hide();
            disableRemoveSelectedMembersButton();
            existingMembersAdvancedSearch = false;
        });
    }

    /* -- PUBLIC - END -- */

    /* -- INITIALIZATION - START -- */

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
        checkboxDiv.text(_("common.select_all"));

        checkbox.appendTo(checkboxDiv);
        checkboxDiv.insertAfter(previousElement);
    }

    function addShowGroupMembersInSearchResultsCheckbox() {
        var clearerDiv = $("#group_addable_members_wrapper div.clearer");
        var checkboxDiv = $("<div/>");

        var checkbox = $("<input/>", {
            type: "checkbox",
            id: "show_members_in_search_results",
        });

        checkbox.appendTo(checkboxDiv);
        checkbox.after(_("groups.details.members_add_search_show_members"));
        checkboxDiv.insertAfter(clearerDiv);
    }

    /* -- INITIALIZATION - END -- */

    /* -- EXISTING MEMBERS' ADVANCED SEARCH - START -- */

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
        SDSB_CENTERUI_COMMON.showSimpleSearchElement(
                existingMembersSimpleSearchSelector);
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                existingMembersSearchLinkSelector, "common.advanced_search");
        existingMembersAdvancedSearch = false;
    }

    function turnExistingMembersSimpleSearchIntoAdvancedSearch() {
        $(existingMembersSimpleSearchSelector).hide();
        fillExistingMemberAdvancedSearchMemberClassSelect();
        showExistingMembersAdvancedSearch();
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                existingMembersSearchLinkSelector, "common.simple_search");
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
            memberClass: $(
                    "#group_existing_members_advanced_search_class").val(),
            subsystem: $(
                    "#group_existing_members_advanced_search_subsystem").val(),
            sdsbInstance: $(
                    "#group_existing_members_advanced_search_sdsb").val(),
            objectType: $("#group_existing_members_advanced_search_type").val()
        };
    }

    function fillExistingMemberAdvancedSearchMemberClassSelect() {
        $.get("application/member_classes", null, function(response){
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    "group_existing_members_advanced_search_class",
                    response.data);
        }, "json");
    }

    /* -- EXISTING MEMBERS' ADVANCED SEARCH - END -- */

    /* -- ADDABLE MEMBERS' ADVANCED SEARCH - START -- */

    function toggleAddableMembersSearchMode() {
        if (addableMembersAdvancedSearch) {
            turnAddableMembersAdvancedSearchIntoSimpleSearch();
        } else {
            turnAddableMembersSimpleSearchIntoAdvancedSearch();
        }
    }

    function turnAddableMembersAdvancedSearchIntoSimpleSearch() {
        hideAddableMembersAdvancedSearch();
        SDSB_CENTERUI_COMMON.showSimpleSearchElement(
                addableMembersSimpleSearchSelector);
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                addableMembersSearchLinkSelector, "common.advanced_search");
        addableMembersAdvancedSearch = false;
    }

    function turnAddableMembersSimpleSearchIntoAdvancedSearch() {
        $(addableMembersSimpleSearchSelector).hide();
        showAddableMembersAdvancedSearch();
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                addableMembersSearchLinkSelector, "common.simple_search");
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
            memberClass: $(
                    "#group_addable_members_advanced_search_class").val(),
            subsystem: $(
                    "#group_addable_members_advanced_search_subsystem").val(),
            sdsbInstance: $(
                    "#group_addable_members_advanced_search_sdsb").val(),
            objectType: $("#group_addable_members_advanced_search_type").val()
        };
    }

    /* -- ADDABLE MEMBERS' ADVANCED SEARCH - END -- */

    /* -- REFRESH DATA - START -- */

    function refreshGroupMemberCount() {
        var params = {groupId: groupId};

        $.get("groups/get_member_count", params, function(response) {
            updateMemberCountElement(response.data.member_count);
        });
    }

    function updateMemberCountElement(memberCount) {
        var memberCountDiv = $("#group_details_member_count");
        memberCountDiv.empty();
        memberCountDiv.text(" (" + memberCount + ")");
    }

    function refreshGlobalGroupsList() {
        try {
            SDSB_GROUPS.updateTable();
        } catch (e){
            /* Do nothing as SDSB_GROUPS.updateTable may not be
            present everywhere. */
        }
    }

    function refreshGroupMembersTable() {
        refreshGroupMemberCount();
        oSpecificGroupMembers.fnReloadAjax();
    }

    function refreshAddableMembersTable() {
        oAddableMembers.fnReloadAjax();
    }

    function updateSelectAllCheckbox(tableRow, checkboxId) {
        if (!tableRow.hasClass("row_selected")) {
            $("#" + checkboxId).removeAttr("checked");
        }
    }

    function fillDescription(description) {
        $("#group_details_description").text(description);
    }

    function fillSdsbInstanceSelect(selectId) {
        $.get("groups/sdsb_instance_codes", null, function(response) {
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    selectId, response.data);
        }, "json");
    }


    function fillTypeSelect(selectId) {
        $.get("groups/types", null, function(response) {
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    selectId, response.data);
        }, "json");
    }

    /* -- REFRESH DATA - END -- */

    /* -- CLEAR FIELDS - START -- */

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

    /* -- CLEAR FIELDS - END -- */

    /* -- POST REQUESTS - START -- */

    function editDescription(dialog) {
        var newDescription = $("#group_description_edit_value").val();

        params = {
            groupId: groupId,
            description: newDescription
        };

        $.post("groups/group_edit_description", params, function(response) {
            $("#group_details_description").text(newDescription);
            refreshGlobalGroupsList();
            $(dialog).dialog("close");
        }, "json");
    }

    function addSelectedMembersToGroup(dialog) {
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

    function deleteGroup() {
        var requestParams = {groupId: groupId};

        confirm("groups.remove.confirm", {group: groupCode}, function() {
            $.post("groups/delete_group", requestParams, function() {
                $("#group_details_dialog").dialog("close");
                refreshGlobalGroupsList();

                deleteMemberGlobalGroup(groupCode);
            }, "json");
        });
    }

    function removeSelectedMembers() {
        requestParams = {
            groupId: groupId,
            removableMemberIds: getRemovableMemberIds()
        };

        confirm("groups.remove.selected_members_confirm", {group: groupCode},
                function() {
            $.post("groups/remove_selected_members", requestParams,
                    function() {
                refreshGroupMembersTable();
                refreshGlobalGroupsList();
                disableRemoveSelectedMembersButton();
            }, "json");
        });
    }
    /* -- POST REQUESTS - END -- */

    /* -- MISC - START -- */

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
        return $("#show_members_in_search_results").attr("checked") ?
                true : false;
    }

    function startAddingMembers() {
        skipFillingAddableMembersTable = true;
        hideAddableMembersAdvancedSearch();
        openGroupMembersAddDialog();
        disableAddSelectedMembersButton();
    }

    function handleAddableMembersSelectAllCheckboxVisibility() {
        var entries = oAddableMembers.fnGetData().length;
        var selectAllCheckbox =
            $("#group_addable_members_select_all").closest("div");

        if (entries > 0) {
            selectAllCheckbox.show();
        } else {
            selectAllCheckbox.hide();
        }
    }

    function handleActionButtonsVisibility() {
        if (isReadonly == false) {
            $("#group_details_delete_group").show();
            $("#group_details_add_members").show();
            $("#group_details_remove_selected_members").show();
        } else {
            $("#group_details_delete_group").hide();
            $("#group_details_remove_selected_members").hide();
            $("#group_details_add_members").hide();
        }
    }

    /* -- MISC - END -- */

    /* -- HANDLERS - START -- */

    function initExistingMembersHandlers() {
        $("#group_details_members tbody tr").live("click", function(ev) {
            if (isReadonly == true) {
                return;
            }

            if (oSpecificGroupMembers.setFocus(0, ev.target.parentNode, true)) {
                if (oSpecificGroupMembers.hasSelectedRows()) {
                    enableRemoveSelectedMembersButton();
                } else {
                    disableRemoveSelectedMembersButton();
                }
            }

            updateSelectAllCheckbox($(this),
                    "group_details_members_select_all");
        });

        $("#group_details_remove_selected_members").live("click", function() {
            removeSelectedMembers();
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

        $("#group_existing_members_advanced_search_execute").live("click",
                function() {
            executingExistingMembersAdvancedSearch = true;
            refreshGroupMembersTable();
        });

        $("#group_existing_members_advanced_search_clear").live("click",
                function() {
            clearExistingMembersAdvancedSearchData();
        });

    }

    function initAddableMembersHandlers() {
        $("#group_addable_members_advanced_search_execute").live("click",
                function() {
            performedAddableMembersSearch = true;
            executingAddableMembersAdvancedSearch = true;
            refreshAddableMembersTable();
        });


        $("#group_addable_members_advanced_search_clear").live("click",
                function() {
            clearAddableMembersAdvancedSearchData();
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

        $("#group_addable_members tbody tr:not(.group_member)")
                .live("click", function(ev) {
            if (oAddableMembers.setFocus(0, ev.target.parentNode, true)) {
                if (oAddableMembers.hasSelectedRows()) {
                    enableAddSelectedMembersButton();
                } else {
                    disableAddSelectedMembersButton();
                }
            }

            updateSelectAllCheckbox($(this),
                    "group_addable_members_select_all");
        });

        $("#show_members_in_search_results").live("change", function() {
            refreshAddableMembersTable();
        });

        $(addableMembersSearchInputSelector).live("keyup", function() {
            performedAddableMembersSearch = true;
        });
    }

    /* -- HANDLERS - END -- */

    /* -- DATA TABLES - START -- */

    function initGroupMembersTable() {
        var opts = defaultTableOpts();
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = "100px";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": "member_code" },
            { "mData": "member_class", "sWidth": "5em" },
            { "mData": "subsystem" },
            { "mData": "sdsb", "sWidth": "3em" },
            { "mData": "type" },
            { "mData": "added", "sWidth": "13em" }
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
                    "value": JSON.stringify(
                            getExistingMemberAdvancedSearchParams())
                });
                executingExistingMembersAdvancedSearch = false;
            }
        };

        opts.aaSorting = [ [6,'desc'] ];

        oSpecificGroupMembers = $('#group_details_members').dataTable(opts);
        oSpecificGroupMembers.fnSetFilteringDelay(600);
    }

    function initAddableMembersTable() {
        var opts = defaultTableOpts();
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = "300px";
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

        opts.sAjaxSource = "groups/addable_members";
        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "groupId",
                "value": groupId
            },
            {
                "name": "skipFillTable",
                "value": skipFillingAddableMembersTable
                        || !performedAddableMembersSearch
            },
            {
                "name": "showMembersInSearchResult",
                "value": areMembersShownInSearchResult()
            });
            if (executingAddableMembersAdvancedSearch) {
                aoData.push({
                    "name": "advancedSearchParams",
                    "value": JSON.stringify(
                            getAddableMemberAdvancedSearchParams())
                });
                executingAddableMembersAdvancedSearch = false;
            }
        };

        opts.fnDrawCallback = function() {
            skipFillingAddableMembersTable = false;

            handleAddableMembersSelectAllCheckboxVisibility();
        }

        opts.fnInitComplete = function() {
            addAdvancedSearchLink("group_addable_members_filter", function() {
                toggleAddableMembersSearchMode();
            });
            addAddableMembersSelectAllCheckbox();
            addShowGroupMembersInSearchResultsCheckbox();

            handleAddableMembersSelectAllCheckboxVisibility();
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

    /* -- DATA TABLES - END -- */

    /* -- DIALOGS - START -- */

    function initDescriptionEditDialog() {
        $("#group_description_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      editDescription(this);
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $("#group_details_edit_description").live("click", function() {
            var description = $("#group_details_description").text();
            $("#group_description_edit_value").val(description);
            $("#group_description_edit_dialog").dialog("open");
        });
    }

    function openGroupDetailsDialog(groupCode) {
        initGroupMembersTable();

        $("#group_details_dialog").initDialog({
            title: _("groups.details.title", {group: groupCode}),
            modal: true,
            height: 600,
            minHeight: 600,
            minWidth: 850,
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ],
            open: function() {
                addAdvancedSearchLink("group_details_members_filter", function(){
                    toggleExistingMembersSearchMode();
                });

                handleActionButtonsVisibility();
            },
            close: function() {
                oSpecificGroupMembers.fnDestroy();
                isReadonly = false;
            }
        });
    }

    function openGroupMembersAddDialog() {
        $("#group_members_add_dialog").initDialog({
            title: _("groups.details.members_add_title", {group: groupCode}),
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("groups.add.selected_members"),
                    id: "add_selected_members_to_group",
                    click: function() {
                        addSelectedMembersToGroup(this);
                    }
                },
                { text: _("common.close"),
                    click: function() {
                        $(this).dialog("close");
                    }
                },
            ],
            open: function() {
                performedAddableMembersSearch = false;
                initAddableMembersTable();
                $(addableMembersSearchInputSelector).focus();
            },
            close: function() {
                oAddableMembers.fnDestroy();
            }
        }).dialog("open");
    }

    /* -- DIALOGS - END -- */

    $(document).ready(function(){
        initDescriptionEditDialog();
        initExistingMembersHandlers();
        initAddableMembersHandlers();

        $("#group_details_delete_group").live("click", function() {
            deleteGroup();
        });

        $("#group_details_add_members").live("click", function() {
            startAddingMembers();
        });
    });

    return {
        open: open,
        openById: openById
    };
}();
