var XROAD_CONFIGURATION_MANAGEMENT = function() {

    var tabRefreshers = {
        "#trusted_anchors_tab": refreshTrustedAnchors
    };

    function initView() {
        $("#configuration_management_tabs").initTabs({
            activate: function(ev, ui) {
                hideTabActions();

                var href = $("a", ui.newTab).attr("href");
                if (href == "#source_tab") {
                    XROAD_CONFIGURATION_SOURCE.refresh();
                    return;
                }

                refreshTab(href);
            }
        });

        $("#configuration_management_tabs").tabs("option", "active", 0);
    }

    function refreshTab(tab) {
        tabRefreshers[tab].call(this);
    }

    function hideTabActions() {
        // Each tab is responsible for opening its own actions.
        $("#upload_trusted_anchor").hide();
    }

    /* -- Tab specific refreshing logic - start -- */

    function refreshTrustedAnchors() {
        XROAD_TRUSTED_ANCHORS.init();
    }

    /* -- Tab specific refreshing logic - end -- */

    $(document).ready(function() {
        initView();
    });
}();
