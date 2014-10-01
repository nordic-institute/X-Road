(function(ACL_SUBJECTS, $, undefined) {

    var oAclSubjects, oServicesOpen, oServicesAll;

    function enableActions() {
        if ($("#acl_subjects .row_selected").length > 0) {
            $("#acl_subject_open_services").enable();
        } else {
            $("#acl_subject_open_services").disable();
        }

        if ($("#services_open .row_selected").length > 0) {
            $("#acl_subject_open_services_remove").enable();
        } else {
            $("#acl_subject_open_services_remove").disable();
        }

        if ($("#services_all .row_selected").length > 0) {
            $("#acl_subject_open_services_add_selected").enable();
        } else {
            $("#acl_subject_open_services_add_selected").disable();
        }

        if ($("#services_all .selectable").length > 0) {
            $("#acl_subject_open_services_add_all").enable();
        } else {
            $("#acl_subject_open_services_add_all").disable();
        }
    }

    function initClientAclSubjectsDialog() {
        $("#client_acl_subjects_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 600,
            width: "95%",
            open: function() {
                oAclSubjects.fnAdjustColumnSizing();
                oAclSubjects.fnFilter('');
                enableActions();
            },
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $("#acl_subjects_add").click(function() {
            ACL_SUBJECTS_SEARCH.openDialogWithNext(oAclSubjects.fnGetData(), function(subject) {
                // add the new subject to subjects table...
                oAclSubjects.fnAddData(subject);

                // ...and select it
                $.each(oAclSubjects.fnGetData(), function(i, val) {
                    if (val.subject_id == subject.subject_id) {
                        oAclSubjects.setFocus(0, oAclSubjects.fnGetNodes(i));
                    }
                });

                $("#acl_subject_open_services_add").click();
            });
        });

        $("#acl_subject_open_services").click(function() {
            var params = {
                client_id: $("#details_client_id").val(),
                subject_id: oAclSubjects.getFocusData().subject_id
            };

            $.get(action("acl_subject_open_services"), params, function(response) {
                oServicesOpen.fnClearTable();
                oServicesOpen.fnAddData(response.data);

                openAclSubjectOpenServicesDialog();
            }, "json");
        });
    }

    function initAclSubjectOpenServicesDialog() {
        $("#acl_subject_open_services_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 500,
            width: "95%",
            open: function() {
                oServicesOpen.fnAdjustColumnSizing();
                oServicesOpen.fnFilter('');
                enableActions();
            },
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      oServicesOpen.fnClearTable();
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $("#acl_subject_open_services_add").click(function() {
            var params = {
                client_id: $("#details_client_id").val()
             };

            $.get(action("acl_services"), params, function(response) {
                oServicesAll.fnClearTable();
                oServicesAll.fnAddData(response.data);
                $("#acl_subject_open_services_add_dialog").dialog("open");
            });
        });

        $("#acl_subject_open_services_remove").click(function() {
            var params = {
                client_id: $("#details_client_id").val(),
                subject_id: oAclSubjects.getFocusData().subject_id,
                service_codes: []
            };

            $("#services_open .row_selected").each(function(idx, row) {
                var service = oServicesOpen.fnGetData(row);
                params.service_codes.push(service.service_code);
            });

            var serviceCodesJoined = "<p class='align-center bold'>" +
                params.service_codes.join(", ") + "</p>";

            confirm("clients.acl_subject_open_services_dialog.delete_selected_confirm",
                    {services: serviceCodesJoined}, function() {

                $.post(action("acl_subject_open_services_remove"), params,
                       function(response) {
                    oServicesOpen.fnClearTable();
                    oServicesOpen.fnAddData(response.data);
                    enableActions();
                });
            });
        });

        $("#acl_subject_open_services_remove_all").click(function() {
            var params = {
                client_id: $("#details_client_id").val(),
                subject_id: oAclSubjects.getFocusData().subject_id
            };

            confirm("clients.acl_subject_open_services_dialog.delete_all_confirm",
                    null, function() {
                $.post(action("acl_subject_open_services_remove"), params,
                       function(response) {
                    oServicesOpen.fnClearTable();
                    oServicesOpen.fnAddData(response.data);
                    enableActions();
                });
            });
        });
    }

    function openAclSubjectOpenServicesDialog() {
        var selected = oAclSubjects.getFocusData();
        var titleParams = {
            type: selected.type,
            name: selected.name_description,
            code: selected.member_group_code,
            subsystem: selected.subsystem_code
        };
        var title = selected.subsystem_code != null
            ? _("clients.acl_subject_open_services_dialog.subsystem_title", titleParams)
            : _("clients.acl_subject_open_services_dialog.other_title", titleParams);

        $("#acl_subject_open_services_dialog").dialog("option", "title", title);
        $("#acl_subject_open_services_dialog").dialog("open");
    }

    function initAclSubjectOpenServicesAddDialog() {
        $("#acl_subject_open_services_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 500,
            width: "95%",
            open: function() {
                oServicesAll.fnAdjustColumnSizing();
                oServicesAll.fnFilter('');
                enableActions();
            },
            buttons: [
                { text: _("clients.acl_subject_open_services_dialog.add_selected"),
                  id: "acl_subject_open_services_add_selected",
                  click: function() {
                      var dialog = this;
                      var params = {
                          client_id: $("#details_client_id").val(),
                          subject_id: oAclSubjects.getFocusData().subject_id,
                          service_codes: []
                      };

                      $.each(oServicesAll.fnGetNodes(), function(idx, val) {
                          if ($(val).hasClass("row_selected")) {
                              var service = oServicesAll.fnGetData(val);
                              params.service_codes.push(service.service_code);
                          }
                      });

                      $.post(action("acl_subject_open_services_add"), params, function(response) {
                          oServicesOpen.fnClearTable();
                          oServicesOpen.fnAddData(response.data);
                          enableActions();

                          $(dialog).dialog("close");

                          if (!$("#acl_subject_open_services_dialog").is(":visible")) {
                              openAclSubjectOpenServicesDialog();
                          }
                      });
                  }
                },
                { text: _("clients.acl_subject_open_services_dialog.add_all"),
                  id: "acl_subject_open_services_add_all",
                  click: function() {
                      var dialog = this;
                      var params = {
                          client_id: $("#details_client_id").val(),
                          subject_id: oAclSubjects.getFocusData().subject_id,
                          service_codes: []
                      };

                      var filterParams = {
                          "filter": "applied",
                          "class": "selectable"
                      };

                      $.each(oServicesAll._('tr', filterParams), function(idx, val) {
                          params.service_codes.push(val.service_code);
                      });

                      $.post(action("acl_subject_open_services_add"), params, function(response) {
                          oServicesOpen.fnClearTable();
                          oServicesOpen.fnAddData(response.data);
                          enableActions();

                          $(dialog).dialog("close");

                          if (!$("#acl_subject_open_services_dialog").is(":visible")) {
                              openAclSubjectOpenServicesDialog();
                          }
                      });
                  }
                },

                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
    }

    function initAclSubjectsTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "name_description" },
            {
                mData: function(source, type, val) {
                    return generateIdElement({
                        "Type": source.type,
                        "Instance": source.sdsb,
                        "Class": source.member_class,
                        "Code": source.member_group_code,
                        "Subsystem": source.subsystem_code
                    });
                }
            }
        ];

        oAclSubjects = $("#acl_subjects").dataTable(opts);

        $(".acl_subjects_actions").prependTo("#acl_subjects_wrapper .dataTables_header");

        $("#acl_subjects tbody tr").live("click", function() {
            oAclSubjects.setFocus(0, this);
            enableActions();
        });
    }

    function initServicesOpenTable() {
        var opts = scrollableTableOpts(190);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "service_code" },
            { "mData": "title" },
            { "mData": "rights_given" }
        ];

        oServicesOpen = $("#services_open").dataTable(opts);

        $("#services_open tbody tr").live("click", function() {
            oServicesOpen.setFocus(0, this, true);
            enableActions();
        });
    }

    function initServicesAllTable() {
        var opts = scrollableTableOpts(300);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "service_code" },
            { "mData": "title" }
        ];
        opts.fnRowCallback = function(nRow, oData) {
            var unselectable = false;
            $.each(oServicesOpen.fnGetData(), function(idx, val) {
                if (val.service_code == oData.service_code) {
                    unselectable = true;
                    return false;
                }
            });

            if (unselectable) {
                $(nRow).addClass("unselectable");
            } else {
                $(nRow).addClass("selectable");
            }

            return nRow;
        };

        oServicesAll = $("#services_all").dataTable(opts);

        $("#services_all .selectable").live("click", function() {
            oServicesAll.setFocus(0, this, true);
            enableActions();
        });
    }

    $(document).ready(function() {
        // initClientAclSubjectsDialog();
        initAclSubjectOpenServicesDialog();
        initAclSubjectOpenServicesAddDialog();

        initAclSubjectsTable();
        initServicesOpenTable();
        initServicesAllTable();
    });

    ACL_SUBJECTS.init = function() {
        var params = {
            client_id: $("#details_client_id").val()
        };

        oAclSubjects.fnClearTable();

        $.get(action("client_acl_subjects"), params, function(response) {
            oAclSubjects.fnAddData(response.data);
            oAclSubjects.fnAdjustColumnSizing();
            oAclSubjects.fnFilter('');
            enableActions();
        }, "json");

        $("#acl_subjects_add").click(function() {
            ACL_SUBJECTS_SEARCH.openDialogWithNext(oAclSubjects.fnGetData(), function(subject) {
                // add the new subject to subjects table...
                oAclSubjects.fnAddData(subject);

                // ...and select it
                $.each(oAclSubjects.fnGetData(), function(i, val) {
                    if (val.subject_id == subject.subject_id) {
                        oAclSubjects.setFocus(0, oAclSubjects.fnGetNodes(i));
                    }
                });

                $("#acl_subject_open_services_add").click();
            });
        });

        $("#acl_subject_open_services").click(function() {
            var params = {
                client_id: $("#details_client_id").val(),
                subject_id: oAclSubjects.getFocusData().subject_id
            };

            oServicesOpen.fnClearTable();

            $.get(action("acl_subject_open_services"), params, function(response) {
                oServicesOpen.fnAddData(response.data);

                openAclSubjectOpenServicesDialog();
            }, "json");
        });
    };

}(window.ACL_SUBJECTS = window.ACL_SUBJECTS || {}, jQuery));
