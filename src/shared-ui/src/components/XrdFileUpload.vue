<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div>
    <input
      v-show="false"
      ref="fileInput"
      type="file"
      :accept="accepts"
      @change="onUploadFileChanged"
    />
    <slot :upload="upload" />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { FileUploadResult } from '../types';

type FileUploadEvent = Event | DragEvent;

// https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-guards-and-differentiating-types
const isDragEvent = (event: FileUploadEvent): event is DragEvent => {
  return (event as DragEvent).dataTransfer !== undefined;
};

export default defineComponent({
  props: {
    accepts: {
      type: String,
      required: true,
    },
  },
  emits: ['file-changed'],
  methods: {
    upload() {
      (this.$refs.fileInput as HTMLInputElement).click();
    },
    onUploadFileChanged(event: FileUploadEvent) {
      const files = isDragEvent(event)
        ? event.dataTransfer?.files
        : (event.target as HTMLInputElement).files;
      if (!files) {
        return; // No files uploaded
      }
      const file = files[0];

      const reader = new FileReader();
      reader.onload = (e) => {
        if (!e?.target?.result || !files) {
          return;
        }
        this.$emit('file-changed', {
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
