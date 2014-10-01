var SDSB_MEMBERS = function() {
    var oMembers;

    function enableActions() {
        $("#add").enable();
        if (oMembers.getFocus()) {
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
        enableActions();
    }

    function initMembersTable() {
        var opts = defaultTableOpts();
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
            SDSB_CENTERUI_COMMON.updateRecordsCount("members");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "members/members_refresh";

        opts.aaSorting = [ [0,'asc'] ];

        oMembers = $('#members').dataTable(opts);
        oMembers.fnSetFilteringDelay(600);
    }

    function addMember(dialog) {
        newMemberToOpen = getNewMemberData();

        $.post("members/member_add", newMemberToOpen, function(response) {
            redrawMembersTable();
            $(dialog).dialog("close");

            SDSB_MEMBER_EDIT.open(response.data);
        }, "json");
    }

    $(document).ready(function() {
        $("#add_form").hide();

        initMembersTable();

        enableActions();
        focusInput();

        $("#members tbody td[class!=dataTables_empty]").live("click",
                function(ev) {
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
                { text: _("common.ok"),
                  click: function() {
                    addMember(this);
                  }
                },
                { text: _("common.cancel"),
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
            SDSB_MEMBER_EDIT.open(oMembers.getFocusData());
        });

        $("#members tbody td").live("click", function(ev) {
            if (oMembers.setFocus(0, ev.target.parentNode) &&
                $("#add_form:visible").length == 0) {
                $(".member-action").enable();
            }
        });

        $("#member_details").click(function() {
            SDSB_MEMBER_EDIT.open(oMembers.getFocusData());
        });

        $("#member_delete").click(function() {
              SDSB_CENTERUI_COMMON.deleteMember(reqParams(oMembers.getFocus()));
        });
    });

    return {
        redrawMembersTable: redrawMembersTable,
    };
}();