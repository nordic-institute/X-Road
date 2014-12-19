require 'active_record'

require 'java'

require 'sdsb/validators'
require 'sdsb/identifier'
require 'sdsb/system_parameter'
require 'sdsb/security_server_client_name'
require 'sdsb/global_group_member'
require 'sdsb/global_group'
require 'sdsb/security_server_client'
require 'sdsb/sdsb_member'
require 'sdsb/subsystem'
require 'sdsb/member_class'
require 'sdsb/client_id'
require 'sdsb/distributed_files'

require 'xtee55_clients_importer/xlogger'

include Xtee55ClientsImporter

module Xtee55ClientsImporter
  class DBManager
    @@log = xlogger(DBManager)

    def initialize(host, database, username, password)
      @warnings = false
      start = Time.now
      establish_connection(host, database, username, password)
      @@log.info("PERFORMANCE: Establishing DB connection: " +
          "#{Time.now - start} seconds")
    end

    # Returns 0 if ok, 1 if warnings.
    def import(groups, consumers, producers)
      @warnings = false
      start = Time.now
      ActiveRecord::Base.transaction do
        import_groups(groups)
        import_clients(consumers, producers)
      end
      @@log.info("PERFORMANCE: Importing data: " +
                "#{Time.now - start} seconds")
      @warnings ? 1 : 0
    end

  private
    def establish_connection(host, database, username, password)
      ActiveRecord::Base.establish_connection(
          :adapter => 'jdbcpostgresql',
          :host => host,
          :database => database,
          :username => username,
          :password => password)
    end

    def import_groups(groups)
      @@log.info("Importing global groups..")
      groups.each do |g|
        if (GlobalGroup.add_group_if_not_exists(g.name, g.description))
          @@log.info("Importing global group '#{g.name}'")
        else
          @@log.info("Global group '#{g.name}' already exists in SDSB, " +
              "skipping")
        end
      end
    end

    def import_clients(consumers, producers)
      @@log.info("Importing clients..")

      mapping_file = DistributedFiles.where(
          file_name: "identifiermapping.xml").first
      if mapping_file == nil
        raise "No identifiermapping.xml file in the database"
      end

      mapping = Java::EeCyberXroadClientsimporter::IdentifierMapping.new(
          mapping_file.file_data)

      instance_identifier = SystemParameter.instance_identifier

      # Process consumers first.
      orgs = consumers + producers

      orgs.each do |o|
        is_consumer = o.instance_of?(Consumer)
        client_id = mapping.getClientId(o.name)

        if client_id == nil
          @@log.warn("Could not find identifier mapping " +
              "for client '#{o.name}', skipping")
          @warnings = true
          next
        end

        if client_id.getSdsbInstance() != instance_identifier
          @@log.warn("Invalid SDSB instance '#{client_id.getSdsbInstance()}' " +
              "for client '#{o.name}', skipping")
          @warnings = true
          next
        end

        object_type = client_id.getObjectType()

        if object_type != Java::EeCyberSdsbCommonIdentifier::SdsbObjectType::MEMBER &&
            object_type != Java::EeCyberSdsbCommonIdentifier::SdsbObjectType::SUBSYSTEM
          @@log.warn("Invalid SDSB object type '#{object_type.name}' " +
              "for client '#{o.name}', skipping")
          @warnings = true
          next
        end

        is_subsystem = object_type ==
            Java::EeCyberSdsbCommonIdentifier::SdsbObjectType::SUBSYSTEM

        member_class = MemberClass.find_by_code(client_id.getMemberClass())

        if member_class == nil
          @@log.warn("Member class '#{client_id.getMemberClass()}' not " +
              "found for client '#{o.name}', skipping")
          @warnings = true
          next
        end

        member = find_member(member_class.id, client_id.getMemberCode())

        if member
          if is_subsystem
            if subsystem_exists?(member.id, client_id.getSubsystemCode())
              @@log.info("Client '#{o.name}' already exists in SDSB, skipping")
            else
              @@log.info("Importing client '#{o.name}'")
              Subsystem.create!(
                  :sdsb_member => member,
                  :subsystem_code => client_id.getSubsystemCode())
            end
          else
            @@log.info("Client '#{o.name}' already exists in SDSB, skipping")
          end
        else
          if is_subsystem
            if is_consumer
              @@log.info("Importing client '#{o.name}'")
              member = SdsbMember.create!(
                  :name => o.full_name,
                  :member_class => member_class,
                  :member_code => client_id.getMemberCode(),
                  :administrative_contact => nil)
              Subsystem.create!(:sdsb_member => member,
                  :subsystem_code => client_id.getSubsystemCode())
            else
              @@log.warn("Member not found for subsystem client '#{o.name}', skipping")
              @warnings = true
              next
            end
          else
            @@log.info("Importing client '#{o.name}'")
            SdsbMember.create!(
                :name => o.full_name,
                :member_class => member_class,
                :member_code => client_id.getMemberCode(),
                :administrative_contact => nil)
          end
        end

        # Update groups info.

        if is_consumer
          o.groups.each do |g|
            global_group = GlobalGroup.find_by_code(g)

            if global_group == nil
              @@log.warn("Cannot add client '#{o.name}' to group, " +
                  "global group '#{g}' not found")
              @warnings = true
              next
            end

            @@log.info("Adding client '#{o.name}' to global group '#{g}'")
            global_group.add_member(get_client_id(client_id))
          end
        end
      end
    end

    def get_client_id(java_client_id)
      ClientId.from_parts(
          java_client_id.getSdsbInstance(),
          java_client_id.getMemberClass(),
          java_client_id.getMemberCode(),
          java_client_id.getSubsystemCode())
    end

    def find_member(member_class_id, member_code)
      members = SdsbMember.where(:member_code => member_code,
          :member_class_id => member_class_id).first
    end

    def subsystem_exists?(member_id, subsystem_code)
      Subsystem.exists?(:sdsb_member_id => member_id,
          :subsystem_code => subsystem_code)
    end
  end
end
