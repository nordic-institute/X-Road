(function(SERVICES, $, undefined) {

    var oServices;

    // let's maintain a list of open rows, so we can restore it on refresh
    var open = [];

    $.fn.dataTableExt.afnFiltering.push(
        function(oSettings, aData, iDataIndex) {
            if (oSettings.sTableId == "services") {
                return aData[0] || aData[1];
            }
            return true;
        }
    );

    function enableActions(restoreOpenRows) {
        restoreOpenRows = (typeof restoreOpenRows !== 'undefined') ?
            restoreOpenRows : true;

        if ($(".wsdl:not(.meta).row_selected").length > 0) {
            $("#wsdl_delete, #wsdl_refresh, " +
              "#wsdl_disable, #wsdl_enable").enable();
        } else {
            $("#wsdl_delete, #wsdl_refresh, " +
              " #wsdl_disable, #wsdl_enable").disable();
        }

        if ($(".service:not(.adapter_service, .meta_service).row_selected, " +
              ".wsdl:not(.meta).row_selected").length == 1) {
            $("#service_params").enable();
        } else {
            $("#service_params").disable();
        }

        if ($(".service.row_selected").length == 1) {
            $("#service_acl").enable();
        } else {
            $("#service_acl").disable();
        }

        if ($(".wsdl:not(.disabled, .meta).row_selected").length > 0) {
            $("#wsdl_enable").hide();
            $("#wsdl_disable").show();
        } else {
            $("#wsdl_disable").hide();
            $("#wsdl_enable").show();
        }

        if (restoreOpenRows) {
            openRows();
        }
    }

    function openRows() {
        $.each(oServices.fnGetNodes(), function(idx, row) {
            var service = oServices.fnGetData(row);
            oServices.fnUpdate(open.indexOf(service.wsdl_id) != -1, row, 2, false);
        });

        oServices.fnDrawAndScroll();
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

    function refreshWSDL(newURL, onSuccess) {
        var params = wsdlParams();

        if (newURL) {
            params.new_url = newURL;
        }

        $.post(action("wsdl_refresh"), params, function(response) {
            oServices.fnClearTable();
            oServices.fnAddData(response.data);
            enableActions();

            if (onSuccess) {
                onSuccess();
            }
        });
    }

    function initWSDLAddDialog() {
        var dialog = $("#wsdl_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 500,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();

                      params.client_id = $("#details_client_id").val();

                      $.post(action("wsdl_add"), params, function(response) {
                          oServices.fnClearTable();
                          oServices.fnAddData(response.data);
                          enableActions();

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

        $("#wsdl_add").live('click', function() {
            $("#wsdl_add_url", dialog).val("");
            $("#wsdl_add_dialog").dialog("option", "title", $(this).html());
            $("#wsdl_add_dialog").dialog("open");
        });
    }

    function initAdapterAddDialog() {
        var dialog = $("#adapter_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 500,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();

                      params.client_id = $("#details_client_id").val();
                      params.adapter_add_sslauth =
                          $("#adapter_add_sslauth").attr("checked");

                      $.post(action("adapter_add"), params, function(response) {
                          oServices.fnClearTable();
                          oServices.fnAddData(response.data);
                          enableActions();

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

        $("#adapter_add").live('click', function() {
            $("#adapter_add_url", dialog).val("");
            $("#adapter_add_wsdl_uri", dialog).val("");
            $("#adapter_add_dialog").dialog("option", "title", $(this).html());
            $("#adapter_add_dialog").dialog("open");
        });

        handleWSDLUrls('#adapter_add_url', '#adapter_add_sslauth', dialog);
    }

    function initWSDLDisableDialog() {
        $("#wsdl_disable_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 500,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = wsdlParams();
                      params.wsdl_disabled_notice =
                          $("#wsdl_disabled_notice", this).val();

                      $.post(action("wsdl_disable"), params, function(response) {
                          oServices.fnClearTable();
                          oServices.fnAddData(response.data);
                          enableActions();

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

        $("#wsdl_disable").live('click', function() {
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
        var serviceParamsDialog = $("#service_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            // height: 350,
            width: 600,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();
                      params.client_id = $("#details_client_id").val();

                      var doPost = function() {
                          $.post(action("service_params"), params, function(response) {
                              oServices.fnClearTable(false);
                              oServices.fnAddData(response.data, false);
                              enableActions();

                              $(dialog).dialog("close");
                          });
                      };

                      if (params.params_timeout == "0") {
                          warning("clients.service_params_dialog.no_timeout",
                              null, doPost);
                      } else {
                          doPost();
                      }
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });
      handleWSDLUrls('#params_url', '#params_sslauth', serviceParamsDialog);
    }

    function initAdapterParamsDialog() {
        var adapterParamsDialog = $("#adapter_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 600,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();
                      params.client_id = $("#details_client_id").val();

                      var postParams = function() {
                          $.post(action("adapter_params"), params, function(response) {
                              oServices.fnClearTable();
                              oServices.fnAddData(response.data);
                              enableActions();
                              $(dialog).dialog("close");
                          });
                      };

                      if (params.params_adapter_url != params.params_adapter_id) {
                          refreshWSDL(params.params_adapter_url, function() {
                              params.params_adapter_id = params.params_adapter_url;
                              delete params.params_adapter_url;
                              postParams();
                          });
                      } else {
                          delete params.params_adapter_url;
                          postParams();
                      }
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        handleWSDLUrls('#params_adapter_url', '#params_adapter_sslauth', adapterParamsDialog);
    }

    function initWSDLParamsDialog() {
        $("#wsdl_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            // height: 200,
            width: 700,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;

                      refreshWSDL($("#params_wsdl_url").val(), function() {
                          $(dialog).dialog("close");
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

    function getConnectionTypeIcon(url, sslAuth) {
        if (url.match("^https")) {
            return sslAuth ? "fa-lock green" : "fa-lock orange";
        }

        return "fa-unlock gray";
    }

    function initServicesTable() {
        var opts = scrollableTableOpts(400);
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.oLanguage.sSearch = _("clients.client_services_dialog.search_services");
        opts.aaSortingFixed = [[0, 'asc'], [1, 'desc']];
        opts.aoColumns = [
            { "mData": "wsdl_id", "bVisible": false, "bSearchable": false },
            { "mData": "wsdl", "bVisible": false,
              "mRender": function(data, type, full) {
                  if (type == 'filter' && data) {
                      // if it is WSDL/ADAPTER row, return current filter
                      // value to always keep the row visible
                      var filterValue = $("#services_filter input").val();
                      return filterValue ? filterValue : data;
                  }

                  return data;
              }
            },
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
              "mRender": function(data, type, full) {
                  if (full.wsdl && full.meta) {
                      return data;
                  }

                  if (full.wsdl && full.adapter) {
                      return data + " (<i class='fa "
                          + getConnectionTypeIcon(full.wsdl_id, full.sslauth)
                          + "'></i>" + full.wsdl_id + ")";
                  }

                  if (full.wsdl) {
                      return data + " (" + full.wsdl_id + ")";
                  }

                  return data + " (" + full.subjects_count + ")";
              },
              "fnCreatedCell": function(nTd, sData, oData) {
                  if (oData.wsdl) {
                      $(nTd).addClass("noclip");
                  }
              } },
            { "mData": "title", "sClass": "align-left", "sWidth": "16em" },
            { "mData": "url", "sClass": "align-left",
              "sWidth": "6.5em",
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      return data;
                  }

                  if (!data) {
                      return null;
                  }

                  return "<div class='left valign-bottom'><i class='fa "
                      + getConnectionTypeIcon(data, full.sslauth) + "'></i></div>"
                      + "<div title='" + data + "' class='nowrap rtl'>"
                      + data + "</div>";
              } },
            { "mData": "timeout", "sClass": "align-center", "sWidth": "4.5em",
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      return data;
                  }
                  return (!full.wsdl && full.adapter) ? "" : data;
              },
            },
            { "mData": "last_refreshed", "sClass": "align-center", "sWidth": "6.5em" }
        ];
        opts.fnRowCallback = function(nRow, oData) {
            if (oData.wsdl) {
                $(nRow).addClass("wsdl");

                if (oData.adapter) {
                    $(nRow).addClass("adapter");
                }
                if (oData.meta) {
                    $(nRow).addClass("meta");
                }
            } else {
                $(nRow).addClass("service");

                if (oData.adapter) {
                    $(nRow).addClass("adapter_service");
                }
                if (oData.meta) {
                    $(nRow).addClass("meta_service");
                }
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

            var newOpenState = !oData.open;
            $.each(oServices.fnGetNodes(), function(idx, row) {
                if (oServices.fnGetData(row).wsdl_id == oData.wsdl_id) {
                    oServices.fnUpdate(newOpenState, row, 2, false);
                }
            });

            oServices.fnDrawAndScroll();

            oServices.closest(".ui-dialog-content")
                .trigger("dialogresizestop");
        });

        $("#services tbody td:not(.open)").live("click", function() {
            var row = $(this).parent();

            if (row.hasClass("wsdl") && row.hasClass("meta")) {
                return;
            }

            var multiselect = row.hasClass("wsdl")
                && $(".service.row_selected").length == 0;

            oServices.setFocus(0, row, multiselect);
            enableActions(false);
        });
    }

    function handleWSDLUrls(inpt, check, dialog) {
        $(inpt, dialog).keyup(function() {
            if ($(this).val().lastIndexOf("https", 0) === 0) {
                $(check).prop('disabled', false);
            } else {
                $(check).prop('checked', true)
                    .prop('disabled', true);
            }
        });

        $(check).prop("disabled", true);
    }

    function initClientServicesActions() {
        $("#wsdl_enable").live('click', function() {
            var params = wsdlParams();
            params.enable = true;

            $.post(action("wsdl_disable"), params, function(response) {
                oServices.fnClearTable();
                oServices.fnAddData(response.data);
                enableActions();
            });
        });

        $("#wsdl_refresh").live('click', function() {
            refreshWSDL();
        });

        $("#wsdl_delete").live('click', function() {
            confirm("clients.client_services_dialog.delete_wsdls_confirm", null,
                    function() {
                $.post(action("wsdl_delete"), wsdlParams(), function(response) {
                    oServices.fnClearTable();
                    oServices.fnAddData(response.data);
                    enableActions();
                }, "json");
            });
        });

        $("#service_params").live('click', function() {
            var service = oServices.getFocusData();

            if (service.wsdl && !service.adapter) {
                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_wsdl_url").val(service.wsdl_id);

                $("#wsdl_params_dialog").dialog("open");

            } else if (service.adapter) {
                $("#params_adapter_id").val(service.wsdl_id);
                $("#params_adapter_url").val(service.wsdl_id);
                $("#params_adapter_wsdl_uri").val(service.adapter_wsdl_uri);
                $("#params_adapter_timeout").val(service.timeout);

                if (service.sslauth) {
                    $("#params_adapter_sslauth").attr("checked", true);
                } else {
                    $("#params_adapter_sslauth").removeAttr("checked");
                }

                $("#adapter_params_dialog").dialog("open");
            } else {
                $("#params_url_all, #params_timeout_all, #params_sslauth_all, " +
                  "#params_security_category_all").removeAttr("checked");

                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_service_id").val(service.service_id);
                $("#params_url").val(service.url);
                $("#params_timeout").val(service.timeout);

                if (service.sslauth) {
                    $("#params_sslauth").attr("checked", true);
                } else {
                    $("#params_sslauth").removeAttr("checked");
                }

                $("input[name='params_security_category[]']").removeAttr("checked");

                $.each(service.security_category, function(idx, val) {
                    $("input[name='params_security_category[]'][value=" + val + "]")
                        .attr("checked", true);
                });

                $("#service_params_dialog").dialog("open");
            }
        });

        $("#service_acl").live('click', function() {
            ACL.openDialog(oServices.getFocusData().service_code);
        });
    }

    $(document).ready(function() {
        initWSDLAddDialog();
        initWSDLDisableDialog();
        initWSDLParamsDialog();
        initAdapterAddDialog();
        initAdapterParamsDialog();
        initServiceParamsDialog();

        initServicesTable();
        initClientServicesActions();
    });

    SERVICES.init = function() {
        var titleParams = {
            member: $('#details_member_name').val(),
            subsystem: $('#details_subsystem_code').val()
        };
        var title = $('#details_subsystem_code').val() !== ''
            ? _("clients.client_services_dialog.subsystem_title", titleParams)
            : _("clients.client_services_dialog.member_title", titleParams);

        var params = {
            client_id: $("#details_client_id").val()
        };

        oServices.fnClearTable();

        $.get(action("client_services"), params, function(response) {
            oServices.fnAddData(response.data);
            enableActions();
        });
    };

    SERVICES.updateSubjectsCount = function(subjectsCount) {
        var serviceData = oServices.getFocusData();
        serviceData.subjects_count = subjectsCount;

        oServices.fnUpdate(serviceData, oServices.getFocus(), undefined, false);
        oServices.fnDrawAndScroll();
    };

}(window.SERVICES = window.SERVICES || {}, jQuery));
