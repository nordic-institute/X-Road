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

# Test case for verifying that operational data is returned in correct
# batches if the amount of relevant records exceeds the value of the
# max-records-in-payload configuration property.
# Expecting that this value has been set via run_tests.py.

import os
import copy
import time
import xml.dom.minidom as minidom
from typing import Optional
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

    xroad_message_ids = []
    client_timestamp_before_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_before_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    # Repeat a regular X-Road request with a different message ID.

    print("\n---- Sending regular X-Road requests to the client's security server ----\n")
    for _ in range(30):
        message_id = common.generate_message_id()
        xroad_message_ids.append(message_id)

        print("Sending a request with message ID {}".format(message_id))
        request_contents = common.format_xroad_request_template(
            xroad_request_template_filename, message_id, query_parameters)
        response = common.post_xml_request(
            client_security_server_address, request_contents)

        common.check_soap_fault(minidom.parseString(response.text))

        time.sleep(1)

    common.wait_for_operational_data()

    client_timestamp_after_requests = common.get_remote_timestamp(
        client_security_server_address, ssh_user)
    producer_timestamp_after_requests = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    # Make operational data requests until all the records have been
    # received or no more are offered.

    print("\n---- Sending operational data requests to the client ----\n")
    _query_operational_data(
        query_data_client_template_filename, client_security_server_address,
        client_timestamp_before_requests, client_timestamp_after_requests,
        copy.deepcopy(xroad_message_ids), query_parameters)

    print("\n---- Sending operational data requests to the producer ----\n")
    _query_operational_data(
        query_data_producer_template_filename, producer_security_server_address,
        producer_timestamp_before_requests, producer_timestamp_after_requests,
        copy.deepcopy(xroad_message_ids), query_parameters, is_client=False)


def _query_operational_data(
        request_template_filename, security_server_address,
        timestamp_before_requests, timestamp_after_requests, expected_message_ids,
        query_parameters, is_client=True):
    # Start with the initial timestamp we obtained before sending
    # the regular requests.
    next_records_from = timestamp_before_requests
    found_message_ids = set()

    while next_records_from is not None:
        # Send one request per second. This means we should get all
        # the records by the end of this loop for sure even with
        # records-available-timestamp-offset-seconds set to several
        # seconds.
        time.sleep(1)

        print("\nUsing recordsFrom with the value ", next_records_from)
        message_id = common.generate_message_id()

        request_contents = common.format_query_operational_data_request_template(
            request_template_filename, message_id,
            next_records_from, timestamp_after_requests, query_parameters)
        print("Generated the following operational data request: \n")
        print(request_contents)

        response = common.post_xml_request(
            security_server_address, request_contents, get_raw_stream=True)
        mime_parts, raw_response = common.parse_multipart_response(response)

        if not mime_parts:
            common.parse_and_check_soap_response(raw_response)
            raise Exception("Expected a multipart response, received a plain SOAP response")

        soap_part, record_count = common.get_multipart_soap_and_record_count(mime_parts[0])
        common.print_multipart_soap_and_record_count(
            soap_part, record_count, is_client=is_client)

        json_payload = common.get_multipart_json_payload(mime_parts[1])
        records = json_payload.get("records")
        for record in records:
            rec_message_id = record.get("messageId")
            found_message_ids.add(rec_message_id)
            if rec_message_id and rec_message_id in expected_message_ids:
                # One of the messages has been found, remove it from
                # the list of expected ID-s. Note that some of
                # the records received match the operational data
                # requests we are sending so we might not find matching
                # ID-s in each response.
                print("Found operational data matching message ID", rec_message_id)
                expected_message_ids.remove(rec_message_id)

        if not expected_message_ids:
            # We have received all the data we expected.
            print("Received all the expected records")
            break

        next_records_from = _get_next_records_from(soap_part)

    if expected_message_ids:
        raise Exception(
            "Operational data about some of the requests sent, was not received "
            "(remaining message ID-s: {})".format(", ".join(expected_message_ids)))


def _get_next_records_from(operational_data_response_soap: bytes) -> Optional[int]:
    """ Return the value of om:nextRecordsFrom if found.

    Sample snippet of operational data response:

    <om:getSecurityServerOperationalDataResponse>
            <om:recordsCount>1</om:recordsCount>
            <om:records>cid:operational-monitoring-data.json.gz</om:records>
            <om:nextRecordsFrom>1479316369</om:nextRecordsFrom>
    </om:getSecurityServerOperationalDataResponse>
    """
    xml = minidom.parseString(operational_data_response_soap)
    next_records_from = xml.documentElement.getElementsByTagName("om:nextRecordsFrom")
    if next_records_from:
        return int(next_records_from[0].firstChild.nodeValue)

    return None
