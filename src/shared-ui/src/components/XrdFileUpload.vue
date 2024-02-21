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
      @change="onFileInputChange"
    />
    <slot :upload="upload" :filedrop="onFileDrop" :errors="errors" />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { FileUploadResult } from '../types';

// https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-guards-and-differentiating-types

const props = defineProps({
  accepts: {
    type: String,
    required: true,
  },
});

const emit = defineEmits<{
  (e: 'file-changed', value: FileUploadResult): void
}>();
const fileInput = ref(null);
const errors = ref([] as string[]);

function _asRegexPart(fragment: string): string {
  if (fragment.includes('/')) {
    return fragment.replace('*', '\\w*')
  } else if (fragment.startsWith('.')) {
    return '.+\\' + fragment + '$';
  }
  return '';
}

const typesRg = computed(() => new RegExp(props.accepts
  .split(',')
  .map(item => _asRegexPart(item.trim()))
  .filter(item => item)
  .join('|')));

function upload() {
  if (fileInput.value) {
    (fileInput.value as HTMLInputElement).click();
  }
}

function _handleFile(file: File) {
  const reader = new FileReader();
  reader.onload = (e) => {
    if (!e?.target?.result) {
      return;
    }
    emit('file-changed', {
      buffer: e.target.result as ArrayBuffer,
      file,
    });
  };
  reader.readAsArrayBuffer(file);
  if (fileInput.value) {
    (fileInput.value as HTMLInputElement).value = ''; //So we can re-upload the same file without a refresh
  }
}

function onFileDrop(event: DragEvent) {
  errors.value = [];

  if (!event.dataTransfer?.files.length) {
    return;
  }

  const files = [...event.dataTransfer.files]
    .filter(item => typesRg.value.test(item.type) || typesRg.value.test(item.name));

  if (!files.length) {
    errors.value.push('not-allowed-type');
    return;
  }

  _handleFile(files[0]);
}

function onFileInputChange(event: Event) {
  errors.value = [];

  const files = (event.target as HTMLInputElement).files;
  if (!files) {
    return; // No files uploaded
  }
  _handleFile(files[0])
}
</script>

<style scoped lang="scss">
div {
  display: inline;
}
</style>
