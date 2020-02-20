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

# Test case for verifying that the correct value of operational
# monitoring data field 'serviceSecurityServerAddress' is stored by
# the operational monitoring daemon of client proxy in case of X-Road
# requests to service cluster.
# securityServer header is used in the X-Road request to determine the
# service provider security server the request is sent to.

import os
import common


def _expected_keys_and_values_of_cluster_query_rec(
        xroad_message_id, security_server_address):
    return [
        ("messageId", xroad_message_id),
        ("serviceSecurityServerAddress", security_server_address),
    ]


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    xroad_query_to_ss0_service_template_filename = os.path.join(
        request_template_dir, "xroad_query_to_ss0_service_template.xml")
    xroad_query_to_ss2_service_template_filename = os.path.join(
        request_template_dir, "xroad_query_to_ss2_service_template.xml")
    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_template.xml")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)

    message_id_ss0 = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request".format(message_id_ss0))

    # Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request to the service provider in "
          "security server {} ----\n".format(query_parameters["producer_server_address"]))

    request_contents = common.format_xroad_request_template(
        xroad_query_to_ss0_service_template_filename, message_id_ss0, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    message_id_ss2 = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request".format(message_id_ss2))

    print("\n---- Sending an X-Road request to the service provider in "
          "security server {} ----\n".format(query_parameters["producer2_server_address"]))

    request_contents = common.format_xroad_request_template(
        xroad_query_to_ss2_service_template_filename, message_id_ss2, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    common.wait_for_operational_data()

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)

    # Now make an operational data request to the client's security server 
    # and check the response payload.

    print("\n---- Sending an operational data request to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_requests, query_parameters)

    print("Generated the following query data request for the client's security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of the required fields in at least one
        # JSON structure.

        # The record describing the X-Road request to service provider
        # in security server 'producer'
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_cluster_query_rec(
                message_id_ss0, query_parameters["producer_server_address"]))

        # The record describing the X-Road request to service provider
        # in security server 'producer2'
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_cluster_query_rec(
                message_id_ss2, query_parameters["producer2_server_address"]))

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)
