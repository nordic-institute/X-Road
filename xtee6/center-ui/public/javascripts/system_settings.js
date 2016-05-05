(function(SYSTEM_SETTINGS, $, undefined) {
    var oMemberClasses;

    function initMemberClassesTable() {
        var opts = scrollableTableOpts(200);
        opts.sDom = "t";
        opts.asStripeClasses = [];
        opts.aoColumns = [
            { "mData": "code", "sClass": "uppercase", "sWidth": "15em" },
            { "mData": "description" }
        ];
        opts.fnRowCallback = null;
        opts.asRowId = ["code"];

        oMemberClasses = $("#member_classes").dataTable(opts);

        $("#member_classes").on("click", "tbody tr", function() {
            if (oMemberClasses.getFocus() == this) {
                oMemberClasses.removeFocus();
                $("#member_class_edit, #member_class_delete").disable();
            } else {
                if (oMemberClasses.setFocus(0, this)) {
                    $("#member_class_edit, #member_class_delete").enable();
                }
            }
        });

        $.get(action("member_classes"), null, function(response) {
            oMemberClasses.fnReplaceData(response.data);
        });
    }

    function initDialogs() {
        $("#central_server_address_edit_dialog").initDialog({
            modal: true,
            autoOpen: false,
            height: "auto",
            width: 500,
            buttons: [{
                text: _("common.ok"),
                click: function() {
                    var self = this;

                    var oldValue = $("#central_server_address").val();
                    var newValue = $("#central_server_address_new").val();

                    if (oldValue == newValue) {
                        $(self).dialog("close");
                        return;
                    }

                    var params = {
                        centralServerAddress: newValue
                    };

                    $.post(action("central_server_address_edit"), params,
                           function(response) {
                        $("#central_server_address").val(newValue);
                        $("#wsdl_address").text(response.data.wsdl_address);
                        $("#services_address").text(response.data.services_address);

                        $(self).dialog("close");
                    }, "json");
                }
            }, {
                text: _("common.cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }]
        });
    }

    function openMemberClassEditDialog(add) {
        $("#member_class_edit_dialog").initDialog({
            title: add
                ? _("system_settings.index.member_class_add")
                : _("system_settings.index.member_class_edit"),
            modal: true,
            autoOpen: true,
            height: "auto",
            width: 500,
            buttons: [{
                text: _("common.ok"),
                click: function() {
                    var self = this;

                    var params = {
                        code: $("#member_class_code").val(),
                        description: $("#member_class_description").val()
                    };

                    var url = action(add ?
                        "member_class_add" : "member_class_edit");

                    $.post(url, params, function(response) {
                        oMemberClasses.fnReplaceData(response.data);
                        $(self).dialog("close");
                    }, "json");
                }
            }, {
                text: _("common.cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }],
            close: function() {
                $(this).dialog("destroy");
            }
        });
    }

    function initActions() {
        $("#member_class_edit, #member_class_delete").disable();

        $("#central_server_address_edit").click(function() {
            $("#central_server_address_new").val(
                $("#central_server_address").val());
            $("#central_server_address_edit_dialog").dialog("open");
        });

        $("#service_provider_edit").click(function() {
            MEMBER_SEARCH_DIALOG.open(null, function(member) {
                var params = {
                    providerClass: member.member_class,
                    providerCode: member.member_code
                };

                if (member.subsystem_code) {
                    params.providerSubsystem = member.subsystem_code;
                }

                $.post(action("service_provider_edit"), params, function(response) {
                    $("#service_provider_id").val(response.data.id);
                    $("#service_provider_name").text(response.data.name);
                }, "json");
            }, false);
        });

        $("#member_class_add").click(function() {
            $("#member_class_code, #member_class_description").val("").enable();

            openMemberClassEditDialog(true);
        });

        $("#member_class_edit").click(function() {
            var memberClass = oMemberClasses.getFocusData();

            $("#member_class_code").val(memberClass.code).disable();
            $("#member_class_description").val(memberClass.description);

            openMemberClassEditDialog(false);
        });

        $("#member_class_delete").click(function() {
            var params = {
                code: oMemberClasses.getFocusData().code
            };

            $.post(action("member_class_delete"), params, function(response) {
                oMemberClasses.fnReplaceData(response.data);
            }, "json");
        });
    }
    function initTestability() {
        // add data-name attributes to improve testability
        $("#central_server_address_edit_dialog").parent().attr("data-name", "central_server_address_edit_dialog");
        $("#member_class_edit_dialog").parent().attr("data-name", "member_class_edit_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initMemberClassesTable();
        initDialogs();
        initActions();
        initTestability();
    });

}(window.SYSTEM_SETTINGS = window.SYSTEM_SETTINGS || {}, jQuery));
