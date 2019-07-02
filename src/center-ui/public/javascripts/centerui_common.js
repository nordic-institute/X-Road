/**
 * Common logic for views.
 */
var XROAD_CENTERUI_COMMON = function() {
    var DIALOG_MAX_HEIGHT = 0.8;

    /* -- PUBLIC - START -- */

    function getDetailsLink(text) {
        return $('<a>', {text: text, href: "#"}).addClass("open_details");
    }

    function fillSelectWithEmptyOption(selectId, options) {
        var select = $("#" + selectId);
        select.find('option').remove();
        select.append('<option value=""></option>');

        $.each(options, function(index, each){
            select.append('<option value="' + each + '">' + each + '</option>');
        });
    }

    /**
     * Checks privileges before opening details view, skips opening if not
     * allowed
     *
     * @param checkAction - controller/action pair that performs checking
     * @param openingCallback - opening action to be executed if allowed
     */
    function openDetailsIfAllowed(checkAction, openingCallback) {
        $.get(checkAction, {}, function(response) {
            if (response.data.can == true) {
                openingCallback();
            }
        }, "json");
    }

    /**
     * Updates count in page heading, invokes action 'get_records_count' on
     * particular controller. Does not apply to tables in modal windows.
     *
     * @param controllerName
     */
    function updateRecordsCount(controllerName) {
        $.get(controllerName + "/get_records_count", null, function(response) {
            $("#records_count").text(" (" + response.data.count + ")")
        }, "json");
    }

    /**
     * Sets height of the dialog. Lets it be 'auto' if it is smaller than
     * maximum * required height. Limits it when it is larger and 'minHeight'
     * property is not set.
     *
     * Assumes that dialog is open (only this way we can get the height)
     */
    function limitDialogHeight(dialog) {
        var maxHeight = $(window).height() * DIALOG_MAX_HEIGHT;

        var heightValue = dialog.height() > maxHeight ? maxHeight : "auto";
        dialog.dialog({ height: heightValue });
    }

    function translateRequestType(row, rawType, columnNo) {
        var translatedRequestType = getTranslatedRequestType(rawType);
        $(row).find("td:eq(" + columnNo + ")").text(translatedRequestType);
    }

    function getTranslatedRequestType(rawRequestType) {
        switch (rawRequestType) {
        case 'AuthCertRegRequest':
            return _("management_requests.auth_cert_reg");
        case 'ClientRegRequest':
            return _("management_requests.client_reg");
        case 'AuthCertDeletionRequest':
            return _("management_requests.auth_cert_deletion");
        case 'ClientDeletionRequest':
            return _("management_requests.client_deletion");
        case 'OwnerChangeRequest':
            return _("management_requests.owner_change");
        default:
            alert("Type '" + rawRequestType + "'is not supported");
        break;
        }
    }

    /* -- PUBLIC - END -- */

    return {
        getDetailsLink: getDetailsLink,
        fillSelectWithEmptyOption: fillSelectWithEmptyOption,
        openDetailsIfAllowed: openDetailsIfAllowed,
        updateRecordsCount: updateRecordsCount,
        limitDialogHeight: limitDialogHeight,
        getTranslatedRequestType: getTranslatedRequestType,
        translateRequestType: translateRequestType
    };
}();
