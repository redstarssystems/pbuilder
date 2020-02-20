.PHONY:	clean

repl:
	@echo '***********************************'
	@echo 'start Clojure repl.'
	@echo '***********************************'
	@clojure -A:debug -A:repl

clean:
	@echo '***********************************'
	@echo 'clean target folder.'
	@echo '***********************************'
	@clojure -A:clean

run:
	@echo '***********************************'
	@echo 'run main process.'
	@echo '***********************************'
	@clojure -A:run