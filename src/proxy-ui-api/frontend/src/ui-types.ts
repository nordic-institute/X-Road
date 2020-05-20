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

// Used in service clients views for listing services than can be granted access rights to
export interface ServiceCandidate {
  service_code: string;
  service_title?: string;
  id: string;
}
