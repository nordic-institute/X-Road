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

# Test case for verifying that the operational monitoring related data
# of HTTP GET metadata requests are stored correctly by the operational
# monitoring daemon.

import os
import time
import common

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
WSDL_QUERY_REQUEST_SOAP_BASE_SIZE = 999
WSDL_QUERY_RESPONSE_MIME_BASE_SIZE = 16473
WSDL_QUERY_RESPONSE_SOAP_BASE_SIZE = 1197


def _wsdl_query_request_parameters_size(query_parameters):
    return (
        len(query_parameters["producer_instance"])
        + len(query_parameters["producer_class"])
        + len(query_parameters["producer_code"])
        + len(query_parameters["producer_system"])
        + len(query_parameters["client_instance"])
        + len(query_parameters["client_class"])
        + len(query_parameters["client_code"])
    )


def _expected_keys_and_values_of_wsdl_query_rec(
        security_server_address, security_server_type, query_parameters):
    request_parameters_size = _wsdl_query_request_parameters_size(query_parameters)
    print("Size of wsdl query request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSize", WSDL_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size),
        ("responseAttachmentCount", 1),
        ("responseMimeSize", WSDL_QUERY_RESPONSE_MIME_BASE_SIZE + request_parameters_size),
        ("responseSize", WSDL_QUERY_RESPONSE_SOAP_BASE_SIZE + request_parameters_size),
        ("securityServerInternalIp", security_server_address),
        ("securityServerType", security_server_type),
        ("serviceCode", "getWsdl"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSecurityServerAddress", query_parameters["producer_server_address"]),
        ("serviceSubsystemCode", query_parameters["producer_system"]),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("succeeded", True),
    ]


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    producer_security_server_address = query_parameters["producer_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_ss_owner_template.xml")
    query_data_producer_template_filename = os.path.join(
        request_template_dir, "query_operational_data_producer_ss_owner_template.xml")

    # Metadata and operational data requests and the relevant checks

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)

    print("\n---- Sending a verificationconf request to the client's security server ----\n")

    response = common.make_get_request(
        client_security_server_address + "/verificationconf")

    common.check_status(response)

    print("Received the following status code and response headers: \n")
    common.print_response_status_and_headers(response)

    print("\n---- Sending a listClients request to the client's security server ----\n")

    response = common.make_get_request(
        client_security_server_address + "/listClients")

    common.check_status(response)

    print("Received the following response: \n")
    common.print_response_status_and_headers(response)

    common.wait_for_operational_data()

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)

    # Now make an operational data request to the client's security
    # server and check the response payload.
    # We expect that neither of the requests sent above have been
    # stored in the operational monitoring database.

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
        common.check_record_count(record_count, 0)
    else:
        common.parse_and_check_soap_response(raw_response)

    # Wait a second to ensure that the previous operational data request
    # is not included in the operational data that we request below.
    time.sleep(1)

    print("\n---- Sending a wsdl request to the client's security server ----\n")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    response = common.make_get_request(
        client_security_server_address
        + "/wsdl?xRoadInstance={}&memberClass={}&memberCode={}&subsystemCode={}"
          "&serviceCode=mock&version=v1".format(
            query_parameters["producer_instance"], query_parameters["producer_class"],
            query_parameters["producer_code"], query_parameters["producer_system"]))

    common.check_status(response)

    print("Received the following response: \n")
    common.print_response_status_and_headers(response)

    common.wait_for_operational_data()

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_after_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    # Now make operational data requests to both security servers and
    # check the response payloads. We expect that the wsdl request has
    # been stored in the operational monitoring database.

    print("\n---- Sending an operational data request to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id, ))

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

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of all the required fields in at least
        # one JSON structure.
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_wsdl_query_rec(
                client_security_server_address, "Client", query_parameters))

        # Check if the timestamps in the response are in the expected
        # range.
        common.assert_expected_timestamp_values(
            json_payload, client_timestamp_before_requests, client_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\n---- Sending an operational data request to the producer's "
          "security server ----\n")

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests, query_parameters)
    print("Generated the following query data request for the producer's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of all the required fields in at least
        # one JSON structure.
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_wsdl_query_rec(
                producer_security_server_address, "Producer", query_parameters))

        # Check timestamp values
        common.assert_expected_timestamp_values(
            json_payload,
            producer_timestamp_before_requests, producer_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)
