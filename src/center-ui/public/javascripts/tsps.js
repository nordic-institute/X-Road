var XROAD_TSPS = function() {
    var oTsps;

    function initTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "valid_from", "sWidth": "14em" },
            { "mData": "valid_to", "sWidth": "14em" }
        ];
        opts.asRowId = ["id"];

        opts.fnDrawCallback = function() {
            XROAD_CENTERUI_COMMON.updateRecordsCount("tsps");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = action("tsps_refresh");

        opts.aaSorting = [ [2,'desc'] ];

        oTsps = $('#tsps').dataTable(opts);
        oTsps.fnSetFilteringDelay(600);

        $("#tsps tbody tr").live("click", function(ev) {
            oTsps.setFocus(0, this);
            enableActions();
        });

        $("#tsps tbody tr").live("dblclick", function(ev) {
            $("#tsp_details").click();
        });
    }

    function enableActions() {
        $("#tsp_add").enable();

        if (oTsps.getFocus()) {
            $(".tsp-action").enable();
        } else {
            $(".tsp-action").disable();
        }
    }

    function addActionHandlers() {
        $("#tsp_add").live("click", function() {
            XROAD_URL_AND_CERT_DIALOG.openAddDialog(
                "tsp", _("tsps.edit_new"), false, {});
        });

        $("#tsp_details").click(function() {
            if (!can("view_approved_tsa_details")) {
                return;
            }

            var selected = oTsps.getFocusData();
            var params = {
                tsp_id: selected.id
            };

            XROAD_URL_AND_CERT_DIALOG.openEditDialog(
                "tsp", _("tsps.edit_existing"), false,
                selected.url, true, params);
        });

        $("#tsp_delete").click(function() {
            var tsp = oTsps.getFocusData();
            var requestParams = {tsp_id: tsp.id};
            var confirmParams = {tsp: tsp.name};

            confirm("tsps.remove_confirm", confirmParams, function() {
                $.post("tsps/delete_tsp", requestParams, function() {
                    oTsps.fnReloadAjax();
                }, "json");
            });
        });
    }

    function initTspDialog() {
        XROAD_URL_AND_CERT_DIALOG.initForPrefix("tsp",
                function(params) { // onAdd
            $.post(action("add_tsp"), params, function() {
                oTsps.fnReloadAjax();
                XROAD_URL_AND_CERT_DIALOG.closeDialog("tsp");
            }, "json");

        }, function(params) { // onEdit
            $.post(action("edit_tsp"), params, function() {
                oTsps.fnReloadAjax();
                XROAD_URL_AND_CERT_DIALOG.closeDialog("tsp");
            }, "json");

        }, function(params) { // onCertView
            $.get(action("view_tsp_cert"), params, function(response) {
                XROAD_CERT_DETAILS_DIALOG.openDialog(response.data);
            }, "json");
        });
    }

    $(document).ready(function() {
        initTable();
        enableActions();
        addActionHandlers();
        initTspDialog();
    });
}();
