var XROAD_REQUEST_CLIENT_DELETION = function(){
    /* -- PUBLIC - START -- */

    function openDialog(fnAfterRequestAdded) {
        $("#securityserver_client_remove_dialog").initDialog({
            autoOpen : false,
            modal : true,
            height : "auto",
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
            } ],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogMaxHeight($(this));
            }
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

        var subsystemCode = client.subsystemCode != null ?
            client.subsystemCode : "";

        $("#server_client_remove_name").text(client.name);
        $("#server_client_remove_class").text(client.memberClass);
        $("#server_client_remove_code").text(client.memberCode);
        $("#server_client_remove_subsystem_code").text(subsystemCode);

        $("#server_client_remove_owner_name").text(server.ownerName);
        $("#server_client_remove_owner_class").text(server.ownerClass);
        $("#server_client_remove_owner_code").text(server.ownerCode);
        $("#server_client_remove_server_code").text(server.serverCode);
    }

    /* -- PUBLIC - END -- */

    function clearServerClientRemoveData() {
        $("#server_client_remove_name").text("");
        $("#server_client_remove_class").text("");
        $("#server_client_remove_code").text("");
        $("#server_client_remove_subsystem_code").text("");
        $("#server_client_remove_owner_name").text("");
        $("#server_client_remove_owner_class").text("");
        $("#server_client_remove_owner_code").text("");
        $("#server_client_remove_server_code").text("");
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
            memberClass : $("#server_client_remove_class").text(),
            memberCode : $("#server_client_remove_code").text(),
            subsystemCode : $("#server_client_remove_subsystem_code").text(),
            ownerClass : $("#server_client_remove_owner_class").text(),
            ownerCode : $("#server_client_remove_owner_code").text(),
            serverCode : $("#server_client_remove_server_code").text()
        };
    }

    return {
        openDialog: openDialog,
        fillRemovableClientData: fillRemovableClientData
    };
}();
