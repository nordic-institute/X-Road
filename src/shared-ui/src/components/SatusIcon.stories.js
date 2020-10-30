import StatusIcon from './StatusIcon.vue';

export default {
  title: 'X-Road/Status icon',
  component: StatusIcon,
  argTypes: {
    status: {    control: {
      type: 'select',
      options: [
        'green', 
        'green-ring',
        'orange',
        'orange-ring',
        'red',
        'red-ring'
      ],
    }, },
  },
};



const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { StatusIcon },
  template:
  
    `
    <div>
    <status-icon v-bind="$props" /><br>
    <status-icon status="green" /><br>
    <status-icon status="green-ring" /><br>
    <status-icon status="orange" /><br>
    <status-icon status="orange-ring" /><br>
    <status-icon status="red" /><br>
    <status-icon status="red-ring" /><br>
    </div>`
});

export const Primary = Template.bind({});
Primary.args = {
  status: 'red'
};
