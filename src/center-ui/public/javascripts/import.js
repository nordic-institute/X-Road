var XROAD_IMPORT = function() {
    var oImportV5;

    function initTable() {
        var opts = scrollableTableOpts(240);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "file_type", "sClass": "wrap", "sWidth": "13em" },
            { "mData": "file_info", "sClass": "wrap" }
        ];

        opts.fnRowCallback = function(nRow, data) {
            $("#last_result").enable(data.file_name != null);
        };

        oImportV5 = $("#v5_data_files").dataTable(opts);
    }

    function uploadCallback(response) {
        if (response.success) {
            closeFileUploadDialog();
        }

        refreshTable();
        showMessages(response.messages);
    }

    function refreshTable() {
        $.get("import/get_imported", {}, function(response) {
            oImportV5.fnReplaceData(response.data);
        });
    }

    $(document).ready(function(){
        initTable();
        refreshTable();

        $("#upload_v5_data").click(function() {
            openFileUploadDialog(
                action("import_v5_data"), _("import_v5.upload_file.title"));
        });

        $("#last_result").click(function() {
            $.get("import/last_result", {}, function(response) {
                initConsoleOutput(
                    response.data.console, _("import_v5.last_result"));
            });
        });
    });

    return {
        uploadCallback: uploadCallback,
    };
}();
