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

# Shared functions for the integration and load testing scripts of
# operational monitoring.

# Optionally use mypy for type checking, based on PEP 484.
# Under Ubuntu 14.04:
# sudo apt-get install python3-pip
# sudo pip3 install mypy-lang
# sudo pip3 install typing

import sys
import json
import time
import zlib
import random
import string
import requests
import subprocess
import xml.dom.minidom as minidom
from typing import Tuple, Union, Optional
from email.encoders import encode_7or8bit
from email.mime.application import MIMEApplication
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

# Allow operational data to be stored a couple of seconds later than the
# moment the corresponding X-Road request was handled. Under normal
# load, longer delays indicate an issue. Even after the operational
# monitoring daemon has been restarted before the test, bootstrapping
# the internal components should not add more than a second. If
# an external operational monitoring daemon is used, we expect
# the clocks to be in sync with the security server.
# Additionally under heavy load store query may be delayed for up to
# 5 seconds (by default op-monitor-buffer.sending-interval-seconds = 5).
WAIT_FOR_OPERATIONAL_DATA_SECONDS = 6

# Size of the output limit for requests and responses that could be
# too big to be readable in the console output.
OUTPUT_SIZE_LIMIT = 5000


def generate_message_id() -> str:
    """ Return a random string of 32 ASCII characters and digits. """
    return ''.join([
        random.choice(string.ascii_letters + string.digits) for _ in range(32)])


def generate_user_and_server(server_address, ssh_user) -> str:
    """ Return user@server to be used with commands run over SSH.

    If ssh_user is not given, simply return the address of the server,
    assuming that the current user is suitable for running the command.
    """
    if ssh_user is None:
        return server_address
    return ssh_user + '@' + server_address


def get_remote_timestamp(server_address, ssh_user) -> int:
    """ Return the current Unix timestamp in seconds at the given
    server.
    """
    user_and_server = generate_user_and_server(server_address, ssh_user)
    command = "date +%s"
    return int(subprocess.check_output(["ssh", user_and_server, command, ]))


def get_opmonitor_restart_timestamp(server_address, ssh_user) -> int:
    """ Return the startup timestamp of xroad-opmonitor in seconds at
    the given server.
    """
    # status opmonitor: xroad-opmonitor start/running, process 24939
    # ls: dr-xr-xr-x 9 xroad xroad 0 1479823227 /proc/24939
    user_and_server = generate_user_and_server(server_address, ssh_user)
    command = ("ls -l --time-style=+%s -d /proc/$(systemctl show xroad-opmonitor | grep ExecMainPID | sed 's/ExecMainPID=//') | awk '{print $6}'")
    return int(subprocess.check_output(["ssh", user_and_server, command, ]))


def restart_service(server_address: str, service: str, ssh_user: str):
    """ Restart the given service at the given server. """
    if service == "opmonitor":
        target = "operational monitoring daemon"
    elif service == "proxy":
        target = "proxy"
    else:
        raise Exception("Programming error: unsupported service name")

    print("\nRestarting the {} in "
          "security server {}".format(target, server_address))
    command = "sudo service xroad-{} restart".format(service)
    user_and_server = generate_user_and_server(server_address, ssh_user)
    try:
        subprocess.check_call(["ssh", user_and_server, command, ])
    except subprocess.CalledProcessError as e:
        print(e)
        sys.exit(1)


def wait_for_operational_data():
    time.sleep(WAIT_FOR_OPERATIONAL_DATA_SECONDS)


def post_xml_request(
        request_path: str, data: str, get_raw_stream: bool = False) -> requests.Response:
    """ Send a POST request with the text/xml content type and return
    the response.
    """
    return requests.post(
        "http://" + request_path, data=data.encode("utf-8"),
        headers={"Content-type": "text/xml; charset=utf-8"},
        stream=get_raw_stream)


def post_multipart_request(
        request_path: str, data: str, attachment_count: int,
        get_raw_stream: bool = False) -> requests.Response:
    """ Build a MIME multipart message, send a POST request with the 
    multipart/related content type and return the response.
    """

    mime_message = MIMEMultipart("related", type="application/xop+xml", start="<rootpart>")

    xml = MIMEApplication(data.encode("utf-8"), "xop+xml", encode_7or8bit)
    xml.set_param("charset", "utf-8")
    xml.set_param("type", "text/xml")
    xml["Content-ID"] = "<rootpart>"
    mime_message.attach(xml)

    for i in range(attachment_count):
        attachment = MIMEText("text", "plain")
        attachment.set_payload("some data to send")
        mime_message.attach(attachment)

    body = mime_message.as_string().split("\n\n", 1)[1]
    headers = dict(mime_message.items())
    print("Generated the following multipart request: \n")
    print(body)

    return requests.post(
        "http://" + request_path, data=body, headers=headers, stream=get_raw_stream)


def make_get_request(request_path: str) -> requests.Response:
    """ Send a GET request and return the response. """
    return requests.get("http://" + request_path)


def check_status(response: requests.Response):
    """ Raise an exception if the response status is 4XX or 5XX. """
    response.raise_for_status()


def print_response_status_and_headers(response: requests.Response):
    print("HTTP status: " + str(response.status_code))

    for header, value in response.headers.items():
        print(header + ": " + value)


def format_xroad_request_template(
        request_template_filename: str, message_id: str, query_parameters: dict) -> str:
    """ Return the given request template with
    the message ID filled.
    """
    with open(request_template_filename) as template_file:
        request_template = template_file.read()
        return request_template.format(
            message_id_placeholder=message_id, params=query_parameters)


def format_query_operational_data_request_template(
        request_template_filename: str, message_id: str,
        # timestamp can be of type 'str' in negative tests
        timestamp_before_request: Union[int, None, str], timestamp_after_request: Optional[int],
        query_parameters: dict) -> str:
    """ Return the given request template with
    the message ID and timestamps filled.
    """
    with open(request_template_filename) as template_file:
        request_template = template_file.read()
        return request_template.format(
            message_id_placeholder=message_id,
            records_from_placeholder=timestamp_before_request,
            records_to_placeholder=timestamp_after_request,
            params=query_parameters)


def format_query_health_data_request_template(
        request_template_filename: str, message_id: str, query_parameters: dict):
    """ Return the given request template with
    the message ID filled.
    """
    with open(request_template_filename) as template_file:
        request_template = template_file.read()
        return request_template.format(
            message_id_placeholder=message_id, params=query_parameters)


def check_soap_fault(response_xml: minidom.Document):
    """ Raise an exception if the XML contains a SOAP fault element. """
    fault = _find_soap_fault(response_xml)
    if fault:
        print("The following unexpected SOAP fault was found in the response:")
        print(fault.toprettyxml())
        raise Exception("A SOAP fault was found in the response")


def assert_soap_fault(response_xml: minidom.Document):
    """ Raise an exception if the XML does not contain
    a SOAP fault element.
    """
    fault = _find_soap_fault(response_xml)
    if fault is None:
        raise Exception("The expected SOAP fault was not found in the response")


def parse_multipart_response(response: requests.Response) -> Tuple:
    """ Return a tuple of the MIME parts and the raw contents of
    a multipart response.
    
    If no boundary is found we assume the response is not a
    multipart one and the raw response can be used.
    """
    content_type_parts = response.headers.get("content-type").split(';')
    boundary = None
    for part in content_type_parts:
        part = part.strip()
        if part.startswith("boundary="):
            # Content-Type header can contain boundary="foo"
            part = part.replace('"', "")
            boundary = "--" + part.replace("boundary=", "")
            break

    contents = response.raw.read()
    mime_parts = []

    if boundary:
        # The response was a multipart message and the parts can be
        # processed.
        for part in contents.split(boundary.encode('utf-8')):
            if part:
                mime_parts.append(part)

    return mime_parts, contents


def clean_whitespace(contents: str) -> str:
    """ Return the given string with all line breaks and spaces removed.

    For some reason our mock service returns a SOAP response with lots
    of whitespace.
    """
    lines = [line.strip(" \r\n") for line in contents.split('\n')]
    return ''.join(lines)


def parse_and_clean_xml(xml_string: str) -> minidom.Document:
    return minidom.parseString(clean_whitespace(xml_string))


def get_multipart_soap_and_record_count(
        response_xml_part: bytes) -> Tuple[bytes, int]:
    """ Return the SOAP part and the record count in the query data
    response.

    Expecting response_xml_part to be the first part of
    the MIME multipart response.
    """
    return _extract_operational_data_response_and_record_count(response_xml_part)


def get_multipart_soap(response_xml_part: bytes) -> bytes:
    """ Return the SOAP part in the response.

    Expecting response_xml_part to be the first part of
    the MIME multipart response.
    """
    return _extract_operational_data_response(response_xml_part)


def get_multipart_json_payload(gzipped_json_payload: bytes) -> dict:
    """ Return the gunzipped JSON payload of the query data
    response.
    """
    return json.loads(_decompress_gzipped_attachment(gzipped_json_payload).decode('utf-8'))


def check_record_count(record_count: int, expected_record_count: int):
    """ Raise an exception if the number of JSON records in response
    payload is inconsistent with the expectation.
    """
    if record_count != expected_record_count:
        print("The JSON record count ({}) is not {} "
              "as expected.".format(record_count, expected_record_count))
        raise Exception("JSON record count is not in the expected range")


def assert_present_in_json(json_payload: dict, fields_and_values: list):
    """ Check if there exists a record where all the fields and values
    match.

    Otherwise, throw an exception -- the response was inconsistent with
    the expectation.
    We cannot assume that there are no other records in the JSON payload
    but we can check that exactly a single record contains all
    the required fields and values.
    """
    full_match_found = False
    for rec in json_payload.get("records", []):
        fields_found = dict()
        for field, value in fields_and_values:
            fields_found[field] = (rec.get(field) == value)
        if all(found is True for found in fields_found.values()):
            # In this record, all the fields and values matched
            # the expectation.
            full_match_found = True
            break

    if not full_match_found:
        print("The following JSON was received in the response:")
        print(json.dumps(json_payload, indent=4, sort_keys=True))
        print("The following fields and values were expected:")
        print(fields_and_values)
        raise Exception(
            "No record was found where all the expected fields and values matched")


def assert_missing_in_json(json_payload: dict, field: str):
    """ Raise an exception if the field exists in any JSON record in 
    response payload.
    """
    for rec in json_payload.get("records", []):
        if rec.get(field):
            raise Exception("The field '{}' is not missing from JSON record "
                            "as expected".format(field))


def assert_json_fields(json_payload: dict, fields_and_values: list):
    """ Raise an exception if the field value or number of fields in
    any JSON record in response payload is inconsistent with
    the expectation.
    """
    for rec in json_payload.get("records", []):
        for field, value in fields_and_values:
            if rec.get(field) != value:
                print("The following fields and values were expected:")
                print(fields_and_values)
                raise Exception("An inconsistent JSON record was found")

        if len(rec) != len(fields_and_values):
            print("The following fields and values were expected:")
            print(fields_and_values)
            raise Exception("JSON record field count is not in the expected range")


def assert_empty_json_records(json_payload: dict):
    """ Raise an exception if any JSON record in response 
    payload is not empty. """
    for rec in json_payload.get("records", []):
        if str(rec) != '{}':
            raise Exception("JSON record is not empty")


def assert_response_mime_size_in_range(
        json_payload: dict, message_id: str, expected_size: int,
        expected_number_of_matches: int):
    """ Check if responseMimeSize is in the expected range for
    the expected number of recs.

    Throw an exception if there are issues with the values or
    the number of matches.
    """
    _assert_operational_data_response_value_in_range(
        json_payload, message_id, "responseMimeSize", expected_size,
        # The response MIME size can vary due to the nature of
        # the gzip compression algorithm (+- 5 bytes?) and due to
        # the presence or absence of the
        # nextRecordsFrom element in the SOAP part of
        # the response (+- 55 bytes?).
        expected_variation=65,
        expected_number_of_matches=expected_number_of_matches)


def assert_response_soap_size_in_range(
        json_payload: dict, message_id: str, expected_size: int,
        expected_number_of_matches: int):
    """ Check if responseSize is in the expected range for
    the expected number of recs.

    Throw an exception if there are issues with the values or
    the number of matches.
    """
    _assert_operational_data_response_value_in_range(
        json_payload, message_id, "responseSize", expected_size,
        # Allow the response SOAP size to vary by 55 bytes
        # -- the nextRecordsFrom element may be present or not.
        expected_variation=55,
        expected_number_of_matches=expected_number_of_matches)


def assert_expected_timestamp_values(
        json_payload: dict, timestamp_before_request: int,
        timestamp_after_request: int):
    """ Check if timestamp values are in expected range.

    Expecting the timestamps have been queried from the target host
    after waiting for operational data to become available.

    Throw an exception if the response was inconsistent with
    the expectation.
    """
    for rec in json_payload.get("records", []):
        timestamp_fields = ["monitoringDataTs", "requestInTs",
                            "requestOutTs", "responseInTs", "responseOutTs"]
        # If proxy responds with a fault, requestOutTs and responseInTs
        # fields are not required
        if (rec.get("faultCode") in ["Server.ServerProxy.UnknownService",
                                         "Server.ClientProxy.UnknownMember"]):
            timestamp_fields.remove("requestOutTs")
            timestamp_fields.remove("responseInTs")
        for field in timestamp_fields:
            if field == "monitoringDataTs":
                timestamp = rec.get("monitoringDataTs")
            else:
                timestamp = int(rec.get(field) / 1000)
            if not timestamp_before_request <= timestamp <= timestamp_after_request:
                print("The actual value of the field '{}' ({}) is not in the "
                      "expected range [{} - {}]".format(
                        field, rec.get(field),
                        timestamp_before_request, timestamp_after_request))
                raise Exception("Timestamp value is not in the expected range")


def assert_equal_timestamp_values(json_payload: dict):
    """ Check if timestamp value 'requestInTs' is equal with
    'requestOutTs' and 'responseInTs' is equal with 'responseOutTs'
    as expected.

    Otherwise, throw an exception -- the response was inconsistent with
    the expectation.
    """
    for rec in json_payload.get("records", []):
        if not (rec.get("requestInTs") == rec.get("requestOutTs")
                and rec.get("responseInTs") == rec.get("responseOutTs")):
            raise Exception("Expected timestamp values are not equal")


def print_multipart_soap_and_record_count(
        soap_part: bytes, record_count: int, is_client: bool = True):
    print("Received the following SOAP response from the "
          "security server of the {}: \n".format("client" if is_client else "producer"))
    xml = minidom.parseString(clean_whitespace(soap_part.decode("utf-8")))
    print(xml.toprettyxml())

    print("The expected number of JSON records in the response payload: {}".format(record_count))


def print_multipart_soap(soap_part: bytes):
    print("\nReceived the following SOAP response from the "
          "security server: \n")
    xml = minidom.parseString(clean_whitespace(soap_part.decode("utf-8")))
    print(xml.toprettyxml())


def print_multipart_soap_headers(soap_part: bytes):
    print("\nReceived the following SOAP response headers from the "
          "security server: \n")
    xml = minidom.parseString(clean_whitespace(soap_part.decode("utf-8")))
    xroad_response_headers = xml.getElementsByTagName(
        "SOAP-ENV:Header")[0].toprettyxml()
    print(xroad_response_headers)


def print_multipart_query_data_response(
        json_payload: dict, expected_message_id: str = None):
    expected_id_part = "" if not expected_message_id else \
        "(expecting messageId {})".format(expected_message_id)
    print("Received the following JSON payload in the response {}:\n ".format(
        expected_id_part))

    print(json.dumps(json_payload, indent=4, sort_keys=True))


def parse_and_check_soap_response(raw_response: bytes):
    xml = minidom.parseString(clean_whitespace(raw_response.decode("utf-8")))
    check_soap_fault(xml)
    print_plain_soap_query_data_response(xml)


def print_plain_soap_query_data_response(response_xml: minidom.Document):
    print("Received the following plain SOAP response from the security server:\n")
    plain_soap = response_xml.toprettyxml()
    # Truncating output if bigger then OUTPUT_SIZE_LIMIT characters
    # For example listMethodsResponse and especially
    # getSecurityServerMetricsResponse can have large SOAP Bodies.
    if len(plain_soap) > OUTPUT_SIZE_LIMIT:
        print(plain_soap[:OUTPUT_SIZE_LIMIT])
        print("... (truncated to {} characters for readability)".format(OUTPUT_SIZE_LIMIT))
    else:
        print(plain_soap)


def assert_get_next_records_from_in_range(operational_data_response_soap: bytes,
                                          timestamp_before_request: int):
    """ Check if 'nextRecordsFrom' value is in expected range.
    
    Otherwise, throw an exception -- the response was inconsistent with
    the expectation.
    """
    xml = minidom.parseString(operational_data_response_soap)
    next_records_from = xml.documentElement.getElementsByTagName("om:nextRecordsFrom")
    if next_records_from:
        next_records_from_value = int(next_records_from[0].firstChild.nodeValue)
        # Allow 'nextRecordsFrom' to be equal or one second later from
        # the timestamp value recorded in the server before the request
        # was sent. Responding to the request is not expected to take
        # more than a second.
        if not (timestamp_before_request <= next_records_from_value
                <= timestamp_before_request + 1):
            print("The actual value of 'nextRecordsFrom' ({}) is not in the "
                  "expected range".format(next_records_from_value))
            raise Exception("nextRecordsFrom value is not in the expected range")
    else:
        raise Exception("nextRecordsFrom was not found in the operational data response")


def remove_key_from_list(key: str, list_of_expected_keys_and_values: list):
    for expected_keys_and_values in list_of_expected_keys_and_values:
        for i, (field, value) in enumerate(expected_keys_and_values):
            if field == key:
                del expected_keys_and_values[i]
                break


# Helpers

def _find_soap_fault(response_xml: minidom.Document) -> Optional[minidom.Element]:
    # First, turn the whole XML tree to lowercase so we won't miss
    # erroneous mock responses etc.
    response_xml = minidom.parseString(response_xml.toxml().lower())

    fault_elements = response_xml.documentElement.getElementsByTagName("soap-env:fault")
    if fault_elements:
        return fault_elements[0]

    return None


def _find_operational_data_response_record_count(response_xml: minidom.Document) -> Optional[int]:
    record_count = response_xml.documentElement.getElementsByTagName("om:recordsCount")
    if record_count:
        return int(record_count[0].firstChild.nodeValue)

    return None


def _extract_operational_data_response_and_record_count(
        response_xml_part: bytes) -> Tuple[bytes, int]:
    response_xml = response_xml_part[response_xml_part.index(b"<?xml"):]
    dom = minidom.parseString(response_xml)
    record_count = _find_operational_data_response_record_count(dom)
    if record_count is None:
        raise Exception("The record count was not found in the operational data response")

    return response_xml, _find_operational_data_response_record_count(dom)


def _extract_operational_data_response(response_xml_part: bytes) -> bytes:
    response_xml = response_xml_part[response_xml_part.index(b"<?xml"):]
    return response_xml


def _decompress_gzipped_attachment(attachment: bytes) -> bytes:
    headers = \
        b"\r\ncontent-type:application/gzip\r\n" \
        b"content-transfer-encoding: binary\r\n" \
        b"content-id: <operational-monitoring-data.json.gz>\r\n\r\n"

    gzipped_payload = attachment[len(headers):].rpartition(b'\r\n')[0]
    # From the manual of zlib:
    # 32 + (8 to 15): Uses the low 4 bits of the value as the window
    # size logarithm, and automatically accepts either the zlib or gzip
    # format.
    # +8 to +15: The base-two logarithm of the window size.
    # The input must include a zlib header and trailer.
    decompressed_payload = zlib.decompress(gzipped_payload, 32 + 15)
    return decompressed_payload


def _assert_operational_data_response_value_in_range(
        json_payload: dict, message_id: str, field_name: str,
        expected_value: int, expected_variation: int, expected_number_of_matches: int):
    found_matches = 0
    lower_limit = expected_value - expected_variation
    upper_limit = expected_value + expected_variation
    for rec in json_payload.get("records", []):
        if rec.get("messageId") == message_id:
            found_matches += 1
            actual_value = rec.get(field_name)
            print("Asserting field_name={}: expected_value={}, actual_value={}".format(
                field_name, expected_value, actual_value))
            if not (lower_limit <= actual_value <= upper_limit):
                raise Exception(
                    "The actual value of the field '{}' ({}) is not in the "
                    "expected range {} from the value {}".format(
                        field_name, actual_value, expected_variation, expected_value))

    if found_matches < expected_number_of_matches:
        raise Exception(
            "The expected number of records ({}) was not found with expected "
            "message ID ({})".format(expected_number_of_matches, message_id))
