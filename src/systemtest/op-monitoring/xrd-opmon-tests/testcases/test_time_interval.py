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

# Test case for verifying that invalid 'recordsFrom' and 'recordsTo'
# values in operational monitoring request result in a SOAP fault and
# 'recordsTo' value in the future results with 'nextRecordsFrom' element
# in operational monitoring response.

import os
import common


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_operational_data_client_template.xml")
    query_data_client_missing_recordsfrom_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_client_missing_recordsfrom_template.xml")
    query_data_client_missing_recordsto_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_client_missing_recordsto_template.xml")
    query_data_client_empty_search_criteria_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_client_empty_search_criteria_template.xml")
    query_data_client_missing_search_criteria_template_filename = os.path.join(
        request_template_dir,
        "query_operational_data_client_missing_search_criteria_template.xml")

    # Operational data requests and the relevant checks

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request where 'recordsTo' is "
          "earlier than 'recordsFrom' to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id, 1479823179,
        1479823175, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Earlier recordsTo than recordsFrom in operational monitoring
    # request must result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request where "
          "'recordsFrom' is in the future to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id, 2479823179,
        2479823185, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # recordsFrom >= (now - records-available-timestamp-offset-seconds)
    # in operational monitoring request must result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request without "
          "'recordsFrom' element to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_missing_recordsfrom_template_filename,
        message_id, None, 1479823185, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Missing recordsFrom element in operational monitoring request must 
    # result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request without "
          "'recordsTo' element to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_missing_recordsto_template_filename,
        message_id, 1479823185, None, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Missing recordsTo element in operational monitoring request must 
    # result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request without 'recordsFrom'"
          " and 'recordsTo' elements to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_empty_search_criteria_template_filename,
        message_id, None, None, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Missing recordsFrom and recordsTo elements in operational
    # monitoring request must result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request without 'searchCriteria'"
          " element to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_missing_search_criteria_template_filename,
        message_id, None, None, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Missing searchCriteria element in operational monitoring 
    # request must result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request with non-numeric 'recordsFrom'"
          " value to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id, "abc", 1479823185, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Non-numeric recordsFrom value in operational monitoring request must 
    # result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request with too large 'recordsTo'"
          " value to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id, 1479823185,
        888888888888888888888, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Too large recordsTo value in operational monitoring request must 
    # result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    print("\n---- Sending an operational data request with negative 'recordsFrom'"
          " and 'recordsTo' values to the client's security server ----\n")

    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id, -1479823185,
        -1479823183, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    # Negative recordsFrom and recordsTo values in operational
    # monitoring request must result in a SOAP fault
    common.assert_soap_fault(xml)

    message_id = common.generate_message_id()
    timestamp_before_request = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    print("\n---- Sending an operational data request where "
          "'recordsTo' is in the future to the client's security server ----\n")

    # Let's craft a request where recordsFrom is in the past 
    # and recordsTo is in the future
    request_contents = common.format_query_operational_data_request_template(
        query_data_client_template_filename, message_id,
        timestamp_before_request - 5, timestamp_before_request + 10, query_parameters)
    print("Generated the following operational data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents,
        get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(soap_part, record_count)
        # In case 'recordsTo' value is in the future, the element
        # 'nextRecordsFrom' is expected in operational 
        # monitoring response. 'nextRecordsFrom' value is expected to be 
        # (now - records-available-timestamp-offset-seconds).
        # records-available-timestamp-offset-seconds value is expected
        # to be set to 0 before the test in run_tests.py.
        common.assert_get_next_records_from_in_range(
            soap_part, timestamp_before_request)
    else:
        common.parse_and_check_soap_response(raw_response)
