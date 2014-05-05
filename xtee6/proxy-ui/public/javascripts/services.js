var oServices;

// let's maintain a list of open rows, so we can restore it on refresh
var open = [];

$.fn.dataTableExt.afnFiltering.push(
    function(oSettings, aData, iDataIndex) {
        if (oSettings.sTableId == "services") {
            return aData[1] || aData[2];
        }
        return true;
    }
);

function enableServicesActions() {
    if ($(".wsdl.row_selected").length > 0) {
        $("#wsdl_delete, #wsdl_refresh, " +
          "#wsdl_disable, #wsdl_enable").enable();
    } else {
        $("#wsdl_delete, #wsdl_refresh, " +
          " #wsdl_disable, #wsdl_enable").disable();
    }

    if ($(".service.row_selected, .adapter.row_selected").length == 1 &&
        $(".wsdl.row_selected:not(.adapter)").length == 0) {
        $("#service_params").enable();
    } else {
        $("#service_params").disable();
    }

    if ($(".service.row_selected").length == 1) {
        $("#service_acl").enable();
    } else {
        $("#service_acl").disable();
    }

    if ($(".wsdl.row_selected:not(.disabled)").length > 0) {
        $("#wsdl_enable").hide();
        $("#wsdl_disable").show();
    } else {
        $("#wsdl_disable").hide();
        $("#wsdl_enable").show();
    }

    openRows();
}

function openRows() {
    $.each(oServices.fnGetNodes(), function(idx, row) {
        var service = oServices.fnGetData(row);
        oServices.fnUpdate(open.indexOf(service.wsdl_id) != -1, row, 2, false);
    });
    oServices.fnDraw();
}

function wsdlParams() {
    var params = {
        client_id: $("#details_client_id").val(),
        wsdl_ids: []
    };

    $("#services .row_selected").each(function(idx, row) {
        params.wsdl_ids.push(oServices.fnGetData(row).wsdl_id);
    });

    return params;
}

function initClientServicesDialog() {
    $("#client_services_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 450,
        width: "95%",
        open: function() {
          oServices.fnAdjustColumnSizing();
        },
        buttons: [
            { text: "Close",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#client_services").click(function() {
        var subsystem = $('#details_subsystem_code').val() !== '' ? ' subsystem ' + $('#details_subsystem_code').val() : '';
        var title = $('#details_member_name').val() + subsystem + ' : Services';
        var params = {
            client_id: $("#details_client_id").val()
        };
        $.get(action("client_services"), params, function(response) {
            oServices.fnClearTable();
            oServices.fnAddData(response.data);
            enableServicesActions();
            $("#client_services_dialog").dialog("option", "title", title);
            $("#client_services_dialog").dialog("open");
        });
    });
}

function initWSDLAddDialog() {
    var dialog = $("#wsdl_add_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 250,
        width: 500,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = $("form", this).serializeObject();

                  params.client_id = $("#details_client_id").val();

                  $.post(action("wsdl_add"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableServicesActions();

                      $(dialog).dialog("close");
                  }, "json");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#wsdl_add").click(function() {
        $("#wsdl_add_type", dialog).val("wsdl");
        $("#wsdl_add_url", dialog).val("");
        $("#wsdl_add_dialog").dialog("option", "title", "Add WSDL");
        $("#wsdl_add_dialog").dialog("open");
    });

    $("#adapter_add").click(function() {
        $("#wsdl_add_type", dialog).val("adapter");
        $("#wsdl_add_url").val("");
        $("#wsdl_add_dialog").dialog("option", "title", "Add Adapter");
        $("#wsdl_add_dialog").dialog("open");
    });
}

function initWSDLDisableDialog() {
    $("#wsdl_disable_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 250,
        width: 500,
        buttons: [
            { text: "OK",
              click: function() {
                  var dialog = this;
                  var params = wsdlParams();
                  params.wsdl_disabled_notice =
                      $("#wsdl_disabled_notice", this).val();

                  $.post(action("wsdl_disable"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableServicesActions();

                      $(dialog).dialog("close");
                  }, "json");
               }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#wsdl_disable").click(function() {
        if ($(".wsdl.row_selected").length == 1) {
            $("#wsdl_disabled_notice").val(
                oServices.getFocusData().disabled_notice);
        } else {
            $("#wsdl_disabled_notice").val("");
        }

        $("#wsdl_disable_dialog").dialog("open");
    });
}

function initServiceParamsDialog() {
    $("#service_params_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 350,
        width: 600,
        buttons: [
            { text: "OK",
              click: function() {
                  var params = $("form", this).serializeObject();
                  params.client_id = $("#details_client_id").val();

                  $.post(action("service_params"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableServicesActions();
                  });
                  $(this).dialog("close");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#adapter_params_dialog").initDialog({
        autoOpen: false,
        modal: true,
        height: 350,
        width: 600,
        buttons: [
            { text: "OK",
              click: function() {
                  var params = $("form", this).serializeObject();
                  params.client_id = $("#details_client_id").val();

                  $.post(action("adapter_params"), params, function(response) {
                      oServices.fnClearTable();
                      oServices.fnAddData(response.data);
                      enableServicesActions();
                  });
                  $(this).dialog("close");
              }
            },
            { text: "Cancel",
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    });

    $("#service_params").click(function() {
        var service = oServices.getFocusData();

        if (service.adapter) {
            $("#params_adapter_id").val(service.wsdl_id);
            $("#params_adapter_timeout").val(service.timeout);
            $("#adapter_params_dialog").dialog("open");
        } else {
            $("#params_wsdl_id").val(service.wsdl_id);
            $("#params_name").val(service.name);
            $("#params_url").val(service.url);
            $("#params_timeout").val(service.timeout);
            $("#params_security_category").val(service.security_category);
            $("#service_params_dialog").dialog("open");
        }
    });
}

function initServicesTable() {
    var opts = scrollableTableOpts(null, 1, 200);
    opts.sDom = "<'dataTables_header'f<'clearer'>>t";
    opts.bPaginate = false;
    opts.aaSortingFixed = [[0, 'asc'], [1, 'desc']];
    opts.aoColumns = [
        { "mData": "wsdl_id", "bVisible": false },
        { "mData": "wsdl", "bVisible": false },
        { "mData": "open",
          "sWidth": "0.5em",
          "mRender": function(data, type, full) {
              if (type == 'filter') {
                  return data;
              }
              return full.wsdl ? (data ? "-" : "+") : "";
          },
          "bSortable": false, "sClass": "thin-right open" },
        { "mData": "name", "sClass": "align-left", "sWidth": "9em",
          "fnCreatedCell": function(nTd, sData, oData) {
              if (oData.wsdl) {
                  $(nTd).addClass("noclip");
              }
          } },
        { "mData": "title", "sClass": "align-left", "sWidth": "16em" },
        { "mData": "url", "sClass": "align-left nowrap rtl",
          "sWidth": "6.5em",
          "mRender": function(data, type, full) {
              return data ?
                  "<span title='" + data + "'>" + data + "</span>" : null;
          } },
        { "mData": "timeout", "sClass": "align-center", "sWidth": "4.5em" },
        { "mData": "security_category", "sClass": "align-center", "sWidth": "5em" },
        { "mData": "last_refreshed", "sClass": "align-center", "sWidth": "6.5em" },
        { "mData": "disabled", "bVisible": false }
    ];
    opts.fnRowCallback = function(nRow, oData) {
        if (oData.wsdl) {
            $(nRow).addClass("wsdl");

            if (oData.adapter) {
                $(nRow).addClass("adapter");
            }
        } else {
            $(nRow).addClass("service");
        }

        if (oData.disabled) {
            $(nRow).addClass("disabled");
        }

        return nRow;
    };

    oServices = $("#services").dataTable(opts);

    $(".services_actions").prependTo("#services_wrapper .dataTables_header");

    $("#services tbody td.open").live("click", function() {
        var nRow = $(this).closest("tr").get(0);
        var oData = oServices.fnGetData(nRow);

        if (!oData.wsdl) {
            return;
        }

        if (oData.open) {
            open.splice(open.indexOf(oData.wsdl_id), 1);
        } else {
            open.push(oData.wsdl_id);
        }

        $.each(oServices.fnGetNodes(), function(val, idx) {
            if (oServices.fnGetData(val).wsdl_id == oData.wsdl_id) {
                oServices.fnUpdate(!oData.open, val, 2, false);
            }
        });
        oServices.fnDraw();
    });

    $("#services tbody tr").live("click", function() {
        var multiselect = $(this).hasClass("wsdl")
            && $(".service.row_selected").length == 0;
        oServices.setFocus(0, this, multiselect);
        enableServicesActions();
    });
}

$(document).ready(function() {
    initServicesTable();
    initClientServicesDialog();
    initWSDLAddDialog();
    initWSDLDisableDialog();
    initServiceParamsDialog();
    enableServicesActions();

    $("#wsdl_enable").click(function() {
        var params = wsdlParams();
        params.enable = true;

        $.post(action("wsdl_disable"), params, function(response) {
            oServices.fnClearTable();
            oServices.fnAddData(response.data);
            enableServicesActions();
        });
    });

    $("#wsdl_refresh").click(function() {
        $.post(action("wsdl_refresh"), wsdlParams(), function(response) {
            oServices.fnClearTable();
            oServices.fnAddData(response.data);
            enableServicesActions();
        });
    });

    $("#wsdl_delete").click(function() {
        confirm("services.delete.confirm", null, function() {
            $.post(action("wsdl_delete"), wsdlParams(), function(response) {
                oServices.fnClearTable();
                oServices.fnAddData(response.data);
                enableServicesActions();
            }, "json");
        });
    });
});
