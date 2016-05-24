require 'test_helper'

# FUTURE: find the way to move test into subproject 'common-ui' where the
# actual production code is
class ScriptUtilsTest < ActiveSupport::TestCase

  EXPECTED_OUTPUT = [
    "Printing each command line argument",
    "-a", "single_word",
    "-b", "with spaces and single quotes",
    "-c", "with spaces and double quotes",
    "-d", "with spaces and escaped double quotes",
    "Using getopts to parse arguments",
    "opt a: single_word",
    "opt b: with spaces and single quotes",
    "opt c: with spaces and double quotes",
    "opt d: with spaces and escaped double quotes"
  ]

  test "Run script with spaces and quotes in the arguments" do
    # Given
    script_file = "#{ENV["XROAD_HOME"]}/"\
        "center-ui/test/resources/echo_script_arguments.sh"

    arguments = ["-a single_word",
                 "-b 'with spaces and single quotes'",
                 '-c "with spaces and double quotes"',
                 "-d \"with spaces and escaped double quotes\""
    ]
    commandline = [script_file] + arguments

    # When
    output = CommonUi::ScriptUtils.run_script(commandline)
    output.collect! { |line| line.strip! }

    # Then
    assert_equal(EXPECTED_OUTPUT, output)
  end
end
