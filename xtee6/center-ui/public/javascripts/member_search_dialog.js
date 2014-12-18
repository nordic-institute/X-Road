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
            { "mData": "name" },
            { "mData": "member_code" },
            { "mData": "member_class" },
            { "mData": "subsystem_code" },
            { "mData": "sdsb_instance" },
            { "mData": "type" },
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
                aoData.push({
                    "name": "advancedSearchParams",
                    "value": JSON.stringify(
                            getAddableClientAdvancedSearchParams())
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
            onSuccess(oMemberSearch.fnGetData(this));
            $("#member_search_dialog").dialog("close");
        });
    }

    function getAddableClientAdvancedSearchParams() {
        return {
            name: $("#securityserver_client_name").val(),
            memberClass: $("#securityserver_client_class").val(),
            memberCode: $("#securityserver_client_code").val(),
            subsystem: $("#securityserver_client_subsystem_code").val()
        };
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
                      onSuccess(oMemberSearch.getFocusData());
                      $(this).dialog("close");
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
