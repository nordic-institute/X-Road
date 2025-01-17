version = "1.0"

//Apply hierarchically defined group id.
group = "${parent!!.group}.${name.replace(Regex("\\W"), "_")}"
