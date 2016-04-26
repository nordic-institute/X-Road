var XROAD_GROUPS = function() {
    var oGlobalGroups;

    function enableActions() {
        $("#global_group_add").enable();

        if (oGlobalGroups.getFocus()) {
            $(".group-action").enable();
        } else {
            $(".group-action").disable();
        }
    }

    function onDraw() {
        if (!oGlobalGroups) {
            return;
        }

        if (!oGlobalGroups.getFocus()
                || $("#add_group_form:visible").length > 0) {
            $(".group-action").disable();
        } else {
            $(".group-action").enable();
        }
    }

    function getNewGroupData() {
        return {
            code: $("#group_add_code").val(),
            description: $("#group_add_description").val()
        }
    }

    function clearNewGroupData() {
        $("#group_add_code").val("");
        $("#group_add_description").val("");
    }

    function initGroupsTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = XROAD_GROUPS.onDraw;
        opts.bServerSide = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "code", "sWidth": "25%", mRender: util.escape },
            { "mData": "description", mRender: util.escape },
            { "mData": "member_count", "sClass": "center", "sWidth": "5em" },
            { "mData": "updated", "sWidth": "14em" }
        ];

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "groups/global_groups_refresh";

        opts.fnDrawCallback = function() {
            XROAD_CENTERUI_COMMON.updateRecordsCount("groups");
            enableActions();
        }

        var updatedAtColumnNo = 0;
        opts.aaSorting = [ [updatedAtColumnNo,'asc'] ];

        oGlobalGroups = $('#global_groups').dataTable(opts);
        oGlobalGroups.fnSetFilteringDelay(600);
    }

    function updateTable() {
        oGlobalGroups.fnReloadAjax();
    }

    function addGroup(dialog) {
        groupData = getNewGroupData();

        $.post("groups/group_add", groupData, function(response) {
            updateTable();
            $(dialog).dialog("close");
        }, "json");
    }

    $(document).ready(function() {
        $("#add_group_form").hide();

        initGroupsTable();

        enableActions();
        focusInput();

        $("#global_groups tbody td").live("click", function(ev) {
            if (oGlobalGroups.setFocus(0, ev.target.parentNode) &&
                    $("#add_group_form:visible").length == 0) {
                $(".group-action").enable();
            }
        });

        $("#global_groups tbody tr").live("dblclick", function() {
            XROAD_GROUP_EDIT.open(oGlobalGroups.getFocusData());
        });

        $("#group_details").click(function() {
            XROAD_GROUP_EDIT.open(oGlobalGroups.getFocusData());
        });

        $("#group_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                    addGroup(this);
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $("#group_add").click(function() {
            clearNewGroupData();
            $("#group_add_dialog").dialog("open");
        });
    });

    return {
        updateTable: updateTable,
        onDraw: onDraw
    }
}();
