var XROAD_REQUESTS = function() {
    var oManagementRequests;
    var translationsToDbValues = {};

    function enableActions() {
        if (oManagementRequests.getFocus()) {
            $(".request-action").enable();
        } else {
            $(".request-action").disable();
        }
    }

    function onDraw() {
        if (!oManagementRequests) return;
        if (!oManagementRequests.getFocus()) {
            $(".request-action").disable();
        } else {
            $(".request-action").enable();
        }
    }

    function getTranslatedSource(rawSource) {
        switch (rawSource) {
        case 'CENTER':
            return _("management_requests.source_center");
        case 'SECURITY_SERVER':
            return _("management_requests.source_security_server");
        default:
            alert("Source '" + rawSource + "'is not supported");
        break;
        }
    }

    function mapTranslationToDbValue(translation, dbValue) {
        translationsToDbValues[translation] = dbValue;
    }

    function createTranslationAndValueMappings() {
        mapTranslationToDbValue(getTranslatedSource('CENTER'), 'CENTER');
        mapTranslationToDbValue(getTranslatedSource('SECURITY_SERVER'),
                'SECURITY_SERVER');

        mapTranslationToDbValue(getTranslatedRequestType('AuthCertRegRequest'),
                'AuthCertRegRequest');
        mapTranslationToDbValue(getTranslatedRequestType('ClientRegRequest'),
                'ClientRegRequest');
        mapTranslationToDbValue(
                getTranslatedRequestType('AuthCertDeletionRequest'),
                'AuthCertDeletionRequest');
        mapTranslationToDbValue(
                getTranslatedRequestType('ClientDeletionRequest'),
                'ClientDeletionRequest');
        mapTranslationToDbValue(
                getTranslatedRequestType('OwnerChangeRequest'),
                'OwnerChangeRequest');
    }

    /**
     * Returns array of DB values with translations containing part of translation.
     *
     * For example, if partOfTranslation is "ser", translation "Security Server"
     * will be taken into consideration
     */
    function getDbValues(partOfTranslation) {
        if (partOfTranslation == null || partOfTranslation.length === 0) {
            return [];
        }

        var result = [];

        $.each(translationsToDbValues, function(key, value){
            if (key.containsIgnoreCase(partOfTranslation)) {
                result.push(value);
            }
        });

        return result;
    }

    function getFilteringValue() {
        return $(".dataTables_filter input").first().val();
    }

    function initRequestsTable() {
        var opts = defaultTableOpts();
        opts.fnDrawCallback = onDraw;
        opts.bServerSide = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";

        opts.fnRowCallback = function(nRow, request) {
            XROAD_CENTERUI_COMMON.translateRequestType(nRow, request.type, 2);

            var translatedSource = getTranslatedSource(request.source);
            $(nRow).find("td:eq(3)").text(translatedSource);
        };

        opts.fnDrawCallback = function() {
            XROAD_CENTERUI_COMMON.updateRecordsCount("requests");
            enableActions();
        }

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "requests/requests_refresh";
        opts.fnServerParams = function(aoData) {
            aoData.push({
                "name": "sSearchConverted",
                "value": getDbValues(getFilteringValue())
            });
        };

        opts.aaSorting = [ [1,'desc'] ];

        opts.aoColumns = [
            { "mData": "id", "sClass": "center", "sWidth": "4em" },
            { "mData": "received", "sWidth": "14em" },
            { "mData": "type"  },
            { "mData": "source" },

            { "mData": "server_owner_name", mRender: util.escape },
            { "mData": "server_owner_class", mRender: util.escape },
            { "mData": "server_owner_code", mRender: util.escape },
            { "mData": "server_code", mRender: util.escape },
            { "mData": "status", "sWidth": "8em", mRender: util.escape }
        ];
        opts.asRowId = ["id"];

        oManagementRequests = $('#management_requests_all').dataTable(opts);
        oManagementRequests.fnSetFilteringDelay(600);
    }

    function updateTable() {
        oManagementRequests.fnReloadAjax();
        enableActions();
    }

    function openRequestDetails() {
        var request = oManagementRequests.getFocusData();
        var updateTablesCallback = function() {
            updateTable();
        };
        XROAD_REQUEST_EDIT.open(request, updateTablesCallback);
    }

    function getTranslatedRequestType(rawRequestType) {
        return XROAD_CENTERUI_COMMON.getTranslatedRequestType(rawRequestType);
    }

    $(document).ready(function() {
        initRequestsTable();

        enableActions();
        focusInput();
        createTranslationAndValueMappings();

        var requestsTable = $("#management_requests_all");
        var requestDetailsButton = $("#request_details");

        requestsTable
        .on("click", "tbody tr", function(ev) {
            if (oManagementRequests.setFocus(0, ev.target.parentNode)) {
                $(".request-action").enable();
            }
        })
        .on("dblclick", "tbody td[class!=dataTables_empty]", function() {
            requestDetailsButton.click();
        });

        requestDetailsButton.click(function() {
            openRequestDetails();
        });
    });

    return {
        updateTable: updateTable
    }
}();
