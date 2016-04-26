var XROAD_CAS = function() {
    var cas;

    function enableActions() {
        $(".approved_ca-action").enable(!!cas.getFocus());
    }

    function initCasTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "valid_from", "sWidth": "14em" },
            { "mData": "valid_to", "sWidth": "14em" }
        ];
        opts.asRowId = ["id"];
        opts.aaSorting = [ [2,'desc'] ];

        cas = $("#cas").dataTable(opts);

        refreshCas();
    }

    function refreshCas(data) {
        if (data) {
            cas.fnReplaceData(data);
            enableActions();
            return;
        }

        $.get(action("top_cas"), null, function(response) {
            cas.fnReplaceData(response.data);
            enableActions();
        });
    }

    $(document).ready(function() {
        initCasTable();

        enableActions();

        $("#cas").on("click", "tbody tr", function() {
            cas.setFocus(0, this);
            enableActions();
        });

        $("#cas").on("dblclick", "tbody tr", function() {
            $("#ca_edit").click();
        });

        $("#ca_add").click(function() {
            XROAD_APPROVED_CA_DIALOG.openAddDialog();
        });

        $("#ca_details").click(function() {
            XROAD_APPROVED_CA_DIALOG.openEditDialog(cas.getFocusData());
        });

        $("#ca_delete").click(function() {
            var ca = cas.getFocusData();
            var requestParams = {ca_id: ca.id};
            var confirmParams = {approvedCa: ca.name};

            confirm("approved_cas.remove_confirm", confirmParams, function() {
                $.post(action("delete_top_ca"), requestParams, function(response) {
                    cas.fnReplaceData(response.data);
                    enableActions();
                }, "json");
            });
        });
    });

    return {
        refreshCas: refreshCas
    };
}();
