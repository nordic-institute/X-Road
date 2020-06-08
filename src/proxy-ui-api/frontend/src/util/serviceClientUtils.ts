import { AccessRight, Service, ServiceDescription } from '@/openapi-types';
import { ServiceCandidate } from '@/ui-types';

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
