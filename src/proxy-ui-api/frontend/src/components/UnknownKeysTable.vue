<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th class="th-title">{{$t(title)}}</th>
        </tr>
      </thead>
      <tbody v-for="key in keys" v-bind:key="key.id">
        <td>
          <div class="name-wrap">
            <i class="icon-xrd_key icon" @click="keyClick(key)"></i>
            <div class="clickable-link" @click="keyClick(key)">{{key.id}}</div>
          </div>
        </td>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';

export default Vue.extend({
  components: {},
  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {},
  methods: {
    keyClick(key: any): void {
      this.$emit('keyClick', key);
    },
  },
});
</script>


<style lang="scss" scoped>
@import '../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
  cursor: pointer;
}

.th-title {
  width: 300px;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
}
</style>
