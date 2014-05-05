var oManagementRequests;
var translationsToDbValues = {};

function enableActions() {
    if (oManagementRequests.setFocus()) {
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

function getTranslatedRequestType(rawRequestType) {
    switch (rawRequestType) {
    case 'AuthCertRegRequest':
        return _("management_requests.request_type.auth_cert_reg");
    case 'ClientRegRequest':
        return _("management_requests.request_type.client_reg");
    case 'AuthCertDeletionRequest':
        return _("management_requests.request_type.auth_cert_deletion");
    case 'ClientDeletionRequest':
        return _("management_requests.request_type.client_deletion");
    default:
        alert("Type '" + rawRequestType + "'is not supported");
    break;
    }
}

function getTranslatedSource(rawSource) {
    switch (rawSource) {
    case 'CENTER':
        return _("management_requests.source.center");
    case 'SECURITY_SERVER':
        return _("management_requests.source.security_server");
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
    mapTranslationToDbValue(getTranslatedRequestType('AuthCertDeletionRequest'),
            'AuthCertDeletionRequest');
    mapTranslationToDbValue(getTranslatedRequestType('ClientDeletionRequest'),
            'ClientDeletionRequest');
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

// TODO: This is probably useful elsewhere as well
function getFilteringValue() {
    return $(".dataTables_filter input").first().val();
}

function initRequestsTable() {
    var opts = defaultOpts(onDraw, 16);
    opts.bProcessing = true;
    opts.bServerSide = true;
    opts.sScrollY = "400px";
    opts.sScrollX = "100%";
    opts.sDom = "<'dataTables_header'f<'clearer'>>tpr";

    opts.fnRowCallback = function(nRow, request) {
        var translatedRequestType =
                getTranslatedRequestType(request.type);
        $(nRow).find("td:eq(2)").text(translatedRequestType);
        var translatedSource = getTranslatedSource(request.source);
        $(nRow).find("td:eq(3)").text(translatedSource);
    };

    opts.fnDrawCallback = function() {
        updateRecordsCount("requests");
        enableActions();
    }

    opts.bScrollInfinite = true;
    opts.sAjaxSource = action("requests_refresh");
    opts.fnServerParams = function(aoData) {
        aoData.push({
            "name": "sSearchConverted",
            "value": getDbValues(getFilteringValue())
        });
    };

    opts.aaSorting = [ [1,'desc'] ];

    opts.aoColumns = [
        { "mData": "id" },
        { "mData": "received" },
        { "mData": "type" },
        { "mData": "source" },

        { "mData": "server_owner_name" },
        { "mData": "server_owner_class" },
        { "mData": "server_owner_code" },
        { "mData": "server_code" },
        { "mData": "status" }
    ];

    oManagementRequests = $('#management_requests_all').dataTable(opts);
    oManagementRequests.fnSetFilteringDelay(600);
}

$(document).ready(function() {
    initRequestsTable();

    enableActions();
    focusInput();
    createTranslationAndValueMappings();

    $("#management_requests_all tbody td[class!=dataTables_empty]")
            .live("click",function(ev) {
        if (oManagementRequests.setFocus(0, ev.target.parentNode)) {
            $(".request-action").enable();
        }
    });

    $("#management_requests_all tbody tr[class!=dataTables_empty]")
            .live("dblclick", function() {
        openRequestDetails(oManagementRequests.getFocusData());
    });

    $("#request_details").click(function() {
        openRequestDetails(oManagementRequests.getFocusData());
    });
});