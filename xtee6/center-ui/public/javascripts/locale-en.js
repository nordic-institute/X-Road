$.i18n.setDictionary({
    "ok": "OK",
    "cancel": "Cancel",
    "back": "Back",
    "next": "Next",
    "finish": "Finish",
    "close": "Close",
    "submit": "Submit",
    "zero_records": "No (matching) records",
    "search": "Search",
    "ip_error": "Invalid IP address: %s",
    "ip_format": "Invalid IP address format: %s",
    "fqdn_error": "Invalid domain name: %s",
    "select": "Select",
    "select_all": "Select/deselect all",
    "advanced_search": "Advanced Search",
    "simple_search": "Simple Search",
    "cert_view": "View certificate",

    "session_timed_out": "<div class='align-center'>Session timed out. Redirecting.</div>",

    "acl.slaves.remove.confirm": 'Are you sure you want to remove the slave "%s"?',
    "acl.slaves.remove.confirm.title": "Removing ACL slave",

    "acl.refresh.remove.confirm": 'Adapter server does not support the following queries\
                                   with entries in access rights database:\
                                   <center><b>%s</b></center>',
    "acl.refresh.remove.confirm.title": "Refresh queries",
    "acl.refresh.remove.confirm.keep": "Keep access rights",
    "acl.refresh.remove.confirm.remove": "Remove access rights",

    "acl.no_queries": "Group/org not selected or no filter match",

    "adapterparams.delacl.confirm": "Are you sure you want to remove the ACL database?",
    "adapterparams.delacl.confirm.title": "Removing ACL database",

    "aggregates.remove.confirm": 'Are you sure you want to remove aggregate DB "%s"?',
    "aggregates.remove.confirm.title": "Removing aggregate DB",
    "aggregates.for_producer_error": "No importing database selected",

    "async.removed": "REMOVED",
    "async.queued": "queued",

    "asynclog.state.1": "Sending failed",
    "asynclog.state.2": "Sent",
    "asynclog.state.3": "Unsent",
    "asynclog.state.4": "Sending prohibited",

    "backup.done": "Configuration backup created",
    "backup.error": "Error making configuration backup, script exited with status code '%s'",

    "cakeys.zero_records": "No certificates found",

    "diagnostics.test": "Performing test %s...",
    "diagnostics.test_failed": "Test %s failed",
    "diagnostics.test_succeeded": "Test succeeded",
    "diagnostics.test_cancelled": "Test canceled",
    "diagnostics.tests_cancelled": "Tests canceled",
    "diagnostics.all_done": "All tests completed successfully",
    "diagnostics.wait": "Wait...",

    "dnskeys.zero_records": "No DNS keys found",

    "login.error": "No such user. Verify that the user name and password are correct",

    "logs.email_default": 'Enter e-mail address...',

    "members.remove.confirm": 'Are you sure you want to remove SDSB member "%s(%s)"?',
    "members.remove.confirm.title": "Removing SDSB member",
    "members.subsystem.remove": "Delete Subsystem",
    "members.remove.subsystem.confirm" : 'Are you sure you want to remove subsystem "%s" SDSB member "%s(%s)"?',
    "members.remove.subsystem.confirm.title" : 'Removing Subsystem',
    "members.add_member_to_group.title" : "Add '%s' to Group",
    "members.remove.global_group.confirm": 'Are you sure you want to remove "%s" from global group "%s"?',
    "members.remove.global_group.title": "Removing Member From Global Group",

    "securityservers.remove.confirm": 'Are you sure you want to remove security server "%s" belonging to "%s(%s)"?',
    "securityservers.remove.confirm.title": 'Removing security server',

    "groups.add.selected_members": "Add Selected to Group",
    "groups.remove.confirm": 'Are you sure you want to remove group "%s"?',
    "groups.remove.confirm.title": "Removing group",
    "groups.remove.selected_members.confirm": 'Are you sure you want to remove selected members from group "%s"?',
    "groups.remove.all_members.confirm": 'Are you sure you want to remove all members from group "%s"?',
    "groups.details.title": 'Global group "%s"',
    "groups.details.members.add.title": "Add Members To Global Group '%s'",
    "groups.details.members.add.search.zero": "Search to get members to add",
    "groups.details.members.add.search.show_members": "Show group members in search results",

    "groups.orgs.remove.confirm" : 'Are you sure you want to remove SDSB member "%s" from group "%s"?',
    "groups.orgs.remove.confirm.title" : "Removing SDSB member from group",

    "timeouts.logging.certs.remove.confirm": "Are you sure you want to remove the certificate?",
    "timeouts.logging.certs.remove.confirm.title": "Removing Certificate",
    "timeouts.zero_records": "No certificates loaded",

    "management_requests.request_type.auth_cert_reg": "Certificate registration",
    "management_requests.request_type.client_reg": "Client registration",
    "management_requests.request_type.auth_cert_deletion": "Certificate deletion",
    "management_requests.request_type.client_deletion": "Client deletion",
    "management_requests.source.center": "SDSB center",
    "management_requests.source.security_server": "Security server",
    "management_requests.cancel.confirm": "Cancel request?",

    "central_services.remove.confirm": 'Are you sure you want to remove central service "%s"?',
    "central_services.new.title": "Add New Central Service",
    "central_service.edit.title": 'Edit central service "%s"',
    "central_services.remove_target_service.confirm": 'Are you sure you want to remove target service from central service "%s"?',

    "pkis.remove.confirm": 'Are you sure you want to remove PKI "%s"?',
    "pkis.add.ocsp_responders.uploading": 'Top CA OCSP responder cert currently being uploaded, try again',
    "pkis.intermediate_ca.new.title": 'Insert intermediate CA',
    "pkis.intermediate_ca.edit.title": 'Edit intermediate CA',

    "tsps.edit.new": "Add Timestamping Service",
    "tsps.edit.existing": "Edit Timestamping Service",
    "tsps.remove.confirm": 'Are you sure you want to remove TSP "%s"?',

    "global_conf_gen_status.no_status_file": "No global conf generation status file present, make sure if center-service is up and running",
    "global_conf_gen_status.out_of_date": "Global conf is out of date, last generation attempt: '%s', make sure if center-service is up and running",
    "global_conf_gen_status.failure": "Global conf generation failed at '%s'"
});
