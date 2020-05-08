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

export interface ServiceCandidate {
  service_code: string;
  service_title?: string;
  id: string;
}
