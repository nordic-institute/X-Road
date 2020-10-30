import Expandable from './Expandable.vue';


export default {
  title: 'X-Road/Expandable',
  component: Expandable,
  argTypes: {
    isOpen: { control: 'boolean' },
    close: { action: 'close' },
    open: { action: 'open' }
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { Expandable },
  template:
    `<expandable @open="open" @close="close" v-bind="$props">
    <template v-slot:action>
      <v-switch></v-switch>
    </template>
    <template v-slot:link>
      <div>
        Link slot
      </div>
    </template>
    <template v-slot:content>
      <div>
        Content slot
      </div>
    </template>
    </expandable>`,
});

export const Primary = Template.bind({});
Primary.args = {
  isOpen: true,
};

