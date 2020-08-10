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
# of X-Road requests and responses that contain attachments are stored
# by the operational monitoring daemon.

import os
import common

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
ONE_ATTACHMENT_QUERY_REQUEST_MIME_BASE_SIZE = 1275
ONE_ATTACHMENT_QUERY_REQUEST_SOAP_BASE_SIZE = 1258
ONE_ATTACHMENT_QUERY_RESPONSE_MIME_BASE_SIZE = 1568
ONE_ATTACHMENT_QUERY_RESPONSE_SOAP_BASE_SIZE = 1493
FIVE_ATTACHMENTS_QUERY_REQUEST_MIME_BASE_SIZE = 1546
FIVE_ATTACHMENTS_QUERY_REQUEST_SOAP_BASE_SIZE = 1461
FIVE_ATTACHMENTS_QUERY_RESPONSE_SOAP_BASE_SIZE = 1503


def _one_attachment_query_request_parameters_size(query_parameters):
    # Request template: xroad_query_for_attachments_template.xml
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


def _expected_keys_and_values_of_one_attachment_query_rec(
        xroad_message_id, security_server_type, query_parameters):
    request_parameters_size = _one_attachment_query_request_parameters_size(query_parameters)
    print("Size of one attachment request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", query_parameters["client_system"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageIssue", "attachmentsPlease"),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 1),
        ("requestMimeSize", (ONE_ATTACHMENT_QUERY_REQUEST_MIME_BASE_SIZE
                             + request_parameters_size)),
        ("requestSize", (ONE_ATTACHMENT_QUERY_REQUEST_SOAP_BASE_SIZE
                             + request_parameters_size)),
        ("responseAttachmentCount", 3),
        ("responseMimeSize", (ONE_ATTACHMENT_QUERY_RESPONSE_MIME_BASE_SIZE
                              + request_parameters_size)),
        ("responseSize", (ONE_ATTACHMENT_QUERY_RESPONSE_SOAP_BASE_SIZE
                              + request_parameters_size)),
        ("securityServerType", security_server_type),
        ("serviceCode", "mockSwaRef"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSecurityServerAddress", query_parameters["producer_server_address"]),
        ("serviceSubsystemCode", query_parameters["producer_system"]),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("succeeded", True),
    ]


def _five_attachments_query_request_parameters_size(query_parameters):
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


def _expected_keys_and_values_of_five_attachments_query_rec(
        xroad_message_id, security_server_type, query_parameters):
    request_parameters_size = _five_attachments_query_request_parameters_size(query_parameters)
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
        ("requestAttachmentCount", 5),
        ("requestMimeSize", (FIVE_ATTACHMENTS_QUERY_REQUEST_MIME_BASE_SIZE
                             + request_parameters_size)),
        ("requestSize", (FIVE_ATTACHMENTS_QUERY_REQUEST_SOAP_BASE_SIZE
                             + request_parameters_size)),
        ("responseAttachmentCount", 0),
        ("responseSize", (FIVE_ATTACHMENTS_QUERY_RESPONSE_SOAP_BASE_SIZE
                              + request_parameters_size)),
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
    xroad_request_attachments_template_filename = os.path.join(
        request_template_dir, "xroad_query_for_attachments_template.xml")
    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_template.xml")
    query_data_producer_template_filename = os.path.join(
        request_template_dir, "query_operational_data_producer_template.xml")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    message_id_one_attachment = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request with one "
          "attachment".format(message_id_one_attachment))

    # Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request with one attachment to the "
          "service that will respond with three attachments ----\n")

    request_contents = common.format_xroad_request_template(
        xroad_request_attachments_template_filename, message_id_one_attachment,
        query_parameters)

    response = common.post_multipart_request(
        client_security_server_address, request_contents,
        attachment_count=1, get_raw_stream=True)

    # Expecting a multipart response with attachments.
    mime_parts, raw_response = common.parse_multipart_response(response)

    print("Received the following X-Road response: \n")
    print(raw_response.decode("utf-8"))

    if not mime_parts:
        common.parse_and_check_soap_response(raw_response)

    message_id_five_attachments = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request with five "
          "attachments".format(message_id_five_attachments))

    print("\n---- Sending an X-Road request with five attachments to the "
          "client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        xroad_request_template_filename, message_id_five_attachments,
        query_parameters)

    # Expecting a simple SOAP response.
    response = common.post_multipart_request(
        client_security_server_address, request_contents, attachment_count=5)

    print("Received the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    common.wait_for_operational_data()

    client_timestamp_after_request = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_after_request = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    # Now make operational data requests to both security servers and
    # check the response payloads.

    print("\n---- Sending an operational data request to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        client_timestamp_before_requests, client_timestamp_after_request,
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

        # Check the presence of all the required fields in at least
        # one JSON structure.

        # The record describing the query with one attachment
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_one_attachment_query_rec(
                message_id_one_attachment, "Client", query_parameters))

        # The record describing the query with five attachments
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_five_attachments_query_rec(
                message_id_five_attachments, "Client", query_parameters))

        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check if the timestamps in the response are in the expected
        # range.
        common.assert_expected_timestamp_values(
            json_payload, client_timestamp_before_requests, client_timestamp_after_request)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)

    print("\n---- Sending an operational data request to the producer's "
          "security server ----\n")

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for query data request".format(message_id))

    request_contents = common.format_query_operational_data_request_template(
        query_data_producer_template_filename, message_id,
        producer_timestamp_before_requests, producer_timestamp_after_request,
        query_parameters)
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

        # The record describing the query with one attachment
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_one_attachment_query_rec(
                message_id_one_attachment, "Producer", query_parameters))

        # The record describing the query with five attachments
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_five_attachments_query_rec(
                message_id_five_attachments, "Producer", query_parameters))
        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check if the timestamps in the response are in the expected range.
        common.assert_expected_timestamp_values(
            json_payload,
            producer_timestamp_before_requests, producer_timestamp_after_request)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)
