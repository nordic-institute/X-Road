/* common application-wide JavaScript functions */

$.fn.disable = function() {
    return this.attr("disabled", "true").addClass("ui-state-disabled");
};

$.fn.enable = function() {
    return this.removeAttr("disabled").removeClass("ui-state-disabled");
};

$.fn.initDialog = function(opts) {
    return this.dialog(opts).dialogExtend({
        "closable" : true,
        "maximizable" : true,
        "dblclick" : "maximize",
        "icons" : {
            "close" : "ui-icon-close"
        }
    });
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

$.fn.dataTableExt.oApi.fnReloadAjax = function ( oSettings, sNewSource, fnCallback, bStandingRedraw )
{
    if ( sNewSource !== undefined && sNewSource !== null ) {
        oSettings.sAjaxSource = sNewSource;
    }
 
    // Server-side processing should just call fnDraw
    if ( oSettings.oFeatures.bServerSide ) {
        this.fnDraw();
        return;
    }
 
    this.oApi._fnProcessingDisplay( oSettings, true );
    var that = this;
    var iStart = oSettings._iDisplayStart;
    var aData = [];
 
    this.oApi._fnServerParams( oSettings, aData );
 
    oSettings.fnServerData.call( oSettings.oInstance, oSettings.sAjaxSource, aData, function(json) {
        /* Clear the old information from the table */
        that.oApi._fnClearTable( oSettings );
 
        /* Got the data - add it to the table */
        var aData =  (oSettings.sAjaxDataProp !== "") ?
            that.oApi._fnGetObjectDataFn( oSettings.sAjaxDataProp )( json ) : json;
 
        for ( var i=0 ; i<aData.length ; i++ )
        {
            that.oApi._fnAddData( oSettings, aData[i] );
        }
         
        oSettings.aiDisplay = oSettings.aiDisplayMaster.slice();
 
        that.fnDraw();
 
        if ( bStandingRedraw === true )
        {
            oSettings._iDisplayStart = iStart;
            that.oApi._fnCalculateEnd( oSettings );
            that.fnDraw( false );
        }
 
        that.oApi._fnProcessingDisplay( oSettings, false );
 
        /* Callback user function - for event handlers etc */
        if ( typeof fnCallback == 'function' && fnCallback !== null )
        {
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
    $('#menu ul').hide();
    $('#menu li a').click(
        function() {
            $(this).next().slideToggle(0);
        }
    );
    $('#menu li a').each(function() {
        if ($(this).attr('href') == location.pathname) {
            $(this).addClass('active');
            $(this).parentsUntil('#menu').show();
        }
    });
}

function refreshMenu() {
    $.ajax({
        url: "/application/menu",
        global: false,
        success: function(data) {
            if (!data.redirect) {
                $("#menu").html(data);
                initMenu();
            }

            if (arePeriodicJobsExecuting()) {
                periodicJobs.clearExecution();
            }
        }
    });
}

function error(msg) {
    addMessage("error", msg);
}

function notice(msg) {
    addMessage("notice", msg);
}

function addMessage(type, msg) {
    if (msg == null || msg == "") {
        return;
    }

    var messages;
    if ($(".ui-dialog").is(":visible")) {
        messages = ".ui-dialog-buttonpane:visible:last ." + type;

        if ($(".ui-dialog-buttonpane ." + type).length == 0) {
            $(".ui-dialog-buttonpane").prepend(
                "<div class='" + type + " message'></div>");
        }
    } else {
        messages = "." + type;
    }

    $(messages).append(msg + "<br/>").show();
}

function clearMessages() {
    $(".message").empty();
    $(".message").hide();
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

function focusInput() {
    $("input[type=text]:first", this != window ? this : document).focus();
}

function action(name) {
    return $("#ctrl").val() + (name == "index" ? "" : "/" + name);
}

function redirect(path) {
    window.location.href = $("meta[name='root_path']").attr("content") + path;
}

/* default options for variable length dataTables */
function defaultOpts(onDraw, length) {
    if (!length) length = 10;
    return {
        "bSort": true,
        "bFilter": true,
        "bPaginate": true,
        "bAutoWidth": false,
        "sDom": "t<'dataTables_footer'fp<'clearer'>>",
        "sPaginationType": "full_numbers",
        "iDisplayLength": length,
        "oLanguage": {
            "sSearch": _('search'),
            "sZeroRecords": _('zero_records'),
            "oPaginate": {
                "sFirst": "&lt;&lt;",
                "sPrevious": "&lt;",
                "sNext": "&gt;",
                "sLast": "&gt;&gt;"
            }
        },
        "fnDrawCallback": function(oSettings) {
            // TODO: Remove adding empty rows altogether?
            // keeps constant table length by adding empty rows if needed
//            var tbody = "#" + oSettings.sTableId + " tbody";
//            var thead = "#" + oSettings.sTableId + " thead";
//            $(tbody + " tr:hidden").show();
//            var tr = "<tr><td class='dataTables_empty' colspan='"
//                + $(thead + " tr:first th").length + "'>&nbsp;</td></tr>";
//            var add = length - $(tbody + " tr").length;
//            for (var i = 0; i < add; i++) {
//                $(tbody).append(tr);
//            }
            if (onDraw) onDraw(oSettings);
        }
    }
}

function scrollableTableOpts(oDraw, length, maxY, maxX) {
    result = defaultOpts(oDraw, length);

    result['sScrollY'] = (maxY != null ? maxY : 200) + 'px';
    if (maxX != null)
        result['sScrollX'] = maxX + '%';
    return result;
}

function _(str, params) {
    return $.i18n._(str, params);
}

function confirm(text, params, success) {
    $("#confirm").html(_(text, params)).initDialog({
        autoOpen: true,
        modal: true,
        width: 400,
        buttons : {
            "Confirm" : function() {
                success();
                $(this).dialog("close");
            },
            "Cancel" : function() {
                $(this).dialog("close");
            }
        }
    });
}

function warning(text, params, success) {
    $("#warning").html(_(text, params)).initDialog({
        autoOpen: true,
        modal: true,
        buttons : {
            "Continue" : function() {
                success();
                $(this).dialog("close");
            },
            "Cancel" : function() {
                $(this).dialog("close");
            }
        }
    });
}

function alert(text, params, success) {
    $("#alert").html(_(text, params)).initDialog({
        autoOpen: true,
        modal: true,
        buttons : {
            "OK" : function() {
                if (success) {
                    success();
                }
                $(this).dialog("close");
            }
        }
    });
}

function isInputFilled(inputSelector) {
    var inputValue = inputSelector.val();
    return inputValue != null && inputValue.length > 0
}

$.ajaxPrefilter(function(options, originalOptions, jqXHR) {
    options.success = function(_data, _textStatus, _jqXHR) {
        if (_data.redirect) {
            alert("session_timed_out", null,
                function() {
                    window.location.href = _data.redirect;
                });
        } else if (_data.warning) {
            warning("warning", [_data.warning.text],
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
        text: _("advanced_search"),
        href: "#",
        click: onClick
    });
    advancedSearchLink.appendTo(filterElement);
}

function arePeriodicJobsExecuting() {
    return typeof periodicJobs != 'undefined' && periodicJobs.areExecuting();
}

$(document).ajaxSend(function(ev, xhr) {
    xhr.setRequestHeader("X-CSRF-Token",
            $("meta[name=csrf-token]").attr("content"));
});

$(document).ajaxStart(function(ev, xhr) {
    if (!arePeriodicJobsExecuting()) {
        clearMessages();
    }

    $("body").addClass("wait");
});

$(document).ajaxStop(function(ev, xhr) {
    $("body").removeClass("wait");
    refreshMenu();
});

$(document).ajaxSuccess(function(ev, xhr, opts) {
    if (opts.dataType != "json") {
        return;
    }

    var response = $.parseJSON(xhr.responseText);

    if (!arePeriodicJobsExecuting()) {
        showMessages(response.messages);
    }
});

$(document).ajaxError(function(ev, xhr) {
    clearMessages();
    error(xhr.responseText);
});

$(document).on("dialogclose", ".ui-dialog", function() {
    clearMessages();
});

$(document).ready(function() {
    initMenu();

    // page heading = active menu item
    var active = $("#menu .active");
    if (active.length > 0) {
        $("#heading:empty").html(active.html());
    } else {
        $("#heading:empty").parent().hide();
    }

    // let's not submit forms with ENTER
    $(document).on("keydown", "form", function(e) {
        return e.which !== 13;
    });

    // correct thead alignment with tbody on browser zoom
    $(window).resize(function() {
        $(".dataTable:has(tbody)").each(function() {
            $(this).dataTable().fnAdjustColumnSizing();
        });
    });
});
