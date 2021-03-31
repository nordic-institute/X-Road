<!--
  Member details view
-->
<template>
  <div>
    <h3>Memberdetails</h3>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

/**
 * Component for a Members details view
 */
export default Vue.extend({
  name: 'MemberDetails',
});
</script>

<style scoped lang="scss">
.header-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-bottom: 24px;
}

.title-search {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  align-items: flex-end;

  .xrd-view-title {
    margin-right: 20px;
  }
}
</style>
