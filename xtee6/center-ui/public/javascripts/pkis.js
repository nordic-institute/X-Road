var SDSB_PKIS = function() {
    var oPkis;

    function enableActions() {
        $("#pki_add").enable();

        if (oPkis.setFocus()) {
            $(".pki-action").enable();
        } else {
            $(".pki-action").disable();
        }
    }

    function updatePkiListActionButtonsVisibility() {
        if (!oPkis) return;
        if (!oPkis.getFocus()) {
            $(".pki-action").disable();
        } else {
            $(".pki-action").enable();
        }
    }

    function deletePki() {
        var pki = oPkis.getFocusData();
        var requestParams = {id: pki.id};
        var confirmParams = {pki: pki.trusted_certification_service};

        confirm("pkis.remove_confirm", confirmParams, function() {
            $.post("pkis/delete_pki", requestParams, function() {
                refreshPkisTable();
            }, "json");
        });
    }

    function initPkisTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = updatePkiListActionButtonsVisibility;
        opts.bProcessing = true;
        opts.bServerSide = true;
        opts.sScrollY = "400px";
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
        opts.aoColumns = [
            { "mData": "trusted_certification_service" },
            { "mData": "valid_from" },
            { "mData": "valid_to" }
        ];

        opts.fnDrawCallback = function() {
            SDSB_CENTERUI_COMMON.updateRecordsCount("pkis");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "pkis/pkis_refresh";

        opts.aaSorting = [ [2,'desc'] ];

        oPkis = $('#pkis').dataTable(opts);
        oPkis.fnSetFilteringDelay(600);
    }

    function refreshPkisTable() {
        oPkis.fnReloadAjax();
    }

    function openExistingPki() {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed("pkis/can_see_details",
                function(){
            var pki = oPkis.getFocusData()
            SDSB_PKI_EDIT.initEditingExistingPki(pki.id, pki.top_ca_id);
        });
    }

    $(document).ready(function() {
        initPkisTable();

        enableActions();

        $("#pkis tbody td").live("click", function(ev) {
            oPkis.setFocus(0, ev.target.parentNode);
            updatePkiListActionButtonsVisibility();
        });

        $("#pki_add").live("click", function() {
            SDSB_PKI_NEW.initAdding();
        });

        $("#pkis tbody tr").live("dblclick", function(ev) {
            openExistingPki();
        });

        $("#pki_details").live("click", function() {
            openExistingPki();
        });

        $("#pki_delete").live("click", function() {
            deletePki();
        });
    });

    return {
        refreshPkisTable: refreshPkisTable
    };
}();
