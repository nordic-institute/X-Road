// Example of a story using @storybook/vue
import { storiesOf } from '@storybook/vue';
import LoveButton from './LoveButton.vue';

storiesOf('X-Road/LoveButton', module).add('default', () => ({
  components: { LoveButton },
  template: `<love-button love="vue"/>`,
}));
