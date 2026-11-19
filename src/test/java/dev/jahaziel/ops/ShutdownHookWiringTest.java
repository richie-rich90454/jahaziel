/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.config.OpsShutdownHookRegistrar;
import dev.jahaziel.config.PluginWiringConfig;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;

/** Verifies that the drain hook is bound to the Spring lifecycle and the JVM shutdown hook. */
class ShutdownHookWiringTest {
    @Test
    void registrarIsDeclaredWithInitAndDestroyMethods() throws Exception {
        Method method =
                PluginWiringConfig.class.getMethod(
                        "opsShutdownHookRegistrar", OpsShutdownHook.class);
        Bean bean = method.getAnnotation(Bean.class);
        assertNotNull(bean);
        assertEquals("register", bean.initMethod());
        assertEquals("unregister", bean.destroyMethod());
    }

    @Test
    void registrarAttachesAndDetachesTheHook() {
        OpsShutdownHook hook = new OpsShutdownHook(new GracefulDrainService(), 10L);
        OpsShutdownHookRegistrar registrar = new OpsShutdownHookRegistrar(hook);
        registrar.register();
        assertTrue(Runtime.getRuntime().removeShutdownHook(hook));
        registrar.unregister();
    }

    @Test
    void hookDrainsExactlyOnce() {
        GracefulDrainService drain = new GracefulDrainService();
        OpsShutdownHook hook = new OpsShutdownHook(drain, 10L);
        hook.run();
        hook.run();
        assertTrue(drain.isDrained());
        assertEquals(0, drain.result().tasks());
    }
}
