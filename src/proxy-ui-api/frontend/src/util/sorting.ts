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
export const sortServiceDescriptionServices = (sd: ServiceDescription) => {
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
