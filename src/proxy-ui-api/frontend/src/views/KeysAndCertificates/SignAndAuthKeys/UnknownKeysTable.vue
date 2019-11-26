<template>
  <div>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{$t(title)}}</th>
          <th>{{$t('keys.id')}}</th>
        </tr>
      </thead>
      <tbody v-for="key in keys" v-bind:key="key.id">
        <td>
          <div class="name-wrap">
            <i class="icon-xrd_key icon" @click="keyClick(key)"></i>
            <div class="clickable-link" @click="keyClick(key)">{{key.label}}</div>
          </div>
        </td>
        <td>
          <div class="id-wrap">
            <div class="clickable-link" @click="keyClick(key)">{{key.id}}</div>
            <SmallButton
              class="gen-csr"
              :disabled="disableGenerateCsr"
              @click="generateCsr(key)"
            >{{$t('keys.generateCsr')}}</SmallButton>
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
import SmallButton from '@/components/ui/SmallButton.vue';
import { Key } from '@/types';

export default Vue.extend({
  components: {
    SmallButton,
  },
  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    disableGenerateCsr: {
      type: Boolean,
    },
  },
  data() {
    return {};
  },
  computed: {},
  methods: {
    keyClick(key: Key): void {
      this.$emit('keyClick', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
    },
  },
});
</script>


<style lang="scss" scoped>
@import '../../../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
  cursor: pointer;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.gen-csr {
  margin-left: auto;
  margin-right: 0;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
}

.id-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
  width: 100%;
}
</style>
