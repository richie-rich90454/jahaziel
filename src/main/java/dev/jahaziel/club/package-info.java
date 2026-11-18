/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Developer Club: additive teaching and collaboration features layered on top of Jahaziel.
 *
 * <p>Club never replaces the workspace or snapshot subsystems. It only calls their public APIs to
 * fork isolated workspaces, run declared validation tasks, and take checkpoint snapshots. Git stays
 * the source of truth for every project: club exports are advisory patches and archives, never an
 * authoritative copy of a participant's work. Club tables and files are optional additions; the
 * platform runs unchanged when no club session exists.
 */
package dev.jahaziel.club;
