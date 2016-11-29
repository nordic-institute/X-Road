#!/usr/bin/env python3

# This module contains test cases that are part of the integration test of the
# operational monitoring system. For running the test cases, import them in the
# main wrapper.

# List the test cases that must be available to the wrapper.
# It must be possible to run the test cases in an arbitrary order.
__all__ = [
    "test_simple_store_and_query",
    "test_soap_fault",
    "test_get_metadata",
    "test_metaservices",
    "test_attachments",
    "test_health_data",
    "test_limited_operational_data_response",
    "test_service_cluster",
    "test_outputspec",
    "test_time_interval",
]
