var oSubjects;

function enableAclActions() {
    if ($("#subjects .row_selected").length > 0) {
        $("#subjects_remove_selected").enable();
    } else {
        $("#subjects_remove_selected").disable();
    }
}

function initAclDialogs() {
    $("#service_acl_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 490,
        width: "95%",
        open: function() { oSubjects.fnAdjustColumnSizing(); },
        buttons: [
            { text: "Close",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#service_acl").click(function() {
        $("#service_acl_dialog #service").html("");

        $.each(oServices.fnGetData(), function(idx, val) {
            if (val.wsdl) {
                $("#service_acl_dialog #service").append(
                    "<option disabled>" + val.wsdl_id + "</option>");
            } else {
                $("#service_acl_dialog #service").append(
                    "<option style='padding-left: 0.5em;' value='"
                        + val.name + "'>" + val.name + "</option>");
                $("#service_acl_dialog #service option:last")
                    .data("wsdl_id", val.wsdl_id);
                $("#service_acl_dialog #service option:last")
                    .data("title", val.title);
            }
        });

        $("#service_acl_dialog #service")
            .val(oServices.getFocusData().name).change();
    });
}

function getSubjectsAddParams() {
    var service = $("#service_acl_dialog #service option:selected");
    return {
        client_id: $("#details_client_id").val(),
        wsdl_id: service.data("wsdl_id"),
        name: service.val(),
        subject_ids: []
    };
}

function onSubjectsAddSuccess(response) {
    oSubjects.fnClearTable();
    oSubjects.fnAddData(response.data);
}

$(document).ready(function() {
    var opts = scrollableTableOpts(null, 1, 230);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "name_description" },
        { "mData": "member_class" },
        { "mData": "member_group_code" },
        { "mData": "subsystem_code" },
        { "mData": "sdsb" },
        { "mData": "type" },
        { "mData": "rights_given" }
    ];

    oSubjects = $("#subjects").dataTable(opts);

    $(".subjects_actions").prependTo("#subjects_wrapper .dataTables_header");

    initAclDialogs();
    enableAclActions();

    $("#service_acl_dialog #service").change(function() {
        var selected = $("option:selected", this);
        var params = {
            client_id: $("#details_client_id").val(),
            wsdl_id: selected.data("wsdl_id"),
            name: selected.val()
        };

        $.get(action("service_acl"), params, function(response) {
            oSubjects.fnClearTable();
            var title = 'ACL for service: ' + selected.val() +
                ' (' + selected.data("title") + ')';
            $("#service_acl_dialog").dialog("option", "title", title);

            oSubjects.fnAddData(response.data);
            $("#service_acl_dialog").dialog("open");
        }, "json");
    });

    $("#subjects tbody tr").live("click", function() {
        oSubjects.setFocus(0, this, true);
        enableAclActions();
    });

    $("#subjects_remove_selected").click(function() {
        var service = $("#service_acl_dialog #service option:selected");
        var params = {
            client_id: $("#details_client_id").val(),
            wsdl_id: service.data("wsdl_id"),
            name: service.val(),
            subject_ids: []
        };

        var subjects = [];
        $("#subjects .row_selected").each(function(i, row) {
            var subject = [oSubjects.fnGetData(row).type, oSubjects.fnGetData(row).name_description, 
                oSubjects.fnGetData(row).member_group_code];
            if(oSubjects.fnGetData(row).subsystem_code !== null)
                subject.push(oSubjects.fnGetData(row).subsystem_code);
            subjects.push( '<li>' + subject.join(', ') + '</li>' );
        });
        
        confirm('acl.remove.selected', ['<ol class="alert-ol">' + subjects.join('') + '</ol>'], function() {
            $("#subjects .row_selected").each(function(idx, row) {
                params.subject_ids.push(oSubjects.fnGetData(row).subject_id);
            });

            $.post(action("subjects_remove"), params, function(response) {
                oSubjects.fnClearTable();
                oSubjects.fnAddData(response.data);
                enableAclActions();
            });
        });

    });

    $("#subjects_remove_all").click(function() {
        var service = $("#service_acl_dialog #service option:selected");
        var params = {
            client_id: $("#details_client_id").val(),
            wsdl_id: service.data("wsdl_id"),
            name: service.val()
        };
        confirm("acl.clearall", null, function() {
            $.post(action("subjects_remove"), params, function(response) {
                oSubjects.fnClearTable();
                oSubjects.fnAddData(response.data);
                enableAclActions();
            });
        });
    });
});
