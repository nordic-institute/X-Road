<template>
  <div>
    <input
      v-show="false"
      ref="anchorUpload"
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
      (this.$refs.anchorUpload as HTMLInputElement).click();
    },
    onUploadFileChanged(event: FileUploadEvent) {
      const files = isDragEvent(event)
        ? event.dataTransfer?.files
        : event.target.files;

      const reader = new FileReader();
      reader.onload = (e) => {
        if (!e?.target?.result) {
          return;
        }
        this.$emit('fileChanged', e.target.result);
      };
      files && reader.readAsArrayBuffer(files[0]);
      (this.$refs.anchorUpload as HTMLInputElement).value = ''; //So we can re-upload the same file without a refresh
    },
  },
});
</script>

<style scoped lang="scss">
div {
  display: inline;
}
</style>
