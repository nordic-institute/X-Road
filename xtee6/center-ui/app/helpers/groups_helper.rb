module GroupsHelper
  private

  def group_org_data(o)
    "['#{escape_javascript(display_name(o.shortName, o.fullName))}', '#{escape_javascript(o.shortName)}']"
  end

  def group_data(g)
    "['#{escape_javascript(g.shortName)}', '#{escape_javascript(g.fullName)}']"
  end

  def group_orgs_data_array(orgs)
    data = orgs.map { |o| group_org_data(o) }
    "[" + data.join(",") + "]"
  end

  def groups_data_array(groups)
    data = groups.map { |g| group_data(g) }
    "[" + data.join(",") + "]"
  end
end

