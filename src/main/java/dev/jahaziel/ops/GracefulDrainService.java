/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates a graceful shutdown: stop accepting work, drain running tasks within a deadline, then
 * close PTYs, language servers and debug adapters. Registration happens through the shutdown hook
 * only; no existing lifecycle class is modified.
 */
public final class GracefulDrainService {
    /** A running task that can be stopped and drained. */
    public abstract static class TaskHandle {
        /** Refuses new work. */
        public abstract void stopAccepting();

        /** Waits up to the timeout for running work. Returns true when fully drained. */
        public abstract boolean drain(long timeoutMillis);

        /** Releases the task's resources. */
        public abstract void close();
    }

    /** A closable component such as a PTY pool or a language server. */
    public abstract static class ClosableComponent {
        /** Releases the component's resources. */
        public abstract void close();
    }

    /** Result of a drain pass. */
    public static final class DrainResult {
        private final boolean complete;
        private final int tasks;
        private final int components;

        /** Creates a result. */
        public DrainResult(boolean complete, int tasks, int components) {
            this.complete = complete;
            this.tasks = tasks;
            this.components = components;
        }

        /** Returns whether every task finished before the deadline. */
        public boolean complete() {
            return complete;
        }

        /** Returns the number of drained tasks. */
        public int tasks() {
            return tasks;
        }

        /** Returns the number of closed components. */
        public int components() {
            return components;
        }
    }

    private final List<TaskHandle> tasks = new ArrayList<TaskHandle>();
    private final List<ClosableComponent> components = new ArrayList<ClosableComponent>();
    private boolean drained;
    private DrainResult result;

    /** Registers a running task. */
    public void registerTask(TaskHandle task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        tasks.add(task);
    }

    /** Registers a closable component. */
    public void registerComponent(ClosableComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }
        components.add(component);
    }

    /** Returns whether a drain has already run. */
    public synchronized boolean isDrained() {
        return drained;
    }

    /** Returns the last drain result, or null before a drain runs. */
    public synchronized DrainResult result() {
        return result;
    }

    /**
     * Drains once. Concurrent or repeated calls return the first result without touching resources
     * again.
     */
    public synchronized DrainResult drain(long timeoutMillis) {
        if (drained) {
            return result;
        }
        drained = true;
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).stopAccepting();
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        boolean complete = true;
        for (int i = 0; i < tasks.size(); i++) {
            long remaining = deadline - System.currentTimeMillis();
            if (!tasks.get(i).drain(Math.max(0L, remaining))) {
                complete = false;
            }
        }
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).close();
        }
        for (int i = 0; i < components.size(); i++) {
            components.get(i).close();
        }
        result = new DrainResult(complete, tasks.size(), components.size());
        return result;
    }
}
