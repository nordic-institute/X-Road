/**
 * Common logic for views.
 */

function getDetailsLink(text) {
    return $('<a>', {text: text, href: "#"}).addClass("open_details");
}

function openMemberDetailsById(memberId) {
    var params = {memberId: memberId};

    $.post("members/get_member_by_id", params, function(response) {
        openMemberDetails(response.data);
        hideMemberDetailsTables();
    }, "json");
}

function openManagementRequestDetails(requestData) {
    openRequestDetails(requestData);
}

function fillSelectWithEmptyOption(selectId, options) {
    select = $("#" + selectId);
    select.find('option').remove();
    select.append('<option value=""></option>');

    $.each(options, function(index, each){
        select.append('<option value="' + each + '">' + each + '</option>');
    });
}

/* Functions related to cert details - start */

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
    var certDetailsLink = getDetailsLink(_("cert_view"));

    certDetailsLink.click(function(){
        handleDetailsLinkClick();
    });

    return certDetailsLink;
}

function openTempCertDetailsById(certId, controllerName) {
    var params = {certId: certId};

    $.post(controllerName + "/get_cert_details_by_id", params,
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

/**
 * Checks privileges before opening details view, skips opening if not allowed
 * 
 * @param checkAction - controller/action pair that performs checking
 * @param openingCallback - opening action to be executed if allowed
 */
function openDetailsIfAllowed(checkAction, openingCallback) {
    $.post(checkAction, {}, function(response) {
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

// -- Common logic for advanced searches - start ---

function showSimpleSearchElement(simpleSearchSelector) {
    var simpleSearchElement = $(simpleSearchSelector);
    simpleSearchElement.show();
    simpleSearchElement.find("input").val("");
}

function fillSdsbInstanceSelect(selectId) {
    $.post("groups/sdsb_instance_codes", null, function(response) {
        fillSelectWithEmptyOption(selectId, response.data);
    }, "json");
}

function fillTypeSelect(selectId) {
    $.post("groups/types", null, function(response) {
        fillSelectWithEmptyOption(selectId, response.data);
    }, "json");
}

function setSearchLinkText(linkSelector, translationKey) {
    $(linkSelector).text(_(translationKey));
}

//-- Common logic for advanced searches - end ---
