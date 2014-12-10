var SDSB_CA_NEW = function(){
    function initAdding() {
        SDSB_CA_COMMON.openUploadTopCaCertDialog();
        SDSB_CA_COMMON.clearSaveableCaData();
        SDSB_CA_CACHE.clearCaEditing();
    }

    function save(closeableDialog) {
        var saveableCa = SDSB_CA_CACHE.getSaveable(); 

        var params = {
            topCaTempCertId: saveableCa.topCaTempCertId,
            nameExtractor: JSON.stringify(saveableCa.nameExtractor),
            topCaOcspInfos: JSON.stringify(saveableCa.topCaOcspInfos),
            intermediateCas: JSON.stringify(saveableCa.intermediateCas)
        }

        $.post("approved_cas/save_new_approved_ca", params, function(){
            SDSB_CA_COMMON.clearSaveableCaData();
            SDSB_CAS.refreshCasTable();
            closeableDialog.dialog("close");
        }, "json");
    }

    return {
        initAdding: initAdding,
        save: save,
    }
}();
