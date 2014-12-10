var SDSB_TSPS = function() {
    var oTsps;

    function initTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = updateActionButtonsVisibility;
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.sScrollY = "400px";
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": "valid_from", "sWidth": "14em" },
            { "mData": "valid_to", "sWidth": "14em" }
        ];
        opts.asRowId = ["id"];

        opts.fnDrawCallback = function() {
            SDSB_CENTERUI_COMMON.updateRecordsCount("tsps");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = action("tsps_refresh");

        opts.aaSorting = [ [2,'desc'] ];

        oTsps = $('#tsps').dataTable(opts);
        oTsps.fnSetFilteringDelay(600);

        $("#tsps tbody td").live("click", function(ev) {
            setTableFocus(0, ev.target.parentNode);
            updateActionButtonsVisibility();
        });

        $("#tsps tbody tr").live("dblclick", function(ev) {
            initEditingExisting();
        });
    }

    function refreshTable() {
        oTsps.fnReloadAjax();
    }

    function enableActions() {
        $("#tsp_add").enable();

        if (oTsps.getFocus()) {
            $(".tsp-action").enable();
        } else {
            $(".tsp-action").disable();
        }
    }

    function setTableFocus(minSize, row) {
        oTsps.setFocus(minSize, row);
    }

    function updateActionButtonsVisibility() {
        if (!oTsps) return;

        if (!oTsps.getFocus()) {
            $(".tsp-action").disable();
        } else {
            $(".tsp-action").enable();
        }
    }

    function initEditingExisting() {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed("tsps/can_see_details",
                function(){
            editableTsp = oTsps.getFocusData();
            SDSB_TSP_EDIT.initEditing(editableTsp.id, editableTsp.url);
        });
    }

    function deleteTsp() {
        var tsp = oTsps.getFocusData();
        var requestParams = {id: tsp.id};
        var confirmParams = {tsp: tsp.name};

        confirm("tsps.remove_confirm", confirmParams, function() {
            $.post("tsps/delete_tsp", requestParams, function() {
                refreshTable();
            }, "json");
        });
    }

    function addActionHandlers() {
        $("#tsp_add").live("click", function() {
            SDSB_TSP_EDIT.initAdding();
        });

        $("#tsp_details").live("click", function() {
            initEditingExisting();
        });

        $("#tsp_delete").live("click", function() {
            deleteTsp();
        });
    }

    $(document).ready(function() {
        initTable();
        enableActions();
        addActionHandlers();
    });

    return {
        refreshTable: refreshTable,
    }
}();
