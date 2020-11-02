import HelpDialog from './HelpDialog.vue';

export default {
  title: 'X-Road/Help dialog',
  component: HelpDialog,
  argTypes: {
    outlined: { control: 'boolean' },
    width: { control: 'boolean' },
    dialog: { control: 'boolean' },
    title: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { HelpDialog },
  template:
    '<help-dialog @cancel="cancel" v-bind="$props" imageSrc="api_keys.png">{{label}}</help-dialog>',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  label: 'Hello world!',
  dialog: true,
};

export const Secondary = Template.bind({});
Secondary.args = {
  label: 'This is a very very long label for a button',
  dialog: true,
};
