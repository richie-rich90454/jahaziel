#!/usr/bin/env bash
# Copyright 2026 The Jahaziel Authors
# SPDX-License-Identifier: Apache-2.0
#
# Jahaziel shell integration. Emits OSC 633 sequences for command audit.
# Installed to /etc/profile.d, /etc/bash.bashrc, and /etc/zsh/zshrc.

if [[ -n "${JAHAZIEL_SHELL_INTEGRATION_LOADED:-}" ]]; then
    return
fi
export JAHAZIEL_SHELL_INTEGRATION_LOADED=1

__jahaziel_osc() {
    printf '\033]633;%s\a' "$1"
}

__jahaziel_preexec() {
    local cmd="$1"
    __jahaziel_osc "C"
    __jahaziel_osc "E;${cmd//[^[:print:]]/}"
    export JAHAZIEL_CMD="$cmd"
    export JAHAZIEL_CMD_START="$(date +%s%N)"
}

__jahaziel_precmd() {
    local exit_code=$?
    if [[ -n "${JAHAZIEL_CMD:-}" ]]; then
        local duration_ms=$(( ($(date +%s%N) - JAHAZIEL_CMD_START) / 1000000 ))
        __jahaziel_osc "D;${exit_code};${duration_ms}"
        unset JAHAZIEL_CMD
        unset JAHAZIEL_CMD_START
    fi
    __jahaziel_osc "A"
}

if [[ -n "${BASH_VERSION:-}" ]]; then
    trap '__jahaziel_preexec "$BASH_COMMAND"' DEBUG
    PROMPT_COMMAND="__jahaziel_precmd${PROMPT_COMMAND:+;$PROMPT_COMMAND}"
fi

if [[ -n "${ZSH_VERSION:-}" ]]; then
    preexec_functions+=(__jahaziel_preexec)
    precmd_functions+=(__jahaziel_precmd)
fi
