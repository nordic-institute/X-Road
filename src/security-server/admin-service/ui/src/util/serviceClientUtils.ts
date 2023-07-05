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
import { ServiceCandidate } from '@/ui-types';
import { compareByServiceCode } from '@/util/sorting';

// returns whether given access right is for given service
const isNotAccessRightToService = (
  service: Service,
  accessRight: AccessRight,
): boolean => accessRight.service_code !== service.service_code;

// returns whether accessrights list contains any access that is for given service
const noAccessRightsToService = (
  service: Service,
  accessRights: AccessRight[],
): boolean =>
  accessRights.every((accessRight: AccessRight) =>
    isNotAccessRightToService(service, accessRight),
  );

/**
 * Returns clients services that can be added to the service client.
 * Services contained by clients service description are filtered by the access rights of the service client
 *
 * @param clientServiceDescriptions service descriptions of the client
 * @param serviceClientAccessRights access rights of the service client
 */
export const serviceCandidatesForServiceClient = (
  clientServiceDescriptions: ServiceDescription[],
  serviceClientAccessRights: AccessRight[],
): ServiceCandidate[] => {
  return (
    clientServiceDescriptions
      // pick all services from service descriptions
      .reduce(
        (curr: Service[], next: ServiceDescription) =>
          curr.concat(...next.services),
        [],
      )
      .sort(compareByServiceCode)
      // filter out services where this service client has access right already
      .filter((service: Service) =>
        noAccessRightsToService(service, serviceClientAccessRights),
      )
      // map to service candidates
      .map(
        (service: Service): ServiceCandidate => ({
          service_code: service.service_code,
          service_title: service.title,
          id: service.id,
        }),
      )
  );
};
