<template>
  <div class="label">
    {{$t(labelText)}}
    <helpIcon v-if="helpText" :text="$t(helpText)" />
  </div>
</template>

<script lang="ts">
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
  data() {
    return {};
  },
  methods: {},
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.generate-row {
  margin-top: 40px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}

.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.label {
  width: 230px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.form-input {
  width: 300px;
}

.button-footer {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  border-top: solid 1px $XRoad-Grey40;
  margin-top: 40px;
  padding-top: 30px;
}

.button-group {
  display: flex;
  flex-direction: row;
  align-items: baseline;

  :not(:last-child) {
    margin-right: 20px;
  }
}
</style>

