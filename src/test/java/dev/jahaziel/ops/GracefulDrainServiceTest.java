/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavior of {@link GracefulDrainService} and its shutdown hook using fakes. */
class GracefulDrainServiceTest {
    private static final class FakeTask extends GracefulDrainService.TaskHandle {
        private int stops;
        private int closes;
        private long timeout;
        private final boolean completes;

        FakeTask(boolean completes) {
            this.completes = completes;
        }

        @Override
        public void stopAccepting() {
            stops++;
        }

        @Override
        public boolean drain(long timeoutMillis) {
            timeout = timeoutMillis;
            return completes;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class FakeComponent extends GracefulDrainService.ClosableComponent {
        private int closes;

        @Override
        public void close() {
            closes++;
        }
    }

    @Test
    void drainsTasksThenClosesComponentsOnce() {
        GracefulDrainService service = new GracefulDrainService();
        FakeTask task = new FakeTask(true);
        FakeComponent pty = new FakeComponent();
        FakeComponent language = new FakeComponent();
        service.registerTask(task);
        service.registerComponent(pty);
        service.registerComponent(language);

        GracefulDrainService.DrainResult result = service.drain(5000L);
        assertTrue(result.complete());
        assertEquals(1, result.tasks());
        assertEquals(2, result.components());
        assertEquals(1, task.stops);
        assertEquals(5000L, task.timeout);
        assertEquals(1, task.closes);
        assertEquals(1, pty.closes);
        assertEquals(1, language.closes);
        assertTrue(service.isDrained());

        service.drain(5000L);
        assertEquals(1, task.stops, "drain is idempotent");
        assertEquals(1, pty.closes);
    }

    @Test
    void closesComponentsEvenWhenTaskTimesOut() {
        GracefulDrainService service = new GracefulDrainService();
        FakeTask task = new FakeTask(false);
        FakeComponent debug = new FakeComponent();
        service.registerTask(task);
        service.registerComponent(debug);
        GracefulDrainService.DrainResult result = service.drain(10L);
        assertFalse(result.complete());
        assertEquals(1, debug.closes);
        assertEquals(1, task.closes);
    }

    @Test
    void shutdownHookTriggersDrain() {
        GracefulDrainService service = new GracefulDrainService();
        FakeTask task = new FakeTask(true);
        service.registerTask(task);
        OpsShutdownHook hook = new OpsShutdownHook(service, 1000L);
        hook.run();
        assertTrue(service.isDrained());
        assertEquals(1, task.stops);
    }
}
