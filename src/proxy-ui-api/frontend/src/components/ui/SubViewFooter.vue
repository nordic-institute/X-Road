<template>
  <div class="footer-content">
    <div>
      <LargeButton @click="close()">{{ $t('action.close') }}</LargeButton>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';

export default Vue.extend({
  components: {
    LargeButton,
  },
  methods: {
    close() {
      this.$emit('close');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';

.footer-content {
  width: 100%;
  display: flex;
  margin-top: 20px;
  padding-top: 30px;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
}
</style>
