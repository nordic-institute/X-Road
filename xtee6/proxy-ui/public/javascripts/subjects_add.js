var oSubjectsSearch;

function enableSubjectsAddActions() {
    if ($("#subjects_search .row_selected").length > 0) {
        $("#subjects_add_selected").enable();
        $('#subjects_add_next').enable();
    } else {
        $("#subjects_add_selected").disable();
        $('#subjects_add_next').disable();
    }

    if ($("#subjects_search .selectable").length > 0) {
        $("#subjects_add_all").enable();
    } else {
        $("#subjects_add_all").disable();
    }
}

function initSubjectsAddDialogs() {
    $("#subjects_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 700,
        width: "95%",
        open: function() { oSubjectsSearch.fnAdjustColumnSizing(); },
        buttons: [
            { text: "Add Selected to ACL",
              id: "subjects_add_selected",
              click: function() {
                  var dialog = this;
                  var params = getSubjectsAddParams();

                  $.each(oSubjectsSearch.fnGetNodes(), function(idx, val) {
                      if ($(val).hasClass("row_selected")) {
                          var subject = oSubjectsSearch.fnGetData(val);
                          params.subject_ids.push(subject.subject_id);
                      }
                  });

                  $.post(action("subjects_add"), params, function(response) {
                      onSubjectsAddSuccess(response);
                      enableSubjectsAddActions();

                      $(dialog).dialog("close");
                  });
              }
            },
            { text: "Add All to ACL",
              id: "subjects_add_all",
              click: function() {
                  var dialog = this;
                  var params = getSubjectsAddParams();

                  $.each(oSubjectsSearch.fnGetNodes(), function(idx, val) {
                      if (!$(val).hasClass("unselectable")) {
                          var subject = oSubjectsSearch.fnGetData(val);
                          params.subject_ids.push(subject.subject_id);
                      }
                  });

                  $.post(action("subjects_add"), params, function(response) {
                      onSubjectsAddSuccess(response);
                      enableSubjectsAddActions();

                      $(dialog).dialog("close");
                  });
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#subjects_add").click(function() {
        oSubjectsSearch.fnClearTable();
        enableSubjectsAddActions();
        $("#subjects_add_dialog").dialog("open");
    });

    $("#service_clients_subjects_add").click(function() {
        oSubjectsSearch.fnClearTable();
        enableSubjectsAddActions();

        $("#subjects_add_dialog").dialog({ 
            title: 'Search for Service Client', 
            opener: 'service_clients',
            clonedBody: null,
            selectedItem: null,
            open: function() {
                $("#subjects_add_next").disable();
                oSubjectsSearch.fnAdjustColumnSizing();
            },
            buttons: [
                { text: 'Cancel',
                  id: 'subjects_add_cancel',
                  click: function() {
                      $(this).dialog('close');
                  }
                },
                { text: 'Next',
                  id: 'subjects_add_next',
                  click: function() {
                      var subject = oSubjectsSearch.getFocusData();
                      var params = {
                          subject_id: subject.subject_id
                      };

                      $.get(action("acl_services_all"), params, function(response) {
                          $('#subjects_add_dialog').dialog('close');
                          oServicesSearch.fnClearTable();
                          oServicesSearch.fnAddData(response.data);

                          var title = subject.type + ': ' +
                              subject.name_description + ' (' +
                              subject.member_group_code + ') ' +
                              (subject.subsystem_code != null ?
                               'subsystem ' + subject.subsystem_code : '');

                          $("#acl_services_add_dialog").data('focusTable', oSubjectsSearch).dialog({
                              title: 'Add Service Access Rights for ' + title
                          });

                          enableActions();
                          $("#acl_services_add_dialog").dialog('open');
                      });
                  }
                }
            ]
        });
        $("#subjects_add_dialog").dialog("open");
    });
}

$(document).ready(function() {
    var opts = scrollableTableOpts(null, 1, 345);
    opts.bPaginate = false;
    opts.sDom = "t";
    opts.aoColumns = [
        { "mData": "name_description" },
        { "mData": "member_class" },
        { "mData": "member_group_code" },
        { "mData": "subsystem_code" },
        { "mData": "sdsb" },
        { "mData": "type" }
    ];
    opts.fnRowCallback = function(nRow, oData) {
        var unselectable = false;
        $.each(oSubjects.fnGetData(), function(idx, val) {
             if (oData.sdsb == val.sdsb &&
                oData.type == val.type &&
                oData.member_class == val.member_class &&
                oData.member_group_code == val.member_group_code &&
                oData.subsystem_code == val.subsystem_code) {
              
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

    oSubjectsSearch = $("#subjects_search").dataTable(opts);

    initSubjectsAddDialogs();
    enableSubjectsAddActions();

    $("#subjects_add_search").click(function() {
        var params = $("#subjects_add_dialog form").serializeObject();

        if ($("#details_client_id").length > 0) {
            params.client_id = $("#details_client_id").val();
        }

        $.get(action("subjects_search"), params, function(response) {
            oSubjectsSearch.fnClearTable();
            oSubjectsSearch.fnAddData(response.data);
            enableSubjectsAddActions();
        }, "json");

        return false;
    });

    $("#subjects_add_clear").click(function() {
        $("#subjects_add_dialog form input, " +
          "#subjects_add_dialog form select").val("");
        return false;
    });

    $("#subjects_search tbody .selectable").live("click", function() {
        if ($("#subjects_add_dialog").dialog('option', 'opener') != null && 
            $("#subjects_add_dialog").dialog('option', 'opener') == 'service_clients') {

            oSubjectsSearch.setFocus(0, this);
        } else {
            oSubjectsSearch.setFocus(0, this, true);
        }
        enableSubjectsAddActions();
    });
});
