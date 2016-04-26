(function(LOCAL_GROUPS, $) {
    var oGroups, oGroupMembers, oMembersSearch;

    $.fn.dataTableExt.afnFiltering.push(
        function(oSettings, aData, iDataIndex) {
            if (oSettings.sTableId == "members_search") {
                var show_existing_members =
                    $("#members_search_actions .show_existing:checked").length > 0;

                var row = oSettings.aoData[iDataIndex].nTr;

                return show_existing_members || !$(row).hasClass("unselectable");
            }

            if (oSettings.sTableId == "group_members") {
                var row = oSettings.aoData[iDataIndex].nTr;

                return show_existing_members || !$(row).hasClass("unselectable");
            }

            return true;
        }
    );

    function enableGroupsActions() {
        if (oGroups.getFocus()) {
            $("#group_details").enable();
        } else {
            $("#group_details").disable();
        }
    }

    function enableGroupMembersActions() {
        if (oGroupMembers.getFocus()) {
            $("#group_members_remove_selected").enable();
        } else {
            $("#group_members_remove_selected").disable();
        }
    }

    function enableMembersAddActions() {
        if (oMembersSearch.getFocus()) {
            $("#group_members_add_selected").enable();
        } else {
            $("#group_members_add_selected").disable();
        }
    }

    function refreshGroups() {
        var params = {
            client_id: $("#details_client_id").val()
        };

        $.get(action("client_groups"), params, function(response) {
            oGroups.fnReplaceData(response.data);
            enableGroupsActions();
        });
    }

    function initGroupAddDialog() {
        $("#group_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 400,
            width: 450,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();
                      params.client_id = $("#details_client_id").val();

                      $.post(action("group_add"), params, function(response) {
                          oGroups.fnReplaceData(response.data);
                          enableGroupsActions();

                          $(dialog).dialog("close");
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

        $("#group_add").live('click', function() {
            $("#add_group_code, #add_group_description").val("");
            $("#group_add_dialog").dialog("open");
        });
    }

    function initGroupDetailsDialog() {
        $("#group_details_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 700,
            width: "95%",
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      refreshGroups();
                      $(this).dialog("close");
                  }
                },
                { id: "group_delete",
                  text: _("clients.group_details_dialog.delete_group"),
                  privilege: "delete_local_group",
                  click: function() {
                      var group = oGroups.getFocusData();
                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: group.code
                      };

                      confirm("clients.group_details_dialog.delete_group_confirm",
                              {group: group.code}, function() {

                          $.post(action("group_delete"), params, function(response) {
                              oGroups.fnReplaceData(response.data);
                              enableGroupsActions();

                              $("#group_details_dialog").dialog("close");
                          }, "json");
                      });
                  }
                },
                { id: "group_members_remove_all",
                  text: _("clients.group_details_dialog.remove_all_members"),
                  privilege: "edit_local_group_members",
                  click: function() {
                      var group = oGroups.getFocusData();
                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: group.code
                      };

                      $.post(action("group_members_remove"), params, function(response) {
                          oGroupMembers.fnReplaceData(response.data);
                          enableGroupMembersActions();

                          $("#group_details_member_count").html(
                              oGroupMembers.fnSettings().fnRecordsTotal());
                      }, "json");
                  }
                },
                { id: "group_members_remove_selected",
                  text: _("clients.group_details_dialog.remove_selected_members"),
                  privilege: "edit_local_group_members",
                  click: function() {
                      var group = oGroups.getFocusData();
                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: group.code,
                          member_ids: []
                      };

                      $.each(oGroupMembers.fnGetNodes(), function(idx, val) {
                          if ($(val).hasClass("row_selected")) {
                              var member = oGroupMembers.fnGetData(val);
                              params.member_ids.push(member.member_id);
                          }
                      });

                      $.post(action("group_members_remove"), params, function(response) {
                          oGroupMembers.fnReplaceData(response.data);
                          enableGroupMembersActions();

                          $("#group_details_member_count").html(
                              oGroupMembers.fnSettings().fnRecordsTotal());
                      }, "json");
                  }
                },
                { id: "group_members_add",
                  text: _("clients.group_details_dialog.add_members"),
                  privilege: "edit_local_group_members",
                  click: function() {
                      var group = oGroups.getFocusData();
                      var title = _("clients.group_members_add_dialog.title", {
                          group: group.code
                      });

                      $("#group_members_add_dialog").dialog("option", "title", title);
                      $("#group_members_add_dialog").dialog("open");

                      oMembersSearch.fnClearTable();
                      enableMembersAddActions();
                  }
                },
            ]
        });

        $("#group_details").click(function() {
            var group = oGroups.getFocusData();
            var title = _("clients.group_details_dialog.title", {group: group.code});

            var params = {
                client_id: $("#details_client_id").val(),
                group_code: group.code
            };

            $.get(action("group_members"), params, function(response) {
                oGroupMembers.fnFilter("");
                oGroupMembers.fnReplaceData(response.data);

                enableGroupMembersActions();

                $("#group_details_description").text(group.description);
                $("#group_description_edit").val(group.description);

                $("#group_details_member_count").text(
                    oGroupMembers.fnSettings().fnRecordsTotal());

                $("#group_details_dialog").dialog("option", "title", title);
                $("#group_details_dialog").dialog("open");
            }, "json");
        });
    }

    function initGroupDescriptionEditDialog() {
        $("#group_description_edit_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 200,
            width: 300,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var groupCode = oGroups.getFocusData().code;
                      var description = $("#group_description_edit").val();

                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: groupCode,
                          description: description
                      };

                      $.post(action("group_description_edit"), params, function(response) {
                          $("#group_details_description").text(description);
                          $(dialog).dialog("close");
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

        $("#group_details_dialog #edit").live('click', function() {
            $("#group_description_edit_dialog").dialog("open");
        });
    }

    function initGroupMembersAddDialog() {
        var dialog = $("#group_members_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 700,
            width: "95%",
            buttons: [
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                },
                { id: "group_members_add_all",
                  text: _("clients.group_members_add_dialog.add_all"),
                  click: function() {
                      var dialog = this;
                      var group = oGroups.getFocusData();
                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: group.code,
                          member_ids: []
                      };

                      $.each(oMembersSearch.fnGetNodes(), function(idx, val) {
                          if (!$(val).hasClass("unselectable")) {
                              var member = oMembersSearch.fnGetData(val);
                              params.member_ids.push(member.subject_id);
                          }
                      });

                      if (params.member_ids.length == 0) {
                          $(dialog).dialog("close");
                          return false;
                      }

                      $.post(action("group_members_add"), params, function(response) {
                          oGroupMembers.fnReplaceData(response.data);
                          enableGroupMembersActions();

                          $("#group_details_member_count").html(
                              oGroupMembers.fnSettings().fnRecordsTotal());

                          $(dialog).dialog("close");
                      }, "json");
                  }
                },
                { id: "group_members_add_selected",
                  text: _("clients.group_members_add_dialog.add_selected"),
                  click: function() {
                      var dialog = this;
                      var group = oGroups.getFocusData();
                      var params = {
                          client_id: $("#details_client_id").val(),
                          group_code: group.code,
                          member_ids: []
                      };

                      $.each(oMembersSearch.fnGetNodes(), function(idx, val) {
                          if ($(val).hasClass("row_selected")) {
                              var member = oMembersSearch.fnGetData(val);
                              params.member_ids.push(member.subject_id);
                          }
                      });

                      $.post(action("group_members_add"), params, function(response) {
                          oGroupMembers.fnReplaceData(response.data);
                          enableGroupMembersActions();

                          $("#group_details_member_count").html(
                              oGroupMembers.fnSettings().fnRecordsTotal());

                          $(dialog).dialog("close");
                      }, "json");
                  }
                }
            ]
        });

        $(".advanced_search .search", dialog).live('click', function() {
            var params = $(".advanced_search", dialog)
                .find("input, select").serializeObject();

            params.members_only = true;
            params.client_id = $("#details_client_id").val();

            $.get(action("acl_subjects_search"), params, function(response) {
                oMembersSearch.fnReplaceData(response.data);

                // let's draw again so we can filter based on added classes
                oMembersSearch.fnDraw();
                enableMembersAddActions();
            }, "json");

            return false;
        });

        $(".simple_search .search", dialog).live('click', function() {
            var params = $(".simple_search input", dialog).serializeObject();
            params.members_only = true;
            params.client_id = $("#details_client_id").val();

            $.get(action("acl_subjects_search"), params, function(response) {
                oMembersSearch.fnReplaceData(response.data);

                // let's draw again so we can filter based on added classes
                oMembersSearch.fnDraw();
                enableMembersAddActions();
            }, "json");

            return false;
        });
    }

    function initGroupsTable() {
        var opts = scrollableTableOpts(250);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "code", mRender: util.escape },
            { "mData": "description", mRender: util.escape },
            { "mData": "member_count" },
            { "mData": "updated" }
        ];
        opts.asRowId = ["code"];

        oGroups = $("#groups").dataTable(opts);

        $("#groups tbody tr").live("click", function() {
            oGroups.setFocus(0, this);
            enableGroupsActions();
        });

        $("#groups tbody tr").live("dblclick", function() {
            oGroups.setFocus(0, this);
            enableGroupsActions();
            $("#group_details").click();
        });
    }

    function initGroupMembersTable() {
        var opts = scrollableTableOpts(250);
        opts.sDom = "t";
        opts.aoColumns = [
            { "mData": "name",
              "mRender": function(data, type, full) {
                  if (type == 'display') {
                      return clientName(data);
                  }
                  return data;
              },
              "fnCreatedCell": function(nTd, sData, oData) {
                  if (!oData.name) {
                      $(nTd).addClass("missing");
                  }
              },
              "sWidth": "30%"
            },
            { "mData": "type", "bVisible": false, mRender: util.escape },
            { "mData": "instance", "bVisible": false, mRender: util.escape },
            { "mData": "class", "bVisible": false, mRender: util.escape },
            { "mData": "code", "bVisible": false, mRender: util.escape },
            { "mData": "subsystem", "bVisible": false, mRender: util.escape },
            { "mData": function(source, type, val) {
                  return generateIdElement({
                      "Type": source.type,
                      "Instance": source.instance,
                      "Class": source.class,
                      "Code": source.code,
                      "Subsystem": source.subsystem
                  });
              }
            },
            { "mData": "added", "sWidth": "15%" }
        ];

        oGroupMembers = $("#group_members").dataTable(opts);

        $("#group_members tbody tr").live("click", function() {
            oGroupMembers.setFocus(0, this, true);
            enableGroupMembersActions();
        });

        var dialog = $("#group_details_dialog");

        $(".simple_search .search", dialog).click(function() {
            oGroupMembers.fnFilter($("[name=subject_search_all]", dialog).val());
            return false;
        });

        $(".advanced_search .search", dialog).click(function() {
            $(".advanced_search", dialog).find("input, select").each(
                function(idx, val) {
                    oGroupMembers.fnFilter($(this).val(), idx);
                });
            return false;
        });
    }

    function initMembersSearchTable() {
        var opts = scrollableTableOpts(250);
        opts.sDom = "<'dataTables_header'<'clearer'>>t";
        opts.aoColumns = [
            { "mData": "name_description", mRender: util.escape },
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
        opts.fnRowCallback = function(nRow, oData) {
            var unselectable = false;

            $.each(oGroupMembers.fnGetData(), function(idx, val) {
                if (val.instance == oData.instance &&
                    val.type == oData.type &&
                    val.class == oData.member_class &&
                    val.code == oData.member_group_code &&
                    val.subsystem == oData.subsystem_code) {

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

        oMembersSearch = $("#members_search").dataTable(opts);

        $("#members_search_actions")
            .prependTo("#members_search_wrapper .dataTables_header");

        $("#members_search_actions .show_existing").change(function() {
            oMembersSearch.fnDraw();
        });

        $("#members_search tbody .selectable").live("click", function() {
            oMembersSearch.setFocus(0, this, true);
            enableMembersAddActions();
        });
    }

    function initTestability() {
        // add data-name attributes to improve testability
        $("#group_add_dialog").parent().attr("data-name", "group_add_dialog");
        $("#group_details_dialog").parent().attr("data-name", "group_details_dialog");
        $("#group_description_edit_dialog").parent().attr("data-name", "group_description_edit_dialog");
        $("#group_members_add_dialog").parent().attr("data-name", "group_members_add_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }
    
    
    $(document).ready(function() {
        initGroupsTable();
        initGroupMembersTable();
        initMembersSearchTable();

        initGroupAddDialog();
        initGroupDetailsDialog();
        initGroupDescriptionEditDialog();
        initGroupMembersAddDialog();
        initTestability();
    });

    LOCAL_GROUPS.init = function() {
        refreshGroups();
    };

}(window.LOCAL_GROUPS = window.LOCAL_GROUPS || {}, jQuery));
