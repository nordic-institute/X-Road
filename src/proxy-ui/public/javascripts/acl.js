(function(ACL, $, undefined) {

    var oSubjects;

    function enableActions() {
        if ($("#subjects .row_selected").length > 0) {
            $("#service_acl_subjects_remove_selected").enable();
        } else {
            $("#service_acl_subjects_remove_selected").disable();
        }
    }

    function initServiceAclDialog() {
        $("#service_acl_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 490,
            width: "95%",
            buttons: [
                {
                    text: _("common.close"),
                    click: function() {
                        SERVICES.updateSubjectsCount(oSubjects.fnGetData().length);
                        $(this).dialog("close");
                    }
                },
                {
                    id: "service_acl_subjects_remove_all",
                    text: _("clients.service_acl_dialog.remove_all"),
                    privilege: "edit_service_acl",
                    click: function() {
                        var service = $("#service_acl_dialog #service option:selected");
                        var params = {
                            client_id: $("#details_client_id").val(),
                            service_code: service.val()
                        };

                        confirm("clients.service_acl_dialog.remove_all_confirm", null,
                                function() {
                            $.post(action("service_acl_subjects_remove"), params,
                                   function(response) {
                                oSubjects.fnReplaceData(response.data);
                                enableActions();
                            }, "json");
                        });
                    }
                },
                {
                    id: "service_acl_subjects_remove_selected",
                    text: _("clients.service_acl_dialog.remove_selected"),
                    privilege: "edit_service_acl",
                    click: function() {
                        var service = $("#service_acl_dialog #service option:selected");
                        var params = {
                            client_id: $("#details_client_id").val(),
                            service_code: service.val(),
                            subject_ids: []
                        };

                        var subjects = [];
                        $("#subjects .row_selected").each(function(i, row) {
                            var selected = oSubjects.fnGetData(row);
                            var subject = [
                                selected.type,
                                selected.name_description,
                                selected.member_group_code];

                            if (oSubjects.fnGetData(row).subsystem_code !== null) {
                                subject.push(oSubjects.fnGetData(row).subsystem_code);
                            }

                            subjects.push('<li>' + util.escape(subject.join(', ')) + '</li>');
                        });

                        var joinedSubjects = '<ol class="alert-ol">' + subjects.join('') + '</ol>';

                        confirm_unsafe(
                            confirm_title("clients.service_acl_dialog.remove_selected_confirm"),
                            _("clients.service_acl_dialog.remove_selected_confirm",{subjects: joinedSubjects}, false),
                            function() {
                                $("#subjects .row_selected").each(function(idx, row) {
                                    params.subject_ids.push(oSubjects.fnGetData(row).subject_id);
                                });
                                $.post(action("service_acl_subjects_remove"), params, function(response) {
                                    oSubjects.fnReplaceData(response.data);
                                    enableActions();
                                }, "json");
                            });
                    }
                },
                {
                    id: "service_acl_subjects_add",
                    text: _("clients.service_acl_dialog.add_subjects"),
                    privilege: "edit_service_acl",
                    click: function() {
                        ACL_SUBJECTS_SEARCH.openDialog(oSubjects.fnGetData(), function(subjectIds) {
                            var service = $("#service_acl_dialog #service option:selected");
                            var params = {
                                client_id: $("#details_client_id").val(),
                                service_code: service.val(),
                                subject_ids: subjectIds
                            };

                            $.post(action("service_acl_subjects_add"), params, function(response) {
                                oSubjects.fnReplaceData(response.data);
                            }, "json");
                        });
                    }
                }
            ]
        });
    }

    function initServiceAclSubjectsTable() {
        var opts = scrollableTableOpts(230);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "name_description",
              "mRender": function(data, type, full) {
                  if (type == 'display') {
                      return full.member_class ?
                          clientName(data) : groupDesc(data);
                  }
                  return data;
              },
              "fnCreatedCell": function(nTd, sData, oData) {
                  if (!oData.name_description) {
                      $(nTd).addClass("missing");
                  }
              }
            },
            { "mData": "type", "bVisible": false, mRender: util.escape },
            { "mData": "instance", "bVisible": false, mRender: util.escape },
            { "mData": "member_class", "bVisible": false, mRender: util.escape },
            { "mData": "member_group_code", "bVisible": false, mRender: util.escape },
            { "mData": "subsystem_code", "bVisible": false, mRender: util.escape },
            {
                mData: function(source, type, val) {
                    return generateIdElement({
                        "Type": source.type,
                        "Instance": source.instance,
                        "Class": source.member_class,
                        "Code": source.member_group_code,
                        "Subsystem": source.subsystem_code
                    });
                }
            },
            { "mData": "rights_given", "sWidth": "15%" }
        ];

        oSubjects = $("#subjects").dataTable(opts);

        var dialog = $("#service_acl_dialog");

        $(".simple_search .search", dialog).click(function() {
            oSubjects.fnFilter($("[name=subject_search_all]", dialog).val());
            return false;
        });

        $(".advanced_search .search", dialog).click(function() {
            $(".advanced_search", dialog).find("input, select").each(
                function(idx, val) {
                    oSubjects.fnFilter($(this).val(), idx);
                });
            return false;
        });
    }

    function initServiceAclActions() {
        $("#service_acl_dialog #service").change(function() {
            var selected = $("option:selected", this);
            var params = {
                client_id: $("#details_client_id").val(),
                service_code: selected.val()
            };

            $.get(action("service_acl"), params, function(response) {
                var titleText = selected.data("title")
                    ? "clients.service_acl_dialog.title_with_service_title"
                    : "clients.service_acl_dialog.title";

                var title = _(titleText, {
                    code: selected.val(),
                    title: selected.data("title")
                });

                $("#service_acl_dialog").dialog("option", "title", title);

                oSubjects.fnFilter("");
                oSubjects.fnReplaceData(response.data);

                enableActions();

                $("#service_acl_dialog").dialog("open");
            }, "json");
        });

        $("#subjects tbody tr").live("click", function() {
            oSubjects.setFocus(0, this, true);
            enableActions();
        });
    }

    function initTestability() {
        // add data-name attributes to improve testability
        $("#service_acl_dialog").parent().attr("data-name", "service_acl_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
        $("button span:contains('Add Subjects')").parent().attr("data-name", "add_subjects");
        $("button span:contains('Remove Selected')").parent().attr("data-name", "remove_selected");
        $("button span:contains('Remove All')").parent().attr("data-name", "remove_all");
    }

    $(document).ready(function() {
        initServiceAclDialog();
        initServiceAclSubjectsTable();
        initServiceAclActions();
        enableActions();
        initTestability();
    });

    ACL.openDialog = function(serviceCode) {
        var params = {
            client_id: $("#details_client_id").val()
        };

        $.get(action("acl_services"), params, function(response) {
            var serviceSelect = $("#service_acl_dialog #service").html("");

            $.each(response.data, function(idx, val) {
                serviceSelect.append(
                    "<option value='" + val.service_code + "'>"
                        + val.service_code + "</option>");

                $("option:last", serviceSelect).data("title", val.title);
            });

            $("#service_acl_dialog #service").val(serviceCode).change();
        }, "json");
    };

}(window.ACL = window.ACL || {}, jQuery));
