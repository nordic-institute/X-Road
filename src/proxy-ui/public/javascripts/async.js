var oProviders, oRequests;

function params() {
    var ret = {
        provider_id: oProviders.getFocusData().provider_id
    };
    if (oRequests.getFocus()) {
        ret.request_id = oRequests.getFocusData().id;
    }
    return ret;
}

function enableActions() {
    $("#last_attempt_result, #reset, #remove, #restore").disable();

    if ($("#providers .row_selected").length > 0) {
        $("#last_attempt_result, #reset").enable();
    }
    if ($("#requests .row_selected").length > 0) {
        if (oRequests.getFocusData().removed) {
            $("#restore").enable();
        } else {
            $("#remove").enable();
        }
    }
}

$(document).ready(function() {
    // init providers table
    var opts = scrollableTableOpts();
    opts.bFilter = false;
    opts.oLanguage.sZeroRecords = "&nbsp;";
    opts.aoColumns = [
        { "mData": "name", mRender: util.escape },
        { "mData": "requests" },
        { "mData": "send_attempts" },
        { "mData": "last_attempt" },
        { "mData": "last_attempt_result",
          "bVisible": false },
        { "mData": "next_attempt" },
        { "mData": "last_success" },
        { "mData": "last_success_id" }
    ];

    oProviders = $("#providers").dataTable(opts);

    // init requests table
    opts = scrollableTableOpts();
    opts.bFilter = false;
    opts.oLanguage.sZeroRecords = "&nbsp;";
    opts.aoColumns = [
        { "mData": "queue_no" },
        { "mData": "id" },
        { "mData": "received" },
        { "mData": "removed" },
        { "mData": "sender", mRender: util.escape },
        { "mData": "user", mRender: util.escape },
        { "mData": "service", mRender: util.escape }
    ];

    oRequests = $("#requests").dataTable(opts);

    $("#providers_actions")
        .insertBefore("#providers_wrapper .dataTables_footer .clearer");
    $("#requests_actions")
        .insertBefore("#requests_wrapper .dataTables_footer .clearer");

    enableActions();

    $("#providers tbody tr").live("click", function(ev) {
        if (oProviders.setFocus(0, this)) {
            oRequests.fnClearTable();

            $.get(action("requests"), params(), function(response) {
                oRequests.fnAddData(response.data);
                enableActions();
            }, "json");
        }
    });

    $("#refresh").click(function() {
        $.get(action("refresh"), null, function(response) {
            oRequests.fnClearTable();
            oProviders.fnReplaceData(response.data);
            enableActions();
        }, "json");
    }).click();

    $("#reset").click(function() {
        $.get(action("reset"), params(), function() {
            $("#refresh").click();
        }, "json");
    });

    $("#last_attempt_result_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 300,
        width: 600,
        buttons: [
            { text: "OK",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#last_attempt_result").click(function() {
        var result = oProviders.getFocusData().last_attempt_result;
        $("#last_attempt_result_dialog textarea").html(result);
        $("#last_attempt_result_dialog").dialog("open");
    });

    $("#requests tbody tr").live("click", function(ev) {
        if (oRequests.setFocus(0, this)) {
            enableActions();
        }
    });

    $("#remove, #restore").click(function() {
        $.get(action(this.id), params(), function() {
            $.get(action("requests"), params(), function(response) {
                oRequests.fnReplaceData(response.data);
                enableActions();
            }, "json");
        }, "json");
    });
});
