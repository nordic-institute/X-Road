// Used in api-keys endpoints, but not defined in OpenAPI definitions
export interface ApiKey {
  id: number;
  roles: string[];
  key?: string;
}
