var oGlobalGroups;

function enableActions() {
    $("#global_group_add").enable();
    if (oGlobalGroups.setFocus()) {
        $(".group-action").enable();
    } else {
        $(".group-action").disable();
    }
}

function onDraw() {
    if (!oGlobalGroups) return;
    if (!oGlobalGroups.getFocus() || $("#add_group_form:visible").length > 0) {
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
    var opts = defaultOpts(onDraw, 16);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.sScrollY = "400px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "code" },
        { "mData": "description" },
        { "mData": "member_count" },
        { "mData": "updated" }
    ];

    opts.bScrollInfinite = true;
    opts.sAjaxSource = action("global_groups_refresh");

    opts.fnDrawCallback = function() {
        updateRecordsCount("groups");
        enableActions();
    }

    var updatedAtColumnNo = 3;
    opts.aaSorting = [ [updatedAtColumnNo,'desc'] ];

    oGlobalGroups = $('#global_groups').dataTable(opts);
    oGlobalGroups.fnSetFilteringDelay(600);
}

function updateGlobalGroupsTable() {
    oGlobalGroups.fnReloadAjax();
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
        openGlobalGroupDetails(oGlobalGroups.getFocusData());
    });

    $("#group_details").click(function() {
        openGlobalGroupDetails(oGlobalGroups.getFocusData());
    });

    $("#group_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 300,
        width: 300,
        buttons: [
            { text: _("ok"),
              click: function() {
                var dialog = this;
                groupData = getNewGroupData();

                $.post(action("group_add"), groupData, function(response) {
                    updateGlobalGroupsTable();
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

    $("#group_add").click(function() {
        clearNewGroupData();
        $("#group_add_dialog").dialog("open");
    });
});

