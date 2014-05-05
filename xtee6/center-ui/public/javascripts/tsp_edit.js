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
            tsps.refreshTable();
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

        $.post("tsps/get_existing_tsp_cert_details", params,
                function(response) {
            addTspCertData(response.data, function() {
                openTspCertDetails();
            });

            openTspEditDialogWindow();
        });
    }

    function openTspCertDetails() {
        var params = {tspId: editableTspId};

        $.post("tsps/get_existing_tsp_cert_dump_and_hash", params,
                function(response) {
            openCertDetailsWindow(response.data);
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
            tsps.refreshTable();
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

function uploadCallbackTspCert(response) {
    if (response.success) {
        var cert = response.data;

        addTspCertData(cert, function() {
            openTempCertDetailsById(cert.temp_cert_id, "tsps")
        });

        $("#save_tsp_details_ok").enable();
        $("#tsp_cert_details").show();

        certsUploader.submitNextCertUpload();
    } else {
        $("#save_tsp_details_ok").disable();
        $("#tsp_cert_details").hide();
    }

    showMessages(response.messages)
}

function addTspCertData(cert, detailsLinkClickHandler) {
    clearTspCertDetailsWindow();
    addTspCertDetailsLink(detailsLinkClickHandler);

    var subjectDn = decorateCertDetails(cert.subject);
    addCertDetailsParts(subjectDn, $("#tsp_cert_details_subject_dn"));

    var issuerDn = decorateCertDetails(cert.issuer);
    addCertDetailsParts(issuerDn, $("#tsp_cert_details_issuer_dn"));

    $("#tsp_cert_details_valid_from").text(cert.valid_from);
    $("#tsp_cert_details_valid_to").text(cert.expires);

    $("#tsp_temp_cert_id").text(cert.temp_cert_id);
}

function addTspCertDetailsLink(clickHandler) {
    var detailsSelector = $("#tsp_cert_details");
    detailsSelector.find("a.open_details").remove();
    detailsSelector.prepend(getCertDetailsLink(clickHandler));
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

function openTspEditDialog() {
    var isNew = !editTsp.editingExistingTsp();

    var dialog = $("#tsp_edit_dialog").initDialog({
        title: isNew? _("tsps.edit.new"): _("tsps.edit.existing"),
        autoOpen: false,
        modal: true,
        height: "auto",
        width: "400px",
        buttons: [
            { text: _("ok"),
              id: "save_tsp_details_ok",
              disabled: "disabled",
              click: function() {
                  var dialog = $(this);
                  certsUploader.initSubmittingCerts(function(){
                      saveTsp(dialog);
                  });
              }
            },
            { text: _("cancel"),
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

$(document).ready(function() {

    $("#tsp_cert_upload_button").live("click", function() {
        $("#tsp_cert_upload_form").submit();
    });

    $('#upload_tsp_cert_file').live("change", function(){
        manageTspSaveOkButtonVisibility();
        certsUploader.manageCertFileSelection($(this));
    });

    $("#tsp_url").live("keyup", function() {
        manageTspSaveOkButtonVisibility();
    });
});
