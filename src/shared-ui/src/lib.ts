// This file is the entry point for the library build
import { VueConstructor } from 'vue';
import VuetifyButton from './components/VuetifyButton.vue';
import LocalisedComponent from './components/LocalisedComponent.vue';
// Import vee-validate so it's configured on the library build
import './plugins/vee-validate';
import i18n from './i18n';

const SharedComponents = {
  install(Vue: VueConstructor, options?: any) {
    /* 
    // In theory a shared vuex store could be used like this:
    if (!options || !options.store) {
      throw new Error('Please initialise plugin with a Vuex store.')
    }
    options.store.registerModule('dummylib', store) 
    */

    Vue.component('vuetify-button', VuetifyButton);
    Vue.component('local-button', LocalisedComponent);
  },
};

export default SharedComponents;
