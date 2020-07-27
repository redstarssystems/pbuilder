SHELL=/bin/bash

CURRENT_TIME = $(shell date)

.EXPORT_ALL_VARIABLES:

.PHONY:	clean repl build install ancient release bump outdated help

YELLOW_PRINT = \033[0;33m
GREEN_PRINT = \033[0;92m
NORMAL_TEXT = \033[0m

define cecho
	@echo '-----------------------------------------------'
	@echo -e "$(GREEN_PRINT)$(CURRENT_TIME)"
	@echo -e "$(YELLOW_PRINT)"
	@echo -e $(1)
	@echo -e "$(NORMAL_TEXT)"
endef

.DEFAULT_GOAL := help

clean: ## Clean project
	$(call cecho,"Clean project")
	@clojure -A:run clean

repl: ## Run Clojure REPL
	$(call cecho,"Run Clojure REPL")
	@clojure -A:repl

build: ## Build jar file (library)
	$(call cecho,"Build jar file (library)")
	@clojure -A:run jar

install: ## Install jar file to local .m2
	$(call cecho,"Install jar file to local .m2")
	@clojure -A:run install

deploy: ## Deploy jar file to clojars
	$(call cecho,"Deploy jar file to clojars")
	@clojure -A:run deploy

release: ## Release artifact.
	$(call cecho,"Release artifact")
	@clojure -A:run release

bump: ## Bump version artifact in build file.
	$(call cecho,"Bump version artifact in build file.")
	@clojure -A:run bump $(filter-out $@,$(MAKECMDGOALS)) # parameter should be one of: major minor patch alpha beta rc qualifier

ancient: ## Check for outdated dependencies.
	$(call cecho,"Check for outdated dependencies")
	@clojure -A:ancient

help: ## Show help
	 @grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# these lines need to work with command line params.
%:
	@:
