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
# of a simple correct X-Road request are stored by the operational
# monitoring daemon and can be queried.

import os
import time
import common

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE = 1461
SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE = 1503
QUERY_DATA_CLIENT_REQUEST_SOAP_BASE_SIZE = 1696
QUERY_DATA_CLIENT_RESPONSE_SOAP_BASE_SIZE = 1766
QUERY_DATA_CLIENT_RESPONSE_MIME_BASE_SIZE = 2207
QUERY_DATA_PRODUCER_REQUEST_SOAP_BASE_SIZE = 1681
QUERY_DATA_PRODUCER_RESPONSE_SOAP_BASE_SIZE = 1751
QUERY_DATA_PRODUCER_RESPONSE_MIME_BASE_SIZE = 2194


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


def _query_data_client_request_parameters_size(query_parameters):
    # Request template: query_operational_data_client_template.xml
    return (
        3 * len(query_parameters["client_instance"])
        + 3 * len(query_parameters["client_class"])
        + 3 * len(query_parameters["client_code"])
        + len(query_parameters["client_system"])
        + len(query_parameters["client_server_code"])
    )


def _expected_keys_and_values_of_query_data_client_rec(
        xroad_message_id, security_server_type, query_parameters):
    request_parameters_size = _query_data_client_request_parameters_size(query_parameters)
    print("Size of query data client request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", query_parameters["client_system"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSize", QUERY_DATA_CLIENT_REQUEST_SOAP_BASE_SIZE + request_parameters_size),
        ("responseAttachmentCount", 1),
        ("securityServerType", security_server_type),
        ("serviceCode", "getSecurityServerOperationalData"),
        ("serviceMemberClass", query_parameters["client_class"]),
        ("serviceMemberCode", query_parameters["client_code"]),
        ("serviceSecurityServerAddress", query_parameters["client_server_address"]),
        ("serviceVersion", "красивая родина"),
        ("serviceXRoadInstance", query_parameters["client_instance"]),
        ("succeeded", True),
    ]


def _query_data_producer_request_parameters_size(query_parameters):
    # Request template: query_operational_data_producer_template.xml
    return (
        3 * len(query_parameters["producer_instance"])
        + 3 * len(query_parameters["producer_class"])
        + 3 * len(query_parameters["producer_code"])
        + len(query_parameters["producer_system"])
        + len(query_parameters["producer_server_code"])
    )


def _expected_keys_and_values_of_query_data_producer_rec(
        xroad_message_id, security_server_type, query_parameters):
    # Request template: query_operational_data_producer_template.xml
    request_parameters_size = _query_data_producer_request_parameters_size(query_parameters)
    print("Size of query data producer request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["producer_class"]),
        ("clientMemberCode", query_parameters["producer_code"]),
        ("clientSecurityServerAddress", query_parameters["producer_server_address"]),
        ("clientSubsystemCode", query_parameters["producer_system"]),
        ("clientXRoadInstance", query_parameters["producer_instance"]),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSize", QUERY_DATA_PRODUCER_REQUEST_SOAP_BASE_SIZE + request_parameters_size),
        ("responseAttachmentCount", 1),
        ("securityServerType", security_server_type),
        ("serviceCode", "getSecurityServerOperationalData"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSecurityServerAddress", query_parameters["producer_server_address"]),
        ("serviceVersion", "[Hüsker Dü?]"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("succeeded", True),
    ]


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

    request_xml = common.parse_and_clean_xml(request_contents)

    # Headers of the original request
    xroad_request_headers = request_xml.getElementsByTagName(
        "SOAP-ENV:Header")[0].toprettyxml()

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
    message_id_client = message_id
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_requests,
        query_parameters)

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

        # Check the presence of all the required fields in at least one
        # JSON structure.
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_simple_query_rec(
                xroad_message_id, "Client", query_parameters))

        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check if the timestamps in the response are in the expected
        # range.
        common.assert_expected_timestamp_values(
            json_payload,
            client_timestamp_before_requests, client_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload, xroad_message_id)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\nThe headers of the original request were: \n")
    print(xroad_request_headers)

    print("\n---- Sending an operational data request to the producer's "
          "security server ----\n")

    message_id = common.generate_message_id()
    message_id_producer = message_id
    print("\nGenerated message ID {} for operational data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests,
        query_parameters)
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

        json_payload = common.get_multipart_json_payload(mime_parts[1])

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

        common.print_multipart_query_data_response(
            json_payload, xroad_message_id)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\nThe headers of the original request were: \n")
    print(xroad_request_headers)

    # Repeat both query_data requests after a second, to ensure the
    # initial attempts were also stored in the operational_data table.
    time.sleep(1)

    print("\n---- Repeating the query_data request to the client's security server ----\n")

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_after_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for operational data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_requests,
        query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of the required fields in the JSON
        # structures.

        # The record describing the original X-Road request
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_simple_query_rec(
                xroad_message_id, "Client", query_parameters))

        # The record describing the query data request at the client
        # proxy side in the client's security server
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_query_data_client_rec(
                message_id_client, "Client", query_parameters))

        # The record describing the query data request at the server
        # proxy side in the client's security server
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_query_data_client_rec(
                message_id_client, "Producer", query_parameters))

        # Check if the value of "responseSize" is in the expected
        # range.
        common.assert_response_soap_size_in_range(
            json_payload, message_id_client, (
                QUERY_DATA_CLIENT_RESPONSE_SOAP_BASE_SIZE
                + _query_data_client_request_parameters_size(query_parameters)
            ), 2)

        # Check if the value of "responseMimeSize" is in the expected
        # range.
        common.assert_response_mime_size_in_range(
            json_payload, message_id_client, (
                QUERY_DATA_CLIENT_RESPONSE_MIME_BASE_SIZE
                + _query_data_client_request_parameters_size(query_parameters)
            ), 2)

        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check timestamp values
        common.assert_expected_timestamp_values(
            json_payload,
            client_timestamp_before_requests, client_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\n----- Repeating the query_data request to the producer's security server ----\n")

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for operational data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_requests,
        query_parameters)
    print("Generated the following operational data request for the producer's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of the required fields in the JSON
        # structures.

        # The record describing the original X-Road request
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_simple_query_rec(
                xroad_message_id, "Producer", query_parameters))

        # The record describing the query data request at the client
        # proxy side in the producer's security server
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_query_data_producer_rec(
                message_id_producer, "Client", query_parameters))

        # The record describing the query data request at the server
        # proxy side in the producer's security server
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_query_data_producer_rec(
                message_id_producer, "Producer", query_parameters))

        # Check if the value of "responseSize" is in the expected
        # range.
        common.assert_response_soap_size_in_range(
            json_payload, message_id_producer, (
                QUERY_DATA_PRODUCER_RESPONSE_SOAP_BASE_SIZE +
                _query_data_producer_request_parameters_size(query_parameters)
            ), 2)

        # Check if the value of "responseMimeSize" is in the expected
        # range.
        common.assert_response_mime_size_in_range(
            json_payload, message_id_producer, (
                QUERY_DATA_PRODUCER_RESPONSE_MIME_BASE_SIZE
                + _query_data_producer_request_parameters_size(query_parameters)
            ), 2)

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
