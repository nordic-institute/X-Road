/*
 * The MIT License
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
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ServiceClientType } from './ServiceClientType';

/**
 * service client. May be a subsystem, local group, or a global group
 */
export interface ServiceClient {
    /**
     * subject id - can be a subsystem id <instance_id>:<member_class>:<member_code>:<subsystem> | globalgroup id <instance_id>:<group_code> | localgroup resource id in number format <id>
     */
    id: string;
    /**
     * name of the ServiceClient - can be the name of a member or the description of a group
     */
    readonly name?: string;
    /**
     * group code in case the object is a local group
     */
    readonly local_group_code?: string;
    service_client_type?: ServiceClientType;
    /**
     * time when access right were given at. When listing client's service clients without specifying the service, the time when first service access right was given to this service client for any service. When listing service clients for a specific service, time when service client was added permission to that service.
     */
    readonly rights_given_at?: string;
}
