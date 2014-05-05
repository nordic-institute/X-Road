var oCentralServices;

var centralServicesAdvancedSearch;

var centralServicesSimpleSearchSelector = "#central_services_filter > label";
var centralServicesSearchLinkSelector = "#central_services_filter > a";

var executingCentralServicesAdvancedSearch = false;

function enableActions() {
    $("#central_service_add").enable();
    if (oCentralServices.setFocus()) {
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

// -- Logic related to advanced search - start

function toggleCentralServicesSearchMode() {
    if (centralServicesAdvancedSearch) {
        turnCentralServicesAdvancedSearchIntoSimpleSearch();
    } else {
        turnCentralServicesSimpleSearchIntoAdvancedSearch();
    }
}

function turnCentralServicesAdvancedSearchIntoSimpleSearch() {
    hideCentralServicesAdvancedSearch();
    showSimpleSearchElement(centralServicesSimpleSearchSelector);
    setSearchLinkText(centralServicesSearchLinkSelector, "advanced_search");
    centralServicesAdvancedSearch = false;
}

function turnCentralServicesSimpleSearchIntoAdvancedSearch() {
    $(centralServicesSimpleSearchSelector).hide();
    showCentralServicesAdvancedSearch();
    setSearchLinkText(centralServicesSearchLinkSelector, "simple_search");
    centralServicesAdvancedSearch = true;
}

function hideCentralServicesAdvancedSearch() {
    $("#central_services_advanced_search_fieldset").hide();
}

function showCentralServicesAdvancedSearch() {
    clearCentralServicesAdvancedSearchData();
    $("#central_services_advanced_search_fieldset").show();
}

function clearCentralServicesAdvancedSearchData() {
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
            $("#central_services_advanced_search_field_impl_service_code").val(),
        name: 
            $("#central_services_advanced_search_field_provider_name").val(),
        memberCode: 
            $("#central_services_advanced_search_field_provider_code").val(),
        memberClass: 
            $("#central_services_advanced_search_field_provider_class").val(),
        subsystem: 
            $("#central_services_advanced_search_field_provider_subsystem").val()
    }
}

// -- Logic related to advanced search - end

function initCentralServicesTable() {
    var opts = defaultOpts(onDraw, 100);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.bScrollInfinite = true;
    opts.sScrollY = "400px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "central_service_code", "sWidth": '250px' },
        { "mData": "id_service_code", "sWidth": '250px' },
        { "mData": "id_provider_code" },
        { "mData": "id_provider_class" },
        { "mData": "id_provider_subsystem" }
    ];

    opts.fnDrawCallback = function() {
        updateRecordsCount("central_services");
        enableActions();
    }

    opts.sAjaxSource = action("services_refresh");

    opts.fnServerParams = function(aoData) {
        if (executingCentralServicesAdvancedSearch) {
            aoData.push({
                "name": "advancedSearchParams",
                "value": JSON.stringify(getCentralServiceAdvancedSearchParams())
            });
            executingCentralServicesAdvancedSearch = false;
        }
    };

    opts.fnRowCallback = function (nRow, member) {
        $(nRow).find("td:eq(1)").addClass("implementing_service_data");
        $(nRow).find("td:eq(2)").addClass("implementing_service_data");
        $(nRow).find("td:eq(3)").addClass("implementing_service_data");
        $(nRow).find("td:eq(4)").addClass("implementing_service_data");
    }

    opts.aaSorting = [ [2,'desc'] ];

    oCentralServices = $('#central_services').dataTable(opts);
    oCentralServices.fnSetFilteringDelay(600);
}

function refreshCentralServicesTable() {
    oCentralServices.fnReloadAjax();
}

$(document).ready(function() {
    $("#central_service_details_form").hide();

    initCentralServicesTable();

    enableActions();
    focusInput();

    hideCentralServicesAdvancedSearch();

    addAdvancedSearchLink("central_services_filter", function(){
        toggleCentralServicesSearchMode();
    });

    $("#central_services tbody tr").live("click", function(ev) {
        if (oCentralServices.setFocus(0, ev.target.parentNode) &&
                $("#central_service_details_form:visible").length == 0) {
            $(".central_service-action").enable();
        }
    });

    $("#central_services tbody tr").live("dblclick", function() {
        openCentralServiceDetails(oCentralServices.getFocusData());
    });

    $("#central_service_details").click(function() {
        openCentralServiceDetails(oCentralServices.getFocusData());
    });

    $("#central_service_add").click(function() {
        openCentralServiceDetails(null);
    });

    $("#central_service_delete").click(function() {
        var deletableServiceCode =
            oCentralServices.getFocusData().central_service_code;
        var requestParams = {serviceCode: deletableServiceCode};
        confirmParams = [deletableServiceCode];

        confirm("central_services.remove.confirm", confirmParams, function() {
            $.post(action("delete_service"), requestParams, function() {
                refreshCentralServicesTable();
            }, "json");
        });
    });

    $("#central_services_advanced_search_execute").live("click",
            function() {
        executingCentralServicesAdvancedSearch = true;
        refreshCentralServicesTable();
    });


    $("#central_services_advanced_search_clear").live("click",
            function() {
        clearCentralServicesAdvancedSearchData();
    });
});