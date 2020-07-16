<template>
  <div>
    <input
      v-show="false"
      ref="fileInput"
      type="file"
      :accept="accepts"
      @change="onUploadFileChanged"
    />
    <slot :upload="upload">
      <large-button @click="upload">{{ $t('action.upload') }}</large-button>
    </slot>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from './LargeButton.vue';
import { FileUploadResult } from '@/ui-types';

type HTMLInputElementEvent = Event & {
  target: HTMLInputElement;
};

type FileUploadEvent = HTMLInputElementEvent | DragEvent;

// https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-guards-and-differentiating-types
const isDragEvent = (event: FileUploadEvent): event is DragEvent => {
  return (event as DragEvent).dataTransfer !== undefined;
};

export default Vue.extend({
  name: 'FileUpload',
  components: {
    LargeButton,
  },
  props: {
    accepts: {
      type: String,
      required: true,
    },
  },
  methods: {
    upload() {
      (this.$refs.fileInput as HTMLInputElement).click();
    },
    onUploadFileChanged(event: FileUploadEvent) {
      const files = isDragEvent(event)
        ? event.dataTransfer?.files
        : event.target.files;
      if (!files) {
        return; // No files uploaded
      }
      const file = files[0];

      const reader = new FileReader();
      reader.onload = (e) => {
        if (!e?.target?.result || !files) {
          return;
        }
        this.$emit('fileChanged', {
          buffer: e.target.result as ArrayBuffer,
          file: file,
        } as FileUploadResult);
      };
      reader.readAsArrayBuffer(file);
      (this.$refs.fileInput as HTMLInputElement).value = ''; //So we can re-upload the same file without a refresh
    },
  },
});
</script>

<style scoped lang="scss">
div {
  display: inline;
}
</style>
