var SDSB_REQUEST_CLIENT_DELETION = function(){
    /* -- PUBLIC - START -- */

    function openDialog(fnAfterRequestAdded) {
        $("#securityserver_client_remove_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : 430,
            width : 460,
            buttons : [
            {
                text : _("common.submit"),
                click : function() {
                    addServerClientDeletionRequest(this, fnAfterRequestAdded);
                }
            }, {
                text : _("common.cancel"),
                click : function() {
                    $(this).dialog("close");
                }
            } ]
        }).dialog("open");
    }

    /**
     * requestData must be in the following format:
     * 
     *  client: {
     *      name: ...,
     *      memberClass: ...,
     *      memberCode: ...,
     *      subsystemCode: ...
     *  },
     *  server: {
     *      ownerName: ...,
     *      ownerClass: ...,
     *      ownerCode: ...,
     *      serverCode: ...
     *  }
     */
    function fillRemovableClientData(requestData) {
        var client = requestData.client;
        var server = requestData.server;
 
        $("#server_client_remove_name").val(client.name);
        $("#server_client_remove_class").val(client.memberClass);
        $("#server_client_remove_code").val(client.memberCode);
        $("#server_client_remove_subsystem_code").val(client.subsystemCode);

        $("#server_client_remove_owner_name").val(server.ownerName);
        $("#server_client_remove_owner_class").val(server.ownerClass);
        $("#server_client_remove_owner_code").val(server.ownerCode);
        $("#server_client_remove_server_code").val(server.serverCode);
    }

    /* -- PUBLIC - END -- */

    function clearServerClientRemoveData() {
        $("#server_client_remove_name").val(""), $("#server_client_remove_class")
                .val(""), $("#server_client_remove_code").val(""), $(
                "#server_client_remove_subsystem_code").val(""), $(
                "#server_client_remove_owner_name").val(""), $(
                "#server_client_remove_owner_class").val(""), $(
                "#server_client_remove_owner_code").val(""), $(
                "#server_client_remove_server_code").val("")
    }

    function addServerClientDeletionRequest(dialog, fnAfterRequestAdded) {
        var params = getClientRemovingRequestParams();

        $.post("members/delete_server_client_request", params,
                function() {
            clearServerClientRemoveData();

            if (typeof fnAfterRequestAdded == 'function') {
                fnAfterRequestAdded();
            }

            $(dialog).dialog("close");
        }, "json");
    }

    function getClientRemovingRequestParams() {
        return {
            memberClass : $("#server_client_remove_class").val(),
            memberCode : $("#server_client_remove_code").val(),
            subsystemCode : $("#server_client_remove_subsystem_code").val(),
            ownerClass : $("#server_client_remove_owner_class").val(),
            ownerCode : $("#server_client_remove_owner_code").val(),
            serverCode : $("#server_client_remove_server_code").val()
        };
    }

    return {
        openDialog: openDialog,
        fillRemovableClientData: fillRemovableClientData
    };
}();