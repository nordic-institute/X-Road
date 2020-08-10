(function(SERVICES, $, undefined) {

    var oServices;

    // let's maintain a list of open rows, so we can restore it on refresh
    var open = [];

    $.fn.dataTableExt.afnFiltering.push(
        function(oSettings, aData, iDataIndex) {
            if (oSettings.sTableId == "services") {
                var oData = oSettings.oInstance.fnGetData()[iDataIndex];
                return aData[0] || open.indexOf(oData.wsdl_id) != -1;
            }
            return true;
        }
    );

    function enableActions() {
        if ($(".wsdl.row_selected").length > 0) {
            $("#wsdl_delete, #wsdl_refresh, " +
              "#wsdl_disable, #wsdl_enable").enable();
        } else {
            $("#wsdl_delete, #wsdl_refresh, " +
              " #wsdl_disable, #wsdl_enable").disable();
        }

        // Toggle edit-button
        if ($(".service.row_selected:visible, .wsdl.row_selected").length == 1) {
            $("#service_params").enable();
        } else {
            $("#service_params").disable();
        }

        // Toggle acl & endpoint buttons
        if ($(".service.row_selected:visible").length == 1) {
            var data = $("#services").dataTable().getFocusData();
            $("#service_acl").enable();

            if (data.endpoint) {
                $("#openapi3_add_endpoint").enable();
            }
            if (data.generated) {
                $("#service_params").disable();
                $("#wsdl_delete").disable();
            } else {
                $("#wsdl_delete").enable();
            }
        } else {
            $("#service_acl").disable();
            $("#openapi3_add_endpoint").disable();
        }

        if ($(".wsdl:not(.disabled).row_selected").length > 0) {
            $("#wsdl_enable").hide();
            $("#wsdl_disable").show();
        } else {
            $("#wsdl_disable").hide();
            $("#wsdl_enable").show();
        }

        if($(".wsdl.row_selected").length > 1) {
            $("wsdl_refresh").disable();
        }

        var methodFields = $('#services .endpoint_row .method');
        $.each(methodFields, function(index, field) {
            if($(field).text() === '*') {
                $(field).text('ALL');
            }
        });

    }

    function endpointParams() {
        var params = {
            client_id: $("#details_client_id").val(),
        };

        $("#services .row_selected").each( function(idx, row) {
            var rowData = oServices.fnGetData(row);
            params.service_code = rowData.service_code;
            params.method = rowData.method;
            params.path = rowData.path;
        });

        return params;
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

    function initWSDLAddDialog() {
        var dialog = $("#wsdl_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 200,
            width: 500,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();

                      params.service_type = "WSDL";
                      params.client_id = $("#details_client_id").val();

                      $.post(action("servicedescription_add"), params, function(response) {
                          oServices.fnReplaceData(response.data);
                          enableActions();

                          $(dialog).dialog("close");
                      }, "json").fail(showOutput);
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

    function initOPENAPI3AddDialog() {
        var dialog = $("#openapi3_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 300,
            width: 600,
            buttons: [
                { text: _("common.ok"),
                    click: function() {
                        var dialog = this;
                        var params = $("form", this).serializeObject();

                        params.client_id = $("#details_client_id").val();

                        $.post(action("servicedescription_add"), params, function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();

                            $(dialog).dialog("close");
                        }, "json").fail(showOutput);

                    }
                },
                { text: _("common.cancel"),
                    click: function() {
                        $(this).dialog("close");
                    }
                }
            ]
        });

        $("#openapi3_add").live('click', function() {
            $("#openapi3_add_url", dialog).val("");
            $("#openapi3_service_code", dialog).val("");
            $("#openapi3_add_dialog").dialog("option", "title", $(this).html());
            $("#openapi3_add_dialog").dialog("open");
        });
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

                      $.post(action("servicedescription_disable"), params, function(response) {
                          oServices.fnReplaceData(response.data);
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
        var dialog = $("#service_params_dialog").initDialog({
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
                              oServices.fnReplaceData(response.data);
                              enableActions();

                              $(dialog).dialog("close");
                          }, "json");
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

        $("#params_url", dialog).keyup(function() {
            enableTLSAuth(oServices.getFocusData().sslauth);
        });
    }

    function enableTLSAuth(checked) {
        var disabled = $("#service_params_dialog #params_url").val()
            .lastIndexOf("https", 0) !== 0;

        $("#params_sslauth, #params_sslauth_all").prop("disabled", disabled);
        $("#params_sslauth").prop("checked", checked);
    }

    function initWSDLParamsDialog() {
        $("#wsdl_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 200,
            width: 700,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = {
                          client_id: $("#details_client_id").val(),
                          wsdl_id: oServices.getFocusData().wsdl_id,
                          new_url: $("#params_wsdl_url").val(),
                          service_type: "WSDL"
                      };

                      $.post(action("servicedescription_edit"), params, function(response) {
                          oServices.fnReplaceData(response.data);
                          enableActions();

                          $(dialog).dialog("close");
                      }, "json").fail(showOutput);
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

    function initOPENAPI3ParamsDialog() {
        $("#openapi3_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 350,
            width: 700,
            buttons: [
                { text: _("common.ok"),
                    click: function() {
                        var dialog = this;

                        var params = {
                            client_id: $("#details_client_id").val(),
                            wsdl_id: oServices.getFocusData().wsdl_id,
                            openapi3_old_service_code: oServices.getFocusData().openapi3_service_code,
                            service_type: $(this).data("service_type"),
                            openapi3_new_url: $("#params_openapi3_url").val(),
                            openapi3_new_service_code: $("#params_openapi3_service_code").val()
                        };

                        $.post(action("servicedescription_edit"), params, function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();

                            $(dialog).dialog("close");
                        }, "json").fail(showOutput);

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

    function initRESTENDPOINTAddDialog() {
        var dialog = $("#rest_add_endpoint_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 360,
            width: 600,
            buttons: [
                { text: _("common.ok"),
                    click: function() {

                        var dialog = this;
                        var params = $("form", this).serializeObject();

                        params.service_code = $("#services tbody tr.row_selected td span").attr("data-servicecode");
                        params.client_id = $("#details_client_id").val();

                        $.post(action("openapi3_endpoint_add"), params, function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();
                            $(dialog).dialog("close");
                        }, "json").fail(showOutput);
                    }
                },
                { text: _("common.cancel"),
                    click: function () {
                        $(this).dialog("close");
                    }
                }
            ]
        });

        $("#openapi3_add_endpoint").live('click', function () {
            $("#endpoint_method", dialog).val("");
            $("#endpoint_path", dialog).val("/");
            $("#rest_add_endpoint_dialog").dialog("option", "title", $(this).html());
            $("#rest_add_endpoint_dialog").dialog("open");
        });

    }

    function initRESTENDPOINTParamsDialog() {
        $("#rest_endpoint_params_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 360,
            width: 600,
            buttons: [
                { text: _("common.ok"),
                    click: function() {
                        var dialog = this;
                        var focusData = oServices.getFocusData();
                        var params = $("form", this).serializeObject();

                        params.old_endpoint_method = $(this).data("old_method");
                        params.old_endpoint_path = $(this).data("old_path");
                        params.client_id = $("#details_client_id").val();
                        params.service_code = focusData.service_code;

                        $.post(action("openapi3_endpoint_edit"), params, function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();
                            $(dialog).dialog("close");
                        }, "json").fail(showOutput);
                    }
                },
                { text: _("common.cancel"),
                    click: function () {
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
        opts.aaSortingFixed = [[0, 'asc'], [1, 'desc']];
        opts.aoColumns = [
            { "mData": "wsdl_id", "bVisible": false, "bSearchable": false },
            { "mData": "wsdl", "bVisible": false,
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      if ( data ) {
                          // if it is WSDL row, return current filter
                          // value to always keep the row visible
                          var filterValue = $("#services_filter input").val();
                          return filterValue ? filterValue : data;
                      } else {
                          return data;
                      }
                  }
                  return util.escape(data);
              }
            },
            { "sDefaultContent": "", "bSortable": false,
              "sWidth": "0.5em", "sClass": "thin-right",
              "fnCreatedCell": function(nTd, sData, oData) {
                  if (oData.wsdl) {
                      $(nTd).addClass(
                          open.indexOf(oData.wsdl_id) == -1
                              ? "closed" : "open");
                  }
              } },
            { "mData": "name",
              "mRender": function(data, type, full) {
                  var rowType = full.wsdl ? 'servicedescription' : full.method ? 'endpoint' : 'service';
                  if (full.wsdl) {
                      return "<span data-type='" + rowType + "'>" + data + " (" + full.wsdl_id + ")</span>";
                  } else if (full.method) {
                      var generatedIcon = full.generated ? "<img src='/Icon_sync.svg' style='width:13px'/>" : "";
                      var rowClasses = full.generated ? 'endpoint_row generated' : 'endpoint_row';
                      return "<span data-type='" + rowType + "' data-servicecode='" + util.escape(data) + "' class='" + rowClasses + "' >" +
                                generatedIcon +
                                "<span class='method'>" + full.method + "</span> " +
                                    full.path + " (" + full.subjects_count + ")" +
                          "</span>";
                  }

                  return "<span data-type='" + rowType + "' data-servicecode='" + util.escape(data) + "'>" + util.escape(data) + " (" + full.subjects_count + ")</span>";
              } },
            { "mData": "title", mRender: util.escape },
            { "mData": "url",
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      return data;
                  }

                  if (!data || full.method) {
                      return null;
                  }

                  return "<div class='left valign-bottom'><i class='fa "
                      + getConnectionTypeIcon(data, full.sslauth) + "'></i></div>"
                      + util.escape(data);
              } },
            { "mData": "timeout", "sClass": "center", "sWidth": "4em",
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      return data;
                  }

                  if(full.method) {
                      return null;
                  }

                  return data;
              },
            },
            { "mData": "last_refreshed", "sClass": "center", "sWidth": "7em",
                "mRender": function (data, type, full) {
                    if(full.method) {
                        return null;
                    }

                    return data;
                }
            }
        ];

        opts.fnRowCallback = function(nRow, oData) {
            if (oData.wsdl) {
                $("td:nth-child(2)", nRow).attr("colspan", "3");
                $("td:nth-child(3), td:nth-child(4)", nRow).hide();

                $(nRow).addClass("wsdl");
                if(oData.name === "REST") {
                    $(nRow).addClass("rest");
                }
            } else {
                $(nRow).addClass("service");
            }

            if (oData.disabled) {
                $(nRow).addClass("disabled");
            }

            return nRow;
        };
        opts.asRowId = ["wsdl_id", "service_id"];

        oServices = $("#services").dataTable(opts);

        $(".services_actions").appendTo("#services_wrapper .dataTables_header");

        $("#services tbody td.open, #services tbody td.closed").live("click",
                function() {
            var nRow = $(this).closest("tr").get(0);
            var oData = oServices.fnGetData(nRow);

            if (!oData.wsdl) {
                return;
            }

            var openIdx = open.indexOf(oData.wsdl_id);
            if (openIdx != -1) {
                open.splice(openIdx, 1);
                $(this).removeClass("open").addClass("closed");
            } else {
                open.push(oData.wsdl_id);
                $(this).removeClass("closed").addClass("open");
            }

            oServices.fnDraw();
            enableActions();

            oServices.closest(".ui-dialog-content")
                .trigger("dialogresizestop");
        });

        $("#services tbody td:not(.open, .closed)").live("click", function() {
            var row = $(this).parent();
            var multiselect = row.hasClass("wsdl")
                && $(".service.row_selected").length == 0;

            oServices.setFocus(0, row, multiselect);
            enableActions();
        });

    }

    function initClientServicesActions() {
        $("#wsdl_enable").click(function() {
            var params = wsdlParams();
            params.enable = true;

            $.post(action("servicedescription_disable"), params, function(response) {
                oServices.fnReplaceData(response.data);
                enableActions();
            }, "json");
        });

        $("#wsdl_refresh").click(function() {
            var params = wsdlParams();

            $.post(action("servicedescription_refresh"), params, function(response) {
                oServices.fnReplaceData(response.data);
                enableActions();
            }, "json").fail(showOutput);
        });

        $("#wsdl_delete").click(function() {
            if($("#services .row_selected").length === 1 && $("#services tbody tr.row_selected td span").attr("data-type") === 'endpoint') {
                confirm("clients.client_services_tab.delete_endpoint_confirm", null,
                    function() {
                        var params = endpointParams();
                        $.post(action("endpoint_delete"), params, function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();
                        }, "json");
                    });
            } else {
                confirm("clients.client_services_tab.delete_wsdls_confirm", null,
                    function() {
                        $.post(action("servicedescription_delete"), wsdlParams(), function(response) {
                            oServices.fnReplaceData(response.data);
                            enableActions();
                        }, "json");
                    });
            }
        });

        $("#service_params").click(function() {
            var service = oServices.getFocusData();

            if (service.method) {
                $("#rest_endpoint_params_dialog #endpoint_method").val(service.method);
                $("#rest_endpoint_params_dialog #endpoint_path").val(service.path);
                $("#rest_endpoint_params_dialog")
                    .data("old_method", service.method)
                    .data("old_path", service.path)
                    .data("service_code", service.service_code)
                    .dialog("open");
            } else if (service.service_type === 'WSDL') {
                // Open WSDL service edit dialog
                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_wsdl_url").val(service.wsdl_id);

                $("#wsdl_params_dialog").dialog("open");
            } else if (service.service_type === 'REST' || service.service_type === 'OPENAPI3') {
                // Open REST service edit dialog
                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_openapi3_url").val(service.wsdl_id);
                $("#params_openapi3_service_code").val(service.openapi3_service_code);

                $("#openapi3_params_dialog input[type='radio']").val([service.service_type]);
                $("#openapi3_params_dialog input[type='radio']").disable();
                $("#openapi3_params_dialog").data('service_type', service.service_type);
                $("#openapi3_params_dialog").dialog("open");
            } else  {
                $("#params_url_all, #params_timeout_all, #params_sslauth_all, " +
                  "#params_security_category_all").removeAttr("checked");

                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_service_id").val(service.service_id);
                $("#params_url").val(service.url);
                $("#params_timeout").val(service.timeout);

                enableTLSAuth(service.sslauth);

                $("input[name='params_security_category[]']").removeAttr("checked");

                $.each(service.security_category, function(idx, val) {
                    $("input[name='params_security_category[]'][value=" + val + "]")
                        .attr("checked", true);
                });

                $("#service_params_dialog").dialog("open");
            }
        });

        $("#service_acl").click(function() {
            var data = oServices.getFocusData();
            if(data.wsdl === false && data.method === undefined && data.path === undefined) {
                // Open access rights dialog for rest service end point where method = * and path = **
                ACL.openDialog(data.service_code, '*', '**')
            } else {
                ACL.openDialog(data.service_code, data.method, data.path);
            }
        });
    }

    function initTestability() {
        // add data-name attributes to improve testability
        $("#wsdl_add_dialog").parent().attr("data-name", "wsdl_add_dialog");
        $("#wsdl_params_dialog").parent().attr("data-name", "wsdl_params_dialog");
        $("#openapi3_add_dialog").parent().attr("data-name", "openapi3_add_dialog");
        $("#openapi3_params_dialog").parent().attr("data-name", "openapi3_params_dialog");
        $("#service_params_dialog").parent().attr("data-name", "service_params_dialog");
        $("#wsdl_disable_dialog").parent().attr("data-name", "wsdl_disable_dialog");
        $("#rest_endpoint_params_dialog").parent().attr("data-name", "rest_endpoint_params_dialog");
        $("#rest_add_endpoint_dialog").parent().attr("data-name", "rest_add_endpoint_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    function showOutput(jqXHR) {
        var response = $.parseJSON(jqXHR.responseText);

        if (response.data.stderr && response.data.stderr.length > 0) {
            initConsoleOutput(response.data.stderr,
                _("clients.client_services_tab.wsdl_validator_output"), 500);
        }
    }

    $(document).ready(function() {
        initWSDLAddDialog();
        initWSDLDisableDialog();
        initWSDLParamsDialog();
        initOPENAPI3AddDialog();
        initOPENAPI3ParamsDialog();
        initRESTENDPOINTAddDialog();
        initRESTENDPOINTParamsDialog();
        initServiceParamsDialog();
        initServicesTable();
        initClientServicesActions();
        initTestability();


    });

    SERVICES.init = function() {
        var titleParams = {
            member: $('#details_member_name').val(),
            subsystem: $('#details_subsystem_code').val()
        };
        var title = $('#details_subsystem_code').val() !== ''
            ? _("clients.client_services_tab.subsystem_title", titleParams)
            : _("clients.client_services_tab.member_title", titleParams);

        var params = {
            client_id: $("#details_client_id").val()
        };

        oServices.fnClearTable();

        $.get(action("client_services"), params, function(response) {
            oServices.fnAddData(response.data);
            enableActions();
        }, "json");
    };

    SERVICES.updateSubjectsCount = function() {
        var params = {
            client_id: $("#details_client_id").val()
        };
        $.get(action("client_services"), params, function(response) {
            oServices.fnClearTable();
            oServices.fnAddData(response.data);
            enableActions();
        }, "json");
    };

}(window.SERVICES = window.SERVICES || {}, jQuery));
