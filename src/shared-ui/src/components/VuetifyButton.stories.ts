/* import { storiesOf } from '@storybook/vue';
import VuetifyButton from './VuetifyButton.vue';

storiesOf('VuetifyButton', module)
  .add('default', () => ({
    components: { VuetifyButton },
    template: `<vuetify-button :disabled="false" :hello="moi"/>`,
  }));

  */


 import { action } from '@storybook/addon-actions';
 import VuetifyButton from './VuetifyButton.vue';
 export default {
   title: 'Vuetify button',
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
   updated_at: new Date(2019, 0, 1, 9, 0),
 };
 
 const taskTemplate = `<vuetify-button :disabled="false" :hello="moi"/>`;

 //const taskTemplate = `<task :task="task" @archiveTask="onArchiveTask" @pinTask="onPinTask"/>`;
 
 // default task state
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