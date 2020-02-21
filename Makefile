SHELL=/bin/bash

CURRENT_TIME = $(shell date)

.EXPORT_ALL_VARIABLES:

.PHONY:	clean repl run javac install deploy jar uberjar help

YELLOW_PRINT = \033[0;33m
GREEN_PRINT = \033[0;92m
NORMAL_TEXT = \033[0m

define cecho
	@echo '------------------------------------------'
	@echo -e "$(GREEN_PRINT)$(CURRENT_TIME)"
	@echo -e "$(YELLOW_PRINT)"
	@echo -e $(1)
	@echo -e "$(NORMAL_TEXT)"
endef

.DEFAULT_GOAL := help

clean: ## Clean project
	$(call cecho,"Clean project")
	@clojure -A:run clean -f pbuild.edn

repl: ## Run Clojure REPL
	$(call cecho,"Run Clojure REPL")
	@clojure -A:repl

run: ## Run main function
	$(call cecho,"Run main function")
	@clojure -A:run

javac: ## Compile java classes
	$(call cecho,"Compile java classes")
	@clojure -A:javac

help: ## Show help
	 @grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

