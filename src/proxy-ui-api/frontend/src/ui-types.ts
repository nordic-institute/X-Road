/*
 TypeScript typings that are used in UI, but not in backend.
 These are not in openapi definitions.
*/
import { Client } from '@/types';

// Interface for Tab data
export interface Tab {
  key: string;
  name: string;
  to: {
    name: string;
    params?: {
      id?: string;
    };
  };
  permission?: string;
}

// Extension for Client
export interface ExtendedClient extends Client {
  visibleName?: string | undefined;
  sortNameAsc?: string | undefined;
  sortNameDesc?: string | undefined;
  type?: string;
}

// Used in service clients views for listing services than can be granted access rights to
export interface ServiceCandidate {
  service_code: string;
  service_title?: string;
  id: string;
}
