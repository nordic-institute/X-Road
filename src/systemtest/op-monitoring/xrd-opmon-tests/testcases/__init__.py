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
    "test_client_filter",
    "test_zero_buffer_size",
]
