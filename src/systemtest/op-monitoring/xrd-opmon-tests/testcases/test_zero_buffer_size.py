#!/usr/bin/env python3

# The MIT License
# Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# Test case for verifying that in case the operational monitoring buffer
# size has been set to zero, the operational monitoring data of X-Road
# requests is not stored by the operational monitoring daemon and can't
# be queried.
# Expecting that the value of op-monitor-buffer.size has been set 
# to 0 via run_tests.py.

import os
import common


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    producer_security_server_address = query_parameters["producer_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    xroad_request_template_filename = os.path.join(
        request_template_dir, "simple_xroad_query_template.xml")
    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_template.xml")
    query_data_producer_template_filename = os.path.join(
        request_template_dir, "query_operational_data_producer_template.xml")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    xroad_message_id = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request".format(xroad_message_id))

    # Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        xroad_request_template_filename, xroad_message_id, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("Received the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    common.wait_for_operational_data()

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_after_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    # Now make operational data requests to both security servers and
    # check the response payloads.

    print("\n---- Sending an operational data request to the client's security server ----\n")
    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_requests, query_parameters)

    print("Generated the following query data request for the client's security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count)

        # op-monitor-buffer.size=0 must result in an empty response
        common.check_record_count(record_count, 0)
    else:
        common.parse_and_check_soap_response(raw_response)

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for operational data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests, query_parameters)
    print("Generated the following operational data request for the producer's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        # op-monitor-buffer.size=0 must result in an empty response
        common.check_record_count(record_count, 0)
    else:
        common.parse_and_check_soap_response(raw_response)
