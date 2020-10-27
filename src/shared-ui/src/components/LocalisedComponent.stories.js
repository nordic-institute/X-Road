// Example of a story using @storybook/vue
import { storiesOf } from '@storybook/vue';
import LocalisedComponent from './LocalisedComponent.vue';

storiesOf('X-Road/Localised Component', module).add('default', () => ({
  components: { LocalisedComponent },
  template: `<localised-component />`,
}));
