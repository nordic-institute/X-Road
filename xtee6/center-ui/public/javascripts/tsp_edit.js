var SDSB_TSP_EDIT = function() {
    var newTsp = function() {
        function initAdding() {
            editTsp.clearTspEditing();
            openTspEditDialog();
        }

        function save(closeableDialog) {
            var params = {
                url: $("#tsp_url").val(),
                tempCertId: $("#tsp_temp_cert_id").text()
            }

            $.post("tsps/save_new_tsp", params, function(){
                clearSaveableTspData();
                SDSB_TSPS.refreshTable();
                closeableDialog.dialog("close");
            }, "json");
        }

        return {
            initAdding: initAdding,

            save: save
        };
    }();

    var editTsp = function() {
        var editableTspId;

        function initEditing(id, url) {
            clearSaveableTspData();
            editableTspId = id;
            $("#tsp_url").val(url);
            openTspEditDialog();
        }

        function editingExistingTsp() {
            return $.isNumeric(editableTspId);
        }

        function openEditingDialogWithData() {
            var params = {tspId: editableTspId};

            $.get("tsps/get_existing_tsp_cert_details", params,
                    function(response) {
                addTspCertData(response.data, function() {
                    openTspCertDetails();
                });

                openTspEditDialogWindow();
            });
        }

        function openTspCertDetails() {
            var params = {tspId: editableTspId};

            $.get("tsps/get_existing_tsp_cert_dump_and_hash", params,
                    function(response) {
                SDSB_CENTERUI_COMMON.openCertDetailsWindow(response.data);
            }, "json");
        }

        function clearTspEditing() {
            editableTspId = null;
            clearSaveableTspData();
        }

        function save(closeableDialog) {
            var params = {
                id: editableTspId,
                url: $("#tsp_url").val()
            };

            $.post("tsps/edit_existing_tsp", params, function() {
                clearSaveableTspData();
                clearTspEditing();
                SDSB_TSPS.refreshTable();
                closeableDialog.dialog("close");
            }, "json");
        }

        return {
            initEditing: initEditing,
            editingExistingTsp: editingExistingTsp,
            openEditingDialogWithData: openEditingDialogWithData,
            clearTspEditing: clearTspEditing,

            save: save
        };
    }();

    /* -- PUBLIC - START -- */

    function initAdding() {
        newTsp.initAdding();
    }

    function initEditing(id, url) {
        editTsp.initEditing(id, url);
    }

    function uploadCallbackTspCert(response) {
        if (response.success) {
            var cert = response.data;

            addTspCertData(cert, function() {
                SDSB_CENTERUI_COMMON.openTempCertDetailsById(
                        cert.temp_cert_id, "tsps");
            });

            $("#tsp_cert_details").show();
            manageTspSaveOkButtonVisibility();

            SDSB_CERTS_UPLOADER.submitNextCertUpload();
        } else {
            $("#save_tsp_details_ok").disable();
            $("#tsp_cert_details").hide();
        }

        showMessages(response.messages)
    }

    /* -- PUBLIC - END -- */

    function addTspCertData(cert, detailsLinkClickHandler) {
        clearTspCertDetailsWindow();
        addTspCertDetailsLink(detailsLinkClickHandler);

        var subjectDn = SDSB_CENTERUI_COMMON.decorateCertDetails(cert.subject);
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                subjectDn, $("#tsp_cert_details_subject_dn"));

        var issuerDn = SDSB_CENTERUI_COMMON.decorateCertDetails(cert.issuer);
        SDSB_CENTERUI_COMMON.addCertDetailsParts(
                issuerDn, $("#tsp_cert_details_issuer_dn"));

        $("#tsp_cert_details_valid_from").text(cert.valid_from);
        $("#tsp_cert_details_valid_to").text(cert.expires);

        $("#tsp_temp_cert_id").text(cert.temp_cert_id);
    }

    function addTspCertDetailsLink(clickHandler) {
        var detailsSelector = $("#tsp_cert_details");
        detailsSelector.find("a.open_details").remove();
        detailsSelector.prepend(SDSB_CENTERUI_COMMON.getCertDetailsLink(clickHandler));
    }

    function clearTspCertDetailsWindow() {
        $("#tsp_cert_details").find(".ca_cert_detail").remove();
    }

    function saveTsp(closeableDialog) {
        if (editTsp.editingExistingTsp()) {
            editTsp.save(closeableDialog);
        } else {
            newTsp.save(closeableDialog);
        }
    }

    function clearSaveableTspData() {
        $("#tsp_cert_details").hide();
        $("#tsp_url").val(""),
        $("#upload_tsp_cert_file").val(""),
        $("#tsp_temp_cert_id").text("")
    }


    function getTspCertUploadRow() {
        return $("#upload_tsp_cert_file").closest("tr");
    }

    function manageTspSaveOkButtonVisibility() {
        var fileUploadInput = $('#upload_tsp_cert_file');
        var tspUrlInput = $("#tsp_url");

        if (isInputFilled(tspUrlInput)
                && (editTsp.editingExistingTsp()
                        || isInputFilled(fileUploadInput))) {
            $("#save_tsp_details_ok").enable();
        } else {
            $("#save_tsp_details_ok").disable();
        }
    }

    function openTspEditDialog() {
        var isNew = !editTsp.editingExistingTsp();

        var dialog = $("#tsp_edit_dialog").initDialog({
            title: isNew ? _("tsps.edit_new") : _("tsps.edit_existing"),
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  id: "save_tsp_details_ok",
                  disabled: "disabled",
                  click: function() {
                      var dialog = $(this);
                      SDSB_CERTS_UPLOADER.initSubmittingCerts(function(){
                          saveTsp(dialog);
                      });
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      clearSaveableTspData();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        if (isNew) {
            getTspCertUploadRow().show();
            dialog.dialog("open");
        } else {
            editTsp.openEditingDialogWithData();
        }
    }

    function openTspEditDialogWindow() {
        $("#tsp_cert_details").show();
        getTspCertUploadRow().hide();
        $("#tsp_edit_dialog").dialog("open")
    }

    $(document).ready(function() {
        $("#tsp_cert_upload_button").live("click", function() {
            $("#tsp_cert_upload_form").submit();
        });

        $('#upload_tsp_cert_file').live("change", function(){
            manageTspSaveOkButtonVisibility();
            SDSB_CERTS_UPLOADER.manageCertFileSelection($(this));
        });

        $("#tsp_url").live("keyup", function() {
            manageTspSaveOkButtonVisibility();
        });
    });

    return {
        initEditing: initEditing,
        initAdding: initAdding,

        uploadCallbackTspCert: uploadCallbackTspCert
    }
}();
