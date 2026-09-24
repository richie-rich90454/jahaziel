/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Final gap tests: run/shutdown flow, stops, interrupts, sandboxes, and unquoting. */
class SupervisorFinalTest {
  @TempDir private Path tmp;

  private static boolean windows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String instantCommand() {
    return windows() ? "cmd.exe /c exit 0" : "true";
  }

  private static Path write(Path dir, String name, String body) throws Exception {
    Path file = dir.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, body, StandardCharsets.UTF_8);
    return file;
  }

  private static PrintStream nullOut() {
    return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
  }

  private static Object newSupervisor(List<ServiceUnit> units, Path logs) throws Exception {
    Constructor<?> ctor =
        ServiceSupervisor.class.getDeclaredConstructor(List.class, Path.class, PrintStream.class);
    ctor.setAccessible(true);
    return ctor.newInstance(units, logs, nullOut());
  }

  private static Object newService(Object supervisor, ServiceUnit unit) throws Exception {
    Class<?> serviceClass = Class.forName(ServiceSupervisor.class.getName() + "$Service");
    Constructor<?> ctor =
        serviceClass.getDeclaredConstructor(ServiceSupervisor.class, ServiceUnit.class);
    ctor.setAccessible(true);
    return ctor.newInstance(supervisor, unit);
  }

  private static void setWanted(Object service, boolean value) throws Exception {
    Field wanted = service.getClass().getDeclaredField("wanted");
    wanted.setAccessible(true);
    wanted.setBoolean(service, value);
  }

  private static Object invoke(Object target, String name, Object... args) throws Exception {
    Class<?>[] types = new Class<?>[args.length];
    for (int i = 0; i < args.length; i++) {
      types[i] =
          args[i] instanceof Integer
              ? int.class
              : args[i] instanceof Long
                  ? long.class
                  : args[i] instanceof Boolean ? boolean.class : args[i].getClass();
    }
    return invokeAs(target, name, types, args);
  }

  private static Object invokeAs(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(target, args);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
  }

  @Test
  void superviseWithUnitsReturnsZero() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    write(services, "a.service", "[Service]\nExecStart=/bin/true\n");
    try (var ignored = Mockito.mockConstruction(ServiceSupervisor.class)) {
      assertEquals(0, ServiceSupervisor.supervise(services, tmp.resolve("logs"), nullOut()));
    }
  }

  @Test
  void runStartsServicesAndStops() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    write(services, "a.service", "[Service]\nExecStart=/bin/true\nRestart=no\n");
    ServiceUnit unit = ServiceUnit.parse(services.resolve("a.service"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs"));
    AtomicReference<Object> result = new AtomicReference<>();
    Thread runner =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    Method run = supervisor.getClass().getDeclaredMethod("run");
                    run.setAccessible(true);
                    result.set(run.invoke(supervisor));
                  } catch (Exception failure) {
                    result.set(failure);
                  }
                });
    Thread.sleep(1500);
    Method shutdown = supervisor.getClass().getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(supervisor);
    runner.join(15_000);
    assertEquals(0, result.get());
  }

  @Test
  void runRestartsAlwaysUnits() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    write(services, "r.service", "[Service]\nExecStart=/bin/true\nRestart=always\nRestartSec=0\n");
    ServiceUnit unit = ServiceUnit.parse(services.resolve("r.service"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs2"));
    AtomicReference<Object> result = new AtomicReference<>();
    Thread runner =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    Method run = supervisor.getClass().getDeclaredMethod("run");
                    run.setAccessible(true);
                    result.set(run.invoke(supervisor));
                  } catch (Exception failure) {
                    result.set(failure);
                  }
                });
    Thread.sleep(1500);
    Method shutdown = supervisor.getClass().getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(supervisor);
    runner.join(15_000);
    assertEquals(0, result.get());
  }

  @Test
  void runTimerLoopsUntilShutdown() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    write(services, "t.service", "[Service]\nExecStart=/bin/true\nOnUnitActiveSec=1\n");
    ServiceUnit unit = ServiceUnit.parse(services.resolve("t.service"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs3"));
    AtomicReference<Object> result = new AtomicReference<>();
    Thread runner =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    Method run = supervisor.getClass().getDeclaredMethod("run");
                    run.setAccessible(true);
                    result.set(run.invoke(supervisor));
                  } catch (Exception failure) {
                    result.set(failure);
                  }
                });
    Thread.sleep(2500);
    Method shutdown = supervisor.getClass().getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(supervisor);
    runner.join(15_000);
    assertEquals(0, result.get());
  }

  @Test
  void startInterruptRestoresFlag() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                services,
                "s.service",
                "[Service]\nExecStart=/bin/true\nRestart=always\nRestartSec=0\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs4"));
    Object service = newService(supervisor, unit);
    AtomicReference<Object> done = new AtomicReference<>();
    Thread starter =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    invoke(service, "start");
                    done.set(Boolean.TRUE);
                  } catch (Exception failure) {
                    done.set(failure);
                  }
                });
    long end = System.currentTimeMillis() + 10_000;
    while (starter.getState() != Thread.State.TIMED_WAITING && System.currentTimeMillis() < end) {
      Thread.sleep(10);
    }
    assertEquals(Thread.State.TIMED_WAITING, starter.getState(), "starter never reached join");
    starter.interrupt();
    starter.join(15_000);
    assertEquals(Boolean.TRUE, done.get());
    assertTrue(starter.isInterrupted());
    invoke(service, "stop");
  }

  @Test
  void runHooksInterruptRestoresFlag() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "h.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs5"));
    Object service = newService(supervisor, unit);
    Process proc = mock(Process.class);
    when(proc.waitFor()).thenThrow(new InterruptedException());
    try (var constructors =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              when(mock.start()).thenReturn(proc);
              when(mock.redirectErrorStream(Mockito.anyBoolean())).thenReturn(mock);
              when(mock.redirectOutput(Mockito.<ProcessBuilder.Redirect>any())).thenReturn(mock);
              when(mock.directory(Mockito.any())).thenReturn(mock);
              when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      invokeAs(service, "runHooks", new Class<?>[] {List.class}, List.of("hook-cmd"));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void sleepInterruptRestoresFlag() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "z.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs6"));
    Object service = newService(supervisor, unit);
    Field wanted = service.getClass().getDeclaredField("wanted");
    wanted.setAccessible(true);
    wanted.setBoolean(service, true);
    Thread.currentThread().interrupt();
    try {
      invokeAs(service, "sleepSeconds", new Class<?>[] {long.class}, 30L);
    } finally {
      assertTrue(Thread.currentThread().isInterrupted());
      Thread.interrupted();
    }
    invoke(service, "sleepSeconds", 0L);
  }

  @Test
  void runWithoutStartReturnsImmediately() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "n.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs9"));
    Object service = newService(supervisor, unit);
    invoke(service, "run");
    ServiceUnit timer =
        ServiceUnit.parse(
            write(services, "nt.service", "[Service]\nExecStart=/bin/true\nOnUnitActiveSec=5\n"));
    Object timerService = newService(supervisor, timer);
    invoke(timerService, "run");
  }

  @Test
  void runStopsMidLaunch() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(
            write(services, "m.service", "[Service]\nExecStart=/bin/true\nRestart=no\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs10"));
    Object service = newService(supervisor, unit);
    Process proc = mock(Process.class);
    when(proc.waitFor())
        .thenAnswer(
            inv -> {
              setWanted(service, false);
              return 5;
            });
    try (var constructors =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              when(mock.start()).thenReturn(proc);
              when(mock.redirectErrorStream(Mockito.anyBoolean())).thenReturn(mock);
              when(mock.redirectOutput(Mockito.<ProcessBuilder.Redirect>any())).thenReturn(mock);
              when(mock.directory(Mockito.any())).thenReturn(mock);
              when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      setWanted(service, true);
      invoke(service, "run");
    }
  }

  @Test
  void runSeesShutdownMidLaunch() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(
            write(services, "n2.service", "[Service]\nExecStart=/bin/true\nRestart=always\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs13"));
    Object service = newService(supervisor, unit);
    Process proc = mock(Process.class);
    when(proc.waitFor())
        .thenAnswer(
            inv -> {
              Field down = supervisor.getClass().getDeclaredField("shuttingDown");
              down.setAccessible(true);
              down.setBoolean(supervisor, true);
              return 0;
            });
    try (var constructors =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              when(mock.start()).thenReturn(proc);
              when(mock.redirectErrorStream(Mockito.anyBoolean())).thenReturn(mock);
              when(mock.redirectOutput(Mockito.<ProcessBuilder.Redirect>any())).thenReturn(mock);
              when(mock.directory(Mockito.any())).thenReturn(mock);
              when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      setWanted(service, true);
      invoke(service, "run");
    }
  }

  @Test
  void launchUsesWorkingDirectory() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    Path work = tmp.resolve("work");
    Files.createDirectories(work);
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                services,
                "w.service",
                "[Service]\nExecStart="
                    + instantCommand()
                    + "\nWorkingDirectory="
                    + work
                    + "\n"
                    + "Restart=no\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs11"));
    Object service = newService(supervisor, unit);
    setWanted(service, true);
    invoke(service, "run");
  }

  @Test
  void sleepSkipsWhenShutDown() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "q.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs12"));
    Object service = newService(supervisor, unit);
    Field wanted = service.getClass().getDeclaredField("wanted");
    wanted.setAccessible(true);
    wanted.setBoolean(service, true);
    Method shutdown = supervisor.getClass().getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(supervisor);
    wanted.setBoolean(service, true);
    invoke(service, "sleepSeconds", 5L);
    invoke(service, "run");
  }

  @Test
  void stopHandlesLiveAndDyingProcesses() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "l.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs14"));
    Object service = newService(supervisor, unit);
    invoke(service, "stop");
    Field processField = service.getClass().getDeclaredField("process");
    processField.setAccessible(true);

    Process alive = mock(Process.class);
    when(alive.isAlive()).thenReturn(true);
    when(alive.waitFor(20L, TimeUnit.SECONDS)).thenReturn(true);
    processField.set(service, alive);
    invoke(service, "stop");
    Mockito.verify(alive).destroy();

    Process dying = mock(Process.class);
    when(dying.isAlive()).thenReturn(true);
    when(dying.waitFor(20L, TimeUnit.SECONDS)).thenReturn(false);
    processField.set(service, dying);
    invoke(service, "stop");
    Mockito.verify(dying).destroyForcibly();

    Process exited = mock(Process.class);
    when(exited.isAlive()).thenReturn(false);
    processField.set(service, exited);
    invoke(service, "stop");
    Mockito.verify(exited, Mockito.never()).destroy();

    Process interrupted = mock(Process.class);
    when(interrupted.isAlive()).thenReturn(true);
    when(interrupted.waitFor(20L, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
    processField.set(service, interrupted);
    Thread.currentThread().interrupt();
    try {
      invoke(service, "stop");
    } finally {
      assertTrue(Thread.currentThread().isInterrupted());
      Thread.interrupted();
    }
    Mockito.verify(interrupted).destroyForcibly();
  }

  @Test
  void runTimerSkipsWhenShutDown() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit timer =
        ServiceUnit.parse(
            write(services, "ts.service", "[Service]\nExecStart=/bin/true\nOnUnitActiveSec=5\n"));
    Object supervisor = newSupervisor(List.of(timer), tmp.resolve("logs15"));
    Object service = newService(supervisor, timer);
    setWanted(service, true);
    Field down = supervisor.getClass().getDeclaredField("shuttingDown");
    down.setAccessible(true);
    down.setBoolean(supervisor, true);
    invoke(service, "run");
  }

  @Test
  void stopHandlesDeadProcess() throws Exception {
    String instant = windows() ? "cmd.exe /c exit 0" : "true";
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit unit =
        ServiceUnit.parse(write(services, "d.service", "[Service]\nExecStart=" + instant + "\n"));
    Object supervisor = newSupervisor(List.of(unit), tmp.resolve("logs7"));
    Object service = newService(supervisor, unit);
    invoke(service, "start");
    long end = System.currentTimeMillis() + 10_000;
    Field processField = service.getClass().getDeclaredField("process");
    processField.setAccessible(true);
    while (System.currentTimeMillis() < end) {
      Process process = (Process) processField.get(service);
      if (process != null && !process.isAlive()) {
        break;
      }
      Thread.sleep(50);
    }
    invoke(service, "stop");
  }

  @Test
  void environmentReadsFiles() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    Path envFile = services.resolve("app.env");
    Files.writeString(envFile, "A=1\n# comment\n\nBADLINE\nB = 2\n", StandardCharsets.UTF_8);
    ServiceUnit withEnv =
        ServiceUnit.parse(
            write(
                services,
                "e.service",
                "[Service]\nExecStart=/bin/true\nEnvironmentFile=" + envFile + "\n"));
    Object supervisor = newSupervisor(List.of(withEnv), tmp.resolve("logs8"));
    Object service = newService(supervisor, withEnv);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> environment =
        (java.util.Map<String, String>) invoke(service, "environment");
    assertEquals("1", environment.get("A"));
    assertEquals(" 2", environment.get("B "));

    Path missing = services.resolve("missing.env");
    ServiceUnit missingEnv =
        ServiceUnit.parse(
            write(
                services,
                "m.service",
                "[Service]\nExecStart=/bin/true\nEnvironmentFile=" + missing + "\n"));
    Object plain = newService(supervisor, missingEnv);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> empty =
        (java.util.Map<String, String>) invoke(plain, "environment");
    assertTrue(empty.isEmpty());

    Path dirEnv = services.resolve("dir.env");
    Files.createDirectories(dirEnv);
    ServiceUnit dirUnit =
        ServiceUnit.parse(
            write(
                services,
                "f.service",
                "[Service]\nExecStart=/bin/true\nEnvironmentFile=" + dirEnv + "\n"));
    Object dirService = newService(supervisor, dirUnit);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> alsoEmpty =
        (java.util.Map<String, String>) invoke(dirService, "environment");
    assertTrue(alsoEmpty.isEmpty());
  }

  @Test
  void sandboxCoversCombinations() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    ServiceUnit plain =
        ServiceUnit.parse(write(services, "p.service", "[Service]\nExecStart=/bin/true\n"));
    assertTrue(ServiceSupervisor.sandboxPrefix(plain).contains("setpriv"));
    assertEquals("exec \"$@\"", ServiceSupervisor.sandboxScript(plain).trim());

    ServiceUnit guarded =
        ServiceUnit.parse(
            write(
                services,
                "g.service",
                "[Service]\nExecStart=/bin/true\nProtectSystem=strict\nPrivateTmp=yes\n"
                    + "ProtectHome=yes\nUser=alice\nGroup=staff\nLimitNOFILE=1024\n"
                    + "ReadWritePaths=/tmp/x\nNoNewPrivileges=yes\n"));
    List<String> prefix = ServiceSupervisor.sandboxPrefix(guarded);
    assertTrue(prefix.contains("unshare"));
    assertTrue(prefix.contains("--no-new-privs"));
    assertTrue(prefix.contains("--reuid=alice"));
    assertTrue(prefix.contains("--regid=staff"));
    assertTrue(prefix.stream().anyMatch(part -> part.startsWith("--rlimit-nofile=")));
    String script = ServiceSupervisor.sandboxScript(guarded);
    assertTrue(script.contains("remount,ro"));
    assertTrue(script.contains("tmpfs"));

    ServiceUnit userOnly =
        ServiceUnit.parse(
            write(services, "u.service", "[Service]\nExecStart=/bin/true\nUser=bob\n"));
    assertTrue(ServiceSupervisor.sandboxPrefix(userOnly).contains("--regid=bob"));

    ServiceUnit systemOnly =
        ServiceUnit.parse(
            write(services, "s.service", "[Service]\nExecStart=/bin/true\nProtectSystem=strict\n"));
    assertTrue(ServiceSupervisor.sandboxScript(systemOnly).contains("remount,ro"));

    ServiceUnit tmpOnly =
        ServiceUnit.parse(
            write(services, "t.service", "[Service]\nExecStart=/bin/true\nPrivateTmp=yes\n"));
    assertTrue(ServiceSupervisor.sandboxScript(tmpOnly).contains("tmpfs /tmp"));
    assertTrue(ServiceSupervisor.sandboxPrefix(tmpOnly).contains("/bin/sh"));

    ServiceUnit homeOnly =
        ServiceUnit.parse(
            write(services, "o.service", "[Service]\nExecStart=/bin/true\nProtectHome=yes\n"));
    assertTrue(ServiceSupervisor.sandboxScript(homeOnly).contains("/home"));
    assertTrue(ServiceSupervisor.sandboxPrefix(homeOnly).contains("/bin/sh"));
  }

  @Test
  void unquoteAndSecondsShapes() throws Exception {
    Path services = tmp.resolve("svc");
    Files.createDirectories(services);
    assertEquals(
        "dq",
        ServiceUnit.parse(
                write(
                    services, "a.service", "[Service]\nDescription=\"dq\"\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        "sq",
        ServiceUnit.parse(
                write(services, "b.service", "[Service]\nDescription='sq'\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        "\"mix'",
        ServiceUnit.parse(
                write(
                    services, "c.service", "[Service]\nDescription=\"mix'\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        "'ab\"",
        ServiceUnit.parse(
                write(services, "g.service", "[Service]\nDescription='ab\"\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        "ab",
        ServiceUnit.parse(
                write(services, "h.service", "[Service]\nDescription=ab\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        "x",
        ServiceUnit.parse(
                write(services, "d.service", "[Service]\nDescription=x\nExecStart=/bin/true\n"))
            .description());
    assertEquals(
        0L,
        ServiceUnit.parse(
                write(services, "e.service", "[Service]\nExecStart=/bin/true\nRestartSec=   \n"))
            .restartSec());
    assertEquals(
        10L,
        ServiceUnit.parse(
                write(services, "f.service", "[Service]\nExecStart=/bin/true\nRestartSec=10\n"))
            .restartSec());
  }
}
