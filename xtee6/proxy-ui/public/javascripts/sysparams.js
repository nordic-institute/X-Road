var oDistributors, oTsps, oTspsApproved, oInternalSslCert;

function distributorAddCallback(response) {
    $("#distributor_add_dialog form")
        .attr("action", action("distributor_cert_load"));

    if (response.success) {
        oDistributors.fnClearTable();
        oDistributors.fnAddData(response.data);
        $("#distributor_add_dialog").dialog("close");
    }

    showMessages(response.messages);
    PERIODIC_JOBS.refreshAlerts();
}

function distributorCertLoadCallback(response) {
    if (response.success) {
        $('#distributor_add_dialog #subject').val(response.data.subject);
        $('#distributor_add_dialog #serial').val(response.data.serial);
    }

    showMessages(response.messages);
};

function populate() {
    $.get(action("distributors"), function(response) {
        oDistributors.fnClearTable();
        oDistributors.fnAddData(response.data);
    });

    $.get(action("tsps"), function(response) {
        oTsps.fnClearTable();
        oTsps.fnAddData(response.data);
    });

    $('#distributor_delete, #tsp_delete').disable();

    $.get(action("async_params"), function(response) {
        $('#async_period').val(response.data.base_delay);
        $('#async_max_period').val(response.data.max_delay);
        $('#async_parallel').val(response.data.max_senders);
    });

    $.get(action("internal_ssl_cert"), function(response) {
        oInternalSslCert.fnClearTable();
        if (response.data.hash) {
            oInternalSslCert.fnAddData(response.data);
            $("#cert_details, #export_internal_ssl_cert").enable();
        } else {
            $("#cert_details, #export_internal_ssl_cert").disable();
        }
    });
};

function initDialogs() {
    $('#distributor_add_dialog').initDialog({
        autoOpen: false,
        modal: true,
        width: 550,
        buttons: [
        {
            text: _("common.ok"),
            click: function() {
                $("#distributor_add_dialog form")
                    .attr("action", action("distributor_add"));
                $("#distributor_add_dialog form").submit();
            }
        },
        {
            text: _("common.cancel"),
            click: function() {
                $(this).dialog('close');
            }
        }
        ]
    });

    $('#tsp_add_dialog').initDialog({
        autoOpen: false,
        width: 550,
        modal: true,
        open: function() {
            oTspsApproved.fnAdjustColumnSizing();

            $.get(action("tsps_approved"), function(response) {
                oTspsApproved.fnClearTable();
                oTspsApproved.fnAddData(response.data);
            });
        },
        buttons: [
        {
            text: _("common.ok"),
            click: function() {
                var selected = oTspsApproved.getFocusData();
                var params = {
                    name: selected.name,
                    url: selected.url
                };

                $.post(action("tsp_add"), params, function(response) {
                    oTsps.fnClearTable();
                    oTsps.fnAddData(response.data);
                });

                $(this).dialog('close');
            }
        },
        {
            text: _("common.cancel"),
            click: function() {
                $(this).dialog('close');
            }
        }
        ]
    });

    $('#cert_details_dialog').initDialog({
        autoOpen: false,
        modal: true,
        width: 710,
        height: 580,
        open: function() {
            var dialog = this;

            $.get(action("internal_ssl_cert_details"), function(response) {
                $('#dump', dialog).text(response.data.dump).scrollTop(0);
                $('#hash', dialog).text(response.data.hash);
            }, 'json');
        },
        buttons: [
            {
                text: _("common.ok"),
                click: function() {
                    $(this).dialog('close');
                }
            }
        ]
    });
};

$(function() {
    var opts = scrollableTableOpts();
    opts.sDom = "t";
    opts.aoColumns = [
        { 'mData': 'url' },
        { 'mData': 'cert_subject' }
    ];

    oDistributors = $('#distributors').dataTable(opts);

    opts = scrollableTableOpts(200);
    opts.sDom = "t";
    opts.aoColumns = [
        { 'mData': 'name' },
        { 'mData': 'url' }
    ];

    oTsps = $('#tsps').dataTable(opts);

    opts = scrollableTableOpts(200);
    opts.sDom = "t";
    opts.aoColumns = [
        { 'mData': 'name' }
    ];

    oTspsApproved = $('#tsps_approved').dataTable(opts);

    opts = scrollableTableOpts(200);
    opts.sDom = "t";
    opts.aoColumns = [
        { 'mData': 'hash' }
    ];
    opts.oLanguage = {
        "sZeroRecords": _("common.zero_records_none")
    };

    oInternalSslCert = $('#internal_ssl_cert').dataTable(opts);

    populate();
    initDialogs();

    $('#distributors tbody tr').live('click', function() {
        oDistributors.setFocus(0, this);
        $('#distributor_delete').enable();
    });

    $('#tsps tbody tr').live('click', function() {
        oTsps.setFocus(0, this);
        $('#tsp_delete').enable();
    });

    $('#tsps_approved tbody tr').live('click', function() {
        oTspsApproved.setFocus(0, this);
    });

    $('#distributor_add').click(function() {
        $('#distributor_add_dialog input[type!=hidden]').val('');
        $('#distributor_add_dialog').dialog('open');
    });

    $('#distributor_delete').click(function() {
        var selected = oDistributors.getFocusData();
        var params = {
            url: selected.url,
            cert_subject: selected.cert_subject
        };

        confirm("sysparams.index.delete_distributor_confirm", null, function() {
            $.post(action("distributor_delete"), params, function(response) {
                oDistributors.fnClearTable();
                oDistributors.fnAddData(response.data);
                $('#distributor_delete').disable();

                PERIODIC_JOBS.refreshAlerts();
            });
        });
    });

    $('#tsp_add').click(function() {
        $('#tsp_add_dialog').dialog('open');
    });

    $('#tsp_delete').click(function() {
        var params = {
            name: oTsps.getFocusData().name
        };

        $.post(action("tsp_delete"), params, function(response) {
            oTsps.fnClearTable();
            oTsps.fnAddData(response.data);
        });
    });

    $('#async_edit').click(function() {
        var params = {
            base_delay: $('#async_period').val(),
            max_delay: $('#async_max_period').val(),
            max_senders: $('#async_parallel').val()
        };

        $.post(action("async_params_edit"), params);
    });

    $('#cert_details').click(function(event) {
        event.preventDefault();

        $('#cert_details_dialog').dialog('open');
    });

    $('#export_internal_ssl_cert').click(function() {
        location.href = action("internal_ssl_cert_export");
    });

    $('#generate_internal_ssl').click(function(event) {
        event.preventDefault();

        confirm("sysparams.index.generate_internal_ssl_confirm", null,
                function() {
            $.get(action("internal_ssl_generate"), function(response) {
                oInternalSslCert.fnClearTable();
                if (response.data.hash) {
                    oInternalSslCert.fnAddData(response.data);
                    $("#cert_details, #export_internal_ssl_cert").enable();
                }
            }, "json");
        });
    });
});
