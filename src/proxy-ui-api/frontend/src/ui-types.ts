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
