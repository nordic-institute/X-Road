/**
 * Common logic for views.
 */
var SDSB_CENTERUI_COMMON = function() {

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

    function deleteMember(requestParams) {
        var confirmParams = {
            memberCode: requestParams["memberCode"],
            memberClass: requestParams["memberClass"]
        };

        confirm("members.remove_confirm", confirmParams, function() {
            $.post("members/delete", requestParams, function() {
                $("#member_edit_dialog").dialog("close");

                if (typeof SDSB_MEMBERS != "undefined") {
                    SDSB_MEMBERS.redrawMembersTable();
                }
            }, "json");
        });
    }

    /* Functions related to cert details - start */

    function addCertDetailsParts(parts, titleSelector) {
        var previousParagraph = titleSelector;
        $.each(parts, function(index, each) {
            var addableItem = $('<p>',
                    {text: each, class: "ca_cert_detail"});
            previousParagraph.after(addableItem);
            previousParagraph = addableItem;
        });
    }

    function decorateCertDetails(details) {
        var raw_details = details.split("\/");
        return raw_details.length > 1 ? raw_details.slice(1) : raw_details;
    }

    function getCertDetailsLink(handleDetailsLinkClick) {
        var certDetailsLink = getDetailsLink(_("common.cert_view"));

        certDetailsLink.click(function(){
            handleDetailsLinkClick();
        });

        return certDetailsLink;
    }

    function openTempCertDetailsById(certId, controllerName) {
        var params = {certId: certId};

        $.get(controllerName + "/get_cert_details_by_id", params,
                function(response) {
            openCertDetailsWindow(response.data);
        }, "json");
    }

    function openCertDetailsWindow(certData) {
        $("#cert_details_dump").val(certData.cert_dump);
        $("#cert_details_hash").text(certData.cert_hash);
        openCertDetailsDialog();
    }

    /* Functions related to cert details - end */

    /* -- Common logic for advanced searches - start -- */

    // Public
    function showSimpleSearchElement(simpleSearchSelector) {
        var simpleSearchElement = $(simpleSearchSelector);
        simpleSearchElement.show();
        simpleSearchElement.find("input").val("");
    }

    function setSearchLinkText(linkSelector, translationKey) {
        $(linkSelector).text(_(translationKey));
    }

    /* -- Common logic for advanced searches - end -- */

    /* -- PUBLIC - END -- */

    function openCertDetailsDialog() {
        $("#cert_details_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 600,
            width: 700,
            buttons: [
              { text: "Close",
                  click: function() {
                      $(this).dialog("close");
                  }
              }
            ]
        }).dialog("open");
    }

    $(document).ready(function() {
        $("#server_auth_cert_file").live("change", function() {
            var uploadButton = $("#add_auth_cert_upload");
            isInputFilled($(this)) ?
                    uploadButton.enable() : uploadButton.disable();
        });
    });

    return {
        getDetailsLink: getDetailsLink,
        fillSelectWithEmptyOption: fillSelectWithEmptyOption,
        openDetailsIfAllowed: openDetailsIfAllowed,
        updateRecordsCount: updateRecordsCount,
        addCertDetailsParts: addCertDetailsParts,
        decorateCertDetails: decorateCertDetails,
        getCertDetailsLink: getCertDetailsLink,
        openTempCertDetailsById: openTempCertDetailsById,
        openCertDetailsWindow: openCertDetailsWindow,
        showSimpleSearchElement: showSimpleSearchElement,
        setSearchLinkText: setSearchLinkText,
        deleteMember: deleteMember
    };
}();
