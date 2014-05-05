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
    var confirmParams = [pki.trusted_certification_service];

    confirm("pkis.remove.confirm", confirmParams, function() {
        $.post(action("delete_pki"), requestParams, function() {
            refreshPkisTable();
        }, "json");
    });
}

function initPkisTable() {
    var opts = defaultOpts(updatePkiListActionButtonsVisibility, 100);
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
        updateRecordsCount("pkis");
        enableActions();
    }

    opts.bScrollInfinite = true;
    opts.sAjaxSource = action("pkis_refresh");

    opts.aaSorting = [ [2,'desc'] ];

    oPkis = $('#pkis').dataTable(opts);
    oPkis.fnSetFilteringDelay(600);
}

function refreshPkisTable() {
    oPkis.fnReloadAjax();
}

function initEditingExistingPki() {
    var pki = oPkis.getFocusData()
    editPki.initEditingExistingPki(pki.id, pki.top_ca_id);
}

$(document).ready(function() {
    initPkisTable();

    enableActions();

    $("#pkis tbody td").live("click", function(ev) {
        oPkis.setFocus(0, ev.target.parentNode);
        updatePkiListActionButtonsVisibility();
    });

    $("#pki_add").live("click", function() {
        newPki.initAdding();
    });

    $("#pkis tbody tr").live("dblclick", function(ev) {
        initEditingExistingPki();
    });

    $("#pki_details").live("click", function() {
        initEditingExistingPki();
    });

    $("#pki_delete").live("click", function() {
        deletePki();
    });
});
