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

# Test case for verifying that in case the operational data request
# includes a list of operational data parameters then only the requested
# parameters are included in the operational data response. In case the
# list of parameters specified in the request contains unknown
# parameters, a SOAP fault is received as the response.
# In case the request contains an empty outputspec element, the response 
# contains all operational data parameters.
# In case the outputspec contains only fields with null values, empty
# JSON records are received as the response.

import os
import common

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE = 1461
SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE = 1503


def _expected_keys_and_values_of_limited_spec_query_rec(query_parameters):
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("serviceCode", "mock"),
        ("serviceVersion", "v1"),
    ]


def _simple_query_request_parameters_size(query_parameters):
    # Request template: simple_xroad_query_template.xml
    return (
        len(query_parameters["producer_instance"])
        + len(query_parameters["producer_class"])
        + len(query_parameters["producer_code"])
        + len(query_parameters["producer_system"])
        + len(query_parameters["client_instance"])
        + len(query_parameters["client_class"])
        + len(query_parameters["client_code"])
        + len(query_parameters["client_system"])
    )


def _expected_keys_and_values_of_simple_query_rec(
        xroad_message_id, security_server_type, query_parameters):
    request_parameters_size = _simple_query_request_parameters_size(query_parameters)
    print("Size of simple query request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", query_parameters["client_system"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageIssue", "453465"),
        ("messageProtocolVersion", "4.0"),
        ("messageUserId", "EE12345678901"),
        ("representedPartyClass", "COM"),
        ("representedPartyCode", "UNKNOWN_MEMBER"),
        ("requestAttachmentCount", 0),
        ("requestSize", SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size),
        ("responseAttachmentCount", 0),
        ("responseSize", SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE + request_parameters_size),
        ("securityServerType", security_server_type),
        ("serviceCode", "mock"),
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

    xroad_request_template_filename = os.path.join(
        request_template_dir, "simple_xroad_query_template.xml")
    query_data_client_outputspec_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_outputspec_template.xml")
    query_data_client_faulty_outputspec_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_client_faulty_outputspec_template.xml")
    query_data_producer_empty_outputspec_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_producer_empty_outputspec_template.xml")
    query_data_producer_outputspec_template_filename = os.path.join(
        request_template_dir, "query_operational_data_producer_outputspec_template.xml")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    xroad_message_id = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road requests".format(xroad_message_id))

    # Regular and operational data requests and the relevant checks

    print("\n---- Sending 3 X-Road requests to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        xroad_request_template_filename, xroad_message_id, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    # Send 3 X-Road requests
    for _ in range(3):
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

    # Now make operational data requests to both security servers 
    # and check the response payloads.

    print("\n---- Sending an operational data request with correct outputspec "
          "to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_outputspec_template_filename, message_id,
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

        common.print_multipart_query_data_response(json_payload)

        # Check the presence of all the required fields.
        # Check the number of fields in JSON records is consistent with
        # the number of parameters requested.
        common.assert_json_fields(
            json_payload,
            _expected_keys_and_values_of_limited_spec_query_rec(query_parameters))
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\n---- Sending an operational data request with faulty outputspec "
          "to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for faulty query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_faulty_outputspec_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_requests, query_parameters)

    print("Generated the following query data request for the client's security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Using an unknown parameter in outputspec must result in
    # a SOAP fault.
    common.assert_soap_fault(xml)

    print("\n---- Sending an operational data request with an empty outputspec "
          "to the producer's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_empty_outputspec_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests, query_parameters)

    print("Generated the following query data request for the producer's security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Empty outputspec element in the request must result in all
        # operational data parameters in the response.
        # Check the presence of all the required fields in at least one
        # JSON structure.
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_simple_query_rec(
                xroad_message_id, "Producer", query_parameters))

        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check timestamp values
        common.assert_expected_timestamp_values(
            json_payload,
            producer_timestamp_before_requests, producer_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\n---- Sending an operational data request with an outputspec "
          "that contains only faultCode to the producer's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_outputspec_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests, query_parameters)

    print("Generated the following query data request for the producer's security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Empty JSON records are expected as the response.
        common.assert_empty_json_records(json_payload)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)
