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

# A quick and dirty script for sending bulk operational data records directly to the gRPC
# StoreOperationalData method of the operational monitoring daemon.
#
# Prerequisites:
#   - Python 3 with grpcio (pip install grpcio). No generated stubs or protobuf package are
#     needed, the few messages used are encoded by hand from opmonitor_service.proto.
#   - The daemon accepts plaintext gRPC on the target (xroad.common-rpc.use-tls=false), as in
#     the op-monitor int-test stack. TLS is not supported by this script.
#   - Sufficient memory is available to the daemon.
#   - A tunnel has been created from localhost to the actual target host, if the daemon is remote.
#
# What it measures: the time of each StoreOperationalData call as seen by the client (request
# sent until response received), the request size, and the resulting records per second over
# the whole sequential send phase. Record generation and encoding happen before the clock starts.
#
# What it does not measure: the TLS handshake and TLS overhead, the Security Server proxy and
# its op-monitoring buffer, parallel load (calls are sequential), database insert time on its own,
# or the query path. Records are generated, never read back, so a successful run does not verify
# what was stored.
#
# The daemon rejects messages above its inbound limit (xroad.op-monitor.rpc.max-inbound-message-size,
# 4 MiB when unset). The single-call mode and every batch are checked against --max-message-size.

import argparse
import random
import statistics
import sys
import time

DEFAULT_TARGET = "localhost:2081"
STORE_METHOD = "/org.niis.xroad.opmonitor.api.OpMonitorService/StoreOperationalData"
NUM_OF_RECORDS_IN_DATA = 10000
DEFAULT_BATCH_SIZE = 100
DEFAULT_MAX_MESSAGE_SIZE = 4194304
CONNECT_TIMEOUT_SECONDS = 10

SECURITY_SERVER_TYPE_CLIENT = 1

# Field numbers of OperationalDataRecordProto.
STRING_FIELDS = {
    "securityServerInternalIp": 1,
    "clientXRoadInstance": 7,
    "clientMemberClass": 8,
    "clientMemberCode": 9,
    "clientSubsystemCode": 10,
    "serviceXRoadInstance": 11,
    "serviceMemberClass": 12,
    "serviceMemberCode": 13,
    "serviceSubsystemCode": 14,
    "serviceCode": 15,
    "serviceVersion": 16,
    "messageId": 22,
    "messageUserId": 23,
    "messageProtocolVersion": 25,
}
VARINT_FIELDS = {
    "securityServerType": 2,
    "requestInTs": 3,
    "responseOutTs": 6,
    "succeeded": 34,
}

SAMPLE_RECORD = {
    "clientMemberCode": "00000001",
    "serviceXRoadInstance": "XTEE-CI-XM",
    "clientSubsystemCode": "System1",
    "serviceCode": "xroadGetRandom",
    "messageProtocolVersion": "4.0",
    "messageId": "c60b7e66-1dc8-4203-a3c1-3235661f6a84",
    "clientXRoadInstance": "XTEE-CI-XM",
    "messageUserId": "EE37702211234",
    "clientMemberClass": "GOV",
    "serviceMemberCode": "00000000",
    "securityServerType": SECURITY_SERVER_TYPE_CLIENT,
    "securityServerInternalIp": "192.168.56.1",
    "serviceVersion": "v1",
    "serviceMemberClass": "GOV",
    "requestInTs": 1479733697687,
    "serviceSubsystemCode": "Center",
    "responseOutTs": 1479733718087,
    "succeeded": True,
}


def _timestamp_with_millis():
    return int(time.time() * 1000)


def _varint(value):
    out = bytearray()
    while value > 0x7F:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def _length_delimited(field_number, payload):
    return _varint((field_number << 3) | 2) + _varint(len(payload)) + payload


def encode_record(record):
    out = bytearray()
    for name, value in record.items():
        if name in STRING_FIELDS:
            out += _length_delimited(STRING_FIELDS[name], value.encode("utf-8"))
        else:
            out += _varint((VARINT_FIELDS[name] << 3) | 0) + _varint(int(value))
    return bytes(out)


def encode_store_request(encoded_records):
    return b"".join(_length_delimited(1, rec) for rec in encoded_records)


def generate_records(count):
    records = []
    for _ in range(count):
        rec = dict(SAMPLE_RECORD)
        rec["requestInTs"] = _timestamp_with_millis()
        rec["responseOutTs"] = _timestamp_with_millis()
        rec["succeeded"] = bool(random.getrandbits(1))
        records.append(encode_record(rec))
    return records


def parse_args():
    parser = argparse.ArgumentParser(
        description="Send %d generated operational data records to op-monitor over gRPC "
                    "StoreOperationalData." % NUM_OF_RECORDS_IN_DATA)
    parser.add_argument("--target", default=DEFAULT_TARGET,
                        help="op-monitor gRPC address, host:port (default: %(default)s)")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE,
                       help="records per call, calls are sequential (default: %(default)s)")
    group.add_argument("--single-call", action="store_true",
                       help="send all records in one call, if the request fits --max-message-size")
    parser.add_argument("--max-message-size", type=int, default=DEFAULT_MAX_MESSAGE_SIZE,
                        help="inbound message limit of the daemon in bytes (default: %(default)s)")
    args = parser.parse_args()
    if args.batch_size < 1:
        parser.error("--batch-size must be at least 1")
    return args


def split_batches(records, batch_size):
    return [records[i:i + batch_size] for i in range(0, len(records), batch_size)]


def main():
    args = parse_args()
    batch_size = NUM_OF_RECORDS_IN_DATA if args.single_call else min(args.batch_size, NUM_OF_RECORDS_IN_DATA)

    records = generate_records(NUM_OF_RECORDS_IN_DATA)
    requests_bytes = [encode_store_request(batch) for batch in split_batches(records, batch_size)]

    largest = max(len(req) for req in requests_bytes)
    if largest > args.max_message_size:
        sys.exit("Largest request is %d bytes, above --max-message-size %d. "
                 "Use a smaller --batch-size." % (largest, args.max_message_size))

    try:
        import grpc
    except ModuleNotFoundError:
        sys.exit("grpcio is not installed: pip install grpcio")

    channel = grpc.insecure_channel(args.target, options=[
        ("grpc.max_send_message_length", args.max_message_size),
    ])
    try:
        grpc.channel_ready_future(channel).result(timeout=CONNECT_TIMEOUT_SECONDS)
    except grpc.FutureTimeoutError:
        sys.exit("Could not connect to %s within %d s" % (args.target, CONNECT_TIMEOUT_SECONDS))
    store = channel.unary_unary(STORE_METHOD,
                                request_serializer=lambda b: b,
                                response_deserializer=lambda b: b)

    print("Target %s, %d records, %d calls, %d records per call"
          % (args.target, len(records), len(requests_bytes), batch_size))

    latencies = []
    start = time.perf_counter()
    for index, request in enumerate(requests_bytes, start=1):
        call_start = time.perf_counter()
        try:
            store(request)
        except grpc.RpcError as e:
            sys.exit("Call %d failed: %s %s" % (index, e.code(), e.details()))
        latency = time.perf_counter() - call_start
        latencies.append(latency)
        print("call %d/%d: %.1f ms, request %d bytes"
              % (index, len(requests_bytes), latency * 1000, len(request)))
    total = time.perf_counter() - start
    channel.close()

    print("Calls: %d" % len(latencies))
    print("Records: %d" % len(records))
    print("Request bytes: %d" % sum(len(r) for r in requests_bytes))
    print("Latency ms (min/avg/max): %.1f / %.1f / %.1f"
          % (min(latencies) * 1000, statistics.mean(latencies) * 1000, max(latencies) * 1000))
    print("Total time: %.3f s" % total)
    print("Records per second: %.1f" % (len(records) / total))


if __name__ == "__main__":
    main()
