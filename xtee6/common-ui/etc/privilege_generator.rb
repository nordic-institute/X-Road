#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

require 'fileutils'

def get_xroad_home
  return ENV['XROAD_HOME']
end

def is_privilege_row?(row)
  return /\A-\s*\w*:\s*\[.*\]\z/ =~ row.strip()
end

def get_filepath(subproject, file)
  return "#{get_xroad_home()}/#{subproject}/config/#{file}"
end

def get_allowed_roles(subproject)
  result = [
    "xroad-security-officer",
    "xroad-registration-officer",
    "xroad-system-administrator"
  ]

  result << "xroad-service-administrator" if "proxy-ui" == subproject

  return result
end

def generate_privileges(role, subproject = "center-ui")
  privileges_file = get_filepath(subproject, "privileges.yml")
  new_privileges = ""

  if get_xroad_home().empty?
    raise "XROAD home must be set!"
  end

  allowed_roles = get_allowed_roles(subproject)
  if !allowed_roles.include?(role)
    raise "Role '#{role}' is not allowed, allowed ones are '#{allowed_roles}'"
  end


  File.open(privileges_file, 'r:UTF-8').each do |each|
    if is_privilege_row?(each) && each.include?(role)
      new_privileges << each
    end
  end

  privileges_backup_file = get_filepath(subproject, "privileges.yml.original")

  FileUtils.mv(privileges_file, privileges_backup_file)

  File.open(privileges_file, 'w:UTF-8') do |f|
    f.write(new_privileges)
  end

  puts "Privileges file generated successfully, to revert result:\n"\
      "\t1) mv #{privileges_backup_file} #{privileges_file}\n"\
      "\t2) hg revert #{privileges_file}"
end
