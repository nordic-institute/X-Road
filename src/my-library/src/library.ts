import DummyButton from "./components/DummyButton.vue";
import VuetifyButton from "./components/VuetifyButton.vue";

const MyComponents = {
  install(Vue: any, options: any) {
    /* if (!options || !options.store) {
      throw new Error('Please initialise plugin with a Vuex store.')
    }

    options.store.registerModule('dummylib', store) */

    Vue.component("vuetify-button", VuetifyButton);
    Vue.component("dummy-button", DummyButton);
  }
};

export default MyComponents;


/*

import * as components from './components'

const AdvancedVuetify =
{
  install(Vue, options = {})
	{
    for (const componentName in components)
    {
      const component = components[componentName];
      Vue.component(component.name, component)
    }
  }
};

export default AdvancedVuetify

if (typeof window !== 'undefined' && window.Vue) {
  window.Vue.use(AdvancedVuetify)
}  


*/