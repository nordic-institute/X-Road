require 'fileutils'

def get_sdsb_home
  return ENV['SDSB_HOME']
end

def is_privilege_row?(row)
  return /\A-\s*\w*:\s*\[.*\]\z/ =~ row
end

def get_filepath(subproject, file)
  return "#{get_sdsb_home()}/#{subproject}/config/#{file}"
end

def get_allowed_roles(subproject)
  result = [
    "sdsb-security-officer",
    "sdsb-registration-officer",
    "sdsb-system-administrator"
  ]

  result << "sdsb-service-administrator" if "proxy-ui" == subproject

  return result
end

def generate_privileges(role, subproject = "center-ui")
  privileges_file = get_filepath(subproject, "privileges.yml")
  new_privileges = ""

  if get_sdsb_home().empty?
    raise "SDSB home must be set!"
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
