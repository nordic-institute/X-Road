(function(PERIODIC_JOBS, $, undefined) {
    function refreshAlerts() {
        if (typeof XROAD_BACKUP == "undefined" || !XROAD_BACKUP.restoreInProgress()) {
            $.ajax({
                url: "/application/alerts",
                global: false,
                data: {
                    allowTimeout: true
                },
                success: function(response) {
                    showAlerts(response.alerts)
                },
                dataType: "json"
            });
        }

        window.setTimeout(refreshAlerts, 30000);
    }

    $(document).ready(function() {
        refreshAlerts();
    });

    PERIODIC_JOBS.refreshAlerts = refreshAlerts;

}(window.PERIODIC_JOBS = window.PERIODIC_JOBS || {}, jQuery));
