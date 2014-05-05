var oIntermediateCas;

/* Namespaces - start */

var pkiCache = function(){
    var uploadingOcspCert = false;
    var fillableOcspResponderFieldset;

    var closeableDialog;

    var topCaCertificateData;
    var saveableIntermediateCa;

    /* -- OCSP cert upload caching - start --- */

    function isUploadingOcspCert() {
        return uploadingOcspCert;
    }

    function setUploadingOcspCert(tempCertIdSelector) {
        uploadingOcspCert = true;
        fillableOcspResponderFieldset = tempCertIdSelector;
    }

    function getFillableOcspResponderFieldset() {
        return fillableOcspResponderFieldset;
    }

    function setFillableOcspResponderFieldset(selector) {
        fillableOcspResponderFieldset = selector;
    }

    function clearFillableOcspResponderFieldset() {
        fillableOcspResponderFieldset = null;
    }

    function clearUploadingOcspCert() {
        uploadingOcspCert = false;
        fillableOcspResponderFieldset = null;
    }

    function addTopCaCertificateData(data) {
        $("#top_ca_cert_temp_id").text(data.temp_cert_id);

        topCaCertificateData = {
            subjectDn: decorateCertDetails(data.subject),
            issuerDn: decorateCertDetails(data.issuer),
            validFrom: data.valid_from,
            validTo: data.expires
        };
    }

    function getTopCaCertificateData() {
        return topCaCertificateData;
    }

    function clearTopCaCertificateData() {
        topCaCertificateData = null;
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

        if (editingIntermediateCa()) {
            replaceEditedIntermediateCa(saveableIntermediateCa);
            $("#itermediate_ca_editing").text("");
        } else {
            addNewIntermediateCa(saveableIntermediateCa);
        }

        clearSaveableIntermediateCa();
    }

    function getNewIntermediateCas() {
        if (oIntermediateCas == null) {
            return [];
        }

        result = [];

        $.each(oIntermediateCas.fnGetData(), function(index, each) {
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

    function clearSaveableIntermediateCa() {
        saveableIntermediateCa = null;
    }

    function getSaveableIntermediateCaId() {
        if (saveableIntermediateCa == null) {
            return null;
        }

        return saveableIntermediateCa.id;
    }

    return {
        isUploadingOcspCert: isUploadingOcspCert,
        setUploadingOcspCert: setUploadingOcspCert,
        getFillableOcspResponderFieldset: getFillableOcspResponderFieldset,
        setFillableOcspResponderFieldset: setFillableOcspResponderFieldset,
        clearFillableOcspResponderFieldset: clearFillableOcspResponderFieldset,
        clearUploadingOcspCert: clearUploadingOcspCert,

        addTopCaCertificateData: addTopCaCertificateData,
        getTopCaCertificateData: getTopCaCertificateData,
        clearTopCaCertificateData: clearTopCaCertificateData,
        addIntermediateCaCertificateData: addIntermediateCaCertificateData,
        cacheIntermediateCaData: cacheIntermediateCaData,
        clearSaveableIntermediateCa: clearSaveableIntermediateCa,
        getSaveableIntermediateCaId: getSaveableIntermediateCaId,
        getNewIntermediateCas: getNewIntermediateCas
    };
}();

var newPki = function(){
    function initAdding() {
        openUploadTopCaCertDialog();
        clearSaveablePkiData();
        editPki.clearPkiEditing();
    }

    function save(closeableDialog) {
        var saveablePki = getSaveable(); 

        var params = {
            topCaTempCertId: saveablePki.topCaTempCertId,
            nameExtractor: JSON.stringify(saveablePki.nameExtractor),
            topCaOcspInfos: JSON.stringify(saveablePki.topCaOcspInfos),
            intermediateCas: JSON.stringify(saveablePki.intermediateCas)
        }

        $.post(action("save_new_pki"), params, function(){
            clearSaveablePkiData();
            refreshPkisTable();
            closeableDialog.dialog("close");
        }, "json");
    }


    function getSaveable() {
        return {
            topCaTempCertId: getTopCaTempCertId(),
            nameExtractor: getTopCaNameExtractor(),
            topCaOcspInfos: getTopCaOcspInfos(),
            intermediateCas: pkiCache.getNewIntermediateCas()
        };
    }

    return {
        initAdding: initAdding,
        save: save,
    }
}();

var editPki = function() {
    var editablePki = null;
    var deletableOcspInfos = [];
    var deletableIntermediateCas = [];

    /* -- Public functions - start --- */

    function editingExistingPki() {
        return editablePki != null && $.isNumeric(editablePki.id);
    }

    function initEditingExistingPki(pkiId, topCaId) {
        clearSaveablePkiData();
        setPkiEditing(pkiId, topCaId);
    }

    function clearPkiEditing() {
        $.post("pkis/clear_editing_pki", null, function() {
            editablePki = null;
            deletableOcspInfos = [];
            deletableIntermediateCas = [];
            pkiEditWizardCache.clear();
        }, "json");
    }

    function openTopCaCertDetails() {
        var params = {pkiId: editablePki.id};

        $.post("pkis/get_top_ca_cert_dump_and_hash", params, function(response) {
            openCertDetailsWindow(response.data);
        }, "json");
    }

    function openNameExtractorDialogWithData() {
        if (pkiEditWizardCache.isNameExtractorCached()) {
            openAddNameExtractorDialogWindow();
            return;
        }

        var params = {pkiId: editablePki.id};

        $.post("pkis/get_name_extractor_data", params, function(response) {
            nameExtractor = response.data;

            if (nameExtractor.auth_only) {
                $("#pki_add_is_ssl_only").attr("checked", "checked");
            }

            $("#pki_add_name_extractor_member_class")
                    .val(nameExtractor.member_class);
            $("#pki_add_extractor_method").val(nameExtractor.method_name);

            pkiEditWizardCache.cacheNameExtractor();
            openAddNameExtractorDialogWindow();
        }, "json");
    }

    function openTopCaOcspRespondersWithData() {
        if (pkiEditWizardCache.areTopCaOcspInfosCached()) {
            openAddTopCaOcspInfosDialogWindow();
            return;
        }

        addOcspRespondersWithData(
                editablePki.topCaId, 
                "top_ca_ocsp_responder", 
                "pki_add_top_ca_ocsp_responder",
                function() {
            pkiEditWizardCache.cacheTopCaOcspInfos();
            openAddTopCaOcspInfosDialogWindow();
        }); 
    }

    function openIntermediateCaOcspRespondersWithData(caId) {
        var intermediateCa = getIntermediateCaFromTable(caId);

        if (intermediateCa != null && intermediateCa.cached) {
            fillOcspResponderFieldsets(
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
            openAddIntermediateCasDialogWindow();
            return;
        }

        var params = {pkiId: editablePki.id};

        $.post("pkis/get_intermediate_cas", params, function(response) {
            $.each(response.data, function(index, each){
                addNewIntermediateCa(each);
            });

            pkiEditWizardCache.cacheIntmermediateCas();
            openAddIntermediateCasDialogWindow();
        });
    }

    function fillOcspResponderFieldsets(
            responders, responderFieldsetClass, addButtonId) {
        var sFieldsetSelector = "fieldset." + responderFieldsetClass

        $.each(responders, function(index, each){
            var responderFieldsets = $(sFieldsetSelector);

            if (index == 0) {
                fillOcspResponderFieldset(responderFieldsets.first(), each);
            } else {
                addOcspResponderFieldset(responderFieldsets, 
                        $("#" + addButtonId), each);
            }
        });

        if(responders.length > 0) {
            $(sFieldsetSelector).show();
        }

        enableOcspResponderAddButton();
    }

    function fillOcspResponderFieldset(fieldset, ocspInfo) {
        fieldset.find(
                "#top_ca_ocsp_responder_url, #intermediate_ca_ocsp_responder_url")
                .val(ocspInfo.url);
        fieldset.find("#ocsp_responder_id").text(ocspInfo.id);

        var tempCertId = ocspInfo.ocspTempCertId;

        showOcspResponderDeleteButton(fieldset);

        if(tempCertId != null && tempCertId.length > 0) {
            pkiCache.setFillableOcspResponderFieldset(fieldset);

            addOcspCertDetailsLink(function() {
                openPkiTempCertDetailsById(tempCertId);
            });
        } else if (ocspInfo.has_cert) {
            pkiCache.setFillableOcspResponderFieldset(fieldset);

            addOcspCertDetailsLink(function() {
                renderExistingOcspCertDetails(ocspInfo.id);
            });
        }
    }

    function editingExistingIntermediateCa(intermediateCaId) {
        return editingExistingPki() && intermediateCaId != null;
    }

    function openIntermediateCaCertDetails(intermediateCaId) {
        var params = {intermediateCaId: intermediateCaId};

        $.post(action("get_existing_intermediate_ca_cert_dump_and_hash"),
                params, function(response) {
            openCertDetailsWindow(response.data);
        }, "json");
    }

    function markOcspResponderForDeletion(ocspResponderFieldset) {
        if (!editingExistingPki()) {
            return;
        }

        var id = ocspResponderFieldset.find("#ocsp_responder_id").text();

        if (!$.isNumeric(id)) {
            return;
        }

        deletableOcspInfos.push(id);
    }

    function markIntermediateCaForDeletion(id) {
        if (!editingExistingPki() || !$.isNumeric(id)) {
            return;
        }

        deletableIntermediateCas.push(id);
    }

    function save(closeableDialog) {
        var params = {
            pki: JSON.stringify(getEditablePkiData()),
            ocspInfos: JSON.stringify(getEditableOcspInfos()),
            intermediateCas: JSON.stringify(getEditableIntermediateCas())
        }

        $.post("pkis/edit_existing_pki", params, function(){
            clearSaveablePkiData();
            clearPkiEditing();
            refreshPkisTable();
            closeableDialog.dialog("close");
        });
    }

    /* -- Public functions - end --- */

    function setPkiEditing(pkiId, topCaId) {
        $.post("pkis/set_editing_pki", null, function() {
            editablePki = {
                id: pkiId,
                topCaId: topCaId
            };

            getAndFillTopCaCertDetails();
        }, "json");
    }

    function getAndFillTopCaCertDetails() {
        var params = {pkiId: editablePki.id};

        $.post("pkis/get_top_ca_cert_details", params, function(response) {
            pkiCache.addTopCaCertificateData(response.data);
            fillTopCaCertDetails();
            openViewTopCaCertDialog();
        }, "json");
    }

    function addOcspRespondersWithData(
            caId, responderFieldsetClass, addButtonId, afterAddCallback) {
        var params = {caId: caId};
        $.post("pkis/get_ocsp_infos", params, function(response) {
            var responders = response.data;

            fillOcspResponderFieldsets(
                    responders, responderFieldsetClass, addButtonId);

            afterAddCallback(responders.length);
        }, "json");
    }

    function renderExistingOcspCertDetails(ocspInfoId) {
        var params = {ocspInfoId: ocspInfoId};

        $.post("pkis/get_existing_ocsp_cert_details", params, function(response) {
            openCertDetailsWindow(response.data);
            pkiCache.clearFillableOcspResponderFieldset();
        }, "json");
    }

    function getIntermediateCaFromTable(intermediateCaId) {
        var intermediateCas = oIntermediateCas.fnGetData();
        var count = intermediateCas.length;

        for (var i = 0; i < count; i++) {
            eachIntermediateCa = intermediateCas[i];
            if (eachIntermediateCa.id == intermediateCaId) {
                return eachIntermediateCa;
            }
        }

        return null;
    }

    /* Functions for getting editable data - start */

    function getEditablePkiData() {
        var nameExtractor = getTopCaNameExtractor();

        return {
            id: editablePki.id,
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

    function getEditableIntermediateCas() {
        return {
            delete: deletableIntermediateCas,
            update: getUpdatedIntermediateCas(),
            new: pkiCache.getNewIntermediateCas()
        }
    }

    function getNewAndUpdatedOcspInfos() {
        result = separateNewOcspInfosFromUpdatedOnes(
                getTopCaOcspInfos(), editablePki.topCaId);

        $.each(getEditableIntermediateCaOcspInfos(), function(index, each) {
            var separatedInfos = separateNewOcspInfosFromUpdatedOnes(
                    each.ocspInfos, each.caId);
            $.merge(result.updated , separatedInfos.updated);
            $.merge(result.new , separatedInfos.new);
        });

        return result;
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
        if (oIntermediateCas == null) {
            return [];
        }

        var result = []

        $.each(oIntermediateCas.fnGetData(), function(i, eachCa) {
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
        if (oIntermediateCas == null) {
            return [];
        }

        result = []

        $.each(oIntermediateCas.fnGetData(), function(index, each) {
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

    /* Functions for getting editable data - end */

    return {
        editingExistingPki: editingExistingPki,
        initEditingExistingPki: initEditingExistingPki,
        clearPkiEditing: clearPkiEditing,
        openTopCaCertDetails: openTopCaCertDetails,
        openNameExtractorDialogWithData: openNameExtractorDialogWithData,
        openTopCaOcspRespondersWithData: openTopCaOcspRespondersWithData,
        openIntermediateCaOcspRespondersWithData: 
                openIntermediateCaOcspRespondersWithData,
        openIntermediateCasWithData: openIntermediateCasWithData,
        fillOcspResponderFieldsets: fillOcspResponderFieldsets,
        fillOcspResponderFieldset: fillOcspResponderFieldset,
        editingExistingIntermediateCa: editingExistingIntermediateCa,
        openIntermediateCaCertDetails: openIntermediateCaCertDetails,

        markIntermediateCaForDeletion: markIntermediateCaForDeletion,
        markOcspResponderForDeletion: markOcspResponderForDeletion,
        save: save
    }
}();

/* Namespaces - end */

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

function initIntermediateCasTable() {
    if (oIntermediateCas != null) {
        oIntermediateCas.fnDestroy();
    }

    var opts = defaultOpts(onDrawIntermediateCas(), 1);
    opts.bPaginate = false;
    opts.sScrollX = "100%"
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

function getTopCaTempCertId() {
    return $("#top_ca_cert_temp_id").text()
}

function clearTopCaCertUploadForm() {
    $("#upload_top_ca_cert_file").val("");
}

function clearTopCaNameExtractorDialog() {
    $("#pki_add_is_ssl_only").val("");
    clearTopCaNameExtractorForm();
}

function uncacheIntermediateCaData() {
    if(oIntermediateCas != null) {
        oIntermediateCas.fnClearTable();
    }
}

function clearTopCaOcspInfosDialog() {
    clearOcspRespondersForm($("fieldset.top_ca_ocsp_responder"));
}


function getTopCaOcspInfos(){
    return getOcspInfos(
            $("fieldset.top_ca_ocsp_responder"), "top_ca_ocsp_responder_url");
}

function getIntermediateCaOcspInfos() {
    return getOcspInfos($("fieldset.intermediate_ca_ocsp_responder"),
            "intermediate_ca_ocsp_responder_url");
}

function getOcspInfos(ocspResponderFieldsets, urlId) {
    cachedOcspInfos = [];

    ocspResponderFieldsets.each(function(){
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

function clearIntermediateCaAddDialog() {
    var intermediateCaEditDialog = $("#intermediate_ca_edit_dialog");
    intermediateCaEditDialog.find("a.open_details").remove();

    intermediateCaEditDialog.find("#upload_intermediate_ca_cert_file").val("");
    intermediateCaEditDialog.find("#intermediate_ca_ocsp_responder_url").val("");
    intermediateCaEditDialog.find("#upload_ocsp_responder_cert_file").val("");

    intermediateCaEditDialog.find("p.ca_cert_detail").remove();
    $("#pki_intermediate_ca_cert_details").hide();

    var ocspRespondersId = "fieldset.intermediate_ca_ocsp_responder";
    clearOcspRespondersForm($(ocspRespondersId));
    $(ocspRespondersId).hide();

    enableIntermediateCaOcspResponderAddButton();
}

function clearOcspRespondersForm(responderFieldsets) {
    responderFieldsets.each(function(index){
        if(index == 0) {
            emptyOcspResponderFieldset($(this));
        } else {
            $(this).remove();
        }
    });
}

function emptyOcspResponderFieldset(fieldset) {
    fieldset.find("#top_ca_ocsp_responder_url").val("");
    fieldset.find("#intermediate_ca_ocsp_responder_url").val("");

    fieldset.find("#upload_ocsp_responder_cert_file").val("");
    fieldset.find("#ocsp_responder_cert_temp_id").text("");
    fieldset.find("#ocsp_responder_id").text("");
    fieldset.find("#ocsp_responder_cert_upload").disable();

    fieldset.find("#top_ca_ocsp_responder_delete").hide();
    fieldset.find("#intermediate_ca_ocsp_responder_delete").hide();

    fieldset.find(".open_details").remove();
}

function showOcspResponderDeleteButton(fieldset) {
    fieldset.find("#top_ca_ocsp_responder_delete," + 
            "#intermediate_ca_ocsp_responder_delete").show();
}

function enableOcspResponderAddButton() {
    $("#pki_add_top_ca_ocsp_responder," + 
    "#pki_add_intermediate_ca_ocsp_responder").enable();
}

function clearTopCaNameExtractorForm() {
    $("#pki_add_is_ssl_only").val("");
    $("#pki_add_name_extractor_member_class").val("");
    $("#pki_add_extractor_method").val("");
}

function clearPkiAddWizardFields() {
    clearTopCaCertUploadForm();
    clearTopCaNameExtractorDialog();
    clearTopCaOcspInfosDialog();
    clearIntermediateCaAddDialog();
}

// TODO: Probably it won't be necessary!
function clearSaveablePkiData() {
    clearPkiAddWizardFields();
    uncacheIntermediateCaData();
    pkiCache.clearTopCaCertificateData();
}

function getTopCaNameExtractor() {
    return {
        authOnly: $("#pki_add_is_ssl_only").attr("checked") ? true : false,
        memberClass: $("#pki_add_name_extractor_member_class").val(),
        extractorMethod: $("#pki_add_extractor_method").val()
    };
}

function fillTopCaCertDetails() {
    $("p.ca_cert_detail").remove();

    var certData = pkiCache.getTopCaCertificateData()
    var subjectDnTitleSelector = $("#pki_add_cert_details_subject_dn");
    addCertDetailsParts(certData.subjectDn, subjectDnTitleSelector);

    var issuerDnTitleSelector = $("#pki_add_cert_details_issuer_dn");
    addCertDetailsParts(certData.issuerDn, issuerDnTitleSelector);

    $("#pki_add_cert_details_valid_from").text(certData.validFrom);
    $("#pki_add_cert_details_valid_to").text(certData.validTo);
}

function fillIntermediateCaCertDetails(data) {
    $("p.ca_cert_detail").remove();
    $("#pki_intermediate_ca_cert_details").show();

    var subjectDnTitleSelector = $("#pki_add_intermediate_ca_cert_details_subject_dn");
    addCertDetailsParts(
            decorateCertDetails(data.subject),
            subjectDnTitleSelector);
}

function openPkiTempCertDetailsById(certId) {
    openTempCertDetailsById(certId, "pkis")
}

function addOcspCertDetailsLink(handleDetailsLinkClick) {
    var ocspResponderFieldset = 
        pkiCache.getFillableOcspResponderFieldset().closest("fieldset");
    ocspResponderFieldset.find("a.open_details").remove();

    var loadButtonSelector = ocspResponderFieldset
            .find("#ocsp_responder_cert_upload");
    loadButtonSelector.after(getCertDetailsLink(handleDetailsLinkClick));
}

function addIntermediateCaCertDetailsLink(handleDetailsLinkClick) {
    var buttonColumn = $("#pki_intermediate_ca_cert_upload").closest("td");
    buttonColumn.find("a.open_details").remove()

    $("#pki_intermediate_ca_cert_upload")
            .after(getCertDetailsLink(handleDetailsLinkClick));
}

function uploadCallbackPkiAddTopCaCert(response) {
    if (response.success) {
        pkiCache.addTopCaCertificateData(response.data);
        $("#add_pki_step1_upload_top_ca_cert_dialog").dialog("close");
        fillTopCaCertDetails();
        openViewTopCaCertDialog();
    } else {
        $("#pki_add_step1_next").disable();
    }

    showMessages(response.messages);
}

function uploadCallbackPkiAddOcspResponderCert(response) {
    if (response.success) {
        var tempCertId = response.data.temp_cert_id
        pkiCache.getFillableOcspResponderFieldset().text(tempCertId);

        addOcspCertDetailsLink(function() {
            openPkiTempCertDetailsById(tempCertId);
        });

        pkiCache.clearUploadingOcspCert();

        certsUploader.submitNextCertUpload();
    } else {
        pkiCache.clearUploadingOcspCert();
    }

    showMessages(response.messages);
}

function uploadCallbackPkiAddIntermediateCaCert(response) {
    $("#pki_intermediate_ca_cert_details").hide();

    if (response.success) {
        refreshIntermediateCaCert(response.data,
                pkiCache.getSaveableIntermediateCaId(), false);
        certsUploader.submitNextCertUpload();
    }

    showMessages(response.messages);
}

function refreshIntermediateCaCert(data, intermediateCaId, certSaved) {
    pkiCache.addIntermediateCaCertificateData(data, intermediateCaId);
    fillIntermediateCaCertDetails(data);

    if (certSaved) {
        addIntermediateCaCertDetailsLink(function() {
            editPki.openIntermediateCaCertDetails(intermediateCaId);
        });
    } else {
        addIntermediateCaCertDetailsLink(function() {
            openPkiTempCertDetailsById(data.temp_cert_id);
        });
    }
    $("#add_intermediate_ca_ok").enable();
}

function fillPkisNameExtractorMemberClassSelect() {
    $.post("application/member_classes", null, function(response){
        fillSelectWithEmptyOption("pki_add_name_extractor_member_class",
                response.data);
    }, "json");
}

function disableNameExtractorInputs() {
    $("#name_extractor_data :input").disable();
    $("#name_extractor_data").addClass("inactive");

    clearTopCaNameExtractorForm();
}

function enableNameExtractorInputs() {
    $("#name_extractor_data :input").enable();
    $("#name_extractor_data").removeClass("inactive");
}

function enableIntermediateCaOcspResponderAddButton() {
    $("pki_add_intermediate_ca_ocsp_responder").enable();
}

function addOcspResponderFieldset(existingFieldsets, addButton, ocspInfo) {
    var lastResponderFieldset = existingFieldsets.last();
    var newResponderFieldset = lastResponderFieldset.clone();

    emptyOcspResponderFieldset(newResponderFieldset);

    if (ocspInfo != null) {
        editPki.fillOcspResponderFieldset(newResponderFieldset, ocspInfo);
    }

    lastResponderFieldset.after(newResponderFieldset);
    addButton.disable();
}

function handleOcspResponderFormButtonsVisibility(
        urlSelector, responderAddButton) {
    var url = urlSelector.val();
    var responderDeleteButton =
        urlSelector.closest("fieldset").find("legend > button");

    if(url.length > 0) {
        responderDeleteButton.show();
        responderAddButton.enable();
    } else {
        responderDeleteButton.hide();
        responderAddButton.disable();
    }
}

function handleOcspResponderElementDeletion(deletionCtx) {
    responderFieldsetId = "fieldset." + deletionCtx.responderFieldsetClass;
    deleteButton = deletionCtx.deleteButton;

    var isMoreThanOneResponder = 
        $(responderFieldsetId).length > 1;

    var ocspResponderFieldset = 
        deleteButton.closest(responderFieldsetId);

    editPki.markOcspResponderForDeletion(ocspResponderFieldset);

    var addButton = deletionCtx.addButton;

    if(isMoreThanOneResponder) {
        ocspResponderFieldset.remove();
    } else {
        emptyOcspResponderFieldset(ocspResponderFieldset);
        deleteButton.hide();
        addButton.disable();
    }

    if(isLastOcspResponderFieldsetEmpty(
            ocspResponderFieldset, deletionCtx.urlId)) {
        addButton.disable();
    }
}

function isLastOcspResponderFieldsetEmpty(ocspResponderFieldset, urlId) {
    var url = ocspResponderFieldset.last().find("#" + urlId).val();
    return url == null || url == ""; 
}

function areNoRespondersInserted(ocspResponderFieldsets, urlId) {
    return ocspResponderFieldsets.length <= 1 
            && isLastOcspResponderFieldsetEmpty(ocspResponderFieldsets, urlId);
}

function editingIntermediateCa() {
    return $("#itermediate_ca_editing").text() != "";
}

function addNewIntermediateCa(intermediateCa) {
    var intermediateCasPresent = oIntermediateCas.fnGetData().length;
    intermediateCa.elementId = intermediateCasPresent;
    oIntermediateCas.fnAddData(intermediateCa);
}

function openIntermediateCaEditDialog(editableIntermediateCa) {
    $("#itermediate_ca_editing").text(editableIntermediateCa.elementId);

    var caId = editableIntermediateCa.id

    if (editPki.editingExistingIntermediateCa(caId)) {
        var params = {intermediateCaId: caId};

        $.post(action("get_existing_intermediate_ca_cert_details"), params,
                function(response){
            openIntermediateCaAddOrEditDialog();
            refreshIntermediateCaCert(response.data, caId, true);
            editPki.openIntermediateCaOcspRespondersWithData(caId);
        }, "json");

    } else {
        var params = {
            intermediateCaTempCertId: editableIntermediateCa.temp_cert_id
        };

        $.post(action("get_intermediate_ca_temp_cert_details"), params,
                function(response){
            var certData = response.data;
            openIntermediateCaAddOrEditDialog();
            refreshIntermediateCaCert(certData, null, false);
            editPki.fillOcspResponderFieldsets(
                    editableIntermediateCa.ocspInfos,
                    "intermediate_ca_ocsp_responder",
                    "pki_add_intermediate_ca_ocsp_responder");
        }, "json");
    }
}

function replaceEditedIntermediateCa(newIntermediateCa) {
    var replaceableCaId = $("#itermediate_ca_editing").text();
    newIntermediateCa.elementId = replaceableCaId;

    var intermediateCasData = oIntermediateCas.fnGetData()

    $.each(intermediateCasData, function(index, each){
        if (each.elementId == replaceableCaId) {
            oIntermediateCas.fnUpdate(newIntermediateCa, index);
            return;
        }
    });
}

function deleteActiveIntermediateCa() {
    var deletableRow = oIntermediateCas.getFocus();

    editPki.markIntermediateCaForDeletion(getActiveIntermediateCaId());

    var index = oIntermediateCas.fnGetPosition(deletableRow);
    oIntermediateCas.fnDeleteRow(index);
}

function getActiveIntermediateCaId() {
    return oIntermediateCas.getFocusData().id;
}

function savePki(closeableDialog) {
    if (editPki.editingExistingPki()) {
        editPki.save(closeableDialog);
    } else {
        newPki.save(closeableDialog);
    }
}

// -- Dialogs for adding steps - start ---

function openUploadTopCaCertDialog() {
    $("#add_pki_step1_upload_top_ca_cert_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: "auto",
        width: "auto",
        buttons: [
            { text: _("cancel"),
              click: function() {
                  clearSaveablePkiData();
                  $(this).dialog("close");
              }
            },
            { text: _("next"),
              id: "pki_add_step1_next",
              disabled: "disabled",
              click: function() {
                  $("#top_ca_cert_upload").submit();
              }
            }
        ]
    }).dialog("open");
}

function openViewTopCaCertDialog() {
    $("#add_pki_step2_view_top_ca_cert_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: "auto",
        width: 300,
        buttons: [
            { text: _("cancel"),
              id: "view_top_ca_cert_button_cancel",
              click: function() {
                  clearSaveablePkiData();
                  $(this).dialog("close");
              }
            },
            { text: _("back"),
              id: "view_top_ca_cert_button_back",
              click: function() {
                  $(this).dialog("close");
                  openUploadTopCaCertDialog();
                  $("#pki_add_step1_next").enable();
              }
            },
            { text: _("next"),
              click: function() {
                  $(this).dialog("close");
                  openAddNameExtractorDialog();
              }
            }
        ]
    }).dialog("open");

    if (editPki.editingExistingPki()) {
        $("#view_top_ca_cert_button_cancel").hide();
        $("#view_top_ca_cert_button_back").hide();
    }
}

function openAddNameExtractorDialog() {
    var dialog = $("#add_pki_step3_add_name_extractor_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: "auto",
        width: "auto",
        buttons: [
            { text: _("cancel"),
              click: function() {
                    clearSaveablePkiData();
                    $(this).dialog("close");
              }
            },
            { text: _("back"),
              click: function() {
                  $(this).dialog("close");
                  openViewTopCaCertDialog();
              }
            },
            { text: _("next"),
              click: function() {
                  $(this).dialog("close");
                  openAddTopCaOcspInfosDialog();
              }
            },
            { text: _("finish"),
              click: function() {
                  savePki($(this));
              }
            }
        ]
    }).dialog("open");

    if (editPki.editingExistingPki()) {
        editPki.openNameExtractorDialogWithData();
    } else {
        $("#pki_add_is_ssl_only").removeAttr("checked");
        dialog.dialog("open");
    }
}

function openAddNameExtractorDialogWindow() {
    $("#add_pki_step3_add_name_extractor_dialog").dialog("open")
}

function openAddTopCaOcspInfosDialog() {
    var dialog = $("#add_pki_step4_add_top_ca_ocsp_responders").initDialog({
        autoOpen: false,
        modal: true,
        height: "auto",
        width: "auto",
        buttons: [
            { text: _("cancel"),
              click: function() {
                  clearSaveablePkiData();
                  $(this).dialog("close");
              }
            },
            { text: _("back"),
              click: function() {
                  $(this).dialog("close");
                  openAddNameExtractorDialog();
              }
            },
            { text: _("next"),
              click: function() {
                  var dialog = $(this);
                  certsUploader.initSubmittingCerts(function(){
                      openAddIntermediateCasDialog();
                      dialog.dialog("close");
                  });
              }
            },
            { text: _("finish"),
              click: function() {
                  var dialog = $(this);
                  certsUploader.initSubmittingCerts(function(){
                      savePki(dialog);
                  });
              }
            }
        ]
    });

    if (editPki.editingExistingPki()) {
        editPki.openTopCaOcspRespondersWithData();
    } else {
        dialog.dialog("open");
    }
}

function openAddTopCaOcspInfosDialogWindow() {
    $("#add_pki_step4_add_top_ca_ocsp_responders").dialog("open");
}

function openAddIntermediateCasDialog() {
    if (!pkiEditWizardCache.areIntermediateCasCached()) {
        initIntermediateCasTable();
    }

    var dialog = $("#add_pki_step5_add_intermediate_cas").initDialog({
        autoOpen: false,
        modal: true,
        height: "auto",
        width: "50%",
        buttons: [
            { text: _("cancel"),
              click: function() {
                  clearSaveablePkiData();
                  $(this).dialog("close");
              }
            },
            { text: _("back"),
              click: function() {
                  $(this).dialog("close");
                  openAddTopCaOcspInfosDialog();
              }
            },
            { text: _("finish"),
              click: function() {
                  savePki($(this));
              }
            }
        ]
    });

    if (editPki.editingExistingPki()) {
        editPki.openIntermediateCasWithData();
    } else {
        dialog.dialog("open");
    }
}

function openAddIntermediateCasDialogWindow() {
    $("#add_pki_step5_add_intermediate_cas").dialog("open");
}

function openIntermediateCaAddOrEditDialog() {
    clearIntermediateCaAddDialog();

    var title = editingIntermediateCa() ? 
            _("pkis.intermediate_ca.edit.title") :
            _("pkis.intermediate_ca.new.title");

    $("#intermediate_ca_edit_dialog").initDialog({
        autoOpen: false,
        modal: true,
        title: _("pkis.intermediate_ca.new.title"),
        height: "auto",
        width: "auto",
        buttons: [
            { text: _("ok"),
              id: "add_intermediate_ca_ok",
              disabled: "disabled",
              click: function() {
                  var dialog = $(this);
                  certsUploader.initSubmittingCerts(function(){
                      pkiCache.cacheIntermediateCaData();
                      dialog.dialog("close");
                  });
              }
            },
            { text: _("cancel"),
              click: function() {
                  pkiCache.clearSaveableIntermediateCa();
                  $(this).dialog("close");
              }
            }
        ]
    }).dialog("open");
}

// -- Dialogs for adding steps - end ---

$(document).ready(function() {
    fillPkisNameExtractorMemberClassSelect();

    $("#upload_top_ca_cert_file").live("click", function() {
        $("#pki_add_step1_next").enable();
    });

    $("#pki_add_open_top_ca_cert_details").live("click", function() {
        if (editPki.editingExistingPki()) {
            editPki.openTopCaCertDetails();
        } else {
            openPkiTempCertDetailsById(getTopCaTempCertId());
        }
    });

    $("#pki_add_is_ssl_only").live("click", function(){ 
        if($(this).attr("checked")) {
            disableNameExtractorInputs();
        } else {
            enableNameExtractorInputs();
        }  
    });

    $("#top_ca_ocsp_responder_url").live("keyup", function(){
        handleOcspResponderFormButtonsVisibility(
                $(this), $("#pki_add_top_ca_ocsp_responder"));
    });

    $("#intermediate_ca_ocsp_responder_url").live("keyup", function(){
        handleOcspResponderFormButtonsVisibility(
                $(this), $("#pki_add_intermediate_ca_ocsp_responder"));
    });

    $('#upload_ocsp_responder_cert_file').live("change", function(){ 
        certsUploader.manageCertFileSelection($(this));
    });

    $('#upload_intermediate_ca_cert_file').live("change", function(){
        if (isInputFilled($(this))) {
            $("#add_intermediate_ca_ok").enable();
        }

        certsUploader.manageCertFileSelection($(this));
    });

    $("#ocsp_responder_cert_upload").live("click", function(){
        if (pkiCache.isUploadingOcspCert()) {
            alert("pkis.add.ocsp_responders.uploading");
            return;
        }

        pkiCache.setUploadingOcspCert($(this).closest("fieldset")
              .find("#ocsp_responder_cert_temp_id"));

        var uploadForm = $(this).closest("table")
                .find("#ocsp_responder_cert_upload_form");

        uploadForm.submit();
    });

    $("#pki_add_top_ca_ocsp_responder").live("click", function(){
        addOcspResponderFieldset($("fieldset.top_ca_ocsp_responder"), $(this));
    });

    $("#pki_add_intermediate_ca_ocsp_responder").live("click", function(){
        ocspResponderFieldsets = $("fieldset.intermediate_ca_ocsp_responder");

        if(areNoRespondersInserted(ocspResponderFieldsets,
                "intermediate_ca_ocsp_responder_url")) {
            ocspResponderFieldsets.show();
            $(this).disable();
        } else {
            addOcspResponderFieldset(ocspResponderFieldsets, $(this));
        }
    });

    $("#top_ca_ocsp_responder_delete").live("click", function(){
        handleOcspResponderElementDeletion({
                deleteButton: $(this),
                addButton: $("#pki_add_top_ca_ocsp_responder"),
                responderFieldsetClass: "top_ca_ocsp_responder",
                urlId: "top_ca_ocsp_responder_url"});
    });

    $("#intermediate_ca_ocsp_responder_delete").live("click", function(){
        handleOcspResponderElementDeletion({
                deleteButton: $(this),
                addButton: $("#pki_add_intermediate_ca_ocsp_responder"),
                responderFieldsetClass: "intermediate_ca_ocsp_responder",
                urlId: "intermediate_ca_ocsp_responder_url"});
    });

    $("#intermediate_ca_add").live("click", function(){
        openIntermediateCaAddOrEditDialog();
    });

    $("#pki_intermediate_ca_cert_upload").live("click", function(){
        $("#intermediate_ca_cert_upload").submit();
    });

    $("#intermediate_cas tbody tr").live("click", function(ev){
        if (!oIntermediateCas.setFocus(0, ev.target.parentNode)) {
            $(".intermediate_ca-action").disable();
        }
    });

    $("#intermediate_cas tbody tr").live("dblclick", function(){
        openIntermediateCaEditDialog(oIntermediateCas.getFocusData());
    });

    $("#intermediate_ca_edit").live("click", function(){
        openIntermediateCaEditDialog(oIntermediateCas.getFocusData());
    });

    $("#intermediate_ca_delete").live("click", function(){
        deleteActiveIntermediateCa();
    });
});
