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
# of a SOAP fault response are stored by the operational monitoring
# daemon.

import os
import common

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
UNKNOWN_SERVICE_QUERY_REQUEST_SOAP_BASE_SIZE = 1124
UNKNOWN_MEMBER_QUERY_REQUEST_SOAP_BASE_SIZE = 1147
SOAP_FAULT_QUERY_REQUEST_SOAP_BASE_SIZE = 1181


def _unknown_service_query_request_parameters_size(query_parameters):
    # Request template: unknown_service_query_template.xml
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


def _expected_keys_and_values_of_unknown_service_query_rec(
        xroad_message_id, security_server_address, security_server_type, query_parameters):
    request_parameters_size = _unknown_service_query_request_parameters_size(query_parameters)
    print("Size of unknown service query request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", query_parameters["client_system"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSize", (UNKNOWN_SERVICE_QUERY_REQUEST_SOAP_BASE_SIZE
                             + request_parameters_size)),
        ("securityServerInternalIp", security_server_address),
        ("securityServerType", security_server_type),
        ("serviceCode", "xroadGetDate"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSecurityServerAddress", query_parameters["producer_server_address"]),
        ("serviceSubsystemCode", query_parameters["producer_system"]),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("faultCode", "Server.ServerProxy.UnknownService"),
        ("faultString", "Unknown service: SERVICE:{}/{}/{}/{}/xroadGetDate/v1".format(
            query_parameters["producer_instance"], query_parameters["producer_class"],
            query_parameters["producer_code"], query_parameters["producer_system"])),
        ("succeeded", False),
    ]


def _unknown_member_query_request_parameters_size(query_parameters):
    # Request template: unknown_member_query_template.xml
    return (
        len(query_parameters["producer_instance"])
        + len(query_parameters["producer_class"])
        + len(query_parameters["producer_code"])
        + len(query_parameters["producer_system"])
        + len(query_parameters["client_instance"])
        + len(query_parameters["client_class"])
        + len(query_parameters["client_code"])
    )


def _expected_keys_and_values_of_unknown_member_query_rec(
        xroad_message_id, security_server_address, security_server_type, query_parameters):
    request_parameters_size = _unknown_member_query_request_parameters_size(query_parameters)
    print("Size of unknown member query request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", "System666"),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestSize", (UNKNOWN_MEMBER_QUERY_REQUEST_SOAP_BASE_SIZE
                             + request_parameters_size)),
        ("securityServerInternalIp", security_server_address),
        ("securityServerType", security_server_type),
        ("serviceCode", "mock"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSubsystemCode", query_parameters["producer_system"]),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("faultCode", "Server.ClientProxy.UnknownMember"),
        ("faultString", "Client 'SUBSYSTEM:{}/{}/{}/System666' not found".format(
            query_parameters["client_instance"], query_parameters["client_class"],
            query_parameters["client_code"])),
        ("succeeded", False),
    ]


def _soap_fault_query_request_parameters_size(query_parameters):
    # Request template: soap_fault_query_template.xml
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


def _expected_keys_and_values_of_soap_fault_query_rec(
        xroad_message_id, security_server_address, security_server_type, query_parameters):
    request_parameters_size = _soap_fault_query_request_parameters_size(query_parameters)
    print("Size of soap fault query request parameters: {}".format(request_parameters_size))
    return [
        ("clientMemberClass", query_parameters["client_class"]),
        ("clientMemberCode", query_parameters["client_code"]),
        ("clientSecurityServerAddress", query_parameters["client_server_address"]),
        ("clientSubsystemCode", query_parameters["client_system"]),
        ("clientXRoadInstance", query_parameters["client_instance"]),
        ("messageId", xroad_message_id),
        ("messageIssue", "faultPlease"),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSize", SOAP_FAULT_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size),
        ("securityServerInternalIp", security_server_address),
        ("securityServerType", security_server_type),
        ("serviceCode", "mock"),
        ("serviceMemberClass", query_parameters["producer_class"]),
        ("serviceMemberCode", query_parameters["producer_code"]),
        ("serviceSecurityServerAddress", query_parameters["producer_server_address"]),
        ("serviceSubsystemCode", query_parameters["producer_system"]),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", query_parameters["producer_instance"]),
        ("faultCode", "Mock.Fault"),
        ("faultString", "Response was not found"),
        ("succeeded", False),
    ]


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    producer_security_server_address = query_parameters["producer_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    unknown_member_query_template_filename = os.path.join(
        request_template_dir, "unknown_member_query_template.xml")
    unknown_service_query_template_filename = os.path.join(
        request_template_dir, "unknown_service_query_template.xml")
    soap_fault_query_template_filename = os.path.join(
        request_template_dir, "soap_fault_query_template.xml")
    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_ss_owner_template.xml")
    query_data_producer_template_filename = os.path.join(
        request_template_dir, "query_operational_data_producer_ss_owner_template.xml")

    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    message_id_serverproxy = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request to unknown service".format(
        message_id_serverproxy))

    # Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request to an unknown service to the client's "
          "security server ----\n")

    request_contents = common.format_xroad_request_template(
        unknown_service_query_template_filename, message_id_serverproxy, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.assert_soap_fault(xml)

    message_id_clientproxy = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request from unknown member".format(
        message_id_clientproxy))

    print("\n---- Sending an X-Road request from an unknown member to the "
          "client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        unknown_member_query_template_filename, message_id_clientproxy, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.assert_soap_fault(xml)

    message_id_service = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request that will cause a SOAP fault".format(
        message_id_service))

    print("\n---- Sending an X-Road request that will cause a SOAP fault to the "
          "client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        soap_fault_query_template_filename, message_id_service, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.assert_soap_fault(xml)

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

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of all the required fields in at least
        # one JSON structure.

        # The record describing the X-Road request that caused a fault
        # in server proxy
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_unknown_service_query_rec(
                message_id_serverproxy, client_security_server_address,
                "Client", query_parameters))

        # The record describing the X-Road request that caused a fault
        # in client proxy
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_unknown_member_query_rec(
                message_id_clientproxy, client_security_server_address,
                "Client", query_parameters))

        # The record describing the X-Road request that caused a fault
        # in test service
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_soap_fault_query_rec(
                message_id_service, client_security_server_address,
                "Client", query_parameters))

        # Check if the timestamps in the response are in the expected
        # range.
        common.assert_expected_timestamp_values(
            json_payload,
            client_timestamp_before_requests, client_timestamp_after_requests)

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

        # The record describing the X-Road request that caused a fault
        # in server proxy
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_unknown_service_query_rec(
                message_id_serverproxy, producer_security_server_address,
                "Producer", query_parameters))

        # The record describing the X-Road request that caused a fault
        # in test service
        common.assert_present_in_json(
            json_payload, _expected_keys_and_values_of_soap_fault_query_rec(
                message_id_service, producer_security_server_address,
                "Producer", query_parameters))

        # Check timestamp values
        common.assert_expected_timestamp_values(
            json_payload,
            producer_timestamp_before_requests, producer_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload)
    else:
        common.parse_and_check_soap_response(raw_response)
