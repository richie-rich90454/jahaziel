# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Dev workflow for Linux/macOS. Usage: make <verb> [ARGS="..."]
# Verbs: bootstrap build build:slim test test:watch run dev reset smoke docs docs:build release clean
# (Colons are backslash-escaped: GNU make needs build\:slim as the target name.)
.PHONY: bootstrap web build build\:slim test test\:watch run dev reset smoke docs docs\:build release clean

JAR := target/jahaziel.jar

bootstrap:
	@java -version 2>&1 | grep -Eq 'version "25([.]|")' || { echo "JDK 25 is required; select an existing JDK." >&2; exit 1; }
	@NODE_MAJOR=$$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null) && test "$$NODE_MAJOR" -ge 24 || { echo "Node.js 24 or newer is required; select an existing Node runtime." >&2; exit 1; }
	@test "$$(pnpm --version 2>/dev/null)" = "12.14.0" || { echo "pnpm 12.14.0 is required; select the existing pinned pnpm runtime." >&2; exit 1; }
	./mvnw -version

web:
	@if [ ! -x web/node_modules/.bin/tsc ]; then pnpm --dir web install --frozen-lockfile; fi
	pnpm --dir web run build

build: web
	./mvnw -B package

build\:slim: web
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
	@if [ ! -x docs/node_modules/.bin/vitepress ]; then pnpm --dir docs install --frozen-lockfile || exit $$?; fi; pnpm --dir docs run docs:dev

docs\:build:
	@if [ ! -x docs/node_modules/.bin/vitepress ]; then pnpm --dir docs install --frozen-lockfile || exit $$?; fi; pnpm --dir docs run docs:build

release: web
	./mvnw -B -DskipTests package
	sha256sum $(JAR) | sed 's| .*|  jahaziel.jar|' > $(JAR).sha256
	@cat $(JAR).sha256

clean:
	./mvnw clean


$(JAR):
	@if [ ! -x web/node_modules/.bin/tsc ]; then pnpm --dir web install --frozen-lockfile; fi
	pnpm --dir web run build
	./mvnw -B -DskipTests package
