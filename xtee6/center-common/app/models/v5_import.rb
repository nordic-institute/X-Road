class V5Import < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :file_name, :presence => true

  def self.write(file_name, console_output_lines)
    V5Import.delete_all()
    V5Import.create!(
        :file_name => file_name,
        :console_output => console_output_lines.join("\n"))
  end

  def self.read()
    return V5Import.all.first()
  end
end
