var XROAD_CENTRAL_SERVICES = function () {
    var oCentralServices;

    function enableActions() {
        $("#central_service_add").enable();
        if (oCentralServices.getFocus()) {
            $(".central_service-action").enable();
        } else {
            $(".central_service-action").disable();
        }
    }

    function onDraw() {
        if (!oCentralServices) return;
        if (!oCentralServices.getFocus()
                || $("#central_service_details_form:visible").length > 0) {
            $(".central_service-action").disable();
        } else {
            $(".central_service-action").enable();
        }
    }

    function initTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = onDraw;
        opts.bServerSide = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "central_service_code", "sWidth": '250px', mRender: util.escape },
            { "mData": "id_service_code", "sWidth": '250px',
              "sClass": "implementing_service_data", mRender: util.escape },
            { "mData": "id_service_version",
              "sClass": "center implementing_service_data", "sWidth": "3em", mRender: util.escape},
            { "mData": "id_provider_code",
              "sClass": "implementing_service_data", mRender: util.escape },
            { "mData": "id_provider_class",
              "sClass": "implementing_service_data", mRender: util.escape },
            { "mData": "id_provider_subsystem", mRender: util.escape }
        ];
        opts.asRowId = ["central_service_code"];

        opts.fnDrawCallback = function() {
            XROAD_CENTERUI_COMMON.updateRecordsCount("central_services");
            enableActions();
        }

        opts.sAjaxSource = "central_services/services_refresh";

        opts.aaSorting = [ [2,'desc'] ];

        oCentralServices = $('#central_services').dataTable(opts);
        oCentralServices.fnSetFilteringDelay(600);
    }

    function refreshTable() {
        oCentralServices.fnReloadAjax();
    }

    $(document).ready(function() {
        $("#central_service_details_form").hide();

        initTable();

        enableActions();
        focusInput();

        $("#central_services tbody tr").live("click", function(ev) {
            if (oCentralServices.setFocus(0, ev.target.parentNode) &&
                    $("#central_service_details_form:visible").length == 0) {
                $(".central_service-action").enable();
            }
        });

        $("#central_services tbody tr").live("dblclick", function() {
            XROAD_CENTRAL_SERVICE_EDIT.open(oCentralServices.getFocusData());
        });

        $("#central_service_details").click(function() {
            XROAD_CENTRAL_SERVICE_EDIT.open(oCentralServices.getFocusData());
        });

        $("#central_service_add").click(function() {
            XROAD_CENTRAL_SERVICE_EDIT.open(null);
        });

        $("#central_service_delete").click(function() {
            var deletableServiceCode =
                oCentralServices.getFocusData().central_service_code;
            var requestParams = {serviceCode: deletableServiceCode};
            confirmParams = {service: deletableServiceCode};

            confirm("central_services.remove_confirm", confirmParams,
                    function() {
                $.post("central_services/delete_service", requestParams,
                        function() {
                    refreshTable();
                }, "json");
            });
        });
    });

    return {
        refreshTable: refreshTable
    };
}();
