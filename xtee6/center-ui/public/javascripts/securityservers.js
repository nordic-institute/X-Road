var oSecurityServers;

function enableActions() {
    if (oSecurityServers.setFocus()) {
        $(".securityserver-action").enable();
    } else {
        $(".securityserver-action").disable();
    }
}

function reqParams(nRow) {
    var row = oSecurityServers.fnGetData(nRow)
    return {
        serverCode: row["server_code"],
        ownerClass: row["owner_class"],
        ownerCode: row["owner_code"]
    };
}

function onDraw() {
    if (!oSecurityServers) return;
    if (!oSecurityServers.getFocus()) {
        $(".securityserver-action").disable();
    } else {
        $(".securityserver-action").enable();
    }
}

function updateSecurityServersTable() {
    oSecurityServers.fnReloadAjax();
}

function initSecurityServersTable() {
    var opts = defaultOpts(onDraw, 16);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.sScrollY = "400px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "server_code" },
        { "mData": "owner_name" },
        { "mData": "owner_class" },
        { "mData": "owner_code" }
    ];

    opts.fnDrawCallback = function() {
        updateRecordsCount("securityservers");
        enableActions();
    }

    opts.bScrollInfinite = true;
    opts.sAjaxSource = "securityservers/securityservers_refresh";
    
    opts.aaSorting = [ [0,'asc'] ];
    
    oSecurityServers = $('#securityservers').dataTable(opts);
    oSecurityServers.fnSetFilteringDelay(600);
}

$(document).ready(function() {
    initSecurityServersTable();

    enableActions();
    focusInput();

    $("#securityservers tbody td[class!=dataTables_empty]")
            .live("click",function(ev) {
        if (oSecurityServers.setFocus(0, ev.target.parentNode)) {
            $(".securityserver-action").enable();
        }
    });

    $("#securityservers tbody tr").live("dblclick", function() {
        openSecurityServerDetails(oSecurityServers.getFocusData());
    });

    $("#securityserver_details").click(function() {
        openSecurityServerDetails(oSecurityServers.getFocusData());
    });

    $("#securityserver_delete").live("click", function() {
        var nServer = oSecurityServers.getFocus();
        var requestParams = reqParams(nServer);
        var confirmParams = [requestParams["serverCode"],
                             requestParams["ownerCode"],
                             requestParams["ownerClass"]];

        confirm("securityservers.remove.confirm", confirmParams, 
                function() {
            $.post(action("delete"), requestParams, function() {
                $("#securityserver_details_dialog").dialog("close");
                updateSecurityServersTable();
                enableActions();
            }, "json");
        });
    });
});
