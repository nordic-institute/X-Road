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

# Test case for verifying that the health data gathered by the
# operational monitoring daemon can be retrieved and is correct, within
# the configured statistics period.

import os
import time
import requests
import common

# This value is ensured to exist in the configuration of the operational
# monitoring daemon via run_tests.py.
# Values lower than 20 might result in failed tests on servers using
# slow USB HSM devices (eTokens).
STATISTICS_PERIOD_SECONDS = 20

MOCK_SERVICE_XML_TEMPLATE = (
    "<om:service id:objectType=\"SERVICE\">"
    "<id:xRoadInstance>{params[producer_instance]}</id:xRoadInstance>"
    "<id:memberClass>{params[producer_class]}</id:memberClass>"
    "<id:memberCode>{params[producer_code]}</id:memberCode>"
    "<id:subsystemCode>{params[producer_system]}</id:subsystemCode>"
    "<id:serviceCode>mock</id:serviceCode><id:serviceVersion>v1</id:serviceVersion></om:service>")

GET_HEALTH_DATA_SERVICE_XML_TEMPLATE = (
    "<om:service id:objectType=\"SERVICE\">"
    "<id:xRoadInstance>{params[producer_instance]}</id:xRoadInstance>"
    "<id:memberClass>{params[producer_class]}</id:memberClass>"
    "<id:memberCode>{params[producer_code]}</id:memberCode>"
    "<id:serviceCode>getSecurityServerHealthData</id:serviceCode></om:service>")

LISTMETHODS_SERVICE_XML_TEMPLATE = (
    "<om:service id:objectType=\"SERVICE\">"
    "<id:xRoadInstance>{params[client_instance]}</id:xRoadInstance>"
    "<id:memberClass>{params[client_class]}</id:memberClass>"
    "<id:memberCode>{params[client_code]}</id:memberCode>"
    "<id:subsystemCode>{params[client_system]}</id:subsystemCode>"
    "<id:serviceCode>listMethods</id:serviceCode>"
    "<id:serviceVersion>v1</id:serviceVersion></om:service>")

# For statistical values related to request duration we expect that the
# fields are present but we cannot expect much about the values -- these
# depend on network load and capabilities as well as the load of the
# target hosts. We can expect with some certainty that the SOAP size
# related data is stable for each given service at a given type of
# server. NOTE: health data are gathered only for the requests that
# have been served in the producer (server proxy) role.
SAMPLE_PRODUCER_MOCK_STATS = {
    "om:successfulRequestCount": 1,
    "om:unsuccessfulRequestCount": 0,
    "om:requestMinDuration": 74.8,
    "om:requestAverageDuration": 78.8,
    "om:requestMaxDuration": 82,
    "om:requestDurationStdDev": 5.65685,
    "om:requestMinSize": 1629,
    "om:requestAverageSize": 1629.0,
    "om:requestMaxSize": 1629,
    "om:requestSizeStdDev": 0.0,
    "om:responseMinSize": 1519,
    "om:responseAverageSize": 1519.0,
    "om:responseMaxSize": 1519,
    "om:responseSizeStdDev": 0.0,
}

SAMPLE_CLIENT_LISTMETHODS_STATS = {
    "om:successfulRequestCount": 1,
    "om:unsuccessfulRequestCount": 0,
    "om:requestMinDuration": 110,
    "om:requestAverageDuration": 110.0,
    "om:requestMaxDuration": 110,
    "om:requestDurationStdDev": 0.0,
    "om:requestMinSize": 1118,
    "om:requestAverageSize": 1118.0,
    "om:requestMaxSize": 1118,
    "om:requestSizeStdDev": 0.0,
    "om:responseMinSize": 2305,
    "om:responseAverageSize": 2305.0,
    "om:responseMaxSize": 2305,
    "om:responseSizeStdDev": 0.0,
}

PREDICTABLE_FIELDS_MOCK = (
    "om:requestMinSize",
    "om:requestMaxSize",
    "om:requestAverageSize",
    "om:requestSizeStdDev",
    "om:responseMinSize",
    "om:responseAverageSize",
    "om:responseMaxSize",
    "om:responseSizeStdDev",
)

# Cannot predict listmethods response size, because subsystem might have
# additional services configured.
PREDICTABLE_FIELDS_LISTMETHODS = (
    "om:requestMinSize",
    "om:requestMaxSize",
    "om:requestAverageSize",
    "om:requestSizeStdDev",
)

# All these fields must be missing in the lastPeriodStatistics element
# for a given service if the value of om:successfulRequestCount is 0.
STATISTICS_FIELDS = (
    "om:requestMinDuration",
    "om:requestAverageDuration",
    "om:requestMaxDuration",
    "om:requestDurationStdDev",
    "om:requestMinSize",
    "om:requestAverageSize",
    "om:requestMaxSize",
    "om:requestSizeStdDev",
    "om:responseMinSize",
    "om:responseAverageSize",
    "om:responseMaxSize",
    "om:responseSizeStdDev",
)

# Base sizes of request and responses.
# Parameters sizes must be added to these values.
SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE = 1461
SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE = 1503
LISTMETHODS_CLIENT_QUERY_REQUEST_SOAP_BASE_SIZE = 1062


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


def _get_producer_mock_stats(query_parameters):
    request_parameters_size = _simple_query_request_parameters_size(query_parameters)
    print("Size of simple query request parameters: {}".format(request_parameters_size))
    updated_stats = SAMPLE_PRODUCER_MOCK_STATS.copy()
    updated_stats["om:requestMinSize"] = \
        SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:requestAverageSize"] = \
        0.0 + SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:requestMaxSize"] = \
        SIMPLE_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:responseMinSize"] = \
        SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:responseAverageSize"] = \
        0.0 + SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:responseMaxSize"] = \
        SIMPLE_QUERY_RESPONSE_SOAP_BASE_SIZE + request_parameters_size
    return updated_stats


def _listmethods_client_query_request_parameters_size(query_parameters):
    # Request template: listmethods_client_query_template.xml
    return (
        2 * len(query_parameters["client_instance"])
        + 2 * len(query_parameters["client_class"])
        + 2 * len(query_parameters["client_code"])
        + 2 * len(query_parameters["client_system"])
    )


def _get_client_listmethods_stats(query_parameters):
    request_parameters_size = _listmethods_client_query_request_parameters_size(query_parameters)
    print("Size of listmethods request parameters: {}".format(request_parameters_size))
    updated_stats = SAMPLE_PRODUCER_MOCK_STATS.copy()
    updated_stats["om:requestMinSize"] = \
        LISTMETHODS_CLIENT_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:requestAverageSize"] = \
        0.0 + LISTMETHODS_CLIENT_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    updated_stats["om:requestMaxSize"] = \
        LISTMETHODS_CLIENT_QUERY_REQUEST_SOAP_BASE_SIZE + request_parameters_size
    return updated_stats


def run(request_template_dir, query_parameters):
    client_security_server_address = query_parameters["client_server_ip"]
    producer_security_server_address = query_parameters["producer_server_ip"]
    ssh_user = query_parameters["ssh_user"]

    xroad_request_template_filename = os.path.join(
        request_template_dir, "simple_xroad_query_template.xml")
    listmethods_query_template_filename = os.path.join(
        request_template_dir, "listmethods_client_query_template.xml")
    soap_fault_query_template_filename = os.path.join(
        request_template_dir, "soap_fault_query_template.xml")

    query_data_client_template_filename = os.path.join(
        request_template_dir, "query_health_data_client_template.xml")
    query_data_producer_template_filename = os.path.join(
        request_template_dir, "query_health_data_producer_template.xml")
    query_data_invalid_client_template_filename = os.path.join(
        request_template_dir, "query_health_data_invalid_client_template.xml")
    query_data_unknown_client_template_filename = os.path.join(
        request_template_dir, "query_health_data_unknown_client_template.xml")
    query_data_without_client_template_filename = os.path.join(
        request_template_dir, "query_health_data_without_client.xml")

    producer_initial_timestamp = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)
    producer_opmonitor_restart_timestamp = common.get_opmonitor_restart_timestamp(
        producer_security_server_address, ssh_user)
    client_opmonitor_restart_timestamp = common.get_opmonitor_restart_timestamp(
        client_security_server_address, ssh_user)

    # First, send a regular X-Road request.

    xroad_message_id = common.generate_message_id()
    print("\nGenerated message ID {} for X-Road request".format(xroad_message_id))

    print("\n---- Sending an X-Road request to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        xroad_request_template_filename, xroad_message_id, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)
    print("Received the following X-Road response: \n")

    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    common.wait_for_operational_data()

    # Make a health check request to the producer.

    print("\n---- Sending a health data request to the producer's "
          "security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    request_contents = common.format_query_health_data_request_template(
        query_data_producer_template_filename, message_id, query_parameters)
    producer_health_data_request = request_contents

    print("Generated the following health data request for the producer's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents)

    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    _assert_monitoring_daemon_start_timestamp_in_range(
        response, producer_opmonitor_restart_timestamp)
    _assert_stats_period(response, STATISTICS_PERIOD_SECONDS)

    print("Looking for the mock service in the response")

    event_data = _find_health_data_events_for_service(
        response, MOCK_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about mock was not found in the response")

    _assert_last_successful_event_timestamp_in_range(event_data, producer_initial_timestamp)
    _assert_successful_events_count(event_data, 1)
    _assert_unsuccessful_events_count(event_data, 0)

    _assert_xml_tags_present(event_data, SAMPLE_PRODUCER_MOCK_STATS.keys())

    _assert_xml_tags_match_values(
        event_data, PREDICTABLE_FIELDS_MOCK, _get_producer_mock_stats(query_parameters))

    # Send a listMethods request to the client.

    listmethods_message_id = common.generate_message_id()
    print("\nGenerated message ID {} for the listMethods request to the "
          "client".format(listmethods_message_id))

    print("\n---- Sending a listMethods request to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        listmethods_query_template_filename, listmethods_message_id, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents, get_raw_stream=True)
    mime_parts, raw_response = common.parse_multipart_response(response)

    if mime_parts:
        soap_part = common.get_multipart_soap(mime_parts[0])
        common.print_multipart_soap(soap_part)

    else:
        common.parse_and_check_soap_response(raw_response)

    # Send a health data request to the client.

    client_pre_health_data_timestamp = common.get_remote_timestamp(
        client_security_server_address, ssh_user)

    common.wait_for_operational_data()

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    print("\n---- Sending a health data request to the client's "
          "security server ----\n")

    request_contents = common.format_query_health_data_request_template(
        query_data_client_template_filename, message_id, query_parameters)
    client_health_data_request = request_contents

    print("Generated the following health data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    _assert_monitoring_daemon_start_timestamp_in_range(
        response, client_opmonitor_restart_timestamp)
    _assert_stats_period(response, STATISTICS_PERIOD_SECONDS)

    print("Looking for the listMethods service in the response")

    event_data = _find_health_data_events_for_service(
        response, LISTMETHODS_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about listMethods was not found in the response")

    _assert_last_successful_event_timestamp_in_range(
        event_data, client_pre_health_data_timestamp)
    _assert_successful_events_count(event_data, 1)
    _assert_unsuccessful_events_count(event_data, 0)

    _assert_xml_tags_present(event_data, SAMPLE_CLIENT_LISTMETHODS_STATS.keys())

    _assert_xml_tags_match_values(
        event_data, PREDICTABLE_FIELDS_LISTMETHODS,
        _get_client_listmethods_stats(query_parameters))

    # Send a health data request to the client, using an invalid
    # client ID in the query criteria.

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    print("\n---- Sending a health data request to the client's "
          "security server, using an invalid client in the filter criteria ----\n")

    request_contents = common.format_query_health_data_request_template(
        query_data_invalid_client_template_filename, message_id, query_parameters)

    print("Generated the following health data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        client_security_server_address, request_contents)

    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    # Using an invalid client ID must result in a SOAP fault.
    common.assert_soap_fault(xml)

    # Send an unfiltered health data request to the client, using
    # the producer as the service provider.

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    print("\n---- Sending an unfiltered health data request to the client's "
          "security server, using the producer as the service provider ----\n")

    request_contents = common.format_query_health_data_request_template(
        query_data_without_client_template_filename, message_id, query_parameters)

    print("Generated the following health data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    # This response must contain several serviceEvents elements (for
    # all the requests that were made to the producer above, including
    # the initial health data request).
    _assert_service_events_min_count(response, 2)

    event_data = _find_health_data_events_for_service(
        response, MOCK_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about mock service was not found in the response")

    event_data = _find_health_data_events_for_service(
        response, GET_HEALTH_DATA_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception(
            "Health data about getSecurityServerHealthData was not found in the response")

    # Send a request using an unknown client ID in the filter. Expect
    # an empty response is returned.

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    print("\n---- Sending a health data request with an unknown client ID to the client's "
          "security server ----\n")

    request_contents = common.format_query_health_data_request_template(
        query_data_unknown_client_template_filename, message_id, query_parameters)

    print("Generated the following health data request for the client's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)
    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    common.check_soap_fault(xml)
    _assert_no_events(response)

    # Sleep and expect that the health data will be reset.

    print("Waiting for the health metrics to be reset\n")
    time.sleep(STATISTICS_PERIOD_SECONDS)

    # Repeat the health data requests and check if the health data has
    # been reset.

    print("Repeating the health data request to the producer\n")
    response = common.post_xml_request(
        producer_security_server_address, producer_health_data_request)

    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    _assert_monitoring_daemon_start_timestamp_in_range(
        response, producer_opmonitor_restart_timestamp)
    _assert_stats_period(response, STATISTICS_PERIOD_SECONDS)

    event_data = _find_health_data_events_for_service(
        response, MOCK_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about mock service was not found in the response")

    _assert_successful_events_count(event_data, 0)
    _assert_unsuccessful_events_count(event_data, 0)
    _assert_xml_tags_missing(event_data, STATISTICS_FIELDS)

    print("Repeating the health data request to the client\n")
    response = common.post_xml_request(
        client_security_server_address, client_health_data_request)

    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    _assert_monitoring_daemon_start_timestamp_in_range(
        response, client_opmonitor_restart_timestamp)
    _assert_stats_period(response, STATISTICS_PERIOD_SECONDS)

    event_data = _find_health_data_events_for_service(
        response, LISTMETHODS_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about listMethods was not found in the response")

    _assert_successful_events_count(event_data, 0)
    _assert_unsuccessful_events_count(event_data, 0)
    _assert_xml_tags_missing(event_data, STATISTICS_FIELDS)

    # Now make an unsuccessful request and check the relevant
    # health data.

    producer_pre_unsuccessful_timestamp = common.get_remote_timestamp(
        producer_security_server_address, ssh_user)

    message_id = common.generate_message_id()
    print("\nGenerated message ID {} for an X-Road request that will cause "
          "a SOAP fault".format(message_id))

    print("\n---- Sending an X-Road request that will cause a SOAP fault at the "
          "service provider, to the client's security server ----\n")

    request_contents = common.format_xroad_request_template(
        soap_fault_query_template_filename, message_id, query_parameters)
    print("Generated the following X-Road request: \n")
    print(request_contents)

    response = common.post_xml_request(client_security_server_address, request_contents)

    print("\nReceived the following X-Road response: \n")
    xml = common.parse_and_clean_xml(response.text)
    print(xml.toprettyxml())

    common.assert_soap_fault(xml)

    common.wait_for_operational_data()

    # Send a health check request to the producer.

    print("\n---- Sending a health data request to the producer's "
          "security server ----\n")

    message_id = common.generate_message_id()
    print("Generated message ID {} for health data request".format(message_id))

    request_contents = common.format_query_health_data_request_template(
        query_data_producer_template_filename, message_id, query_parameters)

    print("Generated the following health data request for the producer's "
          "security server: \n")
    print(request_contents)

    response = common.post_xml_request(
        producer_security_server_address, request_contents)

    xml = common.parse_and_clean_xml(response.text)
    print("Received the following health data response:\n")
    print(xml.toprettyxml())

    common.check_soap_fault(xml)

    # The service is mock but the result was a fault.
    print("Looking for the mock service in the response")

    event_data = _find_health_data_events_for_service(
        response, MOCK_SERVICE_XML_TEMPLATE.format(params=query_parameters))
    if event_data is None:
        raise Exception("Health data about mock service was not found in the response")

    _assert_successful_events_count(event_data, 0)
    _assert_unsuccessful_events_count(event_data, 1)
    _assert_last_unsuccessful_event_timestamp_in_range(
        event_data, producer_pre_unsuccessful_timestamp)


# Helpers

def _parse_xml_for_health_data(health_data_response: requests.Response):
    xml = common.parse_and_clean_xml(health_data_response.text)
    return xml.documentElement.getElementsByTagName(
        "om:getSecurityServerHealthDataResponse")[0]


def _find_all_health_data_events(health_data_response: requests.Response):
    health_data = _parse_xml_for_health_data(health_data_response)
    return health_data.getElementsByTagName("om:serviceEvents")


def _find_health_data_events_for_service(
        health_data_response: requests.Response, service_id: str):
    """ Return the XML subtree of the event data matching the service ID.

    @param health_data_response: a requests.Response object as received from the server
    @param service_id: The XML snippet describing the expected service ID as as string.

    Sample snippet of the health data response:
    <om:getSecurityServerHealthDataResponse>
      <om:monitoringStartupTimestamp>1479127077649</om:monitoringStartupTimestamp>
      <om:statisticsPeriodSeconds>60</om:statisticsPeriodSeconds>
      <om:servicesEvents>
        <om:serviceEvents>
          <om:service id:objectType="SERVICE">
            <id:xRoadInstance>INST</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000000</id:memberCode>
            <id:subsystemCode>System0</id:subsystemCode>
            <id:serviceCode>mock</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
          </om:service>
          <om:lastSuccessfulRequestTimestamp>1479127575144</om:lastSuccessfulRequestTimestamp>
          <om:lastPeriodStatistics>
            <om:successfulRequestCount>1</om:successfulRequestCount>
            <om:unsuccessfulRequestCount>0</om:unsuccessfulRequestCount>
            <om:requestMinDuration>78</om:requestMinDuration>
            <om:requestAverageDuration>78.0</om:requestAverageDuration>
            <om:requestMaxDuration>78</om:requestMaxDuration>
            <om:requestDurationStdDev>0.0</om:requestDurationStdDev>
            <om:requestMinSize>1629</om:requestMinSize>
            <om:requestAverageSize>1629.0</om:requestAverageSize>
            <om:requestMaxSize>1629</om:requestMaxSize>
            <om:requestSizeStdDev>0.0</om:requestSizeStdDev>
            <om:responseMinSize>1519</om:responseMinSize>
            <om:responseAverageSize>1519.0</om:responseAverageSize>
            <om:responseMaxSize>1519</om:responseMaxSize>
            <om:responseSizeStdDev>0.0</om:responseSizeStdDev>
          </om:lastPeriodStatistics>
        </om:serviceEvents>
        <om:serviceEvents>
                ...
    """
    health_data = _parse_xml_for_health_data(health_data_response)
    for event_data in health_data.getElementsByTagName("om:serviceEvents"):
        if event_data.getElementsByTagName("om:service"):
            service = event_data.getElementsByTagName("om:service")[0]
            # Return the whole surrounding serviceEvents element.
            if service.toxml() == service_id:
                return event_data

    return None


def _assert_no_events(health_data_response: requests.Response):
    health_data = _parse_xml_for_health_data(health_data_response)
    events = health_data.getElementsByTagName("om:servicesEvents")
    if events[0].childNodes:
        raise Exception("Expecting an empty servicesEvents element")


def _assert_monitoring_daemon_start_timestamp_in_range(
        health_data_response: requests.Response, opmonitor_restart_timestamp: int):
    health_data = _parse_xml_for_health_data(health_data_response)
    # The value of om:monitoringStartupTimestamp is in milliseconds
    # but we query the op-monitor startup timestamp in seconds (for
    # consistency with the rest of the test code).
    startup_timestamp = int(int(health_data.getElementsByTagName(
        "om:monitoringStartupTimestamp")[0].firstChild.nodeValue) / 1000)
    if abs(startup_timestamp - opmonitor_restart_timestamp) > 10:
        # Allow the service startup time and the integer part of
        # the point in time the timestamp is queried in Java code,
        # to differ by some seconds. Under normal circumstances
        # the value seems to differ by around 2 - 4 secs.
        print("Reported monitoring startup time in seconds:", startup_timestamp)
        print("Startup time of xroad-opmonitor:", opmonitor_restart_timestamp)
        raise Exception(
            "Expecting the reported monitoring startup timestamp to be not more than "
            "10 seconds later than the service startup timestamp ")


def _assert_stats_period(health_data_response: requests.Response, value: int):
    health_data = _parse_xml_for_health_data(health_data_response)
    health_data_stats_period = int(
        health_data.getElementsByTagName("om:statisticsPeriodSeconds")[0].firstChild.nodeValue)
    if health_data_stats_period != value:
        raise Exception(
            "The monitoring statistics period {} did not match the expected "
            "value {}".format(health_data_stats_period, value))


def _assert_service_events_min_count(health_data_response: requests.Response, count: int):
    service_events = _find_all_health_data_events(health_data_response)
    if len(service_events) < 2:
        raise Exception(
            "Expected at least {} serviceEvents elements in the "
            "health data response".format(count))


def _assert_xml_tags_present(event_data_xml_tree, tags):
    for tag in tags:
        if not event_data_xml_tree.getElementsByTagName(tag):
            raise Exception("The '{}' tag is missing in the given XML".format(tag))


def _assert_xml_tags_missing(event_data_xml_tree, tags):
    for tag in tags:
        if event_data_xml_tree.getElementsByTagName(tag):
            raise Exception("The '{}' tag is present in the given XML".format(tag))


def _assert_xml_tags_match_values(
        event_data_xml_tree, field_names, expected_fields_and_values):
    for field_name in field_names:
        tag_value = event_data_xml_tree.getElementsByTagName(field_name)[0].firstChild.nodeValue
        if tag_value != str(expected_fields_and_values.get(field_name)):
            raise Exception(
                "The expected value of tag {} ({}) did not match the actual value {}".format(
                    field_name, expected_fields_and_values.get(field_name), tag_value))


def _assert_successful_events_count(event_data_xml_tree, value):
    _assert_events_count(event_data_xml_tree, value, successful=True)


def _assert_unsuccessful_events_count(event_data_xml_tree, value):
    _assert_events_count(event_data_xml_tree, value, successful=False)


def _assert_events_count(event_data_xml_tree, value, successful=True):
    tag = "om:successfulRequestCount" if successful else "om:unsuccessfulRequestCount"
    msg_part = "successful" if successful else "unsuccessful"

    events_count = int(event_data_xml_tree.getElementsByTagName(tag)[0].firstChild.nodeValue)
    if events_count != value:
        raise Exception(
            "The {} events count {} did not match the expected value {}".format(
                msg_part, events_count, value))


def _assert_last_successful_event_timestamp_in_range(event_data_xml_tree, timestamp):
    _assert_last_event_timestamp_in_range(
        event_data_xml_tree, timestamp, successful=True)


def _assert_last_unsuccessful_event_timestamp_in_range(event_data_xml_tree, timestamp):
    _assert_last_event_timestamp_in_range(event_data_xml_tree, timestamp, successful=False)


def _assert_last_event_timestamp_in_range(
        event_data_xml_tree, timestamp, successful=True):
    tag = "om:lastSuccessfulRequestTimestamp" if successful \
        else "om:lastUnsuccessfulRequestTimestamp"
    msg_part = "successful" if successful else "unsuccessful"
    timestamp_ms = timestamp * 1000

    event_timestamp = int(
        event_data_xml_tree.getElementsByTagName(tag)[0].firstChild.nodeValue)
    # Allow at most 5 seconds in the past and 10 seconds in the future
    # (small clock differences, time for preparing queries etc).
    lower_limit = timestamp_ms - 5000
    upper_limit = timestamp_ms + 10000
    if not (lower_limit <= event_timestamp <= upper_limit):
        raise Exception(
            "Expecting the last {} event timestamp to be in the range "
            "({} - {})".format(msg_part, lower_limit, upper_limit))
