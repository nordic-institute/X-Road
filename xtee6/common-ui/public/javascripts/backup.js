var confBackup = function() {
    var oBackupFiles;
    var restoreInProgress = false;

    function initBackupFilesTable() {
        var opts = scrollableTableOpts(200);
        opts.sScrollX = "100%";
        opts.sScrollY = "400px";
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "name" }
        ];

        opts.fnRowCallback = function(nRow, backupFile) {
            var fileName = backupFile.name;

            removeTableRowButtons($(nRow));

            appendToBackupTableRow($(nRow), getTableRowButton(
                    _("common.delete"), getBackupDeleteHandler(fileName)));
            appendToBackupTableRow($(nRow), getTableRowButton(
                    _("common.restore"), getBackupRestoreHandler(fileName)));
            appendToBackupTableRow($(nRow), getTableRowButton(
                    _("common.download"), getBackupDownloadHandler(fileName)));
        };
        opts.fnDrawCallback = function() {
            $(this).closest(".dataTables_wrapper")
                .find(".dataTables_scrollHead").hide();
        };

        opts.bScrollInfinite = true;

        opts.aaSorting = [ [0,'desc'] ];

        oBackupFiles = $('#backup_files').dataTable(opts);
        oBackupFiles.fnSetFilteringDelay(600);
    }

    function openBackupFileUploadDialog() {
        clearMessages();

        $("#backup_file_upload_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "400px",
            buttons: [
                { text: _("common.ok"),
                  id: "backup_file_upload_ok",
                  disabled: "disabled",
                  click: function() {
                      uploadBackupFile();
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");
    }

    function uploadCallback(response) {
        if (response.success) {
            $("#backup_file_upload_dialog").dialog("close");
            resetUploadForm();
            refreshBackupFiles();
        }

        showMessages(response.messages);
    }

    // -- Handling backup rows - start

    function appendToBackupTableRow(row, button) {
        row.find("td:first").append(button);
    }

    function getBackupDownloadHandler(fileName){
        return function() {
            window.location = action("download?tarfile=" + fileName)
        }
    }

    function getBackupRestoreHandler(fileName){
        return function() {
            confirm("backup.index.item.restore_confirm", {file: fileName}, function() {
                handleRestore(fileName);
            });
        }
    }

    function getBackupDeleteHandler(fileName) {
        return function() {
            confirm("backup.index.item.delete_confirm", {file: fileName}, function() {
                handleDelete(fileName);
            });
        }
    }

    // -- Handling backup rows - end

    function resetUploadForm() {
        $("#new_backup_file_upload_field").val("");
        $("#backup_file_upload_ok").disable();
    }

    function refreshBackupFiles() {
        $.get(action("refresh_files"), {}, function(response) {
            oBackupFiles.fnClearTable();
            oBackupFiles.fnAddData(response.data);
            oBackupFiles.fnDraw();
        });
    }

    // -- Button handlers - start ---

    function handleBackup() {
        $.post(action("backup"), null, function(response) {
            refreshBackupFiles();
            initConsoleOutput(
                response.data.console_output,
                _("backup.index.console_output"));
        }, "json");
    }

    function handleRestore(fileName) {
        restoreInProgress = true;

        $.post(action("restore"), {fileName: fileName}, function(response) {
            var onClose = response.data.activate_hardware_tokens && function() {
                yesno("backup.index.activate_hardware_tokens", null, function(yes) {
                    if (yes) {
                        redirect("keys");
                    }
                });
            };

            initConsoleOutput(response.data.console_output,
                _("restore.index.console_output"), null, onClose);
        }, "json").always(function() {
            restoreInProgress = false;
        });
    }

    function handleDelete(fileName) {
        $.post(action("delete_file"), {fileName: fileName}, function() {
            refreshBackupFiles();
        }, "json");
    }

    function handleUpload() {
        openBackupFileUploadDialog();
    }

    // -- Button handlers - end ---

    function setButtonEnabled(buttonId, enabled) {
        var button = $("#" + buttonId);
        enabled ? button.enable() : button.disable();
    }

    function uploadBackupFile() {
        var uploadForm = $("#upload_new_backup_file");
        var fileName = getUploadableBackupFileName();
        var checkParams = {fileName: fileName};

        $.get("backup/check_backup_file_existence",  checkParams,
                function(response){
            var fileExists = response.data.exists

            if(fileExists) {
                confirm("backup.upload.file_exists", {file: fileName},
                        function() {
                    uploadForm.submit();
                }); 
            } else {
                uploadForm.submit();
            }
        }, "json");
    }

    function getUploadableBackupFileName() {
        return $("#new_backup_file_upload_field")[0].files[0].name;
    }

    return {
        initBackupFilesTable: initBackupFilesTable,
        refreshBackupFiles: refreshBackupFiles,

        handleBackup: handleBackup,
        handleUpload: handleUpload,

        uploadCallback: uploadCallback,
        resetUploadForm: resetUploadForm,

        restoreInProgress: function() {
            return restoreInProgress;
        }
    };
}();

$(document).ready(function() {
    confBackup.initBackupFilesTable();
    confBackup.refreshBackupFiles();
    confBackup.resetUploadForm();

    $("#backup_upload").live("click", function() {
        confBackup.handleUpload();
    });

    $("#new_backup_file_upload_field").live("change", function() {
        var okButton = $("#backup_file_upload_ok");
        isInputFilled($(this)) ? okButton.enable() : okButton.disable();
    });

    $("#backup").click(function() {
        confBackup.handleBackup();
    });
});
