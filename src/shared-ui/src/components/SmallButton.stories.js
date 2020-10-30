import SmallButton from './SmallButton.vue';

export default {
  title: 'X-Road/Small button',
  component: SmallButton,
  argTypes: {
    outlined: { control: 'boolean' },
    disabled: { control: 'boolean' },
    loading: { control: 'boolean' },
    minWidth: { control: 'number' },
    label: { control: 'text' },
    click: { action: 'click' }
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SmallButton },
  template:
    '<small-button @click="click" v-bind="$props">{{label}}</small-button>',
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
