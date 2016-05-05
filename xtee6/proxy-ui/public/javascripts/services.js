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

        if ($(".service.row_selected:visible, " +
              ".wsdl.row_selected").length == 1) {
            $("#service_params").enable();
        } else {
            $("#service_params").disable();
        }

        if ($(".service.row_selected:visible").length == 1) {
            $("#service_acl").enable();
        } else {
            $("#service_acl").disable();
        }

        if ($(".wsdl:not(.disabled).row_selected").length > 0) {
            $("#wsdl_enable").hide();
            $("#wsdl_disable").show();
        } else {
            $("#wsdl_disable").hide();
            $("#wsdl_enable").show();
        }
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
            oServices.fnReplaceData(response.data);
            enableActions();

            if (onSuccess) {
                onSuccess();
            }
        }, "json");
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

        $("#wsdl_add").live('click', function() {
            $("#wsdl_add_url", dialog).val("");
            $("#wsdl_add_dialog").dialog("option", "title", $(this).html());
            $("#wsdl_add_dialog").dialog("open");
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

                      $.post(action("wsdl_disable"), params, function(response) {
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

        handleWSDLUrls('#params_url', '#params_sslauth', serviceParamsDialog);
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
                  if (full.wsdl) {
                      return data + " (" + full.wsdl_id + ")";
                  }

                  return util.escape(data) + " (" + full.subjects_count + ")";
              } },
            { "mData": "title", mRender: util.escape },
            { "mData": "url",
              "mRender": function(data, type, full) {
                  if (type == 'filter') {
                      return data;
                  }

                  if (!data) {
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
                  return data;
              },
            },
            { "mData": "last_refreshed", "sClass": "center", "sWidth": "7em" }
        ];

        opts.fnRowCallback = function(nRow, oData) {
            if (oData.wsdl) {
                $("td:nth-child(2)", nRow).attr("colspan", "3");
                $("td:nth-child(3), td:nth-child(4)", nRow).hide();

                $(nRow).addClass("wsdl");
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
                oServices.fnReplaceData(response.data);
                enableActions();
            }, "json");
        });

        $("#wsdl_refresh").live('click', function() {
            refreshWSDL();
        });

        $("#wsdl_delete").live('click', function() {
            confirm("clients.client_services_tab.delete_wsdls_confirm", null,
                    function() {
                $.post(action("wsdl_delete"), wsdlParams(), function(response) {
                    oServices.fnReplaceData(response.data);
                    enableActions();
                }, "json");
            });
        });

        $("#service_params").live('click', function() {
            var service = oServices.getFocusData();

            if (service.wsdl) {
                $("#params_wsdl_id").val(service.wsdl_id);
                $("#params_wsdl_url").val(service.wsdl_id);

                $("#wsdl_params_dialog").dialog("open");
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

    function initTestability() {
        // add data-name attributes to improve testability
        $("#wsdl_add_dialog").parent().attr("data-name", "wsdl_add_dialog");
        $("#wsdl_params_dialog").parent().attr("data-name", "wsdl_params_dialog");
        $("#service_params_dialog").parent().attr("data-name", "service_params_dialog");
        $("#wsdl_disable_dialog").parent().attr("data-name", "wsdl_disable_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initWSDLAddDialog();
        initWSDLDisableDialog();
        initWSDLParamsDialog();
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

    SERVICES.updateSubjectsCount = function(subjectsCount) {
        var serviceData = oServices.getFocusData();
        serviceData.subjects_count = subjectsCount;

        oServices.fnUpdate(serviceData, oServices.getFocus(), undefined);
    };

}(window.SERVICES = window.SERVICES || {}, jQuery));
