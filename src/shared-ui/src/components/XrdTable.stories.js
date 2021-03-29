import XrdTable from "@/components/XrdTable";

export default {
  title: 'X-Road/Table',
  component: XrdTable,
}

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { XrdTable },
  template: `
    <xrd-table>
      <thead>
        <tr>
          <th>
            Header 1
          </th>
          <th>
            Header 2
          </th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>
            Value 1
          </td>
          <td>
            Value 2
          </td>
        </tr>
        <tr data-test="netum">
          <td>Value 3</td>
          <td>Value 4</td>
        </tr>
      </tbody>
    </xrd-table>
  `
});

export const table = Template.bind({});

