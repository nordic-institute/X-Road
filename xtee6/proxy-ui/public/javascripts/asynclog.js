var oRecords;

$(document).ready(function() {
    var opts = scrollableTableOpts(400);
    opts.aaSorting = [];
    opts.aoColumns = [
        { "mData": "no" },
        { "mData": "logged" },
        { "mData": "state" },
        { "mData": "received" },
        { "mData": "removed" },
        { "mData": "send_attempts" },
        { "mData": "producer", mRender: util.escape },
        { "mData": "sender", mRender: util.escape },
        { "mData": "request_id" }
    ];

    oRecords = $("#records").dataTable(opts);

    $("#refresh").click(function() {
        $.get(action("refresh"), null, function(response) {
            oRecords.fnReplaceData(response.data);
        }, "json");
    }).click();
});
