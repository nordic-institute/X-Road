var oProviders;

var dialogTitle;
var isNew;

function openCentralServiceDetails(serviceData) {
    openDetailsIfAllowed("central_services/can_see_details", function(){
        fillCentralServiceMemberClassSelect(function(){
            if (serviceData == null) {
                initNewCentralServiceForm();
            } else {
                initCentralServiceEditForm(serviceData);
            }
    
            openCentralServiceDetailsDialog();
        });
    });
}

function initNewCentralServiceForm() {
    dialogTitle = _("central_services.new.title")

    clearNewCentralServiceData();

    $("#central_service_details_service_code").enable();

    isNew = true;
}

function initCentralServiceEditForm(serviceData) {
    dialogTitle = _("central_service.edit.title", 
            [serviceData.central_service_code]);

    hideProviderSearchTable();

    $("#central_service_details_service_code").disable();

    fillEditableServiceData(serviceData)
    isNew = false;
}

function clearNewCentralServiceData() {
    $("#central_service_details_service_code").val("");

    clearImplementingServiceData();

    hideProviderSearchTable();
}

function clearImplementingServiceData() {
    $("#central_service_details_target_code").val("");
    $("#central_service_details_target_provider_name").val("");
    $("#central_service_details_target_provider_code").val("");
    $("#central_service_details_target_provider_class").val("");
    $("#central_service_details_target_provider_subsystem").val("");
}

function hideProviderSearchTable() {
    $("#central_service_details_member_search").hide();
}

function getSaveableCentralServiceData() {
    return {
        serviceCode: $("#central_service_details_service_code").val(),
        targetServiceCode: $("#central_service_details_target_code").val(),
        targetProviderName: getProviderName(),
        targetProviderCode: getProviderCode(),
        targetProviderClass: getProviderClass(),
        targetProviderSubsystem: getProviderSubsystem()
    }
}

function getSearchableProviderData() {
    return {
        name: getProviderName(),
        memberCode: getProviderCode(),
        memberClass: getProviderClass(),
        subsystem: getProviderSubsystem()
    }
}

function getProviderName() {
    return $("#central_service_details_target_provider_name").val();
}

function getProviderCode() {
    return $("#central_service_details_target_provider_code").val();
}

function getProviderClass() {
    return $("#central_service_details_target_provider_class").val();
}

function getProviderSubsystem() {
    return $("#central_service_details_target_provider_subsystem").val();
}

function initSearchableProvidersTable() {
    var opts = defaultOpts(onDraw, 100);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.bDestroy = true;
    opts.bScrollInfinite = true;
    opts.sScrollY = "150px";
    opts.sScrollX = "100%";
    opts.sDom = "<<'clearer'>>tpr";
    opts.aoColumns = [
        { "mData": "name" },
        { "mData": "member_code" },
        { "mData": "member_class" },
        { "mData": "subsystem" },
    ];

    opts.sAjaxSource = "central_services/search_providers";

    opts.fnServerParams = function(aoData) {
        aoData.push({
            "name": "providerSearchParams",
            "value": JSON.stringify(getSearchableProviderData())
        })
    };

    opts.fnDrawCallback = function() {
        handleProviderSearchResults();
    }

    opts.aaSorting = [ [1,'asc'] ];

    oProviders = $('#central_service_details_providers').dataTable(opts);
    oProviders.fnSetFilteringDelay(600);
}

function fillCentralServiceMemberClassSelect(callback) {
    $.post("application/member_classes", null, function(response){
        fillSelectWithEmptyOption("central_service_details_target_provider_class",
                response.data);
        callback();
    }, "json");
}

function handleProviderSearchResults() {
    var results = oProviders.fnGetData();
    var resultsLength = results.length;

    if (resultsLength > 1) {
        initFoundProvidersTable();
    } else if (resultsLength == 1) {
        fillProviderData(results[0]);
    }
}

function initFoundProvidersTable() {
    oProviders.setFocus();
    $("#central_service_details_member_search").show();
}

function fillEditableServiceData(service) {
    $("#central_service_details_service_code")
            .val(service.central_service_code);
    $("#central_service_details_target_code").val(service
            .id_service_code);
    $("#central_service_details_target_provider_name")
            .val(service.provider_name);
    $("#central_service_details_target_provider_code")
            .val(service.id_provider_code);
    $("#central_service_details_target_provider_class")
            .val(service.id_provider_class);
    $("#central_service_details_target_provider_subsystem")
            .val(service.id_provider_subsystem);
}

function fillProviderData(provider) {
    $("#central_service_details_target_provider_name").val(provider.name);
    $("#central_service_details_target_provider_code")
            .val(provider.member_code);
    $("#central_service_details_target_provider_class")
            .val(provider.member_class);
    $("#central_service_details_target_provider_subsystem")
            .val(provider.subsystem);
}

function openCentralServiceDetailsDialog() {
    $("#central_service_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        title: dialogTitle,
        height: "auto",
        width: "auto",
        buttons: [
            { text: _("ok"),
              click: function() {
                var dialog = this;
                var serviceData = getSaveableCentralServiceData();
                var controllerAction = isNew ?
                        "save_service" : "update_service";

                $.post("central_services/" + controllerAction, serviceData,
                        function() {
                    refreshCentralServicesTable();
                    $(dialog).dialog("close");
                }, "json");
              }
            },
            { text: _("cancel"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    }).dialog("open");
}

function deleteImplementingService(serviceCode) {
    params = {serviceCode: serviceCode};

    confirm("central_services.remove_target_service.confirm", [serviceCode],
            function() {
        $.post("central_services/delete_target_service", params, function(){
            refreshCentralServicesTable();
            clearImplementingServiceData();
        }, "json");
    });
}

$(document).ready(function(){
    $("#central_service_details_search_provider").live("click", function() {
        initSearchableProvidersTable();
    });

    $("#central_service_details_clear_search").live("click", function() {
        clearImplementingServiceData();
    });

    $("#central_service_details_found_provider_add").live("click", function() {
        fillProviderData(oProviders.getFocusData());
    });

    $("#central_service_details_found_provider_cancel").live("click", function() {
        $("#central_service_details_member_search").hide();
    });
    
    $("#central_service_details_providers tbody tr").live("click", function(ev) {
        oProviders.setFocus(0, ev.target.parentNode)
    });

    $("#central_service_details_providers tbody tr").live("dblclick", function() {
        fillProviderData(oProviders.getFocusData());
    });
});
