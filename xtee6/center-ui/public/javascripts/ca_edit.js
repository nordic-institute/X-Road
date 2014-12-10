var SDSB_CA_EDIT = function() {
    var caEditWizardCache = function() {
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

    function initEditingExistingCa(caId, topCaId) {
        SDSB_CA_CACHE.clearCaEditing();
        SDSB_CA_COMMON.clearSaveableCaData();
        SDSB_CA_CACHE.setCaEditing(caId, topCaId);
    }

    function areIntermediateCasCached() {
        return caEditWizardCache.areIntermediateCasCached();
    }

    function clearEditWizardCache() {
        caEditWizardCache.clear();
    }

    function save(closeableDialog) {
        var params = {
            ca: JSON.stringify(SDSB_CA_CACHE.getEditableCaData()),
            ocspInfos: JSON.stringify(SDSB_CA_CACHE.getEditableOcspInfos()),
            intermediateCas: JSON.stringify(
                    SDSB_CA_CACHE.getEditableIntermediateCas())
        }

        $.post("approved_cas/edit_existing_approved_ca", params, function(){
            SDSB_CA_COMMON.clearSaveableCaData();
            SDSB_CA_CACHE.clearCaEditing();
            SDSB_CAS.refreshCasTable();
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
                SDSB_CA_COMMON.addOcspResponderSection(responderSections, 
                        $("#" + addButtonId), each);
            }
        });

        if(responders.length > 0) {
            $(sSectionSelector).show();
        }

        SDSB_CA_COMMON.enableOcspResponderAddButton();
    }

    function fillOcspResponderSection(section, ocspInfo) {
        section.find(
                "#top_ca_ocsp_responder_url, " +
                "#intermediate_ca_ocsp_responder_url")
                .val(ocspInfo.url);
        section.find("#ocsp_responder_id").text(ocspInfo.id);

        var tempCertId = ocspInfo.ocspTempCertId;

        SDSB_CA_COMMON.showOcspResponderDeleteButton(section);

        if(tempCertId != null && tempCertId.length > 0) {
            SDSB_CA_CACHE.setFillableOcspResponderSection(section);

            SDSB_CA_COMMON.addOcspCertDetailsLink(function() {
                SDSB_CA_COMMON.openCaTempCertDetailsById(tempCertId);
            });
        } else if (ocspInfo.has_cert) {
            SDSB_CA_CACHE.setFillableOcspResponderSection(section);

            SDSB_CA_COMMON.addOcspCertDetailsLink(function() {
                renderExistingOcspCertDetails(ocspInfo.id);
            });
        }
    }

    /* -- Filling OCSP responder data - end -- */

    /* -- Dialog openers - start -- */

    function openTopCaCertDetails() {
        var params = {caId: SDSB_CA_CACHE.getEditable().id};

        $.get("approved_cas/get_top_ca_cert_dump_and_hash", params,
                function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
        }, "json");
    }

    function openNameExtractorDialogWithData() {
        if (caEditWizardCache.isNameExtractorCached()) {
            SDSB_CA_COMMON.openAddNameExtractorDialogWindow();
            return;
        }

        var params = {caId: SDSB_CA_CACHE.getEditable().id};

        $.get("approved_cas/get_name_extractor_data", params, function(response) {
            nameExtractor = response.data;

            if (nameExtractor.auth_only) {
                $("#ca_add_is_ssl_only").attr("checked", "checked");
                SDSB_CA_COMMON.disableNameExtractorInputs();
            }

            $("#ca_add_name_extractor_member_class")
                    .val(nameExtractor.member_class);
            $("#ca_add_extractor_method").val(nameExtractor.method_name);

            caEditWizardCache.cacheNameExtractor();
            SDSB_CA_COMMON.openAddNameExtractorDialogWindow();
        }, "json");
    }

    function openTopCaOcspRespondersWithData() {
        if (caEditWizardCache.areTopCaOcspInfosCached()) {
            SDSB_CA_COMMON.openAddTopCaOcspInfosDialogWindow();
            return;
        }

        addOcspRespondersWithData(
                SDSB_CA_CACHE.getEditable().topCaId, 
                "top_ca_ocsp_responder", 
                "ca_add_top_ca_ocsp_responder",
                function() {
            caEditWizardCache.cacheTopCaOcspInfos();
            SDSB_CA_COMMON.openAddTopCaOcspInfosDialogWindow();
        }); 
    }

    function openIntermediateCaOcspRespondersWithData(caId) {
        var intermediateCa = getIntermediateCaFromTable(caId);

        if (intermediateCa != null && intermediateCa.cached) {
            fillOcspResponderSections(
                    intermediateCa.ocspInfos,
                    "intermediate_ca_ocsp_responder",
                    "ca_add_intermediate_ca_ocsp_responder");
            return;
        }

        addOcspRespondersWithData(
                caId, 
                "intermediate_ca_ocsp_responder", 
                "ca_add_intermediate_ca_ocsp_responder",
                function() {/* Do nothing */}); 
    }

    function openIntermediateCasWithData() {
        if (caEditWizardCache.areIntermediateCasCached()) {
            SDSB_CA_COMMON.openAddIntermediateCasDialogWindow();
            return;
        }

        var params = {caId: SDSB_CA_CACHE.getEditable().id};

        $.get("approved_cas/get_intermediate_cas", params, function(response) {
            $.each(response.data, function(index, each){
                SDSB_CA_CACHE.addNewIntermediateCa(each);
            });

            caEditWizardCache.cacheIntmermediateCas();
            SDSB_CA_COMMON.openAddIntermediateCasDialogWindow();
        });
    }

    function openIntermediateCaCertDetails(intermediateCaId) {
        var params = {intermediateCaId: intermediateCaId};

        $.get("approved_cas/get_existing_intermediate_ca_cert_dump_and_hash",
                params, function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
        }, "json");
    }

    /* -- Dialog openers - end -- */

    /* -- PUBLIC - END -- */

    function addOcspRespondersWithData(
            caId, responderSectionClass, addButtonId, afterAddCallback) {
        var params = {caId: caId};
        $.get("approved_cas/get_ocsp_infos", params, function(response) {
            var responders = response.data;

            fillOcspResponderSections(
                    responders, responderSectionClass, addButtonId);

            afterAddCallback(responders.length);
        }, "json");
    }

    function renderExistingOcspCertDetails(ocspInfoId) {
        var params = {ocspInfoId: ocspInfoId};

        $.get("approved_cas/get_existing_ocsp_cert_details", params,
                function(response) {
            SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
            SDSB_CA_CACHE.clearFillableOcspResponderSection();
        }, "json");
    }

    function getIntermediateCaFromTable(intermediateCaId) {
        var intermediateCas = SDSB_CA_CACHE.getIntermediateCas();
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
        initEditingExistingCa: initEditingExistingCa,
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
