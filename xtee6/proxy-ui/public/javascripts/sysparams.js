var oGlobalconf, oTimestampServices, oTimestamps;

function reloadDistData(response) {
    oGlobalconf.fnClearTable();
    if(response == null) {
        oGlobalconf.fnAddData(response.data);
    } else {
        $.get(action('get_distributors'), function(response) {
            oGlobalconf.fnAddData(response.data);
        });
    }
    
    $("#globalconf_dists_add").dialog('close');
    $('#dists_remove').disable();
}

function certUploadCallback(response) {
    if(response.success)
    {
        $('#dist_dn').val(response.data.issuer);
        $('#dist_serial').val(response.data.serial_number);
        $('#cert_id').val(response.data.temp_cert_id);
        $('#globalconf_add_ok').enable();
    }
};

function updateAsync(response) {
    $.get(action('get_async_requests'), function(response) {
        $('#async_period').val(response.data.base_delay);
        $('#async_max_period').val(response.data.max_delay);
        $('#async_parallel').val(response.data.max_senders);
    });
};

function populate() {
    $.get(action('get_distributors'), function(response) {
        oGlobalconf.fnClearTable();
        oGlobalconf.fnAddData(response.data);
    });

    $.get(action('get_timestamping_services'), function(response) {
        oTimestampServices.fnClearTable();
        oTimestampServices.fnAddData(response.data);
    });

    $.get(action('get_timestamps'), function(response) {
        oTimestamps.fnClearTable();
        oTimestamps.fnAddData(response.data);
    });

    $('#dists_remove, #timestamp_edit, #timestamp_remove').disable();

    updateAsync();
    updateCertDetails();
};

function updateCertDetails() {
    $.get(action('get_cert'), function(response) {
        $('#fingerprint').val(response.data.fingerprint);
    });
};

function initDialogs() {
    $('#globalconf_dists_add').initDialog({
        autoOpen: false,
        modal: true,
        height: 280,
        width: 460,
        buttons: [
        {
            text: _('ok'),
            id: 'globalconf_add_ok',
            click: function() {
                var params = {
                    dist_address: $('#dist_address').val(),
                    dist_certificate: $('#cert_id').val(),
                    dist_dn: $('#dist_dn').val(),
                    dist_serial: $('#dist_serial').val()
                }
                $.post(action('add_distributor'), params, function(response) {
                    reloadDistData(response);
                });
            },
            disabled: true
        },
        {
            text: _('cancel'),
            click: function() {
                $(this).dialog('close');
            }
        }
        ]
    });
    $('#timestamp_service_add').initDialog({
        autoOpen: false,
        modal: true,
        open: function() {
            oTimestamps.fnAdjustColumnSizing();
        },
        buttons: [
        {
            text: _('ok'),
            click: function() {
                var params = {
                    tsp_name: oTimestamps.getFocusData().tsp_name
                };
                $.post(action('add_timestamping_service'), params, function(data) {
                    populate();
                });
                $(this).dialog('close');
            }
        },
        {
            text: _('cancel'),
            click: function() {
                $(this).dialog('close');
            }
        }
        ]
    });
    $('#edit_timestamp_url').initDialog({
        autoOpen: false,
        modal: true,
        buttons: [
        {
            text: _('ok'),
            click: function() {
                var params = {
                    tsp_name: oTimestampServices.getFocusData().tsp_name,
                    tsp_url: $('#tsp_url').val()
                };
                $.post(action('edit_timestamping_service'), params, function(ret) {
                    populate();
                });
                $(this).dialog('close');
            }
        },
        {
            text: _('cancel'),
            click: function() {
                $(this).dialog('close');
            }
        }
        ]
    });
    $('#cert_details').initDialog({
        autoOpen: false,
        modal: true,
        width: 515,
        height: 600,
        open: function() {
            $.get(action('cert_details'), function(response) {
                $('#cert_dump').text(response.data.cert_dump);
                $('#cert_hash').text(response.data.cert_hash);
            });
        },
        buttons: [
            {
                text: _('ok'),
                click: function() {
                    $(this).dialog('close');
                }
            }
        ]
    });
};

$(function() {
    var opts = defaultOpts(null, 1);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.aoColumns = [
        { 'mData': 'url' },
        { 'mData': 'certificate' }
    ];

    oGlobalconf = $('#globalconf_dist').dataTable(opts);

    opts = defaultOpts(null, 1);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.aoColumns = [
        { 'mData': 'tsp_name' },
        { 'mData': 'tsp_url' }
    ];

    oTimestampServices = $('#timestamp_services').dataTable(opts);

    opts = defaultOpts(null, 1);
    opts.bPaginate = false;
    opts.sDom = "<'dataTables_header'<'clearer'>>t";
    opts.aoColumns = [
        { 'mData': 'tsp_name' }
    ];

    oTimestamps = $('#timestamps').dataTable(opts);

    populate();
    initDialogs();

    $('#globalconf_dist tbody tr').live('click', function() {
        oGlobalconf.setFocus(0, this);
        $('#dists_remove').enable();
    });

    $('#timestamp_services tbody tr').live('click', function() {
        oTimestampServices.setFocus(0, this);
        $('#timestamp_edit, #timestamp_remove').enable();
    });

    $('#timestamps tbody tr').live('click', function() {
        oTimestamps.setFocus(0, this);
    });

    $('#dists_add').live('click', function() {
        $('#globalconf_dists_add').dialog('open');
    });

    $('#dists_remove').live('click', function() {
        var params = {
            dist_address: oGlobalconf.getFocusData().url,
            dist_certificate: oGlobalconf.getFocusData().certificate
        };
        $.post(action('delete_distributor'), params, function(response) {
            reloadDistData(response);
        });
    });

    $('#load_cert').live('click', function() {
        $('#dists').submit();
    });

    $('#timestamp_add').live('click', function() {
        $('#timestamp_service_add').dialog('open');
    });

    $('#timestamp_edit').live('click', function() {
        $('#edit_timestamp_url').dialog('open');
    });

    $('#timestamp_remove').live('click', function() {
        var params = {
            tsp_name: oTimestampServices.getFocusData().tsp_name
        };
        $.post(action('delete_timestamping_service'), params, function(data) {
            populate();
        });
    });

    $('#async_edit').click(function() {
        var params = {
            base_delay: $('#async_period').val(),
            max_delay: $('#async_max_period').val(),
            max_senders: $('#async_parallel').val()
        };
        $.post(action('edit_async_requests'), params, function(response) {
            updateAsync();
        });
    });

    $('#new_ssl').click(function(event) {
        event.preventDefault();
        confirm('"Generate new internal SSL key and certificate?', null, function() {
            $.get(action('generate_ssl'), function(response) {
                updateCertDetails();
            });
        });
    });

    $('#cert_details_btn').click(function(event) {
        event.preventDefault();
        $('#cert_details').dialog('open');
    });

});