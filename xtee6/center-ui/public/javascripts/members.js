var oMembers;

function enableActions() {
    $("#add").enable();
    if (oMembers.setFocus()) {
        $(".member-action").enable();
    } else {
        $(".member-action").disable();
    }
}

function reqParams(nRow) {
    var row = oMembers.fnGetData(nRow)
    return {
        memberClass: row["member_class"],
        memberCode: row["member_code"]
    };
}

function onDraw() {
    if (!oMembers) return;
    if (!oMembers.getFocus() || $("#add_form:visible").length > 0) {
        $(".member-action").disable();
    } else {
        $(".member-action").enable();
    }
}

// XXX Is there shorter and more elegant way to do this?
function getNewMemberData() {
    return {
        memberName: $("#member_add_name").val(),
        memberClass: $("#member_add_class").val(),
        memberCode: $("#member_add_code").val(),
        adminContact: $("#member_add_admin_contact").val()
    };
}

function clearNewMemberData() {
    $("#member_add_name").val("");
    $("#member_add_class").val("");
    $("#member_add_code").val("");
    $("#member_add_admin_contact").val("");
}

function redrawMembersTable(){
    oMembers.fnReloadAjax();
}

function deleteMember(requestParams) {
    var confirmParams = [requestParams["memberCode"],
                         requestParams["memberClass"]];

    confirm("members.remove.confirm", confirmParams, function() {
        $.post(action("delete"), requestParams, function() {
            $("#member_edit_dialog").dialog("close");
            redrawMembersTable();
            enableActions();
        }, "json");
    });
}

function initMembersTable() {
    var opts = defaultOpts(onDraw, 16);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.sScrollY = "400px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "name" },
        { "mData": "member_class" },
        { "mData": "member_code" }
    ];

    opts.fnDrawCallback = function() {
        updateRecordsCount("members");
        enableActions();
    }

    opts.bScrollInfinite = true;
    opts.sAjaxSource = action("members_refresh");
    
    opts.aaSorting = [ [0,'desc'] ];

    oMembers = $('#members').dataTable(opts);
    oMembers.fnSetFilteringDelay(600);
}

$(document).ready(function() {
    $("#add_form").hide();

    initMembersTable();

    enableActions();
    focusInput();

    $("#members tbody td[class!=dataTables_empty]").live("click", function(ev) {
        if (oMembers.setFocus(0, ev.target.parentNode) &&
                $("#add_form:visible").length == 0) {
            $(".member-action").enable();
        }
    });

    $("#add").ajaxError(function(ev, xhr) {
        $("#save_ok, #save_cancel").enable();
        $("#fullName, #shortName").removeAttr("readonly");
    });

    $("#member_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 430,
        width: 360,
        buttons: [
            { text: _("ok"),
              click: function() {
                var dialog = this;
                memberData = getNewMemberData();

                $.post(action("member_add"), memberData, function() {
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

    $("#member_add").click(function() {
        clearNewMemberData();
        $("#member_add_dialog").dialog("open");
    });

    $("#members tbody tr").live("dblclick", function() {
        openMemberDetails(oMembers.getFocusData());
    });

    $("#members tbody td").live("click", function(ev) {
        if (oMembers.setFocus(0, ev.target.parentNode) &&
            $("#add_form:visible").length == 0) {
            $(".member-action").enable();
        }
    });

    $("#member_details").click(function() {
        openMemberDetails(oMembers.getFocusData());
    });

    $("#member_delete").click(function() {
        deleteMember(reqParams(oMembers.getFocus()));
    });
});
