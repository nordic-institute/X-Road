var XROAD_BACKUP = function() {
    var oBackupFiles;
    var restoreInProgress = false;

    function initBackupFilesTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "name" },
            { "mData": null,
              "fnCreatedCell": function(nTd, sData, oData) {
                  var params = {
                      file: oData.name
                  };

                  $(nTd).append(getTableRowButton(_("common.delete"), function() {
                      confirm("backup.index.item.delete_confirm", params, function() {
                          handleDelete(params.file);
                      });
                  }));

                  $(nTd).append(getTableRowButton(_("common.restore"), function() {
                      confirm("backup.index.item.restore_confirm", params, function() {
                          handleRestore(params.file);
                      });
                  }));

                  $(nTd).append(getTableRowButton(_("common.download"), function() {
                      window.location = action("download?tarfile=" + params.file)
                  }));
              } }
        ];

        opts.fnDrawCallback = function() {
            $(this).closest(".dataTables_wrapper")
                .find(".dataTables_scrollHead").hide();
            this.fnAdjustColumnSizing(false);
        };

        opts.aaSorting = [[0, "desc"]];

        oBackupFiles = $("#backup_files").dataTable(opts);
    }

    function uploadCallback(response) {
        if (response.success) {
            refreshBackupFiles();
            closeFileUploadDialog();
        }

        showMessages(response.messages);
    }

    function refreshBackupFiles() {
        $.get(action("refresh_files"), {}, function(response) {
            oBackupFiles.fnReplaceData(response.data);
        });
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

            initConsoleOutput(response.data.stderr,
                _("restore.index.console_output"), null, onClose);
        }, "json").always(function() {
            restoreInProgress = false;
        }).fail(function(xhr) {
            var response = $.parseJSON(xhr.responseText);
            initConsoleOutput(response.data.stderr,
                _("restore.index.console_output"));
        });
    }

    function handleDelete(fileName) {
        $.post(action("delete_file"), {fileName: fileName}, function() {
            refreshBackupFiles();
        }, "json");
    }

    function setButtonEnabled(buttonId, enabled) {
        var button = $("#" + buttonId);
        enabled ? button.enable() : button.disable();
    }

    $(document).ready(function() {
        initBackupFilesTable();
        refreshBackupFiles();

        $("#backup_upload").click(function() {
            openFileUploadDialog(action("upload_new"),
                _("backup.index.upload_file_title"), null, function() {
                    var form = $(this).closest(".ui-dialog").find("form");
                    var fileName = $("input[type=file]", form)[0].files[0].name;
                    var fileExists = false;

                    $.each(oBackupFiles.fnGetData(), function(idx, val) {
                        if (val.name == fileName) {
                            fileExists = true;
                            return false;
                        }
                    });

                    if (fileExists) {
                        confirm("backup.index.uploaded_file_exists",
                            {file: fileName}, function() {
                                form.submit();
                            });
                    } else {
                        form.submit();
                    }
                });
        });

        $("#backup").click(function() {
            $.post(action("backup"), null, function(response) {
                refreshBackupFiles();
                initConsoleOutput(
                    response.data.stderr, _("backup.index.console_output"));
            }, "json").fail(function(xhr) {
                var response = $.parseJSON(xhr.responseText);
                initConsoleOutput(
                    response.data.stderr, _("backup.index.console_output"));
            });
        });
    });

    return {
        uploadCallback: uploadCallback,
        restoreInProgress: function() {
            return restoreInProgress;
        }
    };
}();
