var SDSB_PERIODIC_JOBS = function() {
    var executing = false;
    var lastStatusRefreshTime = null;

    /* -- PUBLIC - START -- */

    /**
     * Polls global configuration generation status and refreshes it every
     * minute.
     */
    function refreshCentralStatus() {
        if (canRefresh()) {
            lastStatusRefreshTime = new Date();
            executing = true;

            $.get("system_status/check_status", {}, function(response) {
                var errorMessages =
                        createErrorMessages(response.data.error_messages);
                showAlerts(errorMessages);
            }, "json");
        }

        var intervalInMilliseconds = 60000;
        window.setTimeout('SDSB_PERIODIC_JOBS.refreshCentralStatus()',
                intervalInMilliseconds);
    }

    function areExecuting() {
        return executing;
    }

    function clearExecution() {
        executing = false;
    }

    /* -- PUBLIC - END -- */

    function createErrorMessages(rawErrorMessages) {
        var result = []

        $.each(rawErrorMessages, function(idx, each) {
            var msg = each.text;

            if(each.signing_token_pin_required == true) {
                result.push({text: msg, link: getPinEnteringLink()});
            } else {
                result.push(msg);
            }
        });

        return result;
    }

    function getPinEnteringLink() {
        return $('<a>', {
                text: _("system_status.enter_softtoken_pin"),
                href: "#",
                class: "enter-signing-token-pin",
                click: function() {
                    openSigningTokenPinDialog();
                }});
    }

    function openSigningTokenPinDialog() {
        clearSigningTokenPin();

        $("#enter_pin_dialog").initDialog({
            title: _("system_status.enter_softtoken_pin"),
            autoOpen: false,
            modal: true,
            height: "auto",
            width: "auto",
            buttons: [
                { text: _("common.ok"),
                  click: function() {
                      enterSigningTokenPin(this);
                  }
                },
                { text: _("common.close"),
                  click: function() {
                      clearSigningTokenPin();
                      $(this).dialog("close");
                  }
                }
            ]
        }).dialog("open");
    }

    function enterSigningTokenPin(dialog) {
        var params = {pin: $("#pin").val()}
        clearSigningTokenPin();

        $.post("system_status/enter_signing_token_pin", params,
                function() {
            removeSigningTokenPinErrorMsg();
            $(dialog).dialog("close");
        }, "json");
    }

    function removeSigningTokenPinErrorMsg() {
        $(".enter-signing-token-pin").closest("p").remove();
        setTopPosition();
    }

    function clearSigningTokenPin() {
        $("#pin").val("");
    }

    function canRefresh() {
        if (lastStatusRefreshTime == null) {
            return true;
        }

        var timeLimit = new Date();
        timeLimit.setMinutes(timeLimit.getMinutes()-1);

        return lastStatusRefreshTime <= timeLimit;
    }

    $(document).ready(function() {
        refreshCentralStatus();

        // Enter event
        $("#pin").on("keypress", function(event) {
            var keycode = (event.keyCode ? event.keyCode : event.which);
            if(keycode == '13'){
                enterSigningTokenPin($("#enter_pin_dialog"));
            }
        });
    });

    return {
        refreshCentralStatus: refreshCentralStatus,
        areExecuting: areExecuting,
        clearExecution: clearExecution,
    };
}();
