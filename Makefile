.PHONY: build custom mediator, setup environment for testing

build:
	@rm -f ./tests/resources/lib/*.jar
	mvn clean install -Dmaven.test.skip=true && cp ./target/am*.jar ./tests/resources/lib/mediator.jar

up:
	cd ./tests && docker compose up -d

down:
	cd ./tests && docker compose down -t0

setup:
	@bash tests/setup.sh
