export type FileUploadResult = {
  buffer: ArrayBuffer;
  file: File;
};

export type BackupItem = {
  filename: string;
  local_conf_present?: boolean;
}

export interface BackupHandler {
  showError(error: unknown): void;

  showWarning(textKey: string, data?: Record<string, unknown>): void;

  showSuccess(textKey: string, data?: Record<string, unknown>): void;

  upload(backupFile: File, ignoreWarnings?: boolean): Promise<BackupItem>;

  create(): Promise<BackupItem>;

  delete(filename: string): Promise<unknown>;

  download(filename: string): Promise<unknown>;

  restore(filename: string): Promise<unknown>;
}
