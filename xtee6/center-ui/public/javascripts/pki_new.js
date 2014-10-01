var SDSB_PKI_NEW = function(){
    function initAdding() {
        SDSB_PKI_COMMON.openUploadTopCaCertDialog();
        SDSB_PKI_COMMON.clearSaveablePkiData();
        SDSB_PKI_CACHE.clearPkiEditing();
    }

    function save(closeableDialog) {
        var saveablePki = SDSB_PKI_CACHE.getSaveable(); 

        var params = {
            topCaTempCertId: saveablePki.topCaTempCertId,
            nameExtractor: JSON.stringify(saveablePki.nameExtractor),
            topCaOcspInfos: JSON.stringify(saveablePki.topCaOcspInfos),
            intermediateCas: JSON.stringify(saveablePki.intermediateCas)
        }

        $.post("pkis/save_new_pki", params, function(){
            SDSB_PKI_COMMON.clearSaveablePkiData();
            SDSB_PKIS.refreshPkisTable();
            closeableDialog.dialog("close");
        }, "json");
    }

    return {
        initAdding: initAdding,
        save: save,
    }
}();
