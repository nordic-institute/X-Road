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

# Test case for verifying that the operational monitoring related data of a
# simple correct X-Road request are stored by the operational monitoring daemon
# and can be queried.

import os
import sys
import time

sys.path.append('..')
import python_common as common

def _expected_keys_and_values_of_simple_query_rec(
        xroad_message_id, security_server_type):
    return [
        ("clientMemberClass", "GOV"),
        ("clientMemberCode", "00000001"),
        ("clientSecurityServerAddress", "xtee9.ci.kit"),
        ("clientSubsystemCode", "System1"),
        ("clientXRoadInstance", "XTEE-CI-XM"),
        ("messageId", xroad_message_id),
        ("messageIssue", "453465"),
        ("messageProtocolVersion", "4.0"),
        ("messageUserId", "EE37702211230"),
        ("representedPartyClass", "COM"),
        ("representedPartyCode", "UNKNOWN_MEMBER"),
        ("requestAttachmentCount", 0),
        ("requestSoapSize", 1629),
        ("responseAttachmentCount", 0),
        ("responseSoapSize", 1519),
        ("securityServerType", security_server_type),
        ("serviceCode", "xroadGetRandom"),
        ("serviceMemberClass", "GOV"),
        ("serviceMemberCode", "00000000"),
        ("serviceSecurityServerAddress", "xtee8.ci.kit"),
        ("serviceSubsystemCode", "Center"),
        ("serviceVersion", "v1"),
        ("serviceXRoadInstance", "XTEE-CI-XM"),
        ("succeeded", True),
    ]

def _expected_keys_and_values_of_query_data_client_rec(
        xroad_message_id, security_server_type):
    return [
        ("clientMemberClass", "GOV"),
        ("clientMemberCode", "00000001"),
        ("clientSecurityServerAddress", "xtee9.ci.kit"),
        ("clientSubsystemCode", "System1"),
        ("clientXRoadInstance", "XTEE-CI-XM"),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSoapSize", 1767),
        ("responseAttachmentCount", 1),
        ("securityServerType", security_server_type),
        ("serviceCode", "getSecurityServerOperationalData"),
        ("serviceMemberClass", "GOV"),
        ("serviceMemberCode", "00000001"),
        ("serviceSecurityServerAddress", "xtee9.ci.kit"),
        ("serviceVersion", "красивая родина"),
        ("serviceXRoadInstance", "XTEE-CI-XM"),
        ("succeeded", True),
    ]

def _expected_keys_and_values_of_query_data_producer_rec(
        xroad_message_id, security_server_type):
    return [
        ("clientMemberClass", "GOV"),
        ("clientMemberCode", "00000000"),
        ("clientSecurityServerAddress", "xtee8.ci.kit"),
        ("clientSubsystemCode", "Center"),
        ("clientXRoadInstance", "XTEE-CI-XM"),
        ("messageId", xroad_message_id),
        ("messageProtocolVersion", "4.0"),
        ("requestAttachmentCount", 0),
        ("requestSoapSize", 1751),
        ("responseAttachmentCount", 1),
        ("securityServerType", security_server_type),
        ("serviceCode", "getSecurityServerOperationalData"),
        ("serviceMemberClass", "GOV"),
        ("serviceMemberCode", "00000000"),
        ("serviceSecurityServerAddress", "xtee8.ci.kit"),
        ("serviceVersion", "[Hüsker Dü?]"),
        ("serviceXRoadInstance", "XTEE-CI-XM"),
        ("succeeded", True),
    ]

def run(client_security_server_address, producer_security_server_address,
        ssh_user, request_template_dir):

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
    print("\nGenerated message ID %s for X-Road request" % (xroad_message_id, ))

    # The headers will be extracted after the request template has been
    # formatted.
    xroad_request_headers = None

    ### Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
            xroad_request_template_filename, xroad_message_id)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    request_xml = common.parse_and_clean_xml(request_contents)
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

    # Now make operational data requests to both security servers and check the
    # response payloads.

    print("\n---- Sending an operational data request to the client's security server ----\n")

    message_id = common.generate_message_id()
    message_id_client = message_id
    print("Generated message ID %s for query data request" % (message_id, ))
 
    request_contents = common.format_query_operational_data_request_template(
            query_data_client_template_filename, message_id,
            client_timestamp_before_requests, client_timestamp_after_requests)

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

        # Check the presence of all the required fields in at least one JSON structure.
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_simple_query_rec(
                    xroad_message_id, "Client"))

        # As operational data is queried by regular client, the field
        # 'securityServerInternalIp' is not expected to be included 
        # in the response payload.
        common.assert_missing_in_json(json_payload, "securityServerInternalIp")

        # Check if the timestamps in the response are in the expected range.
        common.assert_expected_timestamp_values(
                json_payload,
                client_timestamp_before_requests, client_timestamp_after_requests)

        common.print_multipart_query_data_response(json_payload, xroad_message_id)

    else:
        common.parse_and_check_soap_response(raw_response)
 
    print("\nThe headers of the original request were: \n")
    print(xroad_request_headers)

    print("\n---- Sending an operational data request to the producer's " \
                "security server ----\n")

    message_id = common.generate_message_id()
    message_id_producer = message_id
    print("\nGenerated message ID %s for operational data request" % (message_id, ))

    request_contents = common.format_query_operational_data_request_template(
            query_data_producer_template_filename, message_id,
            producer_timestamp_before_requests, producer_timestamp_after_requests)
    print("Generated the following operational data request for the producer's " \
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

        # Check the presence of all the required fields in at least one JSON structure.
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_simple_query_rec(
                    xroad_message_id, "Producer"))

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
 
    # Repeat both query_data requests after a second, to ensure the initial attempts
    # were also stored in the operational_data table.
    time.sleep(1)

    print("\n---- Repeating the query_data request to the client's security server ----\n")

    client_timestamp_after_requests = common.get_remote_timestamp(
            client_security_server_address, ssh_user)
    producer_timestamp_after_requests = common.get_remote_timestamp(
            producer_security_server_address, ssh_user)

    message_id = common.generate_message_id()
    print("\nGenerated message ID %s for operational data request" % (message_id, ))

    request_contents = common.format_query_operational_data_request_template(
            query_data_client_template_filename, message_id,
            client_timestamp_before_requests, client_timestamp_after_requests)
    print("Generated the following operational data request for the client's " \
            "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
            client_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of the required fields in the JSON structures.

        # The record describing the original X-Road request
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_simple_query_rec(
                    xroad_message_id, "Client"))

        # The record describing the query data request at the client proxy side in the
        # client's security server
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_query_data_client_rec(
                    message_id_client, "Client"))

        # The record describing the query data request at the server proxy side in the
        # client's security server
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_query_data_client_rec(
                    message_id_client, "Producer"))

        # Check if the value of "responseSoapSize" is in the expected range.
        common.assert_response_soap_size_in_range(
                json_payload, message_id_client, 1837, 2)

        # Check if the value of "responseMimeSize" is in the expected range.
        common.assert_response_mime_size_in_range(
                json_payload, message_id_client, 2274, 2)

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
    print("\nGenerated message ID %s for operational data request" % (message_id, ))

    request_contents = common.format_query_operational_data_request_template(
            query_data_producer_template_filename, message_id,
            producer_timestamp_before_requests, producer_timestamp_after_requests)
    print("Generated the following operational data request for the producer's " \
            "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
            producer_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count, is_client=False)

        json_payload = common.get_multipart_json_payload(mime_parts[1])

        # Check the presence of the required fields in the JSON structures.
 
        # The record describing the original X-Road request
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_simple_query_rec(
                    xroad_message_id, "Producer"))

        # The record describing the query data request at the client proxy side in the
        # producer's security server
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_query_data_producer_rec(
                    message_id_producer, "Client"))

        # The record describing the query data request at the server proxy side in the
        # producer's security server
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_query_data_producer_rec(
                    message_id_producer, "Producer"))

        # Check if the value of "responseSoapSize" is in the expected range.
        common.assert_response_soap_size_in_range(
                json_payload, message_id_producer, 1872, 2)

        # Check if the value of "responseMimeSize" is in the expected range.
        common.assert_response_mime_size_in_range(
                json_payload, message_id_producer, 2311, 2)

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
