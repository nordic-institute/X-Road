var tsps = function() {
    var oTsps;

    /* Public API - start */

    function initTable() {
        var opts = defaultOpts(updateActionButtonsVisibility, 100);
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.sScrollY = "400px";
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": "valid_from" },
            { "mData": "valid_to" }
        ];

        opts.fnDrawCallback = function() {
            updateRecordsCount("tsps");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = action("tsps_refresh");

        opts.aaSorting = [ [2,'desc'] ];

        oTsps = $('#tsps').dataTable(opts);
        oTsps.fnSetFilteringDelay(600);
    }

    function refreshTable() {
        oTsps.fnReloadAjax();
    }

    function enableActions() {
        $("#tsp_add").enable();

        if (oTsps.setFocus()) {
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
        editableTsp = oTsps.getFocusData();
        editTsp.initEditing(editableTsp.id, editableTsp.url);
    }

    function deleteTsp() {
        var tsp = oTsps.getFocusData();
        var requestParams = {id: tsp.id};
        var confirmParams = [tsp.name];

        confirm("tsps.remove.confirm", confirmParams, function() {
            $.post(action("delete_tsp"), requestParams, function() {
                refreshTable();
            }, "json");
        });
    }

    /* Public API - end */
    
    return {
        initTable: initTable,
        refreshTable: refreshTable,
        enableActions: enableActions,
        setTableFocus: setTableFocus,
        updateActionButtonsVisibility: updateActionButtonsVisibility,
        initEditingExisting: initEditingExisting,
        deleteTsp: deleteTsp
    }
}();

$(document).ready(function() {
    tsps.initTable();

    tsps.enableActions();

    $("#tsps tbody td").live("click", function(ev) {
        tsps.setTableFocus(0, ev.target.parentNode);
        tsps.updateActionButtonsVisibility();
    });

    $("#tsp_add").live("click", function() {
        newTsp.initAdding();
    });

    $("#tsps tbody tr").live("dblclick", function(ev) {
        tsps.initEditingExisting();
    });

    $("#tsp_details").live("click", function() {
        tsps.initEditingExisting();
    });

    $("#tsp_delete").live("click", function() {
        tsps.deleteTsp();
    });
});
