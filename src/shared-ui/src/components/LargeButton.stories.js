import LargeButton from './LargeButton.vue';

export default {
  title: 'X-Road/Large button',
  component: LargeButton,
  argTypes: {
    outlined: { control: 'boolean' },
    disabled: { control: 'boolean' },
    loading: { control: 'boolean' },
    minWidth: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { LargeButton },
  template:
    '<large-button @onClick="click" v-bind="$props">{{label}}</large-button>',
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  label: 'Hello world!',
};

export const Secondary = Template.bind({});
Secondary.args = {
  label: 'This is a very very long label for a button',
};
