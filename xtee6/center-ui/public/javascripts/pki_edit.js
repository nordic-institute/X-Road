var SDSB_PKI_EDIT = function() {
    var pkiEditWizardCache = function() {
        var nameExtractorCached = false;
        var topCaOcspInfosCached = false;
        var intermediateCasCached = false;

        function cacheNameExtractor() {
            nameExtractorCached = true;
        }

        function isNameExtractorCached() {
            return nameExtractorCached;
        }

        function cacheTopCaOcspInfos() {
            topCaOcspInfosCached = true;
        }

        function areTopCaOcspInfosCached() {
            return topCaOcspInfosCached;
        }

        function cacheIntmermediateCas() {
            intermediateCasCached = true;
        }

        function areIntermediateCasCached() {
            return intermediateCasCached;
        }

        function clear() {
            nameExtractorCached = false;
            topCaOcspInfosCached = false;
            intermediateCasCached = false;
        }

        return {
            cacheNameExtractor: cacheNameExtractor,
            isNameExtractorCached: isNameExtractorCached,

            cacheTopCaOcspInfos: cacheTopCaOcspInfos,
            areTopCaOcspInfosCached: areTopCaOcspInfosCached,

            cacheIntmermediateCas: cacheIntmermediateCas,
            areIntermediateCasCached: areIntermediateCasCached,

            clear: clear
        }
    }();

    /* -- PUBLIC - START -- */

    function initEditingExistingPki(pkiId, topCaId) {
        SDSB_PKI_CACHE.clearPkiEditing();
        SDSB_PKI_COMMON.clearSaveablePkiData();
        SDSB_PKI_CACHE.setPkiEditing(pkiId, topCaId);
    }

    function areIntermediateCasCached() {
        return pkiEditWizardCache.areIntermediateCasCached();
    }

    function clearEditWizardCache() {
        pkiEditWizardCache.clear();
    }

    function save(closeableDialog) {
        var params = {
            pki: JSON.stringify(SDSB_PKI_CACHE.getEditablePkiData()),
            ocspInfos: JSON.stringify(SDSB_PKI_CACHE.getEditableOcspInfos()),
            intermediateCas: JSON.stringify(
                    SDSB_PKI_CACHE.getEditableIntermediateCas())
        }

        $.post("pkis/edit_existing_pki", params, function(){
            SDSB_PKI_COMMON.clearSaveablePkiData();
            SDSB_PKI_CACHE.clearPkiEditing();
            SDSB_PKIS.refreshPkisTable();
            closeableDialog.dialog("close");
        });
    }

    /* -- Filling OCSP responder data - start -- */

    function fillOcspResponderSections(
            responders, responderSectionClass, addButtonId) {
        var sSectionSelector = "section." + responderSectionClass

        $.each(responders, function(index, each){
            var responderSections = $(sSectionSelector);

            if (index == 0) {
                fillOcspResponderSection(responderSections.first(), each);
            } else {
                SDSB_PKI_COMMON.addOcspResponderSection(responderSections, 
                        $("#" + addButtonId), each);
            }
        });

        if(responders.length > 0) {
            $(sSectionSelector).show();
        }

        SDSB_PKI_COMMON.enableOcspResponderAddButton();
    }

    function fillOcspResponderSection(section, ocspInfo) {
        section.find(
                "#top_ca_ocsp_responder_url, " +
                "#intermediate_ca_ocsp_responder_url")
                .val(ocspInfo.url);
        section.find("#ocsp_responder_id").text(ocspInfo.id);

        var tempCertId = ocspInfo.ocspTempCertId;

        SDSB_PKI_COMMON.showOcspResponderDeleteButton(section);

        if(tempCertId != null && tempCertId.length > 0) {
            SDSB_PKI_CACHE.setFillableOcspResponderSection(section);

            SDSB_PKI_COMMON.addOcspCertDetailsLink(function() {
                SDSB_PKI_COMMON.openPkiTempCertDetailsById(tempCertId);
            });
        } else if (ocspInfo.has_cert) {
            SDSB_PKI_CACHE.setFillableOcspResponderSection(section);

            SDSB_PKI_COMMON.addOcspCertDetailsLink(function() {
                renderExistingOcspCertDetails(ocspInfo.id);
            });
        }
    }

    /* -- Filling OCSP responder data - end -- */

    /* -- Dialog openers - start -- */

    function openTopCaCertDetails() {
        var params = {pkiId: SDSB_PKI_CACHE.getEditable().id};

        $.get("pkis/get_top_ca_cert_dump_and_hash", params,
                function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
        }, "json");
    }

    function openNameExtractorDialogWithData() {
        if (pkiEditWizardCache.isNameExtractorCached()) {
            SDSB_PKI_COMMON.openAddNameExtractorDialogWindow();
            return;
        }

        var params = {pkiId: SDSB_PKI_CACHE.getEditable().id};

        $.get("pkis/get_name_extractor_data", params, function(response) {
            nameExtractor = response.data;

            if (nameExtractor.auth_only) {
                $("#pki_add_is_ssl_only").attr("checked", "checked");
                SDSB_PKI_COMMON.disableNameExtractorInputs();
            }

            $("#pki_add_name_extractor_member_class")
                    .val(nameExtractor.member_class);
            $("#pki_add_extractor_method").val(nameExtractor.method_name);

            pkiEditWizardCache.cacheNameExtractor();
            SDSB_PKI_COMMON.openAddNameExtractorDialogWindow();
        }, "json");
    }

    function openTopCaOcspRespondersWithData() {
        if (pkiEditWizardCache.areTopCaOcspInfosCached()) {
            SDSB_PKI_COMMON.openAddTopCaOcspInfosDialogWindow();
            return;
        }

        addOcspRespondersWithData(
                SDSB_PKI_CACHE.getEditable().topCaId, 
                "top_ca_ocsp_responder", 
                "pki_add_top_ca_ocsp_responder",
                function() {
            pkiEditWizardCache.cacheTopCaOcspInfos();
            SDSB_PKI_COMMON.openAddTopCaOcspInfosDialogWindow();
        }); 
    }

    function openIntermediateCaOcspRespondersWithData(caId) {
        var intermediateCa = getIntermediateCaFromTable(caId);

        if (intermediateCa != null && intermediateCa.cached) {
            fillOcspResponderSections(
                    intermediateCa.ocspInfos,
                    "intermediate_ca_ocsp_responder",
                    "pki_add_intermediate_ca_ocsp_responder");
            return;
        }

        addOcspRespondersWithData(
                caId, 
                "intermediate_ca_ocsp_responder", 
                "pki_add_intermediate_ca_ocsp_responder",
                function() {/* Do nothing */}); 
    }

    function openIntermediateCasWithData() {
        if (pkiEditWizardCache.areIntermediateCasCached()) {
            SDSB_PKI_COMMON.openAddIntermediateCasDialogWindow();
            return;
        }

        var params = {pkiId: SDSB_PKI_CACHE.getEditable().id};

        $.get("pkis/get_intermediate_cas", params, function(response) {
            $.each(response.data, function(index, each){
                SDSB_PKI_CACHE.addNewIntermediateCa(each);
            });

            pkiEditWizardCache.cacheIntmermediateCas();
            SDSB_PKI_COMMON.openAddIntermediateCasDialogWindow();
        });
    }

    function openIntermediateCaCertDetails(intermediateCaId) {
        var params = {intermediateCaId: intermediateCaId};

        $.get("pkis/get_existing_intermediate_ca_cert_dump_and_hash",
                params, function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
        }, "json");
    }

    /* -- Dialog openers - end -- */

    /* -- PUBLIC - END -- */

    function addOcspRespondersWithData(
            caId, responderSectionClass, addButtonId, afterAddCallback) {
        var params = {caId: caId};
        $.get("pkis/get_ocsp_infos", params, function(response) {
            var responders = response.data;

            fillOcspResponderSections(
                    responders, responderSectionClass, addButtonId);

            afterAddCallback(responders.length);
        }, "json");
    }

    function renderExistingOcspCertDetails(ocspInfoId) {
        var params = {ocspInfoId: ocspInfoId};

        $.get("pkis/get_existing_ocsp_cert_details", params,
                function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
            SDSB_PKI_CACHE.clearFillableOcspResponderSection();
        }, "json");
    }

    function getIntermediateCaFromTable(intermediateCaId) {
        var intermediateCas = SDSB_PKI_CACHE.getIntermediateCas();
        var count = intermediateCas.length;

        for (var i = 0; i < count; i++) {
            eachIntermediateCa = intermediateCas[i];
            if (eachIntermediateCa.id == intermediateCaId) {
                return eachIntermediateCa;
            }
        }

        return null;
    }

    return {
        initEditingExistingPki: initEditingExistingPki,
        areIntermediateCasCached: areIntermediateCasCached,
        clearEditWizardCache: clearEditWizardCache,
        save: save,

        fillOcspResponderSections: fillOcspResponderSections,
        fillOcspResponderSection: fillOcspResponderSection,

        openTopCaCertDetails: openTopCaCertDetails,
        openNameExtractorDialogWithData: openNameExtractorDialogWithData,
        openTopCaOcspRespondersWithData: openTopCaOcspRespondersWithData,
        openIntermediateCaOcspRespondersWithData: 
            openIntermediateCaOcspRespondersWithData,
        openIntermediateCasWithData: openIntermediateCasWithData,
        openIntermediateCaCertDetails: openIntermediateCaCertDetails
    }
}();
