/* common application-wide JavaScript functions */

var DEFAULT_DISPLAY_LENGTH = 100;

function can(privilege) {
    return PRIVILEGES.indexOf(privilege) != -1;
}

$.fn.disable = function() {
    return this.attr("disabled", "true").addClass("ui-state-disabled");
};

$.fn.enable = function() {
    return this.removeAttr("disabled").removeClass("ui-state-disabled");
};

$.fn.initDialog = function(opts) {
    var dialog = this;

    dialog.on("dialogcreate", function() {
        var maximize = $('<button/>')
            .append($('<i/>').addClass('fa fa-arrows fa-rotate-45'))
            .addClass('ui-action ui-action-maximize')
            .click(function() {
                var options = dialog.dialog("option");
                delete options.buttons;

                if (options["maximized"]) {
                    options["width"] = options["originalWidth"];
                    options["height"] = options["originalHeight"];
                    options["position"] = options["originalPosition"];
                    options["maximized"] = false;
                } else {
                    options["originalWidth"] = options["width"];
                    options["originalHeight"] = options["height"];
                    options["originalPosition"] = options["position"];

                    options["width"] = $(window).width();
                    options["height"] = $(window).height() - $("#main").position().top;
                    options["position"] = {
                        my: "left top",
                        at: "left top",
                        of: $("#main")
                    };
                    options["maximized"] = true;
                }

                dialog.dialog("option", options);
                dialog.trigger("dialogresizestop");
            });

        var close = $('<button/>')
            .append($('<i/>').addClass('fa fa-times'))
            .addClass('ui-action ui-action-close')
            .click(function() {
                dialog.dialog("close");
            });

        var defaultActions = $('<div/>')
            .addClass('dialog-buttonbar')
            .append(maximize)
            .append(close);

        dialog.siblings(".ui-dialog-titlebar")
            .append(defaultActions);
    });

    dialog.on("dialogopen", function() {
        dialog.trigger("dialogresizestop");
    });

    dialog.on("dialogresizestop", function() {
        // Dialog has a new size, let's find the best size for the
        // datatables within.

        $(".dataTables_scrollBody .dataTable:visible", this).each(function() {
            var table = $(this).dataTable();
            var tableHeader = $(this).closest(".dataTables_wrapper")
                .find(".dataTables_header");
            var dialogContent = $(this).closest(".ui-dialog-content");

            // clean old width properties of table header to stretch it
            tableHeader.css("width", "auto");

            // store the heights before measuring...
            var contentHeight = dialogContent.outerHeight();
            var cssHeight = dialogContent.css("height");
            var cssMinHeight = dialogContent.css("min-height");

            // ...measure the height of everything except table rows
            // in dialog content
            $(this).closest(".dataTables_scrollBody").css("height", 0);
            dialogContent.css("min-height", 0).css("height", "auto");
            var nonRowsHeight = dialogContent.outerHeight();

            // restore the height of dialog
            dialogContent.css("height", cssHeight);
            dialogContent.css("min-height", cssMinHeight);

            // set table to use available height
            table.fnSettings().oScroll.sY =
                Math.max(100, contentHeight - nonRowsHeight);

            table.fnAdjustColumnSizing(false);
        });
    });

    opts.buttons = $.grep(opts.buttons, function(val, idx) {
        if (val.privilege == null || can(val.privilege)) {
            return true;
        }

        return false;
    });

    return this.dialog(opts);
};

$.fn.initTabs = function(opts) {
    var tabs = this;
    var titleBar = tabs.closest(".ui-dialog").find(".ui-dialog-titlebar");

    tabs.on("tabscreate", function() {
        $(".ui-tabs-panel-actions", this).each(function() {
            var tab = $(this).closest(".ui-tabs-panel").attr("id");
            $(this)
                .attr("id", tab + "_actions")
                .appendTo(titleBar);
        });
    });

    tabs.on("tabsactivate", function(event, ui) {
        tabs.tabs("option", "collapsible", false);

        $(".ui-tabs-panel-actions", titleBar).hide();

        // check if tabs are in a dialog
        if (!tabs.closest(".ui-dialog").length > 0) {
            return;
        }

        $($("a", ui.newTab).attr("href") + "_actions", titleBar).show()
            .position({
                my: "right center",
                at: "left center",
                of: titleBar.find(".dialog-buttonbar")
            });

        tabs.closest(".ui-dialog-content").trigger("dialogresizestop");
    });

    // All tabs closed on create, so that we always get the
    // tabsactivate event on first opening.
    opts.collapsible = true;
    opts.active = false;

    return this.tabs(opts);
};

$.fn.dataTableExt.oApi.enable = function() {
    $(this).removeClass("disabled");
}

$.fn.dataTableExt.oApi.disable = function() {
    $(this).addClass("disabled");
}

$.fn.dataTableExt.oApi.isEnabled = function() {
    return !$(this).hasClass("disabled");
}

$.fn.dataTableExt.oApi.removeFocus =
    function(oSettings) {
        var nodes = this.fnGetNodes();
        for (var i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                $(nodes[i]).removeClass("row_selected");
            }
        }

        // manually added rows, like group headers
        $("tr", oSettings.nTable).removeClass("row_selected");

        return true;
    }

$.fn.dataTableExt.oApi.setFocus =
    function(oSettings, minSize, row, multiselect) {
        if (oSettings.fnRecordsDisplay() <= (minSize ? minSize : 0)
            || (row && $(".dataTables_empty", row).length > 0)
            || !this.isEnabled()) {
            return false;
        }

        if (!multiselect) {
            this.removeFocus();
        }

        if (row) {
            $(row, this).toggleClass("row_selected");

            var rowId = $(row, this).data("id");
            if (typeof rowId != 'undefined') {
                $(this).data("selectedRowId", rowId);
            }
        } else {
            $("tbody tr:first", this).addClass("row_selected");
        }

        return true;
    }

$.fn.dataTableExt.oApi.getFocus =
    function(oSettings) {
        return $(".row_selected", this).get(0);
    }

/**
 * Returns data as an array from all selected dataTable rows.
 */
$.fn.dataTableExt.oApi.getSelectedData =
    function() {
        var table = this;
        var result = [];
        $.each($(".row_selected", this), function(index, element) {
            result.push(table.fnGetData(element));
        });
        return result;
    }

$.fn.dataTableExt.oApi.getFocusData =
    function(oSettings) {
        return this.fnGetData(this.getFocus());
    }

$.fn.dataTableExt.oApi.fnSetFilteringDelay = function ( oSettings, iDelay ) {
    var _that = this;

    if ( iDelay === undefined ) {
        iDelay = 250;
    }

    this.each( function ( i ) {
        $.fn.dataTableExt.iApiIndex = i;
        var
            $this = this,
            oTimerId = null,
            sPreviousSearch = null,
            anControl = $( 'input', _that.fnSettings().aanFeatures.f );

            anControl.unbind( 'keyup' ).bind( 'keyup', function() {
            var $$this = $this;

            if (sPreviousSearch === null || sPreviousSearch != anControl.val()) {
                window.clearTimeout(oTimerId);
                sPreviousSearch = anControl.val();
                oTimerId = window.setTimeout(function() {
                    $.fn.dataTableExt.iApiIndex = i;
                    _that.fnFilter( anControl.val() );
                }, iDelay);
            }
        });

        return this;
    } );
    return this;
};

$.fn.dataTableExt.oApi.fnReloadAjax = function(oSettings, sNewSource,
        fnCallback, bStandingRedraw) {

    if (sNewSource !== undefined && sNewSource !== null) {
        oSettings.sAjaxSource = sNewSource;
    }

    // Server-side processing should just call fnDraw
    if (oSettings.oFeatures.bServerSide) {
        this.fnDraw();
        return;
    }

    this.oApi._fnProcessingDisplay(oSettings, true);
    var that = this;
    var iStart = oSettings._iDisplayStart;
    var aData = [];

    this.oApi._fnServerParams(oSettings, aData);

    oSettings.fnServerData.call(oSettings.oInstance, oSettings.sAjaxSource,
            aData, function(json) {

        /* Clear the old information from the table */
        that.oApi._fnClearTable(oSettings);

        /* Got the data - add it to the table */
        var aData = (oSettings.sAjaxDataProp !== "") ?
            that.oApi._fnGetObjectDataFn(oSettings.sAjaxDataProp)(json) : json;

        for (var i = 0; i < aData.length; i++) {
            that.oApi._fnAddData(oSettings, aData[i]);
        }

        oSettings.aiDisplay = oSettings.aiDisplayMaster.slice();

        that.fnDraw();

        if (bStandingRedraw === true) {
            oSettings._iDisplayStart = iStart;
            that.oApi._fnCalculateEnd(oSettings);
            that.fnDraw(false);
        }

        that.oApi._fnProcessingDisplay(oSettings, false);

        /* Callback user function - for event handlers etc */
        if (typeof fnCallback == 'function' && fnCallback !== null) {
            fnCallback( oSettings );
        }
    }, oSettings );
};

$.fn.dataTableExt.oApi.hasSelectedRows =
    function() {
        return $(".row_selected", this).length > 0;
    }

$.fn.dataTableExt.oApi.fnFilterClear = function(oSettings) {
    /* Remove global filter */
    oSettings.oPreviousSearch.sSearch = "";

    /* Remove the text of the global filter in the input boxes */
    if (typeof oSettings.aanFeatures.f != 'undefined') {
        var n = oSettings.aanFeatures.f;
        for (var i = 0, iLen = n.length; i < iLen; i++) {
            $('input', n[i]).val('');
        }
    }

    /* Remove the search text for the column filters */
    for (var i = 0, iLen = oSettings.aoPreSearchCols.length; i < iLen; i++) {
        oSettings.aoPreSearchCols[i].sSearch = "";
    }

    this.fnDraw();
};

$.fn.dataTableExt.oApi.fnReplaceData = function(oSettings, aoData) {
    this.fnClearTable(aoData == null || aoData.length == 0);
    this.fnAddData(aoData);
};

$.fn.serializeObject = function() {
    var o = {};
    var a = this.serializeArray();

    $.each(a, function() {
        if (o[this.name] !== undefined) {
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
        } else {
            o[this.name] = this.value || '';
        }
    });
    return o;
};

String.prototype.containsIgnoreCase = function(searchable) {
    return this.toLowerCase().indexOf(searchable.toLowerCase()) != -1;
};

function initMenu() {
    $('.menu li a').each(function() {
        if ($(this).attr('href') == location.pathname) {
            $(this).parent().addClass('active');
        }
    });
}

function error(msg) {
    addMessage("error", msg);
}

function notice(msg) {
    addMessage("notice", msg);
}

function addMessage(type, message) {
    if (message == null || message == "") {
        return;
    }

    var messageContainer = "." + type;

    if ($(".ui-dialog:visible").length > 0) {
        var buttonPane = ".ui-dialog-buttonpane:visible:last ";
        messageContainer = buttonPane + messageContainer;

        if ($(messageContainer).length == 0) {
            $("<div>")
                .addClass(type)
                .addClass("message")
                .prependTo(buttonPane);
        }
    }

    $(messageContainer).append(message + "<br/>");
}

function clearMessages() {
    $(".message").empty();
}

function showMessages(messages) {
    clearMessages();

    if (!messages) {
        return;
    }

    $.each(messages, function(i, type) {
        $.each(type[1], function(i, message) {
            addMessage(type[0], message);
        });
    });
}

function showAlerts(alerts) {
    if (!alerts) {
        return;
    }

    $(".alerts").html("");

    $.each(alerts, function(idx, val) {
        var text = val;
        var link = null;

        if (typeof val == 'object') {
            text = val.text;
            link = val.link;
        }

        var newAlert = $("<p>");
        $(".alerts").append(newAlert.text(text));

        if (link != null) {
            newAlert.append(" ");
            newAlert.append(link);
        }
    });

    setTopPosition();
}

function setTopPosition() {
    var top = $('#server-info').height() +
        ( $('.alerts').is(':empty') ? $('.alerts').height() : $('.alerts').outerHeight() );
    $('#main').css('top', top);
    $('#user-menu').css('top', top);
}

function focusInput() {
    $("input[type=text]:first", this != window ? this : document).focus();
}

function action(name) {
    return $("#ctrl").val() + (name == "index" ? "" : "/" + name);
}

function redirect(path) {
    window.location.href = $("meta[name='root_path']").attr("content") + path;
}

function registerCallbacks(oSettings) {
    var table = $(oSettings.nTable).dataTable();

    if (table.data("callbacksRegistered")) {
        return;
    }

    oSettings.aoRowCallback.push({
        "sName": "calculateRowId",
        "fn": function(nRow, oData) {
            if (typeof oSettings.oInit.asRowId == 'undefined') {
                return;
            }

            var idParts = [];
            $.each(oSettings.oInit.asRowId, function(idx, val) {
                idParts.push(oData[val]);
            });

            $(nRow).data("id", idParts.join(";"));
        }
    });

    oSettings.aoPreDrawCallback.push({
        "sName": "saveScrollPosition",
        "fn": function(oSettings) {
            table.data("scrollPosition",
                       table.closest(".dataTables_scrollBody").scrollTop());
        }
    });

    oSettings.aoDrawCallback.unshift({
        "sName": "restoreScrollPosition",
        "fn": function(oSettings) {
            table.closest(".dataTables_scrollBody").scrollTop(
                table.data("scrollPosition"));
        }
    });

    oSettings.aoDrawCallback.push({
        "sName": "restoreSelectedRow",
        "fn": function(oSettings) {
            var selectedRowId = table.data("selectedRowId");

            if (typeof selectedRowId == 'undefined') {
                return;
            }

            $("tbody tr:visible", oSettings.nTable).each(function(idx, val) {
                if ($(val).data("id") == selectedRowId) {
                    table.setFocus(0, val);
                    return false;
                }
            });
        }
    });

    table.data("callbacksRegistered", true);
}

$.extend($.fn.dataTable.defaults, {
    "fnInitComplete": function(oSettings) {
        registerCallbacks(oSettings);
    }
});

/* default options for dataTables */
function defaultTableOpts() {
    return {
        "bSort": true,
        "bFilter": true,
        "bPaginate": true,
        "bAutoWidth": false,
        "sDom": "t<'dataTables_footer'fp<'clearer'>>",
        "sPaginationType": "full_numbers",
        "iDisplayLength": DEFAULT_DISPLAY_LENGTH,
        "oLanguage": {
            "sSearch": _("common.search"),
            "sZeroRecords": _("common.zero_records"),
            "oPaginate": {
                "sFirst": "&lt;&lt;",
                "sPrevious": "&lt;",
                "sNext": "&gt;",
                "sLast": "&gt;&gt;"
            }
        },
        // Adds registerCallbacks() to make the callbacks available on
        // first draw in case of ajax source. Also triggers
        // 'dialogresizestop' on success.
        "fnServerData": function(sUrl, aoData, fnCallback, oSettings) {
            registerCallbacks(oSettings);

            oSettings.jqXHR = $.ajax({
                "url":  sUrl,
                "data": aoData,
                "success": function(json) {
                    if (json.sError) {
                        oSettings.oApi._fnLog(oSettings, 0, json.sError);
                    }

                    $(oSettings.oInstance).trigger('xhr', [oSettings, json]);
                    fnCallback(json);

                    var dialog =
                        oSettings.oInstance.closest(".ui-dialog-content");

                    if (dialog.length > 0) {
                        // in case data arrives after dialog is opened
                        dialog.trigger("dialogresizestop");
                    }
                },
                "dataType": "json",
                "cache": false,
                "type": oSettings.sServerMethod,
                "error": function (xhr, error, thrown) {
                    if (error == "parsererror") {
                        oSettings.oApi._fnLog(oSettings, 0,
                            "DataTables warning: JSON data from " +
                            "server could not be parsed. This is caused " +
                            "by a JSON formatting error.");
                    }
                }
            });
        }
    }
}

function scrollableTableOpts(maxY) {
    var result = defaultTableOpts();

    result.bPaginate = false;
    result.bScrollCollapse = true;
    result.sScrollY = maxY != null ? maxY : 200;

    return result;
}

function _(str, params) {
    return I18n.t(str, params);
}

function confirm(text, params, success) {
    var title = _(text + "_title", {
        defaultValue: _("layouts.application.confirm_title")
    });

    $("#confirm").html(_(text, params)).initDialog({
        title: title,
        autoOpen: true,
        modal: true,
        width: "auto",
        minWidth: 500,
        buttons: [
            { text: _("common.confirm"),
              id: "confirm",
              click: function() {
                  success();
                  $(this).dialog("close");
              }},
            { text: _("common.cancel"),
              click: function() {
                  $(this).dialog("close");
              }}
        ]
    });
}

function yesno(text, params, success) {
    var title = _(text + "_title", {
        defaultValue: _("layouts.application.yesno_title")
    });

    $("#yesno").html(_(text, params)).initDialog({
        title: title,
        autoOpen: true,
        modal: true,
        width: "auto",
        minWidth: 500,
        buttons: [
            { text: _("common.yes"),
              click: function() {
                  success(true);
                  $(this).dialog("close");
              }},
            { text: _("common.no"),
              click: function() {
                  success(false);
                  $(this).dialog("close");
              }}
        ]
    });
}

function warning(text, params, success) {
    var title = _(text + "_title", {
        defaultValue: _("layouts.application.warning_title")
    });

    $("#warning").html(_(text, params)).initDialog({
        title: title,
        autoOpen: true,
        modal: true,
        width: "auto",
        minWidth: 500,
        buttons: [
            { text: _("common.continue"),
              click: function() {
                  success();
                  $(this).dialog("close");
              }},
            { text: _("common.cancel"),
              click: function() {
                  $(this).dialog("close");
              }}
        ]
    });
}

function alert(text, params, success) {
    var title = _(text + "_title", {
        defaultValue: _("layouts.application.alert_title")
    });

    $("#alert").html(_(text, params)).initDialog({
        title: title,
        autoOpen: true,
        modal: true,
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  $(this).dialog("close");
              }}
        ],
        close: success
    });
}

// -- Functionality related to console output - start

function openConsoleOutputDialog(dialogTitle, dialogHeight) {
    var height = dialogHeight != null ? dialogHeight : "600";

    $("#console_output_dialog").initDialog({
        autoOpen: false,
        modal: true,
        title: dialogTitle,
        height: height,
        width: "900px",
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  $(this).dialog("close");
              }
            }
        ]
    }).dialog("open");
}


function fillConsoleOutputDialog(consoleOutput) {
    var consoleDiv = $("#command_console_output");
    consoleDiv.find("p").remove();

    for (var i in consoleOutput) {
        consoleDiv.append($('<p/>', {text: consoleOutput[i]}));
    }
}

function initConsoleOutput(consoleOutput, dialogTitle, dialogHeight) {
    fillConsoleOutputDialog(consoleOutput);
    openConsoleOutputDialog(dialogTitle, dialogHeight);
}

// -- Functionality related to console output - end

function isInputFilled(inputSelector) {
    var inputValue = inputSelector.val();
    return inputValue != null && inputValue.length > 0
}

$.ajaxPrefilter(function(options, originalOptions, jqXHR) {
    options.success = function(_data, _textStatus, _jqXHR) {
        if (_data.redirect) {
            alert(_data.reason, null, function() {
                window.location.href = _data.redirect;
            });
        } else if (_data.warning) {
            warning("common.warning", {text: _data.warning.text},
                function() {
                    if (!originalOptions.data.ignore) {
                        originalOptions.data.ignore = [];
                    }
                    originalOptions.data.ignore.push(_data.warning.code);
                    $.ajax(originalOptions);
                });
        } else if (originalOptions.success) {
            originalOptions.success(_data, _textStatus, _jqXHR);
        };
    };
});

function getTableWrapper(tableId) {
    return $("#" + tableId + "_wrapper");
}

function isTableVisible(tableId){
    return getTableWrapper(tableId).is(":visible");
}
/**
 * Toggles visibility of the table and alters text of toggling button.
 *
 * @param toggleButton - javascript button element, text of it will be toggled
 * from "+" to "-" when opening table, vice versa when closing.
 * @param tableId - table id attribute value in HTML (string).
 *
 * @returns true if table is going to be opened.
 */
function toggleTableVisibility(toggleButton, tableId) {
    var toggleButtonText = isTableVisible(tableId) ? "+" : "-";
    toggleButton.text(toggleButtonText);
    getTableWrapper(tableId).toggle();
}

function addAdvancedSearchLink(filterId, onClick) {
    var filterElement = $("#" + filterId);

    var advancedSearchLink = $("<a>", {
        text: _("common.advanced_search"),
        href: "#",
        click: onClick
    });
    advancedSearchLink.appendTo(filterElement);
}

$(document).ajaxSend(function(ev, xhr) {
    xhr.setRequestHeader("X-CSRF-Token",
            $("meta[name=csrf-token]").attr("content"));
});

$(document).ajaxStart(function(ev, xhr) {
    clearMessages();
    $("body").addClass("wait");
});

$(document).ajaxStop(function(ev, xhr) {
    $("body").removeClass("wait");
});

$(document).ajaxSuccess(function(ev, xhr, opts) {
    if (opts.dataType != "json") {
        return;
    }

    var response = $.parseJSON(xhr.responseText);

    if (response.skipMessages) {
        return;
    }

    showMessages(response.messages);
});

$(document).ajaxError(function(ev, xhr) {
    clearMessages();
    error(xhr.responseText);
});

$(document).on("dialogclose", ".ui-dialog", function() {
    clearMessages();
});

// Default uploadCallback
function uploadCallback(response) {
    showMessages(response.messages);
}

function getTableRowButton(buttonText, onClick) {
    return $('<button/>', {
        text: buttonText,
        class: 'right',
        click: onClick
    });
}

function removeTableRowButtons(row) {
    row.find("button").remove();
}

function appendButtonToRow(row, button) {
    row.find("td:last").append(button);
}

function initLocaleSelectDialog() {
    $("#locale_select_dialog").initDialog({
        autoOpen: false,
        modal: true,
        width: 250,
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  var dialog = this;
                  var params = $("#locale", this).serialize();

                  $.post(action("set_locale"), params, function() {
                      location.reload();
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

    $("#locale_select").click(function() {
        $("#locale_select_dialog").dialog("open");
    });
}

function activateToken(tokenId, onSuccess, onFail) {
    $("#activate_token_pin").val("");

    $("#activate_token_dialog").initDialog({
        title: _("common.enter_pin"),
        autoOpen: true,
        modal: true,
        height: 200,
        width: 400,
        buttons: [
            { text: _("common.ok"),
              click: function() {
                  var self = this;
                  var params = {
                      token_id: tokenId,
                      pin: $("#activate_token_pin").val()
                  };

                  $.post("/application/activate_token", params, function() {
                      $(self).dialog("close");

                      if (onSuccess != null) {
                          onSuccess();
                      }
                  }, "json").fail(onFail);
              }
            },
            { text: _("common.close"),
              click: function() {
                  $("#activate_token_pin").val("");
                  $(this).dialog("close");
              }
            }
        ],
        close: function() {
            $(this).dialog("destroy");
        }
    });
}

function deactivateToken(tokenId, onSuccess) {
    $.post("/application/deactivate_token", {
        token_id: tokenId
    }, onSuccess, "json");
}

$(document).ready(function() {
    initMenu();

    // page heading = active menu item
    var active = $("#menu .active");
    if (active.length > 0) {
        $("#heading:empty").html(active.html());
    } else {
        $("#heading:empty").parent().hide();
    }

    // submit only search forms with ENTER
    $(document).on("keydown", "form", function(e) {
        if (e.which == 13) {
            $(this).find(".search").click();
        }

        return e.which !== 13;
    });

    // Manually increase the size of scrollBody to accomodate the
    // increased size of the table after selecting a row (which makes
    // clipped data visible for that row). Redrawing the whole table
    // would cause several troubles.
    $(document).on("click", ".dataTables_scrollBody", function() {
        var table = $("table", this).dataTable();
        var tableHeight = $("table", this).height();
        var visibleHeight = $(this).height();

        if (tableHeight > visibleHeight) {
            $(this).css("height", Math.min(
                tableHeight, table.fnSettings().oScroll.sY));
        }
    });

    $('.' + $('#ctrl').val() + '_actions').find('button').each(function() {
        var clone = $(this).clone();
        $(this).remove();
        $('.button-group').append($('<li/>').addClass('left').html(clone));
    });

    $('#server-names h2').text($('#server-names h2').text().replace(' Administration', ''));
    if($('#server-names h1').text() == '')
        $('#server-names h2').addClass('big');

    if($('#user h2').text() == '')
        $('#user h1').addClass('big');

    $('input[readonly]').focus(function(){
        this.blur();
    });

    initLocaleSelectDialog();

    $(document).tooltip({
        content: function() {
            return $(this).attr("title");
        }
    });
});
