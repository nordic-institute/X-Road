var SDSB_PKI_COMMON = function() {
    /* -- PUBLIC - START -- */

    /* -- UI manipulation - start -- */

    function addOcspCertDetailsLink(handleDetailsLinkClick) {
        var ocspResponderSection = 
            SDSB_PKI_CACHE.getFillableOcspResponderSection().closest("section");
        ocspResponderSection.find("a.open_details").remove();

        var loadButtonSelector = ocspResponderSection
                .find("#ocsp_responder_cert_upload");
        loadButtonSelector.after(
                SDSB_CENTERUI_COMMON.getCertDetailsLink(
                        handleDetailsLinkClick));
    }

    function addOcspResponderSection(existingSections, addButton, ocspInfo) {
        var lastResponderSection = existingSections.last();
        var newResponderSection = lastResponderSection.clone();

        emptyOcspResponderSection(newResponderSection);

        if (ocspInfo != null) {
            SDSB_PKI_EDIT.fillOcspResponderSection(
                    newResponderSection, ocspInfo);
        }

        lastResponderSection.after(newResponderSection);
        addButton.disable();
    }

    function showOcspResponderDeleteButton(section) {
        section.find("#top_ca_ocsp_responder_delete," + 
                "#intermediate_ca_ocsp_responder_delete").show();
    }

    function clearSaveablePkiData() {
        clearPkiAddWizardFields();
        SDSB_PKI_CACHE.uncacheIntermediateCaData();
        SDSB_PKI_CACHE.clearTopCaCertificateData();
    }

    function fillTopCaCertDetails() {
        $("p.ca_cert_detail").remove();

        var certData = SDSB_PKI_CACHE.getTopCaCertificateData()
        var subjectDnTitleSelector = $("#pki_add_cert_details_subject_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                certData.subjectDn, subjectDnTitleSelector);

        var issuerDnTitleSelector = $("#pki_add_cert_details_issuer_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                certData.issuerDn, issuerDnTitleSelector);

        $("#pki_add_cert_details_valid_from").text(certData.validFrom);
        $("#pki_add_cert_details_valid_to").text(certData.validTo);
    }

    function disableNameExtractorInputs() {
        $("#pki_add_name_extractor_member_class").disable();
        $("#pki_add_extractor_method").disable();
        $("#name_extractor_data").addClass("inactive");

        clearTopCaNameExtractorForm();
    }

    function enableOcspResponderAddButton() {
        $("#pki_add_top_ca_ocsp_responder," + 
        "#pki_add_intermediate_ca_ocsp_responder").enable();
    }

    /* -- UI manipulation - end -- */

    /* -- Upload callbacks - start -- */

    function uploadCallbackPkiAddTopCaCert(response) {
        if (response.success) {
            SDSB_PKI_CACHE.addTopCaCertificateData(response.data);
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
            SDSB_PKI_CACHE.getFillableOcspResponderSection().text(tempCertId);

            addOcspCertDetailsLink(function() {
                openPkiTempCertDetailsById(tempCertId);
            });

            SDSB_PKI_CACHE.clearUploadingOcspCert();

            SDSB_CERTS_UPLOADER.submitNextCertUpload();
        } else {
            SDSB_PKI_CACHE.clearUploadingOcspCert();
        }

        showMessages(response.messages);
    }

    function uploadCallbackPkiAddIntermediateCaCert(response) {
        $("#pki_intermediate_ca_cert_details").hide();

        if (response.success) {
            refreshIntermediateCaCert(response.data,
                    SDSB_PKI_CACHE.getSaveableIntermediateCaId(), false);
            SDSB_CERTS_UPLOADER.submitNextCertUpload();
        }

        showMessages(response.messages);
    }

    /* -- Upload callbacks - end -- */

    /* -- Dialog openers - start -- */

    function openUploadTopCaCertDialog() {
        $("#add_pki_step1_upload_top_ca_cert_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.next"),
                  id: "pki_add_step1_next",
                  disabled: "disabled",
                  click: function() {
                      $("#top_ca_cert_upload").submit();
                  }
                },
                { text: _("common.cancel"),
                    click: function() {
                        clearSaveablePkiData();
                        $(this).dialog("close");
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
            width: 375,
            buttons: [
                { text: _("common.next"),
                    click: function() {
                        $(this).dialog("close");
                        openAddNameExtractorDialog();
                    }
                },
                { text: _("common.back"),
                  id: "view_top_ca_cert_button_back",
                  click: function() {
                      $(this).dialog("close");
                      openUploadTopCaCertDialog();
                      $("#pki_add_step1_next").enable();
                  }
                },
                { text: _("common.cancel"),
                    id: "view_top_ca_cert_button_cancel",
                    click: function() {
                        clearSaveablePkiData();
                        $(this).dialog("close");
                    }
                }
            ]
        }).dialog("open");

        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
            $("#view_top_ca_cert_button_back").hide();
        }
    }

    function openAddTopCaOcspInfosDialogWindow() {
        $("#add_pki_step4_add_top_ca_ocsp_responders").dialog("open");
    }

    function openAddNameExtractorDialogWindow() {
        $("#add_pki_step3_add_name_extractor_dialog").dialog("open")
    }

    function openAddIntermediateCasDialogWindow() {
        $("#add_pki_step5_add_intermediate_cas").dialog("open");
    }

    function openIntermediateCaEditDialog(editableIntermediateCa) {
        $("#itermediate_ca_editing").text(editableIntermediateCa.elementId);

        var caId = editableIntermediateCa.id

        if (SDSB_PKI_CACHE.editingExistingIntermediateCa(caId)) {
            var params = {intermediateCaId: caId};

            $.get("pkis/get_existing_intermediate_ca_cert_details", params,
                    function(response){
                openIntermediateCaAddOrEditDialog();
                refreshIntermediateCaCert(response.data, caId, true);
                SDSB_PKI_EDIT.openIntermediateCaOcspRespondersWithData(caId);
            }, "json");

        } else {
            var params = {
                intermediateCaTempCertId: editableIntermediateCa.temp_cert_id
            };

            $.get("pkis/get_intermediate_ca_temp_cert_details", params,
                    function(response){
                var certData = response.data;
                openIntermediateCaAddOrEditDialog();
                refreshIntermediateCaCert(certData, null, false);
                SDSB_PKI_EDIT.fillOcspResponderSections(
                        editableIntermediateCa.ocspInfos,
                        "intermediate_ca_ocsp_responder",
                        "pki_add_intermediate_ca_ocsp_responder");
            }, "json");
        }
    }

    function openPkiTempCertDetailsById(certId) {
        SDSB_CENTERUI_COMMON.openTempCertDetailsById(certId, "pkis")
    }

    /* -- Dialog openers - end -- */

    /* -- PUBLIC - END -- */

    function clearTopCaCertUploadForm() {
        $("#upload_top_ca_cert_file").val("");
    }

    function clearTopCaNameExtractorDialog() {
        $("#pki_add_is_ssl_only").removeAttr("checked");
        clearTopCaNameExtractorForm();
        enableNameExtractorInputs();
    }

    function clearTopCaOcspInfosDialog() {
        clearOcspRespondersForm($("section.top_ca_ocsp_responder"));
    }

    function clearIntermediateCaAddDialog() {
        var intermediateCaEditDialog = $("#intermediate_ca_edit_dialog");
        intermediateCaEditDialog.find("a.open_details").remove();

        intermediateCaEditDialog.find(
                "#upload_intermediate_ca_cert_file").val("");
        intermediateCaEditDialog.find(
                "#intermediate_ca_ocsp_responder_url").val("");
        intermediateCaEditDialog.find(
                "#upload_ocsp_responder_cert_file").val("");

        intermediateCaEditDialog.find(
                "#pki_intermediate_ca_cert_upload").disable();

        intermediateCaEditDialog.find("p.ca_cert_detail").remove();
        $("#pki_intermediate_ca_cert_details").hide();

        var ocspRespondersId = "section.intermediate_ca_ocsp_responder";
        clearOcspRespondersForm($(ocspRespondersId));
        $(ocspRespondersId).hide();

        enableIntermediateCaOcspResponderAddButton();
    }

    function clearOcspRespondersForm(responderSections) {
        responderSections.each(function(index){
            if(index == 0) {
                emptyOcspResponderSection($(this));
            } else {
                $(this).remove();
            }
        });
    }

    function emptyOcspResponderSection(section) {
        section.find("#top_ca_ocsp_responder_url").val("");
        section.find("#intermediate_ca_ocsp_responder_url").val("");

        section.find("#upload_ocsp_responder_cert_file").val("");
        section.find("#ocsp_responder_cert_temp_id").text("");
        section.find("#ocsp_responder_id").text("");
        section.find("#ocsp_responder_cert_upload").disable();

        section.find("#top_ca_ocsp_responder_delete").hide();
        section.find("#intermediate_ca_ocsp_responder_delete").hide();

        section.find(".open_details").remove();
    }

    function clearTopCaNameExtractorForm() {
        $("#pki_add_name_extractor_member_class").val("");
        $("#pki_add_extractor_method").val("");
    }

    function clearPkiAddWizardFields() {
        clearTopCaCertUploadForm();
        clearTopCaNameExtractorDialog();
        clearTopCaOcspInfosDialog();
        clearIntermediateCaAddDialog();
    }

    function fillIntermediateCaCertDetails(data) {
        $("p.ca_cert_detail").remove();
        $("#pki_intermediate_ca_cert_details").show();

        var subjectDnTitleSelector = $(
                "#pki_add_intermediate_ca_cert_details_subject_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                SDSB_CENTERUI_COMMON.decorateCertDetails(data.subject),
                subjectDnTitleSelector);
    }

    function addIntermediateCaCertDetailsLink(handleDetailsLinkClick) {
        var buttonColumn = $("#pki_intermediate_ca_cert_upload").closest("td");
        buttonColumn.find("a.open_details").remove()

        $("#pki_intermediate_ca_cert_upload")
                .after(SDSB_CENTERUI_COMMON.getCertDetailsLink(
                        handleDetailsLinkClick));
    }

    function refreshIntermediateCaCert(data, intermediateCaId, certSaved) {
        SDSB_PKI_CACHE.addIntermediateCaCertificateData(data, intermediateCaId);
        fillIntermediateCaCertDetails(data);

        if (certSaved) {
            addIntermediateCaCertDetailsLink(function() {
                SDSB_PKI_EDIT.openIntermediateCaCertDetails(intermediateCaId);
            });
        } else {
            addIntermediateCaCertDetailsLink(function() {
                openPkiTempCertDetailsById(data.temp_cert_id);
            });
        }
        $("#add_intermediate_ca_ok").enable();
    }

    function fillPkisNameExtractorMemberClassSelect() {
        $.get("application/member_classes", null, function(response){
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    "pki_add_name_extractor_member_class", response.data);
        }, "json");
    }

    function enableNameExtractorInputs() {
        $("#pki_add_name_extractor_member_class").enable();
        $("#pki_add_extractor_method").enable();
        $("#name_extractor_data").removeClass("inactive");
    }

    function enableIntermediateCaOcspResponderAddButton() {
        $("pki_add_intermediate_ca_ocsp_responder").enable();
    }

    function handleOcspResponderFormButtonsVisibility(
            urlSelector, responderAddButton) {
        var url = urlSelector.val();
        var responderDeleteButton =
            urlSelector.closest("section").find("legend > button");

        if(url.length > 0) {
            responderDeleteButton.show();
            responderAddButton.enable();
        } else {
            responderDeleteButton.hide();
            responderAddButton.disable();
        }
    }

    function handleOcspResponderElementDeletion(deletionCtx) {
        var responderSectionId = "section." + deletionCtx.responderSectionClass;
        var deleteButton = deletionCtx.deleteButton;

        var isMoreThanOneResponder = 
            $(responderSectionId).length > 1;

        var ocspResponderSection = 
            deleteButton.closest(responderSectionId);

        SDSB_PKI_CACHE.markOcspResponderForDeletion(ocspResponderSection);

        var addButton = deletionCtx.addButton;

        if(isMoreThanOneResponder) {
            ocspResponderSection.remove();
        } else {
            emptyOcspResponderSection(ocspResponderSection);
            deleteButton.hide();
            addButton.disable();
        }

        if(isLastOcspResponderSectionEmpty(
                ocspResponderSection, deletionCtx.urlId)) {
            addButton.disable();
        }
    }

    function isLastOcspResponderSectionEmpty(ocspResponderSection, urlId) {
        var url = ocspResponderSection.last().find("#" + urlId).val();
        return url == null || url == ""; 
    }

    function areNoRespondersInserted(ocspResponderSections, urlId) {
        return ocspResponderSections.length <= 1 
                && isLastOcspResponderSectionEmpty(
                        ocspResponderSections, urlId);
    }

    function savePki(closeableDialog) {
        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
            SDSB_PKI_EDIT.save(closeableDialog);
        } else {
            SDSB_PKI_NEW.save(closeableDialog);
        }

        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
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
                { text: _("common.finish"),
                    click: function() {
                        savePki($(this));
                    }
                },
                { text: _("common.next"),
                    click: function() {
                        $(this).dialog("close");
                        openAddTopCaOcspInfosDialog();
                    }
                },
                { text: _("common.back"),
                    click: function() {
                        $(this).dialog("close");
                        openViewTopCaCertDialog();
                    }
                },
                { text: _("common.cancel"),
                  click: function() {
                        clearSaveablePkiData();
                        $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");

        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
            SDSB_PKI_EDIT.openNameExtractorDialogWithData();
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
                { text: _("common.finish"),
                    click: function() {
                        var dialog = $(this);
                        SDSB_CERTS_UPLOADER.initSubmittingCerts(function(){
                            savePki(dialog);
                        });
                    }
                },
                { text: _("common.next"),
                    click: function() {
                        var dialog = $(this);
                        SDSB_CERTS_UPLOADER.initSubmittingCerts(function(){
                            openAddIntermediateCasDialog();
                            dialog.dialog("close");
                        });
                    }
                },
                { text: _("common.back"),
                    click: function() {
                        $(this).dialog("close");
                        openAddNameExtractorDialog();
                    }
                },
                { text: _("common.cancel"),
                  click: function() {
                      clearSaveablePkiData();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
            SDSB_PKI_EDIT.openTopCaOcspRespondersWithData();
        } else {
            dialog.dialog("open");
        }
    }

    function openAddIntermediateCasDialog() {
        if (!SDSB_PKI_EDIT.areIntermediateCasCached()) {
            SDSB_PKI_CACHE.initIntermediateCasTable();
        }

        var dialog = $("#add_pki_step5_add_intermediate_cas").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "50%",
            buttons: [
                { text: _("common.finish"),
                    click: function() {
                        savePki($(this));
                    }
                },
                { text: _("common.back"),
                    click: function() {
                        $(this).dialog("close");
                        openAddTopCaOcspInfosDialog();
                    }
                },
                { text: _("common.cancel"),
                  click: function() {
                      clearSaveablePkiData();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        if (SDSB_PKI_CACHE.isEditingExistingPki()) {
            SDSB_PKI_EDIT.openIntermediateCasWithData();
        } else {
            dialog.dialog("open");
        }
    }

    function openIntermediateCaAddOrEditDialog() {
        clearIntermediateCaAddDialog();

        var title = SDSB_PKI_CACHE.isEditingIntermediateCa() ?
                _("pkis.intermediate_ca_edit_title") :
                _("pkis.intermediate_ca_new_title");

        $("#intermediate_ca_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            title: title,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  id: "add_intermediate_ca_ok",
                  disabled: "disabled",
                  click: function() {
                      var dialog = $(this);
                      SDSB_CERTS_UPLOADER.initSubmittingCerts(function(){
                          SDSB_PKI_CACHE.cacheIntermediateCaData();
                          dialog.dialog("close");
                      });
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      SDSB_PKI_CACHE.clearSaveableIntermediateCa();
                      $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");
    }

    $(document).ready(function() {
        fillPkisNameExtractorMemberClassSelect();

        $("#upload_top_ca_cert_file").live("click", function() {
            $("#pki_add_step1_next").enable();
        });

        $("#pki_add_open_top_ca_cert_details").live("click", function() {
            if (SDSB_PKI_CACHE.isEditingExistingPki()) {
                SDSB_PKI_EDIT.openTopCaCertDetails();
            } else {
                openPkiTempCertDetailsById(SDSB_PKI_CACHE.getTopCaTempCertId());
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
            SDSB_CERTS_UPLOADER.manageCertFileSelection($(this));
        });

        $('#upload_intermediate_ca_cert_file').live("change", function(){
            var okButton = $("#add_intermediate_ca_ok");
            var uploadButton = $("#pki_intermediate_ca_cert_upload");

            if (isInputFilled($(this))) {
                okButton.enable();
                uploadButton.enable();
            } else {
                okButton.disable();
                uploadButton.disable();
            }

            SDSB_CERTS_UPLOADER.manageCertFileSelection($(this));
        });

        $("#ocsp_responder_cert_upload").live("click", function(){
            if (SDSB_PKI_CACHE.isUploadingOcspCert()) {
                alert("pkis.add.ocsp_responders.uploading");
                return;
            }

            SDSB_PKI_CACHE.setUploadingOcspCert($(this).closest("section")
                  .find("#ocsp_responder_cert_temp_id"));

            var uploadForm = $(this).closest("table")
                    .find("#ocsp_responder_cert_upload_form");

            uploadForm.submit();
        });

        $("#pki_add_top_ca_ocsp_responder").live("click", function(){
            addOcspResponderSection(
                    $("section.top_ca_ocsp_responder"), $(this));
        });

        $("#pki_add_intermediate_ca_ocsp_responder").live("click", function(){
            ocspResponderSections = $("section.intermediate_ca_ocsp_responder");

            if(areNoRespondersInserted(ocspResponderSections,
                    "intermediate_ca_ocsp_responder_url")) {
                ocspResponderSections.show();
                $(this).disable();
            } else {
                addOcspResponderSection(ocspResponderSections, $(this));
            }
        });

        $("#top_ca_ocsp_responder_delete").live("click", function(){
            handleOcspResponderElementDeletion({
                    deleteButton: $(this),
                    addButton: $("#pki_add_top_ca_ocsp_responder"),
                    responderSectionClass: "top_ca_ocsp_responder",
                    urlId: "top_ca_ocsp_responder_url"});
        });

        $("#intermediate_ca_ocsp_responder_delete").live("click", function(){
            handleOcspResponderElementDeletion({
                    deleteButton: $(this),
                    addButton: $("#pki_add_intermediate_ca_ocsp_responder"),
                    responderSectionClass: "intermediate_ca_ocsp_responder",
                    urlId: "intermediate_ca_ocsp_responder_url"});
        });

        $("#intermediate_ca_add").live("click", function(){
            openIntermediateCaAddOrEditDialog();
        });

        $("#pki_intermediate_ca_cert_upload").live("click", function(){
            $("#intermediate_ca_cert_upload").submit();
        });
    });

    return {
        addOcspCertDetailsLink: addOcspCertDetailsLink,
        addOcspResponderSection: addOcspResponderSection,
        showOcspResponderDeleteButton: showOcspResponderDeleteButton,
        clearSaveablePkiData: clearSaveablePkiData,
        fillTopCaCertDetails: fillTopCaCertDetails,
        disableNameExtractorInputs: disableNameExtractorInputs,
        enableOcspResponderAddButton: enableOcspResponderAddButton,

        openUploadTopCaCertDialog: openUploadTopCaCertDialog,
        openViewTopCaCertDialog: openViewTopCaCertDialog,
        openAddTopCaOcspInfosDialogWindow: openAddTopCaOcspInfosDialogWindow,
        openAddNameExtractorDialogWindow: openAddNameExtractorDialogWindow,
        openAddIntermediateCasDialogWindow: openAddIntermediateCasDialogWindow,
        openIntermediateCaEditDialog: openIntermediateCaEditDialog,
        openPkiTempCertDetailsById: openPkiTempCertDetailsById,

        uploadCallbackPkiAddTopCaCert: uploadCallbackPkiAddTopCaCert,
        uploadCallbackPkiAddOcspResponderCert:
            uploadCallbackPkiAddOcspResponderCert,
        uploadCallbackPkiAddIntermediateCaCert:
            uploadCallbackPkiAddIntermediateCaCert
    };
}();