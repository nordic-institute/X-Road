var XROAD_GROUP_EDIT = function() {
    var oGroupMembers, oAddableMembers;

    var groupId, groupCode, isReadonly;

    /* -- PUBLIC - START -- */

    function openById(groupId) {
        if (!can("view_group_details")) {
            return;
        }

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
        if (!can("view_group_details")) {
            return;
        }

        groupId = groupData.id;
        groupCode = groupData.code;
        isReadonly = groupData.is_readonly;

        $("#group_details_description").text(groupData.description);

        $("#group_details_dialog").dialog(
            "option", "title", _("groups.details.title", {group: groupCode}));

        $("#group_details_dialog").dialog("open");

        refreshGroupMemberCount();

        disableRemoveSelectedMembersButton();
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function refreshGroupMemberCount() {
        var params = {groupId: groupId};

        $.get("groups/get_member_count", params, function(response) {
            $("#group_details_member_count")
                .text(" (" + response.data.member_count + ")");
        });
    }

    function refreshGlobalGroupsList() {
        try {
            XROAD_GROUPS.updateTable();
        } catch (e) {
            /* Do nothing as XROAD_GROUPS.updateTable may not be
            present everywhere. */
        }
    }

    function refreshGroupMembersTable() {
        refreshGroupMemberCount();
        oGroupMembers.fnReloadAjax();
    }

    /* -- REFRESH DATA - END -- */

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

    /* -- POST REQUESTS - END -- */

    /* -- MISC - START -- */

    function enableRemoveSelectedMembersButton() {
        $("#group_details_remove_selected_members").enable();
    }

    function disableRemoveSelectedMembersButton() {
        $("#group_details_remove_selected_members").disable();
    }

    function getRemovableMemberIds() {
        result = [];

        $.each(oGroupMembers.getSelectedData(), function(index, each) {
            result.push({
                xRoadInstance: each.xroad,
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

    /* -- MISC - END -- */

    /* -- HANDLERS - START -- */

    function initExistingMembersHandlers() {
        $("#group_members tbody tr").live("click", function(ev) {
            if (isReadonly == true) {
                return;
            }

            if (oGroupMembers.setFocus(0, ev.target.parentNode, true)) {
                if (oGroupMembers.hasSelectedRows()) {
                    enableRemoveSelectedMembersButton();
                } else {
                    disableRemoveSelectedMembersButton();
                }
            }
        });

        $("#group_details_remove_selected_members").live("click", function() {
            var params = {
                groupId: groupId,
                removableMemberIds: getRemovableMemberIds()
            };

            confirm("groups.remove.selected_members_confirm", {group: groupCode},
                    function() {
                $.post("groups/remove_selected_members", params, function() {
                    refreshGroupMembersTable();
                    refreshGlobalGroupsList();
                    disableRemoveSelectedMembersButton();
                }, "json");
            });
        });
    }

    function initAddableMembersHandlers() {
        $("#group_addable_members tbody tr:not(.unselectable)")
                .live("click", function(ev) {
            if (oAddableMembers.setFocus(0, ev.target.parentNode, true)) {
                if (oAddableMembers.hasSelectedRows()) {
                    $("#add_selected_members_to_group").enable();
                } else {
                    $("#add_selected_members_to_group").disable();
                }
            }
        });

        $("#show_members_in_search_results").live("change", function() {
            if (oAddableMembers.data("advancedSearch") ||
                oAddableMembers.data("simpleSearch")) {
                oAddableMembers.fnReloadAjax();
            }
        });
    }

    /* -- HANDLERS - END -- */

    /* -- DATA TABLES - START -- */

    function initGroupMembersTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.iDeferLoading = 0;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = 200;
        opts.sDom = "tp";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "member_code", mRender: util.escape },
            { "mData": "member_class", "sWidth": "5em", mRender: util.escape },
            { "mData": "subsystem", mRender: util.escape },
            { "mData": "xroad", "sWidth": "5em", mRender: util.escape },
            { "mData": "type", mRender: util.escape },
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

            if (this.data("advancedSearch")) {
                aoData.push(getAdvancedSearchParams(this));
            }
        };

        opts.aaSorting = [[6, "desc"]];

        oGroupMembers = $("#group_members").dataTable(opts);
    }

    function initAddableMembersTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.iDeferLoading = 1;
        opts.sScrollY = 300;
        opts.sDom = "<'dataTables_header'<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "member_code", mRender: util.escape },
            { "mData": "member_class", mRender: util.escape },
            { "mData": "subsystem", mRender: util.escape },
            { "mData": "xroad", mRender: util.escape },
            { "mData": "type", mRender: util.escape }
        ];
        opts.oTableTools = {
            "sRowSelect": "multi"
        };

        opts.sAjaxSource = "groups/addable_members";
        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "groupId",
                "value": groupId
            },
            {
                "name": "showMembersInSearchResult",
                "value": areMembersShownInSearchResult()
            });

            if (this.data("advancedSearch")) {
                aoData.push(getAdvancedSearchParams(this));
            }
        };

        opts.fnInitComplete = function() {
            $("#group_addable_members_actions")
                .prependTo("#group_addable_members_wrapper .dataTables_header");
        }

        opts.fnRowCallback = function (nRow, member) {
            if (member.belongs_to_group == true) {
                $(nRow).addClass("unselectable");
            }
        }

        opts.aaSorting = [[1, 'desc']];

        oAddableMembers = $('#group_addable_members').dataTable(opts);
    }

    function clearAddableMembers() {
        oAddableMembers.fnSettings().bAjaxDataGet = false;
        oAddableMembers.fnClearTable(false);
        oAddableMembers.fnDraw();
        oAddableMembers.fnSettings().bAjaxDataGet = true;

        oAddableMembers.data("simpleSearch", false);
        oAddableMembers.data("advancedSearch", false);
    }

    function getAdvancedSearchParams(table) {
        var advancedSearch = table.closest(".ui-dialog")
            .find(".advanced_search");

        var select = function(field) {
            return advancedSearch
                .find("[name=group_members_search_" + field + "]");
        };

        return {
            "name": "advancedSearchParams",
            "value": JSON.stringify({
                name: select("name").val(),
                memberCode: select("code").val(),
                memberClass: select("class").val(),
                subsystem: select("subsystem").val(),
                xRoadInstance: select("xroad").val(),
                objectType: select("type").val()
            })
        };
    }

    /* -- DATA TABLES - END -- */

    /* -- DIALOGS - START -- */

    function initDescriptionEditDialog() {
        $("#group_description_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 300,
            width: 500,
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

    function initGroupDetailsDialog(groupCode) {
        $("#group_details_dialog").initDialog({
            autoOpen: false,
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
                oGroupMembers.fnReloadAjax();

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
        });
    }

    function initGroupMembersAddDialog() {
        var dialog = $("#group_members_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 600,
            width: 800,
            buttons: [
                { text: _("common.cancel"),
                    click: function() {
                        $(this).dialog("close");
                    }
                },
                { text: _("groups.add.all"),
                  click: function() {
                      var self = this;

                      $.post("groups/add_all_clients_to_group",
                              getRemainingMembersParams(), function(response) {
                          refreshGroupMembersTable();
                          refreshGlobalGroupsList();
                          $(self).dialog("close");
                      }, "json");
                    }
                },
                { text: _("groups.add.selected"),
                  id: "add_selected_members_to_group",
                  click: function() {
                      var self = this;
                      var params = {
                          groupId: groupId,
                          selectedMembers:
                              oAddableMembers._("tr.row_selected")
                      };

                      $.post("groups/add_members_to_group", params,
                             function(response) {
                          refreshGroupMembersTable();
                          refreshGlobalGroupsList();
                          $(self).dialog("close");
                      }, "json");
                    }
                }
            ]
        });
    }

    function getRemainingMembersParams() {
        return {
            groupId: groupId,
            advancedSearchParams:
                    getAdvancedSearchParams(oAddableMembers)["value"],
            searchable:
                    $("#group_addable_members_simple_search_tab input").val()
        };
    }

    function initGroupMembersSearch() {
        $(".simple_search .search").click(function() {
            var filterValue = $(this).closest(".simple_search")
                .find("[name=group_members_search_all]").val();

            var table = $(this).closest(".ui-dialog")
                .find(".dataTables_scrollBody .dataTable");

            table.data("simpleSearch", true);
            table.data("advancedSearch", false);
            table.dataTable().fnFilter(filterValue);
        });

        $(".advanced_search .search").click(function() {
            var table = $(this).closest(".ui-dialog")
                .find(".dataTables_scrollBody .dataTable");

            table.data("simpleSearch", false);
            table.data("advancedSearch", true);
            table.dataTable().fnFilter("");
        });
    }
    function initTestability() {
        // add data-name attributes to improve testability
        $("#group_description_edit_dialog").parent().attr("data-name", "group_description_edit_dialog");
        $("#group_details_dialog").parent().attr("data-name", "group_details_dialog");
        $("#group_members_add_dialog").parent().attr("data-name", "group_members_add_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }
    /* -- DIALOGS - END -- */

    $(document).ready(function(){
        initDescriptionEditDialog();

        initGroupDetailsDialog();
        initGroupMembersTable();
        initGroupMembersSearch();

        initGroupMembersAddDialog();
        initAddableMembersTable();

        initExistingMembersHandlers();
        initAddableMembersHandlers();

        $("#group_details_delete_group").click(function() {
            var requestParams = {groupId: groupId};

            confirm("groups.remove.confirm", {group: groupCode}, function() {
                $.post("groups/delete_group", requestParams, function() {
                    // TODO: update globalgroupmembership table in member_edit_dialog
                    refreshGlobalGroupsList();

                    $("#group_details_dialog").dialog("close");
                }, "json");
            });
        });

        $("#group_details_add_members").click(function() {
            clearAddableMembers();

            $("#group_members_add_dialog").dialog("option", "title",
                _("groups.details.members_add_title", {group: groupCode}));
            $("#group_members_add_dialog").dialog("open");

            $("#add_selected_members_to_group").disable();
        });
        initTestability();
    });

    return {
        open: open,
        openById: openById
    };
}();
