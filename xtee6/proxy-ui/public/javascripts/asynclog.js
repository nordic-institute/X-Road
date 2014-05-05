var oRecords;

$(document).ready(function() {
    var opts = defaultOpts(null, 1);
    opts.sScrollY = "400px";
    opts.bScrollCollapse = true;
    opts.bPaginate = false;
    opts.aaSorting = [];
    opts.aoColumns = [
        { "mData": "no" },
        { "mData": "logged" },
        { "mData": "state" },
        { "mData": "received" },
        { "mData": "removed" },
        { "mData": "send_attempts" },
        { "mData": "producer" },
        { "mData": "sender" },
        { "mData": "request_id" }
    ];

    oRecords = $("#records").dataTable(opts);

    $("#refresh").click(function() {
        $.get(action("refresh"), null, function(response) {
            oRecords.fnClearTable(false);
            oRecords.fnAddData(response.data);
        }, "json");
    }).click();
});
