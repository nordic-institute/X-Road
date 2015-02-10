var SDSB_PERIODIC_JOBS = function() {
    function refreshAlerts() {
        if (typeof SDSB_BACKUP == "undefined" || !SDSB_BACKUP.restoreInProgress()) {
            $.ajax({
                url: "/system_status/check_status",
                global: false,
                success: function(response) {
                    showAlerts(response.alerts);
                },
                dataType: "json"
            });
        }

        window.setTimeout(refreshAlerts, 30000);
    }

    $(document).ready(function() {
        refreshAlerts();
    });

    return {
        refreshAlerts: refreshAlerts
    };
}();
