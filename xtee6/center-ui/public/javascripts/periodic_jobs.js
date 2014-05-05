var periodicJobs = function() {
    var executing = false;
    /**
     * Polls global configuration generation status and refreshes it every minute.
     */
    function refreshGlobalConfGenStatus() {
        executing = true;
        $.get("/global_conf_gen_status/check_status", {}, function(response) {
            showGlobalConfGenStatus(response.data)
        }, "json");

        var intervalInMilliseconds = 60000;
        window.setTimeout('periodicJobs.refreshGlobalConfGenStatus()',
                intervalInMilliseconds);
    }

    function showGlobalConfGenStatus(responseData) {
        switch(responseData.status) {
        case "NO_STATUS_FILE":
            addGlobalConfGenError(_("global_conf_gen_status.no_status_file"))
            break;
        case "OUT_OF_DATE":
            var message = _("global_conf_gen_status.out_of_date",
                    [responseData.last_attempt_time]);

            addGlobalConfGenError(message);
            break;
        case "SUCCESS":
            removeGlobalConfGenError();
            break;
        case "FAILURE":
            var message = _("global_conf_gen_status.failure",
                    [responseData.last_attempt_time]);

            addGlobalConfGenError(message);
            break;
        }
    }

    function addGlobalConfGenError(message) {
        removeGlobalConfGenError();

        errorDiv = $("<div>", {id: "global_conf_gen_error",
            class: "error-external"});
        errorDiv.text(message);
        $("#header").prepend(errorDiv);
    }

    function removeGlobalConfGenError() {
        $("#global_conf_gen_error").remove();
    }

    function areExecuting() {
        return executing;
    }

    function clearExecution() {
        executing = false;
    }

    return {
        refreshGlobalConfGenStatus: refreshGlobalConfGenStatus,
        areExecuting: areExecuting,
        clearExecution: clearExecution
    };
}();

$(document).ready(function() {
    periodicJobs.refreshGlobalConfGenStatus();
});
