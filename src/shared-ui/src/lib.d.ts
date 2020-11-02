declare module '@niis/shared-ui' {
  import { PluginFunction } from 'vue';

  export const install: PluginFunction<Record<string, unknown>>;

  // The result of the FileUpload components fileChanged event
  export type FileUploadResult = {
    buffer: ArrayBuffer;
    file: File;
  };
}
