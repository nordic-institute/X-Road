#!/usr/bin/env python3

# Test case for verifying that the correct value of operational monitoring data
# field 'serviceSecurityServerAddress' is stored by the operational monitoring
# daemon of client proxy in case of X-Road requests to service cluster.
# securityServer header is used in the X-Road request to determine the
# service provider security server the request is sent to.

import os
import sys
import time
import xml.dom.minidom as minidom

sys.path.append('..')
import python_common as common

def _expected_keys_and_values_of_cluster_query_rec(
        xroad_message_id, security_server_address):
    return [
        ("messageId", xroad_message_id),
        ("serviceSecurityServerAddress", security_server_address),
    ]

def run(client_security_server_address, producer_security_server_address,
        request_template_dir):
    xroad_query_to_ss0_service_template_filename = os.path.join(
            request_template_dir, "xroad_query_to_ss0_service_template.xml")
    xroad_query_to_ss2_service_template_filename = os.path.join(
            request_template_dir, "xroad_query_to_ss2_service_template.xml")
    query_data_client_template_filename = os.path.join(
            request_template_dir, "query_operational_data_client_template.xml")

    timestamp_before_request = common.generate_timestamp()

    message_id_ss0 = common.generate_message_id()
    print("\nGenerated message ID %s for X-Road request" % (message_id_ss0, ))

    ### Regular and operational data requests and the relevant checks

    print("\n---- Sending an X-Road request to the service provider in " \
          "security server 00000000_1 ----\n")

    request_contents = common.format_xroad_request_template(
            xroad_query_to_ss0_service_template_filename, message_id_ss0)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
            client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    # For some reason our mock service returns a SOAP response with lots of
    # whitespace
    xml = minidom.parseString(common.clean_whitespace(response.text))
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    message_id_ss2 = common.generate_message_id()
    print("\nGenerated message ID %s for X-Road request" % (message_id_ss2, ))

    print("\n---- Sending an X-Road request to the service provider in " \
          "security server 00000002_1 ----\n")

    request_contents = common.format_xroad_request_template(
            xroad_query_to_ss2_service_template_filename, message_id_ss2)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
            client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    # For some reason our mock service returns a SOAP response with lots of
    # whitespace
    xml = minidom.parseString(common.clean_whitespace(response.text))
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    # Wait a couple of seconds for the operational data to be stored with some certainty.
    time.sleep(3)
    timestamp_after_request = common.generate_timestamp()

    # Now make an operational data request to the client's security server 
    # and check the response payload.

    print("\n---- Sending an operational data request to the client's security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID %s for query data request" % (message_id, ))
 
    request_contents = common.format_query_operational_data_request_template(
            query_data_client_template_filename, message_id,
            timestamp_before_request - 5, timestamp_after_request + 5)

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

        # Check the presence of the required fields in at least one JSON structure.

        # The record describing the X-Road request to service provider in 
        # security server 00000000_1
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_cluster_query_rec(
                    message_id_ss0, "xtee8.ci.kit"))

        # The record describing the X-Road request to service provider in 
        # security server 00000002_1
        common.assert_present_in_json(
                json_payload, _expected_keys_and_values_of_cluster_query_rec(
                    message_id_ss2, "xtee10.ci.kit"))

        common.print_multipart_query_data_response(json_payload)

    else:
        common.parse_and_check_soap_response(raw_response)
 
