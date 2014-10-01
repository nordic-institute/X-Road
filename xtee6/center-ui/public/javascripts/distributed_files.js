var SDSB_DISTRIBUTED_FILES = function() {
    var oDistributedFiles;

    /* -- PUBLIC - START -- */

    function uploadCallbackIdentifierMapping(response) {
        if (response.success) {
            $("#identifier_mapping_upload_dialog").dialog("close");
            resetIdentifierMappingUploadForm();
        }

        redrawDistributedFilesTable(response.data.table);
        showMessages(response.messages);
    }

    /* -- PUBLIC - END -- */

    function initDistributedFilesTable() {
        var opts = scrollableTableOpts(240);
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "file_type" },
            { "mData": "file_info" },
            { "mData": null }
        ];

        opts.fnRowCallback = function(nRow, eachFile) {
            removeTableRowButtons($(nRow));

            appendButtonToRow($(nRow), getDownloadButton(eachFile));
            appendButtonToRow($(nRow), getLastResultButton(eachFile));

            if (isIdentifierMappingFile(eachFile)) {
                $("#identifier_mapping_first_upload").hide();
                var uploadButton = getIdentifierMappingUploadButton();
                appendButtonToRow($(nRow), uploadButton);
                eachFile.can_upload ? uploadButton.show() : uploadButton.hide();
            }
        };

        opts.bScrollInfinite = true;

        opts.aaSorting = [ [0,'desc'] ];

        oDistributedFiles = $('#distributed_files').dataTable(opts);
        oDistributedFiles.fnSetFilteringDelay(600);
    }


    function openIdentifierMappingUploadDialog() {
        resetIdentifierMappingUploadForm();
        $("#identifier_mapping_upload_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "400px",
            buttons: [
                { text: _("common.ok"),
                  id: "identifier_mapping_upload_ok",
                  disabled: "disabled",
                  click: function() {
                      $("#upload_new_identifier_mapping").submit();
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

    function resetIdentifierMappingUploadForm() {
        $("#identifier_mapping_file").val("");
        $("#identifier_mapping_upload_ok").disable();
    }

    function isIdentifierMappingFile(file) {
        return "identifiermapping.xml" == file.file_name;
    }

    function getIdentifierMappingUploadButton() {
        return getTableRowButton(_("common.upload"), function(){
            openIdentifierMappingUploadDialog();
        });
    }

    function getLastResultButton(file) {
        var result = getTableRowButton(_("distributed_files.last_result"),
                function() {
            $.get("distributed_files/last_result", toParams(file),
                    function(response) {
                initConsoleOutput(
                        response.data.console,
                        _("distributed_files.last_result"));
            });
        });

        if (!file.log_exists) {
            result.disable();
        }

        return result;
    }

    function getDownloadButton(file) {
        var result = getTableRowButton(_("common.download"), function(){
            window.location =
                action("download?file_id=" + file.file_id
                        + "&table=" + file.table);
        });

        if (file.empty) {
            result.disable();
        }

        return result;
    }

    function toParams(file) {
        return {
            file_id: file.file_id,
            table: file.table
        }
    }

    function refreshDistributedFiles() {
        $.get(action("get_files"), {}, function(response) {
            redrawDistributedFilesTable(response.data);
        });
    }

    function redrawDistributedFilesTable(data) {
        oDistributedFiles.fnClearTable();
        oDistributedFiles.fnAddData(data);
        oDistributedFiles.fnDraw();
    }

    $(document).ready(function(){
        initDistributedFilesTable();
        refreshDistributedFiles();
        resetIdentifierMappingUploadForm();

        $("#identifier_mapping_file").live("change", function() {
            var okButton = $("#identifier_mapping_upload_ok");
            isInputFilled($(this)) ? okButton.enable() : okButton.disable();
        });
    });

    return {
        uploadCallbackIdentifierMapping: uploadCallbackIdentifierMapping
    };
}();
