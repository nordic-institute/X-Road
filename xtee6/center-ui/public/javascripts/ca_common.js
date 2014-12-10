var SDSB_CA_COMMON = function() {
    /* -- PUBLIC - START -- */

    /* -- UI manipulation - start -- */

    function addOcspCertDetailsLink(handleDetailsLinkClick) {
        var ocspResponderSection =
            SDSB_CA_CACHE.getFillableOcspResponderSection().closest("section");
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
            SDSB_CA_EDIT.fillOcspResponderSection(
                    newResponderSection, ocspInfo);
        }

        lastResponderSection.after(newResponderSection);
        addButton.disable();
    }

    function showOcspResponderDeleteButton(section) {
        section.find("#top_ca_ocsp_responder_delete," +
                "#intermediate_ca_ocsp_responder_delete").show();
    }

    function clearSaveableCaData() {
        clearCaAddWizardFields();
        SDSB_CA_CACHE.uncacheIntermediateCaData();
        SDSB_CA_CACHE.clearTopCaCertificateData();
    }

    function fillTopCaCertDetails() {
        $("p.ca_cert_detail").remove();

        var certData = SDSB_CA_CACHE.getTopCaCertificateData()
        var subjectDnTitleSelector = $("#ca_add_cert_details_subject_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                certData.subjectDn, subjectDnTitleSelector);

        var issuerDnTitleSelector = $("#ca_add_cert_details_issuer_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                certData.issuerDn, issuerDnTitleSelector);

        $("#ca_add_cert_details_valid_from").text(certData.validFrom);
        $("#ca_add_cert_details_valid_to").text(certData.validTo);
    }

    function disableNameExtractorInputs() {
        $("#ca_add_name_extractor_member_class").disable();
        $("#ca_add_extractor_method").disable();
        $("#name_extractor_data").addClass("inactive");

        clearTopCaNameExtractorForm();
    }

    function enableOcspResponderAddButton() {
        $("#ca_add_top_ca_ocsp_responder," +
        "#ca_add_intermediate_ca_ocsp_responder").enable();
    }

    /* -- UI manipulation - end -- */

    /* -- Upload callbacks - start -- */

    function uploadCallbackApprovedCaAddTopCaCert(response) {
        if (response.success) {
            SDSB_CA_CACHE.addTopCaCertificateData(response.data);
            $("#add_ca_step1_upload_top_ca_cert_dialog").dialog("close");
            fillTopCaCertDetails();
            openViewTopCaCertDialog();
        } else {
            $("#ca_add_step1_next").disable();
        }

        showMessages(response.messages);
    }

    function uploadCallbackApprovedCaAddOcspResponderCert(response) {
        if (response.success) {
            var tempCertId = response.data.temp_cert_id
            SDSB_CA_CACHE.getFillableOcspResponderSection().text(tempCertId);

            addOcspCertDetailsLink(function() {
                openCaTempCertDetailsById(tempCertId);
            });

            SDSB_CA_CACHE.clearUploadingOcspCert();

            SDSB_CERTS_UPLOADER.submitNextCertUpload();
        } else {
            SDSB_CA_CACHE.clearUploadingOcspCert();
        }

        showMessages(response.messages);
    }

    function uploadCallbackApprovedCaAddIntermediateCaCert(response) {
        $("#ca_intermediate_ca_cert_details").hide();

        if (response.success) {
            refreshIntermediateCaCert(response.data,
                    SDSB_CA_CACHE.getSaveableIntermediateCaId(), false);
            SDSB_CERTS_UPLOADER.submitNextCertUpload();
        }

        showMessages(response.messages);
    }

    /* -- Upload callbacks - end -- */

    /* -- Dialog openers - start -- */

    function openUploadTopCaCertDialog() {
        $("#add_ca_step1_upload_top_ca_cert_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.next"),
                  id: "ca_add_step1_next",
                  disabled: "disabled",
                  click: function() {
                      $("#top_ca_cert_upload").submit();
                  }
                },
                { text: _("common.cancel"),
                    click: function() {
                        clearSaveableCaData();
                        $(this).dialog("close");
                    }
                }
            ]
        }).dialog("open");
    }

    function openViewTopCaCertDialog() {
        $("#add_ca_step2_view_top_ca_cert_dialog").initDialog({
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
                      $("#ca_add_step1_next").enable();
                  }
                },
                { text: _("common.cancel"),
                    id: "view_top_ca_cert_button_cancel",
                    click: function() {
                        clearSaveableCaData();
                        $(this).dialog("close");
                    }
                }
            ]
        }).dialog("open");

        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            $("#view_top_ca_cert_button_back").hide();
        }
    }

    function openAddTopCaOcspInfosDialogWindow() {
        $("#add_ca_step4_add_top_ca_ocsp_responders").dialog("open");
    }

    function openAddNameExtractorDialogWindow() {
        $("#add_ca_step3_add_name_extractor_dialog").dialog("open")
    }

    function openAddIntermediateCasDialogWindow() {
        $("#add_ca_step5_add_intermediate_cas").dialog("open");
    }

    function openIntermediateCaEditDialog(editableIntermediateCa) {
        $("#itermediate_ca_editing").text(editableIntermediateCa.elementId);

        var caId = editableIntermediateCa.id

        if (SDSB_CA_CACHE.editingExistingIntermediateCa(caId)) {
            var params = {intermediateCaId: caId};

            $.get("approved_cas/get_existing_intermediate_ca_cert_details", params,
                    function(response){
                openIntermediateCaAddOrEditDialog();
                refreshIntermediateCaCert(response.data, caId, true);
                SDSB_CA_EDIT.openIntermediateCaOcspRespondersWithData(caId);
            }, "json");

        } else {
            var params = {
                intermediateCaTempCertId: editableIntermediateCa.temp_cert_id
            };

            $.get("approved_cas/get_intermediate_ca_temp_cert_details", params,
                    function(response){
                var certData = response.data;
                openIntermediateCaAddOrEditDialog();
                refreshIntermediateCaCert(certData, null, false);
                SDSB_CA_EDIT.fillOcspResponderSections(
                        editableIntermediateCa.ocspInfos,
                        "intermediate_ca_ocsp_responder",
                        "ca_add_intermediate_ca_ocsp_responder");
            }, "json");
        }
    }

    function openCaTempCertDetailsById(certId) {
        SDSB_CENTERUI_COMMON.openTempCertDetailsById(certId, "approved_cas")
    }

    /* -- Dialog openers - end -- */

    /* -- PUBLIC - END -- */

    function clearTopCaCertUploadForm() {
        $("#upload_top_ca_cert_file").val("");
    }

    function clearTopCaNameExtractorDialog() {
        $("#ca_add_is_ssl_only").removeAttr("checked");
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
                "#ca_intermediate_ca_cert_upload").disable();

        intermediateCaEditDialog.find("p.ca_cert_detail").remove();
        $("#ca_intermediate_ca_cert_details").hide();

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
        $("#ca_add_name_extractor_member_class").val("");
        $("#ca_add_extractor_method").val("");
    }

    function clearCaAddWizardFields() {
        clearTopCaCertUploadForm();
        clearTopCaNameExtractorDialog();
        clearTopCaOcspInfosDialog();
        clearIntermediateCaAddDialog();
    }

    function fillIntermediateCaCertDetails(data) {
        $("p.ca_cert_detail").remove();
        $("#ca_intermediate_ca_cert_details").show();

        var subjectDnTitleSelector = $(
                "#ca_add_intermediate_ca_cert_details_subject_dn");
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                SDSB_CENTERUI_COMMON.decorateCertDetails(data.subject),
                subjectDnTitleSelector);
    }

    function addIntermediateCaCertDetailsLink(handleDetailsLinkClick) {
        var buttonColumn = $("#ca_intermediate_ca_cert_upload").closest("td");
        buttonColumn.find("a.open_details").remove()

        $("#ca_intermediate_ca_cert_upload")
                .after(SDSB_CENTERUI_COMMON.getCertDetailsLink(
                        handleDetailsLinkClick));
    }

    function refreshIntermediateCaCert(data, intermediateCaId, certSaved) {
        SDSB_CA_CACHE.addIntermediateCaCertificateData(data, intermediateCaId);
        fillIntermediateCaCertDetails(data);

        if (certSaved) {
            addIntermediateCaCertDetailsLink(function() {
                SDSB_CA_EDIT.openIntermediateCaCertDetails(intermediateCaId);
            });
        } else {
            addIntermediateCaCertDetailsLink(function() {
                openCaTempCertDetailsById(data.temp_cert_id);
            });
        }
        $("#add_intermediate_ca_ok").enable();
    }

    function fillCasNameExtractorMemberClassSelect() {
        $.get("application/member_classes", null, function(response){
            SDSB_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    "ca_add_name_extractor_member_class", response.data);
        }, "json");
    }

    function enableNameExtractorInputs() {
        $("#ca_add_name_extractor_member_class").enable();
        $("#ca_add_extractor_method").enable();
        $("#name_extractor_data").removeClass("inactive");
    }

    function enableIntermediateCaOcspResponderAddButton() {
        $("ca_add_intermediate_ca_ocsp_responder").enable();
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

        SDSB_CA_CACHE.markOcspResponderForDeletion(ocspResponderSection);

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

    function saveCa(closeableDialog) {
        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            SDSB_CA_EDIT.save(closeableDialog);
        } else {
            SDSB_CA_NEW.save(closeableDialog);
        }

        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            $("#view_top_ca_cert_button_cancel").hide();
            $("#view_top_ca_cert_button_back").hide();
        }
    }

    function clearIntermediateCaEditing() {
        $("#itermediate_ca_editing").text("");
    }

    function openAddNameExtractorDialog() {
        var dialog = $("#add_ca_step3_add_name_extractor_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.finish"),
                    click: function() {
                        saveCa($(this));
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
                        clearSaveableCaData();
                        $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");

        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            SDSB_CA_EDIT.openNameExtractorDialogWithData();
        } else {
            $("#ca_add_is_ssl_only").removeAttr("checked");
            dialog.dialog("open");
        }
    }

    function openAddNameExtractorDialogWindow() {
        $("#add_ca_step3_add_name_extractor_dialog").dialog("open")
    }

    function openAddTopCaOcspInfosDialog() {
        var dialog = $("#add_ca_step4_add_top_ca_ocsp_responders").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.finish"),
                    click: function() {
                        var dialog = $(this);
                        SDSB_CERTS_UPLOADER.initSubmittingCerts(function(){
                            saveCa(dialog);
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
                      clearSaveableCaData();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            SDSB_CA_EDIT.openTopCaOcspRespondersWithData();
        } else {
            dialog.dialog("open");
        }
    }

    function openAddIntermediateCasDialog() {
        if (!SDSB_CA_EDIT.areIntermediateCasCached()) {
            SDSB_CA_CACHE.initIntermediateCasTable();
        }

        var dialog = $("#add_ca_step5_add_intermediate_cas").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "50%",
            buttons: [
                { text: _("common.finish"),
                    click: function() {
                        saveCa($(this));
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
                      clearSaveableCaData();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        if (SDSB_CA_CACHE.isEditingExistingCa()) {
            SDSB_CA_EDIT.openIntermediateCasWithData();
        } else {
            dialog.dialog("open");
        }
    }

    function openIntermediateCaAddOrEditDialog() {
        clearIntermediateCaAddDialog();

        var title = SDSB_CA_CACHE.isEditingIntermediateCa() ?
                _("approved_cas.intermediate_ca_edit_title") :
                _("approved_cas.intermediate_ca_new_title");

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
                          SDSB_CA_CACHE.cacheIntermediateCaData();
                          dialog.dialog("close");
                      });
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      SDSB_CA_CACHE.clearSaveableIntermediateCa();
                      $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");
    }

    $(document).ready(function() {
        fillCasNameExtractorMemberClassSelect();

        $("#upload_top_ca_cert_file").live("click", function() {
            $("#ca_add_step1_next").enable();
        });

        $("#ca_add_open_top_ca_cert_details").live("click", function() {
            if (SDSB_CA_CACHE.isEditingExistingCa()) {
                SDSB_CA_EDIT.openTopCaCertDetails();
            } else {
                openCaTempCertDetailsById(SDSB_CA_CACHE.getTopCaTempCertId());
            }
        });

        $("#ca_add_is_ssl_only").live("click", function(){
            if($(this).attr("checked")) {
                disableNameExtractorInputs();
            } else {
                enableNameExtractorInputs();
            }
        });

        $("#top_ca_ocsp_responder_url").live("keyup", function(){
            handleOcspResponderFormButtonsVisibility(
                    $(this), $("#ca_add_top_ca_ocsp_responder"));
        });

        $("#intermediate_ca_ocsp_responder_url").live("keyup", function(){
            handleOcspResponderFormButtonsVisibility(
                    $(this), $("#ca_add_intermediate_ca_ocsp_responder"));
        });

        $('#upload_ocsp_responder_cert_file').live("change", function(){
            SDSB_CERTS_UPLOADER.manageCertFileSelection($(this));
        });

        $('#upload_intermediate_ca_cert_file').live("change", function(){
            var okButton = $("#add_intermediate_ca_ok");
            var uploadButton = $("#ca_intermediate_ca_cert_upload");

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
            if (SDSB_CA_CACHE.isUploadingOcspCert()) {
                alert("cas.add.ocsp_responders.uploading");
                return;
            }

            SDSB_CA_CACHE.setUploadingOcspCert($(this).closest("section")
                  .find("#ocsp_responder_cert_temp_id"));

            var uploadForm = $(this).closest("table")
                    .find("#ocsp_responder_cert_upload_form");

            uploadForm.submit();
        });

        $("#ca_add_top_ca_ocsp_responder").live("click", function(){
            addOcspResponderSection(
                    $("section.top_ca_ocsp_responder"), $(this));
        });

        $("#ca_add_intermediate_ca_ocsp_responder").live("click", function(){
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
                    addButton: $("#ca_add_top_ca_ocsp_responder"),
                    responderSectionClass: "top_ca_ocsp_responder",
                    urlId: "top_ca_ocsp_responder_url"});
        });

        $("#intermediate_ca_ocsp_responder_delete").live("click", function(){
            handleOcspResponderElementDeletion({
                    deleteButton: $(this),
                    addButton: $("#ca_add_intermediate_ca_ocsp_responder"),
                    responderSectionClass: "intermediate_ca_ocsp_responder",
                    urlId: "intermediate_ca_ocsp_responder_url"});
        });

        $("#intermediate_ca_add").live("click", function(){
            clearIntermediateCaEditing();
            openIntermediateCaAddOrEditDialog();
        });

        $("#ca_intermediate_ca_cert_upload").live("click", function(){
            $("#intermediate_ca_cert_upload").submit();
        });
    });

    return {
        addOcspCertDetailsLink: addOcspCertDetailsLink,
        addOcspResponderSection: addOcspResponderSection,
        showOcspResponderDeleteButton: showOcspResponderDeleteButton,
        clearSaveableCaData: clearSaveableCaData,
        fillTopCaCertDetails: fillTopCaCertDetails,
        disableNameExtractorInputs: disableNameExtractorInputs,
        enableOcspResponderAddButton: enableOcspResponderAddButton,

        openUploadTopCaCertDialog: openUploadTopCaCertDialog,
        openViewTopCaCertDialog: openViewTopCaCertDialog,
        openAddTopCaOcspInfosDialogWindow: openAddTopCaOcspInfosDialogWindow,
        openAddNameExtractorDialogWindow: openAddNameExtractorDialogWindow,
        openAddIntermediateCasDialogWindow: openAddIntermediateCasDialogWindow,
        openIntermediateCaEditDialog: openIntermediateCaEditDialog,
        openCaTempCertDetailsById: openCaTempCertDetailsById,

        uploadCallbackApprovedCaAddTopCaCert: uploadCallbackApprovedCaAddTopCaCert,
        uploadCallbackApprovedCaAddOcspResponderCert:
            uploadCallbackApprovedCaAddOcspResponderCert,
        uploadCallbackApprovedCaAddIntermediateCaCert:
            uploadCallbackApprovedCaAddIntermediateCaCert
    };
}();
