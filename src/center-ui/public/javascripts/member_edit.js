var XROAD_MEMBER_EDIT = function() {
    var oAddableUsedServers;
    var remainingGlobalGroups = {};
    var fnServerDataRefresh;

    function open(memberData) {
        if (can("view_member_details")) {
            fnServerDataRefresh = openMemberEditDialog(memberData);
        }
    }

    function openById(memberId) {
        var params = {
            memberId: memberId
        };

        $.get("members/get_member_by_id", params, function(response) {
            open(response.data);
        }, "json");
    }

    function openMemberNameEditDialog(memberId, memberName, onSubmit) {
        $("#member_edit_name_new").val(memberName);

        $("#member_name_edit_dialog").initDialog({
            modal: true,
            height: 175,
            width: 700,
            buttons: [{
                text: _("common.ok"),
                click: function() {
                    var self = this;
                    var newName = $("#member_edit_name_new").val();
                    var params = {
                        memberClass: memberId.memberClass,
                        memberCode: memberId.memberCode,
                        memberName: newName
                    };

                    $.post("members/member_edit", params, function(response) {
                        if (typeof onSubmit != "undefined") {
                            onSubmit(response.data.name);
                        }

                        XROAD_MEMBERS.redrawMembersTable();

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

    function openOwnedServersAddDialog(memberId, memberName, onSubmit) {
        clearOwnedServerAddData();

        $("#owned_server_add_dialog .auth_cert_details").hide();
        $("#owned_server_owner_name").text(memberName);
        $("#owned_server_owner_class").text(memberId.memberClass);
        $("#owned_server_owner_code").text(memberId.memberCode);

        $("#owned_server_add_dialog").initDialog({
            modal: true,
            height: "auto",
            width: 500,
            buttons: [{
                text: _("common.submit"),
                id: "add_owned_server_submit",
                disabled: "disabled",
                click: function() {
                    var self = this;
                    var params = {
                        ownerClass: $("#owned_server_owner_class").text(),
                        ownerCode: $("#owned_server_owner_code").text(),
                        serverCode: $("#owned_server_add_servercode").val(),
                        tempCertId: $("#owned_server_temp_cert_id").text()
                    };

                    $.post("members/add_new_owned_server_request", params,
                            function(response) {
                        clearOwnedServerAddData();

                        if (typeof onSubmit != "undefined") {
                            onSubmit();
                        }

                        $(self).dialog("close");
                    }, "json");
                }
            }, {
                text: _("common.cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogHeight($(this));
            },
            close: function() {
                $("#owned_server_cert_upload").val("");
                $(this).dialog("destroy");
            }
        });

        // FUTURE: Make it work with enableForInput() instead!
        // Test with Linux and Chromium browser!
        $("#owned_server_add_servercode").on("change keyup paste",
                function(ev) {
            updateOwnedServerCertSubmitButton(ev);
        });

        $("#owned_server_cert_upload").unbind("change")
            .change(function() {
                $("#member_auth_cert_upload").submit();
                // function uploadCallbackOwnedServerAuthCert manages
                // post-submission activities on UI part
            });
        // for testability
        $("#owned_server_add_dialog").parent().attr("data-name", "owned_server_add_dialog");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('Submit')").parent().attr("data-name", "submit");
    }

    function uploadCallbackOwnedServerAuthCert(response) {
        var submitButton = $("#add_owned_server_submit");

        if (response.success) {
            $("#owned_server_authcert_csp").text(response.data.csp);
            $("#owned_server_authcert_serial_number").text(
                response.data.serial_number);
            $("#owned_server_authcert_subject").text(response.data.subject);
            $("#owned_server_authcert_expires").text(response.data.expires);
            $("#owned_server_temp_cert_id").text(response.data.temp_cert_id);

            updateOwnedServerCertSubmitButton();

            $(".auth_cert_details").show();
        } else {
            clearOwnedServerCertData();

            submitButton.disable();
            $(".auth_cert_details").hide();
        }

        showMessages(response.messages);

        return true;
    }

    function refreshServerData() {
        if (typeof fnServerDataRefresh != 'function') {
            return;
        }

        fnServerDataRefresh();
    }

    function clearOwnedServerAddData() {
        $("#owned_server_add_servercode").val("");
        clearOwnedServerCertData();
    }

    function clearOwnedServerCertData() {
        $("#add_owned_server_submit").disable();
        $("#owned_server_temp_cert_id").text("");

        $("#owned_server_authcert_csp").val("");
        $("#owned_server_authcert_serial_number").val("");
        $("#owned_server_authcert_subject").val("");
        $("#owned_server_authcert_expires").val("");
    }

    function openSubsystemAddDialog(memberId, memberName, onSubmit) {
        $("#subsystem_add_code").val("");

        $("#subsystem_add_dialog").initDialog({
            modal: true,
            height: 200,
            width: 500,
            buttons: [{
                id: "subsystem_add_submit",
                text: _("common.ok"),
                click: function() {
                    var self = this;
                    var params = {
                        memberClass: memberId.memberClass,
                        memberCode: memberId.memberCode,
                        subsystemCode: $("#subsystem_add_code", this).val()
                    };

                    $.post("members/add_subsystem", params, function(response) {
                        onSubmit(response);
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

        $("#subsystem_add_submit").enableForInput("#subsystem_add_code");
    }

    function openMemberToGlobalGroupAddDialog(memberId, memberName, onSubmit) {
        $("#member_to_group_add_dialog").initDialog({
            title: _("members.edit.global_group_membership.add_member_to_group", {
                member: memberName
            }, false),
            modal: true,
            width: 600,
            open: function() {
                $.get("members/subsystem_codes", memberId, function(response) {
                    var options = response.data;

                    XROAD_CENTERUI_COMMON.fillSelectWithEmptyOption(
                        "member_to_group_add_select_subsystem", options);

                    if (options.length > 0) {
                        $("#member_to_group_add_select_subsystem").enable();
                    } else {
                        $("#member_to_group_add_select_subsystem").disable();
                    }
                }, "json");

                fillGlobalGroupSelect(memberId);
            },
            buttons: [{
                text: _("common.ok"),
                click: function() {
                    var self = this;
                    var params = {
                        memberClass: memberId.memberClass,
                        memberCode: memberId.memberCode,
                        subsystemCode:
                            $("#member_to_group_add_select_subsystem").val(),
                        groupCode: $("#member_to_group_add_select_group").val()
                    };

                    $.post("members/add_member_to_global_group", params, function(response) {
                        if (typeof onSubmit != "undefined") {
                            onSubmit(response);
                        }

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

        $("#member_to_group_add_select_subsystem").unbind("change")
            .change(function() {
                fillGlobalGroupSelect(memberId);
            });

        $("#member_to_group_add_select_group").unbind("change")
            .change(function() {
                $("#member_to_group_add_description").val(
                    remainingGlobalGroups[this.value]);
            });
    }

    function fillGlobalGroupSelect(memberId) {
        var select = $("#member_to_group_add_select_group");
        select.find('option').remove();

        var params = memberId;
        params.subsystemCode = $("#member_to_group_add_select_subsystem").val();

        $.get("members/remaining_global_groups", params, function(response) {
            $.each(response.data, function(index, each) {
                var code = each.code;
                select.append(
                    '<option value="' + code + '">' + code + '</option>');
                remainingGlobalGroups[code] = each.description;
            });

            $("#member_to_group_add_description").val(
                remainingGlobalGroups[select.val()]);
        }, "json");
    }

    function selectUsedServer(usedServerSearchDialog) {
        fillUsedServerFields();
        updateUsedServerSubmitButton();
        closeDialog(usedServerSearchDialog);
    }

    function fillUsedServerFields() {
        var serverData = oAddableUsedServers.getFocusData();

        $("#used_server_owner_name").text(serverData.owner_name);
        $("#used_server_owner_class").text(serverData.owner_class);
        $("#used_server_owner_code").text(serverData.owner_code);
        $("#used_server_server_code").text(serverData.server_code);
    }

    function updateUsedServerSubmitButton() {
        var usedServerSubmitButton = $("#member_used_server_register_submit");

        if (isReadonlyInputFilled($("#used_server_owner_class")) &&
                isReadonlyInputFilled($("#used_server_owner_code"))) {
            usedServerSubmitButton.enable();
        } else {
            usedServerSubmitButton.disable();
        }
    }

    function updateOwnedServerCertSubmitButton(ev) {
        var submitButton = $("#add_owned_server_submit");

        if (isInputFilled($("#owned_server_add_servercode"), ev)
                && isInputFilled($("#owned_server_cert_upload"))) {
            submitButton.enable();
        } else {
            submitButton.disable();
        }
    }

    function openUsedServersRegisterDialog(memberId, memberName, onSubmit,
            clientIsManagementServiceProvider) {
        clearUsedServerAddData();

        var title = clientIsManagementServiceProvider
            ? _("members.edit.used_servers.add.management_service_provider_title")
            : _("members.edit.used_servers.add.title");

        $("#used_server_name").text(memberName);
        $("#used_server_class").text(memberId.memberClass);
        $("#used_server_code").text(memberId.memberCode);

        if (clientIsManagementServiceProvider) {
            $("#used_server_subsystem_code")
                .val(memberId.subsystemCode).disable();
        }

        $("#member_used_server_register_dialog").initDialog({
            title: title,
            modal: true,
            height: 600,
            width: 560,
            buttons: [{
                text: _("common.submit"),
                disabled: "disabled",
                id: "member_used_server_register_submit",
                click: function() {
                    var self = this;
                    var params = {
                        memberClass: $("#used_server_class").text(),
                        memberCode: $("#used_server_code").text(),
                        subsystemCode: $("#used_server_subsystem_code").val(),
                        ownerClass: $("#used_server_owner_class").text(),
                        ownerCode: $("#used_server_owner_code").text(),
                        serverCode: $("#used_server_server_code").text()
                    };

                    var postUrl = clientIsManagementServiceProvider
                        ? "system_settings/service_provider_register"
                        : "members/add_new_server_client_request";

                    $.post(postUrl, params, function(response) {
                        clearUsedServerAddData();

                        $(self).dialog("close");

                        if (typeof onSubmit != "undefined") {
                            onSubmit(response);
                        }
                    }, "json");
                }
            }, {
                text: _("common.cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }],
            open: function() {
                XROAD_CENTERUI_COMMON.limitDialogHeight($(this));
                initUsedServerSubsystemCodeAutocomplete();
            },
            close: function() {
                destroyUsedServerSubsystemCodeAutocomplete();
                $(this).dialog("destroy");
            }
        });

        $("#used_server_server_search").unbind("click")
            .click(function() {
                openSecurityServerSearchDialog();
            });
    }

    function openSecurityServerSearchDialog() {
        $("#member_securityserver_search_select").disable();
        $("#securityserver_search_dialog").dialog("open");
    }

    function clearUsedServerAddData() {
        $("#used_server_name").text(""),
        $("#used_server_class").text(""),
        $("#used_server_code").text(""),
        $("#used_server_subsystem_code").val(""),

        $("#used_server_owner_name").text(""),
        $("#used_server_owner_class").text(""),
        $("#used_server_owner_code").text(""),
        $("#used_server_server_code").text("")
    }

    function initUsedServerSubsystemCodeAutocomplete() {
        var subsystemCodeInput = $("#used_server_subsystem_code");
        var params = {
            memberClass: $("#used_server_class").text(),
            memberCode: $("#used_server_code").text()
        };

        $.get("members/subsystem_codes", params, function(response) {
            subsystemCodeInput.autocomplete({source: response.data});
        }, "json");
    }

    function destroyUsedServerSubsystemCodeAutocomplete() {
        $("#used_server_subsystem_code").autocomplete("destroy");
    }

    function initSecurityServerSearchDialog() {
        $("#securityserver_search_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 430,
            width: 860,
            open: function() {
                initAddableUsedServersTable();
            },
            buttons: [{
                text: _("common.select"),
                disabled: "disabled",
                id: "member_securityserver_search_select",
                click: function() {
                    selectUsedServer(this);
                }
            }, {
                text: _("common.cancel"),
                click: function() {
                    $(this).dialog("close");
                }
            }]
        });
    }

    function initAddableUsedServersTable() {
        var opts = defaultTableOpts();
        opts.bDestroy = true;
        opts.bServerSide = true;
        opts.sScrollY = 400;
        opts.bScrollCollapse = true;
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData" : "owner_name", "mRender": util.escape },
            { "mData" : "owner_class", "mRender": util.escape },
            { "mData" : "owner_code", "mRender": util.escape },
            { "mData" : "server_code", "mRender": util.escape }
        ];

        opts.bScrollInfinite = true;
        opts.sAjaxSource = "securityservers/securityservers_refresh";
        opts.aaSorting = [[2, "desc"]];

        oAddableUsedServers = $("#used_server_search_all").dataTable(opts);

        oAddableUsedServers
        .on("click", "tbody td[class!=dataTables_empty]",
                function(ev) {
            if (oAddableUsedServers.setFocus(0, ev.target.parentNode)) {
                $("#member_securityserver_search_select").enable();
            }
        })
        .off("dblclick")
        .on( "dblclick", "tbody td[class!=dataTables_empty]", function() {
            $("#member_securityserver_search_select").click();
        });
    }
    function initTestability() {
        // add data-name attributes to improve testability
        $("#member_name_edit_dialog").parent().attr("data-name", "member_name_edit_dialog");
        $("#owned_server_add_dialog").parent().attr("data-name", "owned_server_add_dialog");
        $("#member_to_group_add_dialog").parent().attr("data-name", "member_to_group_add_dialog");
        $("#securityserver_search_dialog").parent().attr("data-name", "securityserver_search_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initSecurityServerSearchDialog();
        initTestability();
    });

    return {
        open: open,
        openById: openById,

        openMemberNameEditDialog: openMemberNameEditDialog,
        openOwnedServersAddDialog: openOwnedServersAddDialog,
        openMemberToGlobalGroupAddDialog: openMemberToGlobalGroupAddDialog,
        openSubsystemAddDialog: openSubsystemAddDialog,
        openUsedServersRegisterDialog: openUsedServersRegisterDialog,

        refreshServerData: refreshServerData,

        uploadCallbackOwnedServerAuthCert: uploadCallbackOwnedServerAuthCert
    };
}();
