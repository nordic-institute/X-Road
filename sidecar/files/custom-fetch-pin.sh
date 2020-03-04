  #!/bin/bash
  
  PIN_CODE=$(curl http://localhost:5555/autologin)
  echo "${PIN_CODE}"
  exit 0