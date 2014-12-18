var SDSB_CENTRAL_SERVICES = function () {
    var oCentralServices;

    var isSearchAdvanced;

    var simpleSearchSelector = "#central_services_filter > label";
    var searchLinkSelector = "#central_services_filter > a";

    var executingAdvancedSearch = false;

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

    /* -- Logic related to advanced search - start -- */

    function toggleSearchMode() {
        if (isSearchAdvanced) {
            turnAdvancedSearchIntoSimpleSearch();
        } else {
            turnSimpleSearchIntoAdvancedSearch();
        }
    }

    function turnAdvancedSearchIntoSimpleSearch() {
        hideAdvancedSearch();
        SDSB_CENTERUI_COMMON.showSimpleSearchElement(simpleSearchSelector);
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                searchLinkSelector, "common.advanced_search");
        isSearchAdvanced = false;
    }

    function turnSimpleSearchIntoAdvancedSearch() {
        $(simpleSearchSelector).hide();
        showAdvancedSearch();
        SDSB_CENTERUI_COMMON.setSearchLinkText(
                searchLinkSelector, "common.simple_search");
        isSearchAdvanced = true;
    }

    function hideAdvancedSearch() {
        $("#central_services_advanced_search_fieldset").hide();
    }

    function showAdvancedSearch() {
        clearAdvancedSearchData();
        $("#central_services_advanced_search_fieldset").show();
    }

    function clearAdvancedSearchData() {
        $("#central_services_advanced_search_field_service_code").val("");
        $("#central_services_advanced_search_field_impl_service_code").val("");
        $("#central_services_advanced_search_field_provider_name").val("");
        $("#central_services_advanced_search_field_provider_code").val("");
        $("#central_services_advanced_search_field_provider_class").val("");
        $("#central_services_advanced_search_field_provider_subsystem").val("");
    }

    function getCentralServiceAdvancedSearchParams() {
        return {
            centralServiceCode:
                $("#central_services_advanced_search_field_service_code").val(),
            serviceCode:
                $("#central_services_advanced_search_field_impl_service_code")
                .val(),
            name:
                $("#central_services_advanced_search_field_provider_name")
                .val(),
            memberCode:
                $("#central_services_advanced_search_field_provider_code")
                .val(),
            memberClass:
                $("#central_services_advanced_search_field_provider_class")
                .val(),
            subsystem:
                $("#central_services_advanced_search_field_provider_subsystem")
                .val()
        }
    }

    /* -- Logic related to advanced search - end -- */

    function initTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = onDraw;
        opts.bServerSide = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "central_service_code", "sWidth": '250px' },
            { "mData": "id_service_code", "sWidth": '250px',
              "sClass": "implementing_service_data" },
            { "mData": "id_service_version",
              "sClass": "center implementing_service_data", "sWidth": "3em"},
            { "mData": "id_provider_code",
              "sClass": "implementing_service_data" },
            { "mData": "id_provider_class",
              "sClass": "implementing_service_data" },
            { "mData": "id_provider_subsystem" }
        ];
        opts.asRowId = ["central_service_code"];

        opts.fnDrawCallback = function() {
            SDSB_CENTERUI_COMMON.updateRecordsCount("central_services");
            enableActions();
        }

        opts.sAjaxSource = "central_services/services_refresh";

        opts.fnServerParams = function(aoData) {
            if (executingAdvancedSearch) {
                aoData.push({
                    "name": "advancedSearchParams",
                    "value": JSON.stringify(
                            getCentralServiceAdvancedSearchParams())
                });
                executingAdvancedSearch = false;
            }
        };

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

        hideAdvancedSearch();

        addAdvancedSearchLink("central_services_filter", function(){
            toggleSearchMode();
        });

        $("#central_services tbody tr").live("click", function(ev) {
            if (oCentralServices.setFocus(0, ev.target.parentNode) &&
                    $("#central_service_details_form:visible").length == 0) {
                $(".central_service-action").enable();
            }
        });

        $("#central_services tbody tr").live("dblclick", function() {
            SDSB_CENTRAL_SERVICE_EDIT.open(oCentralServices.getFocusData());
        });

        $("#central_service_details").click(function() {
            SDSB_CENTRAL_SERVICE_EDIT.open(oCentralServices.getFocusData());
        });

        $("#central_service_add").click(function() {
            SDSB_CENTRAL_SERVICE_EDIT.open(null);
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

        $("#central_services_advanced_search_execute").live("click",
                function() {
            executingAdvancedSearch = true;
            refreshTable();
        });


        $("#central_services_advanced_search_clear").live("click",
                function() {
            clearAdvancedSearchData();
        });
    });

    return {
        refreshTable: refreshTable
    };
}();
