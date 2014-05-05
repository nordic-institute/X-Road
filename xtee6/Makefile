all: legacy main 

main:
	gradle build

legacy:
	( cd legacy && $(MAKE) all ) || exit 1

clean:
	( gradle clean && cd legacy && $(MAKE) clean ) || exit 1

.PHONY: legacy
