var XROAD_CENTRAL_SERVICE_EDIT = function() {
    var oProviders;

    var dialogTitle;
    var isNew;
    var isSearchableProvidersDialogOpen = false;

    /* -- PUBLIC - START -- */

    function open(serviceData) {
        XROAD_CENTERUI_COMMON.openDetailsIfAllowed(
                "central_services/can_see_details", function() {
            fillCentralServiceMemberClassSelect(function(){
                if (serviceData == null) {
                    initNewCentralServiceForm();
                } else {
                    initCentralServiceEditForm(serviceData);
                }

                openEditDialog();
                updateCentralServiceSaveOkButtonVisibility();
            });
        });
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function initNewCentralServiceForm() {
        dialogTitle = _("central_services.new_title");

        clearNewCentralServiceData();

        $("#central_service_details_service_code").enable();

        isNew = true;
    }

    function initCentralServiceEditForm(serviceData) {
        dialogTitle = _("central_services.edit_title",
            {service: serviceData.central_service_code});

        hideProviderSearchTable();

        $("#central_service_details_service_code").disable();

        fillEditableServiceData(serviceData);

        isNew = false;
    }

    function fillEditableServiceData(service) {
        $("#central_service_details_service_code")
                .val(service.central_service_code);
        $("#central_service_details_target_code").val(service
                .id_service_code);
        $("#central_service_details_service_version").val(service
                .id_service_version);
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

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

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

    function getSaveableCentralServiceData() {
        return {
            serviceCode: $("#central_service_details_service_code").val(),
            targetServiceCode: $("#central_service_details_target_code").val(),
            targetServiceVersion:
                $("#central_service_details_service_version").val(),
            targetProviderName: getProviderName(),
            targetProviderCode: getProviderCode(),
            targetProviderClass: getProviderClass(),
            targetProviderSubsystem: getProviderSubsystem()
        }
    }

    /* -- GET DATA - END -- */

    /* -- CLEAR FIELDS - START -- */

    function clearNewCentralServiceData() {
        $("#central_service_details_service_code").val("");

        clearImplementingServiceData();

        hideProviderSearchTable();
    }

    function clearImplementingServiceData() {
        $("#central_service_details_target_code").val("");
        $("#central_service_details_service_version").val("");
        $("#central_service_details_target_provider_name").val("");
        $("#central_service_details_target_provider_code").val("");
        $("#central_service_details_target_provider_class").val("");
        $("#central_service_details_target_provider_subsystem").val("");
    }

    /* -- CLEAR FIELDS - END -- */

    /* -- POST REQUESTS - START -- */

    function deleteImplementingService(serviceCode) {
        params = {serviceCode: serviceCode};

        confirm("central_services.remove_target_service_confirm",
                {service: serviceCode}, function() {
            $.post("central_services/delete_target_service", params, function(){
                XROAD_CENTRAL_SERVICES.refreshTable();
                clearImplementingServiceData();
            }, "json");
        });
    }

    function save(dialog) {
        var serviceData = getSaveableCentralServiceData();
        var controllerAction = isNew ?
                "save_service" : "update_service";

        $.post("central_services/" + controllerAction, serviceData, function() {
            XROAD_CENTRAL_SERVICES.refreshTable();
            $(dialog).dialog("close");
        }, "json");
    }

    /* -- POST REQUESTS - END -- */

    /* -- MISC - START -- */

    function hideProviderSearchTable() {
        $("#central_service_details_member_search").css({
            'visibility': 'hidden',
            'overflow-y': 'hidden',
            'height': '1px'
        });
    }

    function isProviderDataFilled() {
        return isInputFilled($(
                "#central_service_details_target_provider_name")) ||
            isInputFilled($(
                    "#central_service_details_target_provider_code")) ||
            isInputFilled($(
                    "#central_service_details_target_provider_class")) ||
            isInputFilled($(
                    "#central_service_details_target_provider_subsystem"));
    }

    function fillCentralServiceMemberClassSelect(callback) {
        $.get("application/member_classes", null, function(response){
            XROAD_CENTERUI_COMMON.fillSelectWithEmptyOption(
                    "central_service_details_target_provider_class",
                    response.data);
            callback();
        }, "json");
    }

    function focusSearchableProvidersFiltering() {
        $("#central_service_details_providers_filter label input").focus();
    }

    /* -- MISC - END -- */

    /* -- HANDLERS - START -- */

    function updateCentralServiceSaveOkButtonVisibility() {
        var okButton = $("#central_service_save_ok");
        if (isInputFilled($("#central_service_details_service_code"))) {
            okButton.enable();
        } else {
            okButton.disable();
        }
    }

    /* -- HANDLERS - END -- */

    /* -- DATA TABLES - START -- */

    function initSearchableProvidersTable() {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = "100px";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "name", "sWidth": "10em", mRender: util.escape },
            { "mData": "member_code", "sWidth": "10em", mRender: util.escape },
            { "mData": "member_class", "sWidth": "10em", mRender: util.escape },
            { "mData": "subsystem", "sWidth": "10em", mRender: util.escape }
        ];

        opts.sAjaxSource = "central_services/search_providers";

        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "providerSearchParams",
                "value": JSON.stringify(getSearchableProviderData())
            });
            aoData.push({
                "name": "allowZeroProviders",
                "value": isSearchableProvidersDialogOpen
            });
        };

        opts.aaSorting = [ [1,'asc'] ];

        oProviders = $('#central_service_details_providers').dataTable(opts);

        $("#central_service_details_providers tbody tr").live(
                "click", function(ev) {
            oProviders.setFocus(0, ev.target.parentNode)
        });

        $("#central_service_details_providers tbody tr").live(
                "dblclick", function() {
            fillProviderData(oProviders.getFocusData());
            $("#central_service_member_search_dialog").dialog("close");
        });
    }

    /* -- DATA TABLES - END -- */

    /* -- DIALOGS - START -- */

    function openSearchableProvidersDialog() {
        $("#central_service_member_search_dialog").initDialog({
            modal: true,
            height: 400,
            minHeight: 300,
            width: 800,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      fillProviderData(oProviders.getFocusData());
                      $(this).dialog("close");
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ],
            open: function() {
                isSearchableProvidersDialogOpen = true;

                initSearchableProvidersTable();
                focusSearchableProvidersFiltering();
            },
            close: function() {
                isSearchableProvidersDialogOpen = false;

                oProviders.fnDestroy();
            }
        });
    }

    function openEditDialog() {
        $("#central_service_details_dialog").initDialog({
            autoOpen: false,
            modal: true,
            title: dialogTitle,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  disabled: "disabled",
                  id: "central_service_save_ok",
                  click: function() {
                      save(this);
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

    /* -- DIALOGS - END -- */

    $(document).ready(function(){

        $("#central_service_details_search_provider").live("click", function() {
            openSearchableProvidersDialog();
        });

        $("#central_service_details_clear_search").live("click", function() {
            clearImplementingServiceData();
        });

        $("#central_service_details_found_provider_add").live(
                "click", function() {
            fillProviderData(oProviders.getFocusData());
        });

        $("#central_service_details_found_provider_cancel").live(
                "click", function() {
            $("#central_service_details_member_search").css({
                'visibility': 'hidden',
                'overflow-y': 'hidden',
                'height': '1px'
            });
        });

        $("#central_service_details_service_code").live("keyup", function() {
            updateCentralServiceSaveOkButtonVisibility();
        });
    });

    return {
        open: open
    };
} ();
