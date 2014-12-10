var SDSB_CAS = function() {
    var oCas;

    function enableActions() {
        $("#ca_add").enable();

        if (oCas.getFocus()) {
            $(".approved_ca-action").enable();
        } else {
            $(".approved_ca-action").disable();
        }
    }

    function updateCaListActionButtonsVisibility() {
        if (!oCas) return;
        if (!oCas.getFocus()) {
            $(".approved_ca-action").disable();
        } else {
            $(".approved_ca-action").enable();
        }
    }

    function deleteCa() {
        var ca = oCas.getFocusData();
        var requestParams = {id: ca.id};
        var confirmParams = {approvedCa: ca.trusted_certification_service};

        confirm("approved_cas.remove_confirm", confirmParams, function() {
            $.post("approved_cas/delete_approved_ca", requestParams, function() {
                refreshCasTable();
            }, "json");
        });
    }

    function initCasTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = updateCaListActionButtonsVisibility;
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.sScrollY = "400px";
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "trusted_certification_service" },
            { "mData": "valid_from", "sWidth": "14em" },
            { "mData": "valid_to", "sWidth": "14em" }
        ];
        opts.asRowId = ["id"];

        opts.fnDrawCallback = function() {
            SDSB_CENTERUI_COMMON.updateRecordsCount("approved_cas");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "approved_cas/refresh";

        opts.aaSorting = [ [2,'desc'] ];

        oCas = $('#cas').dataTable(opts);
        oCas.fnSetFilteringDelay(600);
    }

    function refreshCasTable() {
        oCas.fnReloadAjax();
    }

    function openExistingCa() {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed("approved_cas/can_see_details",
                function(){
            var ca = oCas.getFocusData();
            SDSB_CA_EDIT.initEditingExistingCa(ca.id, ca.top_ca_id);
        });
    }

    $(document).ready(function() {
        initCasTable();

        enableActions();

        $("#cas tbody td").live("click", function(ev) {
            oCas.setFocus(0, ev.target.parentNode);
            updateCaListActionButtonsVisibility();
        });

        $("#ca_add").live("click", function() {
            SDSB_CA_NEW.initAdding();
        });

        $("#cas tbody tr").live("dblclick", function(ev) {
            openExistingCa();
        });

        $("#ca_details").live("click", function() {
            openExistingCa();
        });

        $("#ca_delete").live("click", function() {
            deleteCa();
        });
    });

    return {
        refreshCasTable: refreshCasTable
    };
}();
