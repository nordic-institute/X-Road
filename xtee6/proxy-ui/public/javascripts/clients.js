(function(CLIENTS, $, undefined) {
    var oClients, oClientsGlobal, oClientCerts;

    var stateSortOrder = [
        "registered",
        "registration in progress",
        "saved",
        "global error",
        "deletion in progress"
    ];

    function enableActions() {
        if (oClientsGlobal && oClientsGlobal.getFocus()) {
            $("#client_select_ok").enable();
        } else {
            $("#client_select_ok").disable();
        }

        if (oClients && oClients.getFocus()) {
            var client = oClients.getFocusData();

            var clientActions =
                $("#client_register, #client_unregister, #client_delete");

            clientActions.css("visibility", "visible");

            if (client.register_enabled) {
                $("#client_register").enable();
            } else {
                $("#client_register").disable();
            }

            if (client.unregister_enabled) {
                $("#client_unregister").show();
            } else {
                $("#client_unregister").hide();
            }

            if (client.delete_enabled) {
                $("#client_delete").show();
            } else {
                $("#client_delete").hide();
            }

            if (client.owner) {
                // let's not hide, positioning the empty div would
                // give wrong results
                clientActions.css("visibility", "hidden");
            }

            adjustDetailsTitle();
        }
    }

    function adjustDetailsTitle() {
        var dialog = $("#client_details_dialog");

        var client = oClients.getFocusData();

        var clientId = generateIdElement({
            "Type": client.type,
            "Instance": client.instance,
            "Class": client.member_class,
            "Code": client.member_code,
            "Subsystem": client.subsystem_code
        });

        var title = _("clients.client_details_dialog.title")
            + $("<p>").append(clientId).html();

        var totalWidth = dialog.innerWidth();

        var defaultButtonsWidth = dialog.parents(".ui-dialog")
            .find(".dialog-buttonbar").outerWidth();

        var tabButtonsWidth = dialog
            .siblings(".ui-dialog-titlebar")
            .find(".ui-tabs-panel-actions:visible")
            .outerWidth();

        dialog.parents(".ui-dialog").find(".ui-dialog-title")
            .css("display", "block")
            .css("width", totalWidth - defaultButtonsWidth - tabButtonsWidth)
            .html(title);
    }

    var tabRefreshers = {
        "#details_tab": refreshDetails,
        "#acl_subjects_tab": refreshServiceClients,
        "#services_tab": refreshServices,
        "#internal_certs_tab": refreshInternalServers,
        "#local_groups_tab": refreshLocalGroups
    };

    function refreshTab(tab) {
        tabRefreshers[tab].call(this);
    }

    function refreshDetails() {
        var params = {
            client_id: $("#details_client_id").val()
        };

        oClientCerts.fnClearTable();

        $.get(action("client_certs"), params, function(response) {
            oClientCerts.fnAddData(response.data);
        });

        enableActions();
    }

    function refreshServiceClients() {
        ACL_SUBJECTS.init();
    }

    function refreshServices() {
        SERVICES.init();
    }

    function refreshInternalServers() {
        INTERNAL_CERTS.init();
    }

    function refreshLocalGroups() {
        LOCAL_GROUPS.init();
    }

    function initClientAddDialog() {
        $("#client_add_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 370,
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      var dialog = this;
                      var params = $("form", this).serializeObject();

                      $.post(action("client_add"), params, function(response) {
                          oClients.fnReplaceData(response.data);

                          $(dialog).dialog("close");

                          var regParams = {
                              member_class: params.add_member_class,
                              member_code: params.add_member_code,
                              subsystem_code: params.add_subsystem_code
                          };

                          confirm("clients.client_add_dialog.send_regreq", null, function() {
                              $.post(action("client_regreq"), regParams, function() {
                                  refreshClients();
                              });
                          });
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

        $("#client_add").click(function() {
            $("#client_add_dialog form input[type!=hidden]").val("");
            $("#client_add_dialog form select").val("");
            $("#client_add_dialog #add_member_name").html("");
            $("#client_add_dialog").dialog("open");
        });

        var namefetch = function() {
            var timer = 0;
            return function(callback, ms) {
                clearTimeout(timer);
                timer = setTimeout(callback, ms);
            };
        }();

        $("#add_member_class").change(function() {
            $("#add_member_code").keyup();
        });

        $("#add_member_code").keyup(function() {
            namefetch(function () {
                var params = $("#add_member_class, #add_member_code").serialize();
                $.get(action("client_name"), params, function(response) {
                    $("#add_member_name").html(response.data.name);
                });
            }, 500);
        });
    }

    function initClientSelectDialog() {
        $("#client_select_dialog").initDialog({
            autoOpen: false,
            modal: true,
            width: 666,
            height: 500,
            open: function() {
                oClientsGlobal.fnAdjustColumnSizing();
                $("#client_select_ok").disable();
            },
            buttons: [
                { text: _("common.ok"),
                  id: "client_select_ok",
                  click: function() {
                      var client = oClientsGlobal.getFocusData();
                      $("#add_member_name").text(client.member_name);
                      $("#add_member_class").val(client.member_class);
                      $("#add_member_code").val(client.member_code);
                      $("#add_subsystem_code").val(client.subsystem_code);
                      $(this).dialog("close");
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ]
        });

        $("#client_select").click(function() {
            oClientsGlobal.fnClearTable();
            $("#client_select_dialog").dialog("open");
        });


        $("#client_select_dialog .simple_search_form .search").click(updateClientList);
        $("#search_filter").change(updateClientList);
    }

    function updateClientList() {
        var params = $("#search_member").serialize();
        var filter = $("#search_filter").is(":checked");
        if(filter) {
            $.get(action("clients_search"), params, function(response) {
                $.get(action("clients_refresh"), null, function(r) {
                    var filtered = response.data.filter(function(res) {
                        var keep = false;
                        r.data.forEach(function(t) {
                            if(t.type === "MEMBER") {
                                if (res.member_code === t.member_code && res.member_class === t.member_class) {
                                    keep = true;
                                }
                            }
                        });
                        return keep;
                    });

                    oClientsGlobal.fnReplaceData(filtered);
                    oClientsGlobal.trigger("dialogresizestop");
                }, "json");
            }, "json");
        } else {
            $.get(action("clients_search"), params, function(response) {
                oClientsGlobal.fnReplaceData(response.data);
                oClientsGlobal.trigger("dialogresizestop");
            }, "json");
        }

        return false;
    }


    function initClientDetailsDialog() {
        $("#client_details_dialog").initDialog({
            autoOpen: false,
            modal: true,
            height: 600,
            width: 800,
            minWidth: 700,
            buttons: [
                { text: _("common.close"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ],
            open: function() {
                oClientCerts.fnAdjustColumnSizing();
            }
        }).on("dialogresizestop", function() {
            adjustDetailsTitle();
            oClientCerts.fnAdjustColumnSizing();
        });

        $("#client_details_tabs").initTabs({
            activate: function(event, ui) {
                refreshTab($("a", ui.newTab).attr("href"));
            }
        });
    }

    function refreshClients() {
        $.get(action("clients_refresh"), null, function(response) {
            oClients.fnReplaceData(response.data);
        }, "json");
    }

    function openClientDetails(client, tab) {
        $("#details_client_id").val(client.client_id);
        $("#details_member_class").val(client.member_class);
        $("#details_member_code").val(client.member_code);
        $("#details_subsystem_code").val(client.subsystem_code);
        $("#details_member_name").val(client.member_name);

        $("#client_details_dialog").dialog("open");

        var oldTabIndex = $("#client_details_tabs").tabs("option", "active");
        var newTabIndex = $("#client_details_tabs a[href='" + tab + "']")
            .parent().index();

        $("#client_details_tabs").tabs("option", "active", newTabIndex);

        if (oldTabIndex === newTabIndex) {
            refreshTab(tab);
        }

        enableActions();
    }

    function confirmDelete(text) {
        var params = {
            client_id: $("#details_client_id").val(),
        };
        confirm(text, null, function() {
            $.post(action("client_delete"), params, function(response) {
                oClients.fnReplaceData(response.data.clients);

                if (response.data.ask_delete_certs) {
                    yesno("clients.client_details_tab.delete_certs", params,
                        function(yes) {
                            if (yes) {
                                $.post(action("client_delete_certs"), params);
                            }
                        });
                }

                $("#client_details_dialog").dialog("close");
            }, "json");
        });
    }

    function generateStateElement(state) {
        var states = {
            'saved': {
                color: 'waiting',
                icon: 'fa-circle-o'
            },
            'registration in progress': {
                color: 'waiting',
                icon: 'fa-circle'
            },
            'deletion in progress': {
                color: 'fail',
                icon: 'fa-circle'
            },
            'global error': {
                color: 'fail',
                icon: 'fa-circle-o'
            },
            'no activated tokens': {
                color: 'fail',
                icon: 'fa-circle'
            },
            'no usable certificates': {
                color: 'fail',
                icon: 'fa-circle'
            },
            'ok': {
                color: 'ok',
                icon: 'fa-circle'
            },
            'registered': {
                color: 'ok',
                icon: 'fa-circle'
            }
        };

        if (typeof states[state.toLowerCase()] != 'undefined') {
            var stateSpan = $("<span>")
                .addClass("status")
                .addClass(states[state.toLowerCase()].color)
                .attr("title", state)
                .html("<i class='fa " + states[state.toLowerCase()].icon + "'></i>")
            return $("<p>").html(stateSpan).html();
        } else {
            return "";
        }
    }

    function generateTableActions(actions) {
        var wrap = $('<div/>');
        var ul = $('<ul/>').addClass('tableitem-actions right cf');

        for (var action in actions) {
            if (actions.hasOwnProperty(action)) {
                var li = $('<li/>')
                    .attr('data-tab', action)
                    .attr('data-id', actions[action].id)
                    .attr('data-name', actions[action].id)
                    .attr('title', actions[action].title)
                    .append($('<i/>').addClass('fa fa-' + actions[action].icon));
                ul.append(li);
            }
        }

        return wrap.html(ul).html();
    }

    function initClientsTable() {
        var opts = scrollableTableOpts(410);
        opts.asRowId = ["client_id"];
        opts.fnDrawCallback = enableActions;
        opts.sDom = "<'dataTables_header'f<'clearer'>>t";
        opts.aoColumns = [
            {
                mData: function(s, t, v) {
                    if (t == 'sort') {
                        return stateSortOrder.indexOf(s.state).toString();
                    }

                    return generateStateElement(s.state);
                },
                sClass: "noclip"

            },
            {
                mData: "member_name",
                mRender: function(data, type, full) {
                    if (type == 'display') {
                        return clientName(data);
                    }
                    return data;
                },
                fnCreatedCell: function(nTd, sData, oData) {
                    if (!oData.member_name) {
                        $(nTd).addClass("missing");
                    }
                },
                sWidth: "30%"
            },
            {
                mData: function(source, type, val) {
                    var id = source.client_id.split('/')[0].split(':');
                    return generateIdElement({
                        "Type": id[0],
                        "Instance": id[1],
                        "Class": source.member_class,
                        "Code": source.member_code,
                        "Subsystem": source.subsystem_code
                    });
                }
            },
            {
                mData: function(s, t, v) {
                    var actions = {};

                    if (s.can_view_client_details) {
                        actions['#details_tab'] = {
                            icon: 'info',
                            title: 'Details',
                            id: s.client_id
                        };
                    }

                    if (s.can_view_client_acl_subjects) {
                        actions['#acl_subjects_tab'] = {
                            icon: 'gears',
                            title: 'Service Clients',
                            id: s.client_id
                        };
                    }

                    if (s.can_view_client_services) {
                        actions['#services_tab'] = {
                            icon: 'wrench',
                            title: 'Services',
                            id: s.client_id
                        };
                    }

                    if (s.can_view_client_internal_certs) {
                        actions['#internal_certs_tab'] = {
                            icon: 'cloud',
                            title: 'Internal Servers',
                            id: s.client_id
                        };
                    }

                    if (s.can_view_client_local_groups) {
                        actions['#local_groups_tab'] = {
                            icon: 'group',
                            title: 'Local Groups',
                            id: s.client_id
                        };
                    }

                    return generateTableActions(actions);
                },
                sWidth: "150px"
            },
            {
                mData: 'owner',
                bVisible: false
            }
        ];
        opts.fnRowCallback = function(nRow, oData) {
            if (oData.owner) {
                $(nRow).find('td:nth-child(2)').addClass('bold');
            }
        };

        opts.aaSortingFixed = [[4,'desc']];
        opts.aaSorting = [[1,'asc']];

        oClients = $("#clients").dataTable(opts);

        $("#clients tbody tr").live("dblclick", function() {
            var clientData = oClients.fnGetData(this);
            if (clientData.can_view_client_details) {
                openClientDetails(oClients.fnGetData(this), "#details_tab");
            }
        });

        $('#clients').on('click', 'tbody tr', function() {
            oClients.setFocus(0, this);
        });

        $("#clients").on("click", ".tableitem-actions li", function() {
            oClients.setFocus(0, $(this).closest("tr"));
            openClientDetails(oClients.getFocusData(), $(this).data("tab"));
        });
    }

    function initClientsGlobalTable() {
        var opts = scrollableTableOpts(200);
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            { "mData": "member_name", "sWidth": "40%", "mRender" : util.escape },
            { "mData": "member_class", "sWidth": "15%", "mRender" : util.escape  },
            { "mData": "member_code", "mRender" : util.escape },
            { "mData": "subsystem_code", "mRender" : util.escape }
        ];

        oClientsGlobal = $("#clients_global").dataTable(opts);

        $("#clients_global tbody tr").live("click", function() {
            oClientsGlobal.setFocus(0, this);
            enableActions();
        });
    }

    function initClientCertsTable() {
        var opts = scrollableTableOpts(200);
        opts.bPaginate = false;
        opts.sDom = "t";
        opts.bFilter = false;
        opts.aoColumns = [
            { "mData": "csp" },
            { "mData": "serial" },
            { "mData": "state" },
            { "mData": "expires" }
        ];

        oClientCerts = $("#certificates").dataTable(opts);
    }

    function initTestability() {
        // add data-name attributes to improve testability
        $("#client_details_dialog").parent().attr("data-name", "client_details_dialog");
        $("button span:contains('Close')").parent().attr("data-name", "close");
        $("button span:contains('Cancel')").parent().attr("data-name", "cancel");
        $("button span:contains('OK')").parent().attr("data-name", "ok");
    }

    $(document).ready(function() {
        initClientAddDialog();
        initClientSelectDialog();
        initClientDetailsDialog();

        initClientsTable();
        initClientsGlobalTable();
        initClientCertsTable();

        refreshClients();

        $("#client_register").live('click', function() {
            var params = {
                member_class: $("#details_member_class").val(),
                member_code: $("#details_member_code").val(),
                subsystem_code: $("#details_subsystem_code").val()
            };
            confirm("clients.client_details_tab.send_regreq_confirm", null,
                    function() {
                $.post(action("client_regreq"), params, function(response) {
                    oClients.fnUpdate(response.data, oClients.getFocus());
                }, "json");
            });
        });

        $("#client_unregister").live('click', function() {
            var params = {
                member_class: $("#details_member_class").val(),
                member_code: $("#details_member_code").val(),
                subsystem_code: $("#details_subsystem_code").val()
            };
            confirm("clients.client_details_tab.send_delreq_confirm", null,
                    function() {
                $.post(action("client_delreq"), params, function(response) {
                    oClients.fnUpdate(response.data, oClients.getFocus());

                    confirmDelete("clients.client_details_tab.delete_client");
                }, "json");
            });
        });

        $("#client_delete").live('click', function() {
            confirmDelete("clients.client_details_tab.delete_client_confirm");
        });

        initTestability();
    });

    CLIENTS.getClientId = function() {
        return $("#details_client_id").val();
    };

}(window.CLIENTS = window.CLIENTS || {}, jQuery));
