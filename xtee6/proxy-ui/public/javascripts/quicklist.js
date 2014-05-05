var oSubjects, oServices, oServicesUnselected, oServicesSelected;

function enableActions() {
    if ($("#subjects .row_selected").length > 0) {
        $("#subject_acl").enable();
    } else {
        $("#subject_acl").disable();
    }

    if ($("#services .row_selected").length > 0) {
        $("#acl_services_remove").enable();
    } else {
        $("#acl_services_remove").disable();
    }

    if ($("#services_search .row_selected").length > 0) {
        $("#acl_services_add_selected").enable();
    } else {
        $("#acl_services_add_selected").disable();
    }
    if ($("#services_search .selectable").length > 0) {
        $("#acl_services_add_all").enable();
    } else {
        $("#acl_services_add_all").disable();
    }
}

function refresh(callback) {
    $.get(action("refresh"), null, function(response) {
        oSubjects.fnClearTable();
        oSubjects.fnAddData(response.data);

        if (callback) {
            callback();
        }
    }, "json");
}

function openAcl() {
    var selected = oSubjects.getFocusData();
    var params = {
        subject_id: selected.subject_id
    };

    $.get(action("subject_acl"), params, function(response) {
        oServices.fnClearTable();
        oServices.fnAddData(response.data);
        
        var title = selected.type + ': ' +
            selected.name_description + ' (' +
            selected.member_group_code + ') ' +
            (selected.subsystem_code != null ?
             'subsystem ' + selected.subsystem_code : '');

        $("#subject_acl_dialog").dialog("option", "title", 'Service Access Rights for ' + title);
        $("#subject_acl_dialog").dialog("open");
    }, "json");
}

function initDialogs() {
    $("#subject_acl_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 430,
        width: "95%",
        open: function() { oServices.fnAdjustColumnSizing(); },
        buttons: [
            { text: "Close",
              click: function() {
                  oServices.fnClearTable();
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#subject_acl").click(function() {
        openAcl();
    });

    $("#acl_services_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 500,
        width: "95%",
        open: function() { oServicesSearch.fnAdjustColumnSizing(); },
        buttons: [
            { text: "Add Selected to ACL",
              id: "acl_services_add_selected",
              click: function() {
                
                  var dialog = this;
                  var params = {
                      subject_id: oSubjects.getFocusData().subject_id,
                      service_ids: []
                  };

                  var table = $(this).data('focusTable');
                  if (typeof table != 'undefined') {
                      params.subject_id = $(this).data('focusTable').getFocusData().subject_id;
                  }

                  $.each(oServicesSearch.fnGetNodes(), function(idx, val) {
                      if ($(val).hasClass("row_selected")) {
                          var service = oServicesSearch.fnGetData(val);
                          params.service_ids.push(service.service_id);
                      }
                  });

                  $.post(action("acl_services_add"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableActions();

                      if (typeof table != 'undefined') {
                          refresh(function() {
                              $.each(oSubjects.fnGetData(), function(i, val) {
                                  if (val.subject_id == params.subject_id) {
                                      oSubjects.setFocus(0, oSubjects.fnGetNodes(i));
                                  }
                              });
                              openAcl();
                          });
                      }

                      $(dialog).dialog("close");
                  });
              }
            },
            { text: "Add All to ACL",
              id: "acl_services_add_all",
              click: function() {
                  var dialog = this;
                  var params = {
                      subject_id: oSubjects.getFocusData().subject_id,
                      service_ids: []
                  };

                  var table = $(this).data('focusTable');
                  if (typeof table != 'undefined') {
                      params.subject_id =
                          $(this).data('focusTable').getFocusData().subject_id;
                  }

                  $.each(oServicesSearch._('tr', {"filter":"applied",
                      "class":"selectable"}), function(idx, val) {
                        params.service_ids.push(val.service_id);
                  });

                  $.post(action("acl_services_add"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableActions();

                      if (typeof table != 'undefined') {
                          refresh(function() {
                              $.each(oSubjects.fnGetData(), function(i, val) {
                                  if (val.subject_id == params.subject_id) {
                                      oSubjects.setFocus(0, oSubjects.fnGetNodes(i));
                                  }
                              });
                              openAcl();
                          });
                      }

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

    $("#acl_services_add").click(function() {
        oServicesSearch.fnClearTable();

        var params = {
            subject_id: oSubjects.getFocusData().subject_id
        };

        $.get(action("acl_services_all"), params, function(response) {
            oServicesSearch.fnAddData(response.data);
            enableActions();
            $("#acl_services_add_dialog").dialog("open");
        });
    });
}

function getSubjectsAddParams() {
    return {
        subject_ids: []
    };
}

function onSubjectsAddSuccess(response) {
    oSubjects.fnAddData(response.data);
}

$(document).ready(function() {
    var opts = defaultOpts(null, 1);
    opts.sScrollY = "400px";
    opts.bScrollCollapse = true;
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "name_description" },
        { "mData": "member_class" },
        { "mData": "member_group_code" },
        { "mData": "subsystem_code"},
        { "mData": "type" },
        { "mData": "sdsb" },
    ];

    oSubjects = $("#subjects").dataTable(opts);

    $(".subjects_actions").prependTo("#subjects_wrapper .dataTables_header");

    opts = scrollableTableOpts(null, 1, 190);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "service_name" },
        { "mData": "title" },
        { "mData": "provider_name" },
        { "mData": "provider_class" },
        { "mData": "provider_code" },
        { "mData": "provider_subsystem" },
        { "mData": "rights_given" }
    ];

    oServices = $("#services").dataTable(opts);

    $(".services_actions").prependTo("#services_wrapper .dataTables_header");

    opts = scrollableTableOpts(null, 1, 300);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.aoColumns = [
        { "mData": "service_name" },
        { "mData": "title" },
        { "mData": "provider_name" },
        { "mData": "provider_class" },
        { "mData": "provider_code" },
        { "mData": "provider_subsystem" }
    ];
    opts.fnRowCallback = function(nRow, oData) {
        var unselectable = false;
        $.each(oServices.fnGetData(), function(idx, val) {
            if (val.service_id == oData.service_id) {
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

    oServicesSearch = $("#services_search").dataTable(opts);

    refresh();
    initDialogs();
    enableActions();

    $("#subjects tbody tr").live("click", function() {
        oSubjects.setFocus(0, this);
        enableActions();
    });

    $("#services tbody tr").live("click", function() {
        oServices.setFocus(0, this, true);
        enableActions();
    });

    $("#acl_services_remove").click(function() {
        var params = {
            subject_id: oSubjects.getFocusData().subject_id,
            service_ids: []
        };

        var names = [];
        $("#services .row_selected").each(function(idx, row) {
            var service = oServices.fnGetData(row);
            params.service_ids.push(service.service_id);
            names.push(service.service_name);
        });

        confirm("quicklist.subject_services.delete.confirm", [names.join(", ")], function() {
            $.post(action("acl_services_remove"), params, function(response) {
                oServices.fnClearTable();
                oServices.fnAddData(response.data);
                enableActions();
            });
        });
    });

    $("#acl_services_remove_all").click(function() {
        var params = {
            subject_id: oSubjects.getFocusData().subject_id
        };

        confirm("quicklist.subject_services.delete_all.confirm", null, function() {
            $.post(action("acl_services_remove"), params, function(response) {
                oServices.fnClearTable();
                oServices.fnAddData(response.data);
                enableActions();
            });
        });
    });

    $("#services_search .selectable").live("click", function() {
        oServicesSearch.setFocus(0, this, true);
        enableActions();
    });
});
