require 'fileutils'

module CommonUi
  module TarFile
    def self.restrictions
      return UploadedFile::Restrictions.new(
          ["tar"],
          ["application/x-tar", "application/octet-stream"])
    end

    class Validator
      def validate(tar_file, original_filename)
        # TODO: Could we validate tar file through some Ruby API?
        system("tar", "tf", tar_file.path)

        raise I18n.t("backup.error.not_tar") if $? != 0
      end
    end
  end
end
