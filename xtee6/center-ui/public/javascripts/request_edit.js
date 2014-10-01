var SDSB_REQUEST_EDIT = function(){
    /* -- PUBLIC - START -- */

    function open(requestData) {
        SDSB_CENTERUI_COMMON.openDetailsIfAllowed("requests/can_see_details",
                function(){
            fillCommonRequestDetails(requestData);

            var params = {
                id: requestData.id
            };

            switch (requestData.type) {
            case 'AuthCertRegRequest':
                $.get("requests/get_auth_cert_reg_request_data", params,
                        function(response) {
                    $("#auth_cert_reg_request_edit_dialog").dialog("open");
                    fillRequestAuthCertData(response.data);
                    blurInputs();
                }, "json");
                break;
            case 'ClientRegRequest':
                $.get("requests/get_client_reg_request_data", params,
                        function(response) {
                    $("#client_reg_request_edit_dialog").dialog("open");
                    fillClientData(response.data);
                    blurInputs();
                }, "json");
                break;
            case 'AuthCertDeletionRequest':
                $.get("requests/get_auth_cert_deletion_request_data", params,
                        function(response) {
                    $("#auth_cert_deletion_request_edit_dialog").dialog("open");
                    fillRequestAuthCertData(response.data)
                    blurInputs();
                }, "json");
                break;
            case 'ClientDeletionRequest':
                $.get("requests/get_client_deletion_request_data", params,
                        function(response) {
                    $("#client_deletion_request_edit_dialog").dialog("open");
                    fillClientData(response.data);
                    blurInputs();
                }, "json");
                break;
            default:
                // Should not reach this point!
                alert("Type '" + requestData.type + "'is not supported");
                break;
            };
        });
    }

    /* -- PUBLIC - END -- */

    /* -- REFRESH DATA - START -- */

    function fillCommonRequestDetails(requestData) {
        $(".management_request_id").val(requestData.id);
        $(".management_request_received").val(requestData.received);
        $(".management_request_source").val(requestData.source);
        $(".management_request_status").val(requestData.status);
        $(".management_request_complementary_id")
                .val(requestData.complementary_id);
        $(".management_request_revoking_id").val(requestData.revoking_id);
        $(".management_request_comments").val(requestData.comments);

        $(".management_request_server_owner_name")
                .val(requestData.server_owner_name);
        $(".management_request_server_owner_class")
                .val(requestData.server_owner_class);
        $(".management_request_server_owner_code")
                .val(requestData.server_owner_code);
        $(".management_request_server_code").val(requestData.server_code);
        $(".management_request_server_address").val(requestData.server_address);

        initializeRequestDetailsForm(requestData);
    }

    function initializeRequestDetailsForm(requestData) {
        var complementary_id = $(".complementary_id");
        var revoking_id = $(".revoking_id");
        var status = requestData.status

        complementary_id.show();
        revoking_id.hide();
        $(".reg_request_post_submit").hide();
        $(".reg_request_revoke").hide();

        if (status == "WAITING") {
            $(".reg_request_revoke").show();
        } else if (status == "SUBMITTED FOR APPROVAL") {
            $(".reg_request_post_submit").show();
        } else if (status == "REVOKED") {
            complementary_id.hide();
            revoking_id.show();
        }

        if (requestData.source == "SECURITY_SERVER") {
            $(".reg_request_revoke").hide();
        }

        if (requestData.type == "ClientDeletionRequest"
                || requestData.type == "AuthCertDeletionRequest") {
            complementary_id.hide();
            $(".request_status").hide();
        }
    }

    function fillRequestAuthCertData(certData) {
        $(".auth_cert_details_csp").val(certData.csp);
        $(".auth_cert_details_serial_number").val(certData.serial_number);
        $(".auth_cert_details_subject").val(certData.subject);
        $(".auth_cert_details_expires").val(certData.expires);
    }

    function fillClientData(clientData) {
        $(".client_details_name").val(clientData.member_name);
        $(".client_details_class").val(clientData.member_class);
        $(".client_details_code").val(clientData.member_code);
        $(".client_details_subsystem_code").val(clientData.subsystem_code);
    }

    function updateManagementRequestsTable() {
        if (typeof SDSB_REQUESTS != 'undefined') {
            SDSB_REQUESTS.updateTable();
        }

        if (typeof SDSB_MEMBER_EDIT != 'undefined') {
            SDSB_MEMBER_EDIT.refreshManagementRequests();
        }

        if (typeof SDSB_SECURITYSERVER_EDIT != 'undefined') {
            SDSB_SECURITYSERVER_EDIT.refreshManagementRequests();
        }
    }

    /* -- REFRESH DATA - END -- */

    /* -- GET DATA - START -- */

    function getManagementRequestId() {
        return $("#management_request_edit_common")
                .find("#management_request_id").val();
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
                updateManagementRequestsTable();
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
            height: 700,
            width: "auto",
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
            height: 700,
            width: "auto",
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
            height: 700,
            width: "auto",
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
            height: 700,
            width: "auto",
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

    function blurInputs() {
        $("input").blur();
    }

    $(document).ready(function() {
        initDialogs();
    });

    return {
        open: open
    };
}();
