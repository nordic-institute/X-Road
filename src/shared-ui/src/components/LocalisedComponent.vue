<template>
  <div class="wrapper">
    <label for="locale">locale</label>
    <select v-model="locale">
      <option>en</option>
      <option>ja</option>
    </select>
    <p>Translated text: {{ $t('hello') }}</p>
    <slot></slot>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
export default Vue.extend({
  props: {
    disabled: {
      type: Boolean,
      default: false,
    },
    khello: {
      type: String,
      required: false,
      default: 'wow!',
    },
  },
  data() {
    this.$i18n.locale = 'en';
    return { locale: 'en' };
  },
  i18n: {
    messages: {
      en: {
        foo: 'bar',
        hello: 'text in english',
      },
      ja: {
        foo: 'japania',
        hello: 'こんにちは、世界！',
      },
    },
  },
  watch: {
    locale(val) {
      this.$i18n.locale = val;
    },
  },
  computed: {},
  methods: {},
});
</script>

<style lang="scss" scoped>
$large-button-width: 300px;

.wrapper {
  border: solid grey 3px;
  width: 320px;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin: 10px;
}

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
