import { action } from '@storybook/addon-actions';
import VuetifyButton from './VuetifyButton.vue';
export default {
  title: 'X-Road/Vuetify button',
  // Our exports that end in "Data" are not stories.
  excludeStories: /.*Data$/,
};
export const actionsData = {
  onPinTask: action('onPinTask'),
  onArchiveTask: action('onArchiveTask'),
};

export const taskData = {
  id: '1',
  title: 'Test Task',
  state: 'Task_INBOX',
};

const taskTemplate = `<vuetify-button :disabled="false" hello="moi"/>`;

// default state
export const Default = () => ({
  components: { VuetifyButton },
  template: taskTemplate,
  props: {
    task: {
      default: () => taskData,
    },
  },
  methods: actionsData,
});
