find . -maxdepth 1 -type d -exec sh -c 'cd {}; zip -r {}.asice *; mv {}.asice ../..; cd ..' ';'
