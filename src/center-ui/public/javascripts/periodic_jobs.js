var XROAD_PERIODIC_JOBS = function() {
    function refreshAlerts() {
        if (typeof XROAD_BACKUP == "undefined" || !XROAD_BACKUP.restoreInProgress()) {
            $.ajax({
                url: "/system_status/check_status",
                global: false,
                data: {
                    allowTimeout: true
                },
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
