/*
 TypeScript typings that are used in UI, but not in backend.
 These are not in openapi definitions.
*/
import { Client } from '@/openapi-types';
import { Location } from 'vue-router';

// Interface for Tab data
export interface Tab {
  key: string;
  name: string;
  to: string | Location; // Same type as https://router.vuejs.org/api/#to
  permission?: string;
}

// Extension for Client
export interface ExtendedClient extends Client {
  visibleName?: string;
  sortNameAsc?: string;
  sortNameDesc?: string;
  type?: string;
}

// Used in service clients views for listing services than can be granted access rights to
export interface ServiceCandidate {
  service_code: string;
  service_title?: string;
  id: string;
}
