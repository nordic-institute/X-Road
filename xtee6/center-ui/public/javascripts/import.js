var SDSB_IMPORT = function() {
    var oImportV5;

    function initTable() {
        var opts = scrollableTableOpts(240);
        opts.sScrollX = "100%";
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "file_type" },
            { "mData": "file_info" },
            { "mData": null },
        ];

        opts.fnRowCallback = function(nRow, eachFile) {
            removeTableRowButtons($(nRow));

            var lastResultButton = getLastResultButton(eachFile);

            appendButtonToRow($(nRow), lastResultButton);
            appendButtonToRow($(nRow), getV5DataUploadButton());

            if (eachFile.file_name == null) {
                lastResultButton.disable();
            }
        };

        opts.bScrollInfinite = true;

        oImportV5 = $('#import_v5_data_table').dataTable(opts);
        oImportV5.fnSetFilteringDelay(600);
    }

    function openV5DataUploadDialog() {
        $("#import_upload_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "400px",
            buttons: [
                { text: _("common.ok"),
                  id: "import_v5_upload_ok",
                  disabled: "disabled",
                  click: function() {
                      setCursorToWaiting();
                      $("#upload_new_v5_data_file").submit();
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

    function resetUploadForm() {
        $("#v5_mapping_file").val("");
        $("#import_v5_upload_ok").disable();
    }

    function uploadCallback(response) {
        if (response.success) {
            $("#import_upload_dialog").dialog("close");
        }

        resetUploadForm();
        refreshTable();
        showMessages(response.messages);
        clearCursorWaiting();
    }

    function refreshTable() {
        $.get("import/get_imported", {}, function(response) {
            oImportV5.fnClearTable();
            oImportV5.fnAddData(response.data);
            oImportV5.fnDraw();
        });
    }

    function getV5DataUploadButton() {
        return getTableRowButton(_("common.upload"), function(){
            openV5DataUploadDialog();
        });
    }

    function getLastResultButton(file) {
        var result = getTableRowButton(_("import_v5.last_result"), function(){
            $.get("import/last_result", {}, function(response) {
                initConsoleOutput(
                        response.data.console, 
                        _("import_v5.last_result"));
            });
        });

        if (file.empty) {
            result.disable();
        }

        return result;
    }

    function setCursorToWaiting() {
        $("body").css("cursor", "wait");
    }

    function clearCursorWaiting() {
        $("body").css("cursor", "default");
    }

    $(document).ready(function(){
        initTable();
        refreshTable();
        resetUploadForm();
        
        $("#v5_mapping_file").live("change", function() {
            var okButton = $("#import_v5_upload_ok");
            isInputFilled($(this)) ? okButton.enable() : okButton.disable();
        });
    });

    return {
        uploadCallback: uploadCallback,
    };
}();
