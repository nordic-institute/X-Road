// This file is the entry point for the library build
import { VueConstructor } from 'vue';
import LargeButton from './components/LargeButton.vue';
// Import vee-validate so it's configured on the library build
import './plugins/vee-validate';
import './i18n';

const SharedComponents = {
  install(Vue: VueConstructor): void {
    Vue.component('large-button', LargeButton);
  },
};

export default SharedComponents;
