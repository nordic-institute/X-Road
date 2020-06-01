<template>
  <div>
    <div v-if="sourceObject[childKey]">
      <b v-if="label" class="cert-label">{{ label }}:</b>
      <b v-else class="cert-label"
        >{{ childKey | prettyTitle | upperCaseWords }}:</b
      >

      <div v-if="chunk" class="chunk">
        <pre>{{ sourceObject[childKey] | colonize | lineBreaks }}</pre>
      </div>

      <span v-else>{{ formattedData() }}</span>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
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
    },
    info: {
      type: String,
      required: false,
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
  filters: {
    prettyTitle(value: string) {
      // Replace "snake case" with spaces
      return value.replace(new RegExp('_', 'g'), ' ');
    },

    lineBreaks(value: string) {
      // Add line break after every 60 characters
      return value.replace(/(.{60})/g, '$1\n');
    },
  },
  data() {
    return {};
  },
  computed: {},
  methods: {
    formattedData(): string {
      if (this.info) {
        return this.info;
      }

      if (this.arrayType) {
        return this.formatArray(this.sourceObject[this.childKey]);
      }

      if (this.date) {
        // Return readable presentation of date
        const event = new Date(this.sourceObject.not_before);
        return event.toString();
      }

      return this.sourceObject[this.childKey];
    },

    formatArray(arr: []): string {
      let translated: string[];
      translated = [];

      arr.forEach((element) => {
        // @ts-ignore: Vue has no call signature for l18n $t
        translated.push(this.$t('cert.keyUsage.' + element) as string);
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
