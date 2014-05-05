function fillCommonRequestDetails(requestData) {
    $(".management_request_id").val(requestData.id);
    $(".management_request_received").val(requestData.received);
    $(".management_request_source").val(requestData.source);
    $(".management_request_status").val(requestData.status);
    $(".management_request_complementary_id").val(requestData.complementary_id);
    $(".management_request_canceling_id").val(requestData.canceling_id);
    $(".management_request_comments").val(requestData.comments);

    $(".management_request_server_owner_name").val(requestData.server_owner_name);
    $(".management_request_server_owner_class").val(requestData.server_owner_class);
    $(".management_request_server_owner_code").val(requestData.server_owner_code);
    $(".management_request_server_code").val(requestData.server_code);
    $(".management_request_server_address").val(requestData.server_address);

    initializeRequestDetailsForm(requestData);
}

function initializeRequestDetailsForm(requestData) {
    var complementary_id = $(".complementary_id");
    var canceling_id = $(".canceling_id");

    complementary_id.show();
    canceling_id.hide();

    if (requestData.status == "WAITING") {
        $(".reg_request_cancel").show();
    } else if (requestData.status == "CANCELED") {
        complementary_id.hide();
        canceling_id.show();
        $(".reg_request_cancel").hide();
    }

    if (requestData.source == "SECURITY_SERVER") {
        $(".reg_request_cancel").hide();
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

function openRequestDetails(requestData) {
    openDetailsIfAllowed("requests/can_see_details", function(){
        fillCommonRequestDetails(requestData);

        var params = {
            id: requestData.id
        };

        switch (requestData.type) {
        case 'AuthCertRegRequest':
            $.post("requests/get_auth_cert_reg_request_data", params,
                    function(response) {
                $("#auth_cert_reg_request_details_dialog").dialog("open");
                fillRequestAuthCertData(response.data);
            }, "json");
            break;
        case 'ClientRegRequest':
            $.post("requests/get_client_reg_request_data", params,
                    function(response) {
                $("#client_reg_request_details_dialog").dialog("open");
                fillClientData(response.data);
            }, "json");
            break;
        case 'AuthCertDeletionRequest':
            $.post("requests/get_auth_cert_deletion_request_data", params,
                    function(response) {
                $("#auth_cert_deletion_request_details_dialog").dialog("open");
                fillRequestAuthCertData(response.data)
            }, "json");
            break;
        case 'ClientDeletionRequest':
            $.post("requests/get_client_deletion_request_data", params,
                    function(response) {
                $("#client_deletion_request_details_dialog").dialog("open");
                fillClientData(response.data);
            }, "json");
            break;
        default:
            // Should not reach this point!
            alert("Type '" + requestData.type + "'is not supported");
            break;
        }
    });
}

function cancelAuthCertRegRequest(dialog) {
    cancelManagementRequest("cancel_auth_cert_reg_request", dialog);
}

function cancelClientRegRequest(dialog) {
    cancelManagementRequest("cancel_client_reg_request", dialog);
}

function cancelManagementRequest(action, dialog) {
    var params = {requestId: getManagementRequestId()};

    confirm("management_requests.cancel.confirm", [], function() {
        $.post("requests/" + action, params, function(){
            refreshManagementRequestsTable();
            dialog.dialog("close");
        }, "json");
    });
}

function getManagementRequestId() {
    return $("#management_request_details_common")
            .find("#management_request_id").val();
}

function refreshManagementRequestsTable() {
    if (oManagementRequests != null) {
        oManagementRequests.fnReloadAjax();
    }
}

$(document).ready(function() {
    $("#auth_cert_reg_request_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 650,
        width: 500,
        buttons: [
        { text: _("cancel"),
            class: "reg_request_cancel",
            click: function() {
                cancelAuthCertRegRequest($(this));
            }
        },
        { text: _("close"),
            click: function() {
                $(this).dialog("close");
            }
        }]
    });

    $("#client_reg_request_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: 700,
        buttons: [
        { text: _("cancel"),
            class: "reg_request_cancel",
            click: function() {
                cancelClientRegRequest($(this));
            }
        },
        { text: _("close"),
            click: function() {
                $(this).dialog("close");
            }
        }]
    });

    $("#auth_cert_deletion_request_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: 700,
        buttons: [
        { text: _("close"),
            click: function() {
                $(this).dialog("close");
            }
        }]
    });

    $("#client_deletion_request_details_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: 700,
        buttons: [
        { text: _("close"),
            click: function() {
                $(this).dialog("close");
            }
        }]
    });
});