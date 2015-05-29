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
        select = $("#" + selectId);
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

    function limitDialogMaxHeight(dialog) {
        dialog.dialog({ maxHeight: $(window).height() * DIALOG_MAX_HEIGHT });
    }

    /* -- PUBLIC - END -- */

    return {
        getDetailsLink: getDetailsLink,
        fillSelectWithEmptyOption: fillSelectWithEmptyOption,
        openDetailsIfAllowed: openDetailsIfAllowed,
        updateRecordsCount: updateRecordsCount,
        limitDialogMaxHeight: limitDialogMaxHeight
    };
}();
