var XROAD_REQUEST_EDIT = function(){
    var updateTables = function() {
        // By default do nothing.
    }

    /* -- PUBLIC - START -- */

    function open(requestData, updateTablesCallback) {
        XROAD_CENTERUI_COMMON.openDetailsIfAllowed("requests/can_see_details",
                function(){
            updateTables = updateTablesCallback;
            fillRequestDetails(requestData);
        });
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function fillRequestDetails(requestData) {
        var params = {
            id: requestData.id
        };

        $.get("requests/get_additional_request_data", params,
                function(response) {
            var completeRequestData = $.extend({}, requestData, response.data);

            fillCommonRequestDetails(completeRequestData);
            fillSpecificRequestDetails(requestData);
        }, "json");
    }

    function fillCommonRequestDetails(requestData) {
        var comments = requestData.comments != null ? requestData.comments : "";
        var serverAddress = requestData.server_address != null ?
            requestData.server_address : "";

        $(".management_request_id").text(requestData.id);
        $(".management_request_received").text(requestData.received);
        $(".management_request_source").text(requestData.source);
        $(".management_request_status").text(requestData.status);
        $(".management_request_complementary_id")
                .text(requestData.complementary_id);
        $(".management_request_revoking_id").text(requestData.revoking_id);
        $(".management_request_comments").text(comments);

        fillMemberName(
                "management_request_server_owner_name",
                requestData.server_owner_name);
        $(".management_request_server_owner_class")
                .text(requestData.server_owner_class);
        $(".management_request_server_owner_code")
                .text(requestData.server_owner_code);
        $(".management_request_server_code").text(requestData.server_code);
        $(".management_request_server_address").text(serverAddress);

        initializeRequestDetailsForm(requestData);
    }

    function fillSpecificRequestDetails(requestData) {
        var params = {
            id: requestData.id
        };

        switch (requestData.type) {
        case 'AuthCertRegRequest':
            $.get("requests/get_auth_cert_reg_request_data", params,
                    function(response) {
                openRequestEditDialog(
                    $("#auth_cert_reg_request_edit_dialog"));
                fillRequestAuthCertData(response.data);
            }, "json");
            break;
        case 'ClientRegRequest':
            $.get("requests/get_client_reg_request_data", params,
                    function(response) {
                openRequestEditDialog(
                    $("#client_reg_request_edit_dialog"));
                fillClientData(response.data);
            }, "json");
            break;
        case 'AuthCertDeletionRequest':
            $.get("requests/get_auth_cert_deletion_request_data", params,
                    function(response) {
                openRequestEditDialog(
                    $("#auth_cert_deletion_request_edit_dialog"));
                fillRequestAuthCertData(response.data)
            }, "json");
            break;
        case 'ClientDeletionRequest':
            $.get("requests/get_client_deletion_request_data", params,
                    function(response) {
                openRequestEditDialog(
                    $("#client_deletion_request_edit_dialog"));
                fillClientData(response.data);
            }, "json");
            break;
        default:
            // Should not reach this point!
            alert("Type '" + requestData.type + "'is not supported");
            break;
        };
    }

    function openRequestEditDialog(dialog) {
        XROAD_CENTERUI_COMMON.limitDialogMaxHeight(dialog);
        dialog.dialog("open");
        blurInputs();
    }

    function initializeRequestDetailsForm(requestData) {
        var complementaryId = $(".complementary_id");
        var revokingId = $(".revoking_id");
        var status = requestData.status

        complementaryId.show();
        revokingId.hide();
        $(".reg_request_post_submit").hide();
        $(".reg_request_revoke").hide();

        if (status == "WAITING") {
            $(".reg_request_revoke").show();
            complementaryId.hide();
        } else if (status == "SUBMITTED FOR APPROVAL") {
            $(".reg_request_post_submit").show();
        } else if (status == "REVOKED") {
            complementaryId.hide();
            revokingId.show();
        }

        if (requestData.source == "SECURITY_SERVER") {
            $(".reg_request_revoke").hide();
        }

        if (requestData.type == "ClientDeletionRequest"
                || requestData.type == "AuthCertDeletionRequest") {
            complementaryId.hide();
            $(".request_status").hide();
        }
    }

    function fillRequestAuthCertData(certData) {
        $(".auth_cert_details_csp").text(certData.csp);
        $(".auth_cert_details_serial_number").text(certData.serial_number);
        $(".auth_cert_details_subject").text(certData.subject);
        $(".auth_cert_details_expires").text(certData.expires);
    }

    function fillClientData(clientData) {
        var subsystemCode = clientData.subsystem_code != null ?
            clientData.subsystem_code : "";

        fillMemberName("client_details_name", clientData.member_name);

        $(".client_details_class").text(clientData.member_class);
        $(".client_details_code").text(clientData.member_code);
        $(".client_details_subsystem_code").text(subsystemCode );
    }

    function fillMemberName(memberNameClass, memberName) {
        var nameFieldSelector = $("." + memberNameClass);
        var notFoundClass = "notfound";

        if (memberFound(memberName)) {
            nameFieldSelector.text(memberName);
            nameFieldSelector.removeClass(notFoundClass);
        } else {
            nameFieldSelector.text(
                    _("management_requests.details.member_not_found"));
            nameFieldSelector.addClass(notFoundClass);
        }
    }


    function memberFound(memberName) {
        return memberName && memberName.trim().length > 0;
    }

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

    function getManagementRequestId() {
        return $("#management_request_edit_common")
                .find("#management_request_id").text();
    }

    /* -- GET DATA - END -- */

    /* -- POST REQUESTS - START -- */

    function revokeAuthCertRegRequest(dialog) {
        revokeManagementRequest("revoke_auth_cert_reg_request", dialog);
    }

    function revokeClientRegRequest(dialog) {
        revokeManagementRequest("revoke_client_reg_request", dialog);
    }

    function revokeManagementRequest(action, dialog) {
        handleManagementRequestDialogAction(
            action, dialog, "management_requests.details.revoke_confirm");
    }

    function approveRegRequest(dialog) {
        handleManagementRequestDialogAction(
            "approve_reg_request", dialog,
            "management_requests.details.approve_confirm");
    }

    function declineRegRequest(dialog) {
        handleManagementRequestDialogAction(
            "decline_reg_request", dialog,
            "management_requests.details.decline_confirm");
    }

    function handleManagementRequestDialogAction(
            action, dialog, confirmTranslationKey) {
        var params = {requestId: getManagementRequestId()};

        confirm(confirmTranslationKey, null, function() {
            $.post("requests/" + action, params, function(){
                updateTables();
                dialog.dialog("close");
            }, "json");
        });
    }

    /* -- POST REQUESTS - END -- */

    /* -- DIALOGS - START -- */

    function initDialogs() {
        initAuthCertRegRequestEditDialog();
        initClientRegRequestEditDialog();
        initAuthCertDeletionRequestEditDialog();
        initClientDeletionRequestEditDialog();
    }

    function initAuthCertRegRequestEditDialog() {
        $("#auth_cert_reg_request_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 600,
            minWidth: 500,
            buttons: [
              { text: _("common.approve"),
                  class: "reg_request_post_submit",
                  click: function() {
                      approveRegRequest($(this));
                  }
              },
              { text: _("common.decline"),
                  class: "reg_request_post_submit",
                  click: function() {
                      declineRegRequest($(this));
                  }
              },
              { text: _("common.close"),
                  class: "right",
                  click: function() {
                      $(this).dialog("close");
                  }
              },
              { text: _("common.revoke"),
                  class: "reg_request_revoke right",
                  click: function() {
                      revokeAuthCertRegRequest($(this));
                  }
              }]
        });
    }

    function initClientRegRequestEditDialog() {
        $("#client_reg_request_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 600,
            minWidth: 500,
            buttons: [
              { text: _("common.approve"),
                  class: "reg_request_post_submit",
                  click: function() {
                      approveRegRequest($(this));
                  }
              },
              { text: _("common.decline"),
                  class: "reg_request_post_submit",
                  click: function() {
                      declineRegRequest($(this));
                  }
              },
              { text: _("common.close"),
                  class: "right",
                  click: function() {
                      $(this).dialog("close");
                  }
              },
              { text: _("common.revoke"),
                  class: "reg_request_revoke right",
                  click: function() {
                      revokeClientRegRequest($(this));
                  }
              }]
        });
    }

    function initAuthCertDeletionRequestEditDialog() {
        $("#auth_cert_deletion_request_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 600,
            minWidth: 500,
            buttons: [
              { text: _("common.close"),
                  class: "right",
                  click: function() {
                      $(this).dialog("close");
                  }
              }]
        });
    }

    function initClientDeletionRequestEditDialog() {
        $("#client_deletion_request_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: "auto",
            width: 600,
            minWidth: 500,
            buttons: [
              { text: _("common.close"),
                  class: "right",
                  click: function() {
                      $(this).dialog("close");
                  }
              }]
        });
    }
    /* -- DIALOGS - END -- */
    function initTestability() {
        // add data-name attributes to improve testability
        $("#auth_cert_reg_request_edit_dialog").parent().attr("data-name", "auth_cert_reg_request_edit_dialog");
        $("#client_reg_request_edit_dialog").parent().attr("data-name", "client_reg_request_edit_dialog");
        $("#auth_cert_deletion_request_edit_dialog").parent().attr("data-name", "auth_cert_deletion_request_edit_dialog");
        $("#client_deletion_request_edit_dialog").parent().attr("data-name", "client_deletion_request_edit_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }
    function blurInputs() {
        $("input").blur();
    }

    $(document).ready(function() {
        initDialogs();
        initTestability();
    });

    return {
        open: open
    };
}();
