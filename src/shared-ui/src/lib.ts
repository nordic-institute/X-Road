// This file is the entry point for the library build
import { VueConstructor } from 'vue';
import LargeButton from './components/LargeButton.vue';
import SmallButton from './components/SmallButton.vue';
// Import vee-validate so it's configured on the library build
import './plugins/vee-validate';
import './i18n';

const SharedComponents = {
  install(Vue: VueConstructor): void {
    Vue.component('LargeButton', LargeButton);
    Vue.component('SmallButton', SmallButton);
  },
};

export default SharedComponents;
