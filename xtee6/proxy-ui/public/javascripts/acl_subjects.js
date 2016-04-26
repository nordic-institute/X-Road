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

    function initClientAclSubjectsTab() {
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
    }

    function initAclSubjectOpenServicesDialog() {
        $("#acl_subject_open_services_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 500,
            width: "95%",
            open: function() {
                oServicesOpen.fnFilter('');
                enableActions();
            },
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      // In case a new subject is added to
                      // #acl_subjects, we need a clean #services_open
                      // table to display openable services correctly.
                      oServicesOpen.fnClearTable();
                      $(this).dialog("close");
                  }
                },
                { id: "acl_subject_open_services_remove_all",
                  text: _("common.remove_all"),
                  privilege: "edit_acl_subject_open_services",
                  click: function() {
                      var params = {
                          client_id: $("#details_client_id").val(),
                          subject_id: oAclSubjects.getFocusData().subject_id
                      };

                      confirm("clients.acl_subject_open_services_dialog.delete_all_confirm",
                              null, function() {
                          $.post(action("acl_subject_open_services_remove"), params,
                                 function(response) {
                              oServicesOpen.fnReplaceData(response.data);
                              enableActions();
                          }, "json");
                      });
                  }
                },
                { id: "acl_subject_open_services_remove",
                  text: _("common.remove_selected"),
                  privilege: "edit_acl_subject_open_services",
                  click: function() {
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
                              oServicesOpen.fnReplaceData(response.data);
                              enableActions();
                          }, "json");
                      });
                  }
                },
                { id: "acl_subject_open_services_add",
                  text: _("clients.acl_subject_open_services_dialog.add_services"),
                  privilege: "edit_acl_subject_open_services",
                  click: function() {
                      var params = {
                          client_id: $("#details_client_id").val()
                      };

                      $.get(action("acl_services"), params, function(response) {
                          oServicesAll.fnReplaceData(response.data);
                          $("#acl_subject_open_services_add_dialog").dialog("open");
                      });
                  }
                }
            ]
        });
    }

    function openAclSubjectOpenServicesDialog() {
        var selected = oAclSubjects.getFocusData();
        var titleParams = {
            name: selected.name_description ? selected.name_description : "",
            id: $(".xroad-id", oAclSubjects.getFocus()).text()
        };
        var title =
            _("clients.acl_subject_open_services_dialog.title", titleParams);

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
                          oServicesOpen.fnReplaceData(response.data);
                          enableActions();

                          $(dialog).dialog("close");

                          if (!$("#acl_subject_open_services_dialog").is(":visible")) {
                              openAclSubjectOpenServicesDialog();
                          }
                      }, "json");
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
                          oServicesOpen.fnReplaceData(response.data);
                          enableActions();

                          $(dialog).dialog("close");

                          if (!$("#acl_subject_open_services_dialog").is(":visible")) {
                              openAclSubjectOpenServicesDialog();
                          }
                      }, "json");
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
            { mData: "name_description",
              mRender: function(data, type, full) {
                  if (type == 'display') {
                      return full.member_class ?
                          clientName(data) : groupDesc(data);
                  }
                  return data;
              },
              fnCreatedCell: function(nTd, sData, oData) {
                  if (!oData.name_description) {
                      $(nTd).addClass("missing");
                  }
              }
            },
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
            { "mData": "rights_given", "sWidth": "15%" }
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
    
    function initTestability() {
        // add data-name attributes to improve testability
        $("#acl_subject_open_services_dialog").parent().attr("data-name", "acl_subject_open_services_dialog");
        $("#acl_subject_open_services_add_dialog").parent().attr("data-name", "acl_subject_open_services_add_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
    }

    $(document).ready(function() {
        initClientAclSubjectsTab();
        initAclSubjectOpenServicesDialog();
        initAclSubjectOpenServicesAddDialog();

        initAclSubjectsTable();
        initServicesOpenTable();
        initServicesAllTable();
        initTestability();
    });

    ACL_SUBJECTS.init = function() {
        var params = {
            client_id: $("#details_client_id").val()
        };

        oAclSubjects.fnClearTable();

        $.get(action("client_acl_subjects"), params, function(response) {
            $("#acl_subjects_add").enable(response.data.has_services);

            oAclSubjects.fnAddData(response.data.acl_subjects);
            oAclSubjects.fnFilter('');
            enableActions();
        }, "json");
    };

}(window.ACL_SUBJECTS = window.ACL_SUBJECTS || {}, jQuery));
