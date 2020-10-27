// This file is the entry point for the library build
import { VueConstructor } from 'vue';
import VuetifyButton from './components/VuetifyButton.vue';
import LocalisedComponent from './components/LocalisedComponent.vue';
// Import vee-validate so it's configured on the library build
import './plugins/vee-validate';
import './i18n';

const SharedComponents = {
  install(Vue: VueConstructor) {
    Vue.component('vuetify-button', VuetifyButton);
    Vue.component('local-button', LocalisedComponent);
  },
};

export default SharedComponents;
