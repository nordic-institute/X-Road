(function(MEMBER_SEARCH_DIALOG, $, undefined) {

    var oMemberSearch;

    function initMemberSearchTable(securityServerCode, onSuccess, allowEmptySelection) {
        var opts = defaultTableOpts();
        opts.bServerSide = true;
        opts.bDestroy = true;
        opts.bScrollCollapse = true;
        opts.bScrollInfinite = true;
        opts.sScrollY = "100px";
        opts.sDom = "<'dataTables_header'f<'clearer'>>tp";
        opts.aoColumns = [
            { "mData": "name", mRender: util.escape },
            { "mData": "member_code", mRender: util.escape },
            { "mData": "member_class", mRender: util.escape },
            { "mData": "subsystem_code", mRender: util.escape },
            { "mData": "xroad_instance", mRender: util.escape },
            { "mData": "type", mRender: util.escape },
        ];

        opts.sAjaxSource = "members/member_search";
        opts.fnDrawCallback = function() {
            if (!allowEmptySelection && !oMemberSearch.getFocus()) {
                $("#member_search_select").disable();
            }
        };

        if (securityServerCode) {
            opts.fnServerParams = function(aoData) {
                aoData.push({
                    "name": "securityServerCode",
                    "value": securityServerCode
                });
            };
        }

        opts.aaSorting = [[1, 'asc']];

        oMemberSearch = $("#member_search").dataTable(opts);
        oMemberSearch.fnSetFilteringDelay(600);

        oMemberSearch.on("click", "tbody tr", function(ev) {
            if (oMemberSearch.setFocus(0, this)) {
                if (!allowEmptySelection) {
                    $("#member_search_select").enable();
                }
            }
        });

        oMemberSearch.on("dblclick", "tbody tr", function(ev) {
            $("#member_search_dialog").dialog("close");
            onSuccess(oMemberSearch.fnGetData(this));
        });
    }

    MEMBER_SEARCH_DIALOG.open =
            function(securityServerCode, onSuccess, allowEmptySelection) {

        $("#member_search_dialog").initDialog({
            modal: true,
            title: _("members.search"),
            height: 400,
            minHeight: 400,
            width: 800,
            buttons: [
                { id: "member_search_select",
                  text: _("common.ok"),
                  click: function() {
                      $(this).dialog("close");
                      onSuccess(oMemberSearch.getFocusData());
                  }
                },
                { text: _("common.cancel"),
                  click: function() {
                      $(this).dialog("close");
                  }
                }
            ],
            open: function () {
                if (!allowEmptySelection) {
                    $("#member_search_select").disable();
                }

                initMemberSearchTable(
                    securityServerCode, onSuccess, allowEmptySelection);
            }
        });
    }

}(window.MEMBER_SEARCH_DIALOG = window.MEMBER_SEARCH_DIALOG || {}, jQuery));
