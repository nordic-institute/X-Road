var SDSB_CA_CACHE = function() {
    var oIntermediateCas;

    var uploadingOcspCert = false;
    var fillableOcspResponderSection;

    var closeableDialog;

    var topCaCertificateData;
    var saveableIntermediateCa = {};

    var editableCa = null;
    var deletableOcspInfos = [];
    var deletableIntermediateCas = [];

    /* -- PUBLIC - START -- */

    /* CA editing state - start */

    function isEditingExistingCa() {
        return editableCa != null && $.isNumeric(editableCa.id);
    }

    function editingExistingIntermediateCa(intermediateCaId) {
        return isEditingExistingCa() && intermediateCaId != null;
    }

    function setCaEditing(caId, topCaId) {
        editableCa = {
            id: caId,
            topCaId: topCaId
        };

        getAndFillTopCaCertDetails();
    }

    function clearCaEditing() {
        editableCa = null;
        deletableOcspInfos = [];
        deletableIntermediateCas = [];
        SDSB_CA_EDIT.clearEditWizardCache();
    }

    /* CA editing state - end */

    /* OCSP cert uploading state - start */

    function isUploadingOcspCert() {
        return uploadingOcspCert;
    }

    function setUploadingOcspCert(tempCertIdSelector) {
        uploadingOcspCert = true;
        fillableOcspResponderSection = tempCertIdSelector;
    }

    function clearUploadingOcspCert() {
        uploadingOcspCert = false;
        fillableOcspResponderSection = null;
    }
    /* OCSP cert uploading state - end */


    /* Managing fillable OCSP responder section - start */
    function getFillableOcspResponderSection() {
        return fillableOcspResponderSection;
    }

    function setFillableOcspResponderSection(selector) {
        fillableOcspResponderSection = selector;
    }

    function clearFillableOcspResponderSection() {
        fillableOcspResponderSection = null;
    }

    function markOcspResponderForDeletion(ocspResponderSection) {
        if (!isEditingExistingCa()) {
            return;
        }

        var id = ocspResponderSection.find("#ocsp_responder_id").text();

        if (!$.isNumeric(id)) {
            return;
        }

        deletableOcspInfos.push(id);
    }

    /* Managing fillable OCSP responder section - end */

    /* Managing service CA certificate data - start */

    function getTopCaTempCertId() {
        return $("#top_ca_cert_temp_id").text()
    }

    function getTopCaCertificateData() {
        return topCaCertificateData;
    }

    function addTopCaCertificateData(data) {
        $("#top_ca_cert_temp_id").text(data.temp_cert_id);

        topCaCertificateData = {
            subjectDn: SDSB_CENTERUI_COMMON.decorateCertDetails(data.subject),
            issuerDn: SDSB_CENTERUI_COMMON.decorateCertDetails(data.issuer),
            validFrom: data.valid_from,
            validTo: data.expires
        };
    }

    function clearTopCaCertificateData() {
        topCaCertificateData = null;
    }

    /* Managing service CA certificate data - end */

    /* Managing intermediate CA data - start */

    function initIntermediateCasTable() {
        if (oIntermediateCas != null) {
            oIntermediateCas.fnDestroy();
        }

        var opts = defaultTableOpts();
        opts.fnDrawCallback = onDrawIntermediateCas;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "intermediate_ca" },
            { "mData": "valid_from" },
            { "mData": "valid_to" }
        ];

        opts.fnDrawCallback = function() {
            enableIntermediateCaActions();
        }

        opts.aaSorting = [ [0,'desc'] ];

        oIntermediateCas = $('#intermediate_cas').dataTable(opts);
    }

    function getIntermediateCas() {
        return oIntermediateCas.fnGetData();
    }

    function getEditableIntermediateCas() {
        return {
            delete: deletableIntermediateCas,
            update: getUpdatedIntermediateCas(),
            new: getNewIntermediateCas()
        }
    }

    function getSaveableIntermediateCaId() {
        if (saveableIntermediateCa == null || 
                typeof saveableIntermediateCa.id == 'undefined') {
            return null;
        }

        return saveableIntermediateCa.id;
    }

    function isEditingIntermediateCa() {
        return $("#itermediate_ca_editing").text() != "";
    }

    function addNewIntermediateCa(intermediateCa) {
        var intermediateCasPresent = getIntermediateCas().length;
        intermediateCa.elementId = intermediateCasPresent;
        oIntermediateCas.fnAddData(intermediateCa);
    }

    function addIntermediateCaCertificateData(data, intermediateCaId) {
        saveableIntermediateCa = {
                id: intermediateCaId,
                temp_cert_id: data.temp_cert_id,
                intermediate_ca: data.subject,
                valid_from: data.valid_from,
                valid_to: data.expires,
        };
    }

    function cacheIntermediateCaData() {
        var ocspInfos = getIntermediateCaOcspInfos();

        saveableIntermediateCa.ocspInfos = ocspInfos;
        saveableIntermediateCa.cached = true;

        if (isEditingIntermediateCa()) {
            replaceEditedIntermediateCa(saveableIntermediateCa);
            $("#itermediate_ca_editing").text("");
        } else {
            addNewIntermediateCa(saveableIntermediateCa);
        }

        clearSaveableIntermediateCa();
    }

    function clearSaveableIntermediateCa() {
        saveableIntermediateCa = null;
    }

    function uncacheIntermediateCaData() {
        if(oIntermediateCas != null) {
            oIntermediateCas.fnClearTable();
        }
    }

    /* Managing intermediate CA data - end */

    function getEditable() {
        return editableCa;
    }

    function getSaveable() {
        return {
            topCaTempCertId: getTopCaTempCertId(),
            nameExtractor: getTopCaNameExtractor(),
            topCaOcspInfos: getTopCaOcspInfos(),
            intermediateCas: getNewIntermediateCas()
        };
    }

    function getEditableCaData() {
        var nameExtractor = getTopCaNameExtractor();

        return {
            id: editableCa.id,
            authOnly: nameExtractor.authOnly,
            nameExtractorMemberClass: nameExtractor.memberClass,
            nameExtractorMethodName: nameExtractor.extractorMethod
        };
    }

    function getEditableOcspInfos() {
        var existingOcspInfos = getNewAndUpdatedOcspInfos();
        return {
            delete: deletableOcspInfos,
            update: existingOcspInfos.updated,
            new: existingOcspInfos.new
        }
    }

    /* -- PUBLIC - END -- */

    function getNewIntermediateCas() {
        if (oIntermediateCas == null) {
            return [];
        }

        result = [];

        $.each(getIntermediateCas(), function(index, each) {
            if ($.isNumeric(each.id)) {
                return;
            }

            result.push({
                intermediateCaTempCertId: each.temp_cert_id,
                ocspInfos: each.ocspInfos
            });
        });
        return result;
    }

    function enableIntermediateCaActions() {
        if (oIntermediateCas == null) {
            $(".intermediate_ca-action").disable();
        } else if (oIntermediateCas.setFocus()) {
            $(".intermediate_ca-action").enable();
        } else {
            $(".intermediate_ca-action").disable();
        }
    }

    function onDrawIntermediateCas() {
        if (!oIntermediateCas) {
            return;
        }

        if (!oIntermediateCas.getFocus()) {
            $(".intermediate_ca-action").disable();
        } else {
            $(".intermediate_ca-action").enable();
        }
    }

    function replaceEditedIntermediateCa(newIntermediateCa) {
        var replaceableCaId = $("#itermediate_ca_editing").text();
        newIntermediateCa.elementId = replaceableCaId;

        var intermediateCasData = getIntermediateCas()

        $.each(intermediateCasData, function(index, each){
            if (each.elementId == replaceableCaId) {
                oIntermediateCas.fnUpdate(newIntermediateCa, index);
                return;
            }
        });
    }

    function getActiveIntermediateCaId() {
        return oIntermediateCas.getFocusData().id;
    }

    function getTopCaNameExtractor() {
        return {
            authOnly: $("#ca_add_is_ssl_only").attr("checked") ? true : false,
            memberClass: $("#ca_add_name_extractor_member_class").val(),
            extractorMethod: $("#ca_add_extractor_method").val()
        };
    }


    function getTopCaOcspInfos(){
        return getOcspInfos(
                $("section.top_ca_ocsp_responder"),
                "top_ca_ocsp_responder_url");
    }

    function getIntermediateCaOcspInfos() {
        return getOcspInfos($("section.intermediate_ca_ocsp_responder"),
                "intermediate_ca_ocsp_responder_url");
    }

    function getOcspInfos(ocspResponderSections, urlId) {
        cachedOcspInfos = [];

        ocspResponderSections.each(function(){
            var url = $(this).find("#" + urlId).val()

            if (url != null && url.length > 0) {
                cachedOcspInfos.push({
                    id: $(this).find("#ocsp_responder_id").text(),
                    url: url,
                    ocspTempCertId:
                        $(this).find("#ocsp_responder_cert_temp_id").text()
                });
            }
        });

        return cachedOcspInfos;
    }

    function areIntermediateCasInitialized() {
        return oIntermediateCas != null;
    }

    function deleteActiveIntermediateCa() {
        var deletableRow = oIntermediateCas.getFocus();

        markIntermediateCaForDeletion(getActiveIntermediateCaId());

        var index = oIntermediateCas.fnGetPosition(deletableRow);
        oIntermediateCas.fnDeleteRow(index);
    }

    function markIntermediateCaForDeletion(id) {
        if (!isEditingExistingCa() || !$.isNumeric(id)) {
            return;
        }

        deletableIntermediateCas.push(id);
    }

    function getNewAndUpdatedOcspInfos() {
        result = separateNewOcspInfosFromUpdatedOnes(
                getTopCaOcspInfos(), editableCa.topCaId);

        $.each(getEditableIntermediateCaOcspInfos(), function(index, each) {
            var separatedInfos = separateNewOcspInfosFromUpdatedOnes(
                    each.ocspInfos, each.caId);
            $.merge(result.updated , separatedInfos.updated);
            $.merge(result.new , separatedInfos.new);
        });

        return result;
    }

    function getAndFillTopCaCertDetails() {
        var params = {caId: editableCa.id};

        $.get("approved_cas/get_top_ca_cert_details", params, function(response) {
            addTopCaCertificateData(response.data);
            SDSB_CA_COMMON.fillTopCaCertDetails();
            SDSB_CA_COMMON.openViewTopCaCertDialog();
        }, "json");
    }

    function separateNewOcspInfosFromUpdatedOnes(ocspInfos, caInfoId) {
        var updatedInfos = []
        var newInfos = []

        $.each(ocspInfos, function(index, each){
            if ($.isNumeric(each.id)) {
                updatedInfos.push(each);
            } else {
                each.caInfoId = caInfoId;
                newInfos.push(each);
            }
        });

        return {
            updated: updatedInfos,
            new: newInfos
        }
    }

    function getEditableIntermediateCaOcspInfos() {
        if (!areIntermediateCasInitialized()) {
            return [];
        }

        var result = []

        $.each(getIntermediateCas(), function(i, eachCa) {
            var caOcspInfos = {
                caId: eachCa.id,
                ocspInfos: []
            }

            if (eachCa.ocspInfos != null) {
                $.each(eachCa.ocspInfos, function(j, eachOcspInfo) {
                    caOcspInfos.ocspInfos.push(eachOcspInfo);
                });
            }

            result.push(caOcspInfos);
        });

        return result;
    }

    function getUpdatedIntermediateCas() {
        if (!areIntermediateCasInitialized()) {
            return [];
        }

        result = []

        $.each(getIntermediateCas(), function(index, each) {
            if (!$.isNumeric(each.id)) {
                return;
            }

            result.push({
                id: each.id,
                intermediateCaTempCertId: each.temp_cert_id
            });
        });

        return result;
    }

    $(document).ready(function() {
        $("#intermediate_cas tbody tr").live("click", function(ev){
            if (!oIntermediateCas.setFocus(0, ev.target.parentNode)) {
                $(".intermediate_ca-action").disable();
            }
        });

        $("#intermediate_cas tbody tr").live("dblclick", function(){
            SDSB_CA_COMMON.openIntermediateCaEditDialog(
                    oIntermediateCas.getFocusData());
        });

        $("#intermediate_ca_edit").live("click", function(){
            SDSB_CA_COMMON.openIntermediateCaEditDialog(
                    oIntermediateCas.getFocusData());
        });

        $("#intermediate_ca_delete").live("click", function(){
            deleteActiveIntermediateCa();
        });
    });

    return {
        isEditingExistingCa: isEditingExistingCa,
        editingExistingIntermediateCa: editingExistingIntermediateCa,
        setCaEditing: setCaEditing,
        clearCaEditing: clearCaEditing,

        isUploadingOcspCert: isUploadingOcspCert,
        setUploadingOcspCert: setUploadingOcspCert,
        clearUploadingOcspCert: clearUploadingOcspCert,

        getFillableOcspResponderSection: getFillableOcspResponderSection,
        setFillableOcspResponderSection: setFillableOcspResponderSection,
        clearFillableOcspResponderSection: clearFillableOcspResponderSection,
        markOcspResponderForDeletion: markOcspResponderForDeletion,

        getTopCaTempCertId: getTopCaTempCertId,
        getTopCaCertificateData: getTopCaCertificateData,
        addTopCaCertificateData: addTopCaCertificateData,
        clearTopCaCertificateData: clearTopCaCertificateData,

        initIntermediateCasTable: initIntermediateCasTable,
        getIntermediateCas: getIntermediateCas,
        getEditableIntermediateCas: getEditableIntermediateCas,
        getSaveableIntermediateCaId: getSaveableIntermediateCaId,
        isEditingIntermediateCa: isEditingIntermediateCa,
        addNewIntermediateCa: addNewIntermediateCa,
        addIntermediateCaCertificateData: addIntermediateCaCertificateData,
        cacheIntermediateCaData: cacheIntermediateCaData,
        clearSaveableIntermediateCa: clearSaveableIntermediateCa,
        uncacheIntermediateCaData: uncacheIntermediateCaData,

        getEditable: getEditable,
        getSaveable: getSaveable,
        getEditableCaData: getEditableCaData,
        getEditableOcspInfos: getEditableOcspInfos,
    };
} ();
