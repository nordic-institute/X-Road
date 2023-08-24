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
    <div v-if="sourceObject[childKey]">
      <b v-if="label" class="cert-label">{{ label + ':' }}</b>
      <b v-else class="cert-label">{{ formattedChildKey + ':' }}</b>

      <div v-if="chunk" class="chunk">
        <pre>{{ formattedChunk }}</pre>
      </div>

      <span v-else>{{ formattedData() }}</span>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { colonize, upperCaseWords } from '@/util/helpers';

export default defineComponent({
  props: {
    childKey: {
      type: String,
      required: true,
    },
    sourceObject: {
      type: Object,
      required: true,
    },
    label: {
      type: String,
      required: false,
      default: undefined,
    },
    info: {
      type: String,
      required: false,
      default: undefined,
    },
    arrayType: {
      type: Boolean,
      required: false,
    },
    chunk: {
      type: Boolean,
      required: false,
    },
    date: {
      type: Boolean,
      required: false,
    },
  },
  data() {
    return {};
  },
  computed: {
    formattedChildKey() {
      return upperCaseWords(this.prettyTitle(this.childKey));
    },
    formattedChunk() {
      return this.lineBreaks(colonize(this.sourceObject[this.childKey]));
    },
  },
  methods: {
    prettyTitle(value: string) {
      // Replace "snake case" with spaces
      return value.replace(new RegExp('_', 'g'), ' ');
    },

    lineBreaks(value: string) {
      // Add line break after every 60 characters
      return value.replace(/(.{60})/g, '$1\n');
    },
    formattedData(): string {
      if (this.info) {
        return this.info;
      }

      if (this.arrayType) {
        return this.formatArray(this.sourceObject[this.childKey]);
      }

      if (this.date) {
        // Return readable presentation of date
        const event = new Date(this.sourceObject[this.childKey]);
        return event.toString();
      }

      return this.sourceObject[this.childKey];
    },

    formatArray(arr: []): string {
      const translated: string[] = [];

      arr.forEach((element) => {
        translated.push(this.$t(`cert.keyUsage.${element}`) as string);
      });

      // Return nice looking string representation of an array of strings
      return translated
        .toString()
        .split(',')
        .map((s) => ' ' + s)
        .join(',');
    },
  },
});
</script>

<style lang="scss" scoped>
.cert-label {
  margin-right: 10px;
}

.chunk {
  padding-left: 20px;
}
</style>
