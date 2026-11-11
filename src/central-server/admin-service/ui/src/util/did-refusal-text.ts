/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import type { DidRefusalReason } from '@/openapi-types';

export const DID_REFUSAL_TEXT: Record<DidRefusalReason, string> = {
  NO_STORED_VALUE: 'error_code.did_not_stored',
  WRONG_SCHEME: 'error_code.did_wrong_scheme',
  DID_TOO_LONG: 'error_code.did_too_long',
  NOT_CANONICAL: 'error_code.did_not_canonical',
  HOST_DIFFERS: 'error_code.did_host_differs',
  NON_ASCII_HOST: 'error_code.did_non_ascii_host',
  UNPARSEABLE_REGISTERED_ADDRESS: 'error_code.did_unparseable_server_address',
  UNDECODABLE_DID: 'error_code.did_undecodable',
  FOREIGN_SYSTEM_DID: 'error_code.did_foreign_system',
  DID_OF_OTHER_MEMBER: 'error_code.did_of_other_member',
  MEMBER_NOT_SERVED: 'error_code.did_member_not_served',
  DID_HELD_BY_ANOTHER_SERVER: 'error_code.did_held_by_another_server',
};
