<template>
  <div class="label">
    {{ $t(labelText) }}
    <helpIcon v-if="helpText" :text="$t(helpText)" />
  </div>
</template>

<script lang="ts">
/**
 * Label and help icon that can be used with inputs
 */
import Vue from 'vue';
import HelpIcon from '@/components/ui/HelpIcon.vue';

export default Vue.extend({
  components: {
    HelpIcon,
  },
  props: {
    labelText: {
      type: String,
      required: true,
    },
    helpText: {
      type: String,
      required: false,
    },
  },
});
</script>

<style lang="scss" scoped>
.label {
  width: 230px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
</style>
