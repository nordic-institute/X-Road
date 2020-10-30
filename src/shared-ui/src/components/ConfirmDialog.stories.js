import ConfirmDialog from './ConfirmDialog.vue';

export default {
  title: 'X-Road/Confirm dialog',
  component: ConfirmDialog,
  argTypes: {
    width: { control: 'boolean' },
    dialog: { control: 'boolean' },
    loading: { control: 'boolean' },
    disableSave: { control: 'boolean' },
    title: { control: 'text' },
    content: { control: 'text' },
    saveButtonText: { control: 'text' },
    cancelButtonText: { control: 'text' },
    showClose: { control: 'boolean' },
    width: {control: 'number' },
    accept: { action: 'accept' },
    cancel: { action: 'cancel' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { ConfirmDialog },
  template:
    '<confirm-dialog @accept="accept" @cancel="cancel" v-bind="$props">{{label}}</confirm-dialog>',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  dialog: true,
  label: 'Hello world!',
};

export const Secondary = Template.bind({});
Secondary.args = {
  dialog: true,
  label: 'This is a very very long label for a button',
};
