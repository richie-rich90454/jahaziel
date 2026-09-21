# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Dev workflow for Linux/macOS. Usage: make <verb> [ARGS="..."]
# Verbs: bootstrap build build:slim test test:watch run dev reset smoke docs docs:build release clean
# (Colons are backslash-escaped: GNU make needs build\:slim as the target name.)
.PHONY: bootstrap build build\:slim test test\:watch run dev reset smoke docs docs\:build release clean

JAR := target/jahaziel.jar

bootstrap:
	java -version
	./mvnw -version

build:
	./mvnw -B package

build\:slim:
	./mvnw -B -DskipTests package

test:
	./mvnw test

test\:watch:
	@if command -v entr >/dev/null; then git ls-files '*.java' | entr -c ./mvnw -q test; \
	else echo "install entr for auto-watch; running once"; ./mvnw -q test; fi

run: $(JAR)
	java -jar $(JAR) $(ARGS)

dev: $(JAR)
	JAHAZIEL_DEMO=true java -Dspring.profiles.active=dev -jar $(JAR) --demo $(ARGS)

reset:
	@read -p "Delete data/ (keeps .env)? [y/N] " ans; \
	if [ "$$ans" = "y" ] || [ "$$ans" = "Y" ]; then rm -rf data; echo "reset: data/ removed"; \
	else echo "reset cancelled"; fi

smoke: $(JAR)
	./mvnw -Psmoke verify -Djahaziel.jar=$(abspath $(JAR))

docs:
	npm ci --prefix docs --no-audit --no-fund && npm run docs:dev --prefix docs

docs\:build:
	npm run docs:build --prefix docs

release:
	./mvnw -B -DskipTests package
	sha256sum $(JAR) | sed 's| .*|  jahaziel.jar|' > $(JAR).sha256
	@cat $(JAR).sha256

clean:
	./mvnw clean

$(JAR):
	./mvnw -B -DskipTests package
