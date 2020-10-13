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
import { AccessRight, Service, ServiceDescription } from '@/openapi-types';

/**
 * Sorting function for comparing Services by (full) service codes
 */
export const compareByServiceCode = (a: Service, b: Service): number => {
  if (a.full_service_code && b.full_service_code) {
    return a.full_service_code.localeCompare(b.full_service_code);
  }

  // fallback to comparing with service code - shouldn't come to this
  return a.service_code.localeCompare(b.service_code);
};

/**
 * Sort services ascending by full service code
 */
export const sortServicesAscendingByFullServiceCode = (
  services: Service[],
): Service[] => {
  return services.sort(compareByServiceCode);
};

/**
 * Sort services in ServiceDescription
 */
export const sortServiceDescriptionServices = (
  sd: ServiceDescription,
): ServiceDescription => {
  sd.services = sortServicesAscendingByFullServiceCode(sd.services);
  return sd;
};

/**
 * Sort access rights by service code
 */
export const sortAccessRightsByServiceCode = (
  acls: AccessRight[],
): AccessRight[] => {
  return acls.sort((a: AccessRight, b: AccessRight) => {
    return a.service_code.localeCompare(b.service_code);
  });
};
