var SDSB_SECURITYSERVERS = function() {
    var oSecurityServers;

    function enableActions() {
        if (oSecurityServers.getFocus()) {
            $(".securityserver-action").enable();
        } else {
            $(".securityserver-action").disable();
        }
    }

    function onDraw() {
        if (!oSecurityServers) { return; }
        if (!oSecurityServers.getFocus()) {
            $(".securityserver-action").disable();
        } else {
            $(".securityserver-action").enable();
        }
    }

    function updateTable() {
        oSecurityServers.fnReloadAjax();
    }

    function initSecurityServersTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = onDraw;
        opts.bServerSide = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "server_code", "sWidth": "20%" },
            { "mData": "owner_name" },
            { "mData": "owner_class", "sWidth": "15%" },
            { "mData": "owner_code", "sWidth": "25%" }
        ];

        opts.fnDrawCallback = function() {
            SDSB_CENTERUI_COMMON.updateRecordsCount("securityservers");
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
            SDSB_SECURITYSERVER_EDIT.open(oSecurityServers.getFocusData(), true);
        });

        $("#securityserver_edit").click(function() {
            SDSB_SECURITYSERVER_EDIT.open(oSecurityServers.getFocusData(), true);
        });
    });

    return {
        updateTable: updateTable
    };
}();
