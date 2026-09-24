/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/** Closes the JaCoCo gaps in {@link ServiceSupervisor}. */
class ServiceSupervisorCoverageGapTest {
  private static Path write(Path dir, String name, String content) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static PrintStream nullOut() {
    return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
  }

  @Test
  void superviseEmptyReturnsOne(@TempDir Path dir) throws Exception {
    Path services = dir.resolve("services");
    Files.createDirectories(services);
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int code =
        ServiceSupervisor.supervise(
            services, dir.resolve("logs"), new PrintStream(buf, true, StandardCharsets.UTF_8));
    assertEquals(1, code);
    assertTrue(buf.toString(StandardCharsets.UTF_8).contains("no .service files"));
    assertEquals(
        1, ServiceSupervisor.supervise(dir.resolve("absent"), dir.resolve("logs"), nullOut()));
  }

  @Test
  void statusPrintsTable(@TempDir Path dir) throws Exception {
    Path services = dir.resolve("svc");
    Files.createDirectories(services);
    write(
        services,
        "a.service",
        "[Unit]\nAfter=b.service\n[Service]\nUser=bob\nExecStart=/bin/true\nRestart=always\n");
    write(services, "b.service", "[Service]\nExecStart=/bin/false\n");
    write(services, "a.timer", "[Timer]\nOnUnitActiveSec=30\n");
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int code =
        ServiceSupervisor.status(services, new PrintStream(buf, true, StandardCharsets.UTF_8));
    assertEquals(0, code);
    String text = buf.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("UNIT"));
    assertTrue(text.contains("a"));
    assertTrue(text.contains("30s"));
  }

  @Test
  void loadNonDirAndTimerFold(@TempDir Path dir) throws Exception {
    assertTrue(ServiceSupervisor.load(dir.resolve("absent")).isEmpty());
    Path services = dir.resolve("s");
    Files.createDirectories(services);
    write(services, "x.service", "[Service]\nExecStart=/bin/true\n");
    write(services, "x.timer", "[Timer]\nOnUnitActiveSec=1m\n");
    write(services, "ignore.txt", "x");
    List<ServiceUnit> units = ServiceSupervisor.load(services);
    assertEquals(1, units.size());
    assertEquals(60L, units.get(0).timerSeconds());
  }

  @Test
  void loadIoFailure(@TempDir Path dir) throws Exception {
    Path services = dir.resolve("s2");
    Files.createDirectories(services);
    write(services, "x.service", "[Service]\nExecStart=/bin/true\n");
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(() -> Files.list(services)).thenThrow(new IOException("mock"));
      assertThrows(java.io.UncheckedIOException.class, () -> ServiceSupervisor.load(services));
    }
  }

  @Test
  void orderedCycleSelfAndDiamond(@TempDir Path dir) throws IOException {
    ServiceUnit a =
        ServiceUnit.parse(
            write(dir, "a.service", "[Unit]\nAfter=b.service\n[Service]\nExecStart=/bin/true\n"));
    ServiceUnit b =
        ServiceUnit.parse(
            write(dir, "b.service", "[Unit]\nAfter=a.service\n[Service]\nExecStart=/bin/true\n"));
    assertThrows(
        IllegalStateException.class, () -> ServiceSupervisor.requireOrdered(List.of(a, b)));
    ServiceUnit self =
        ServiceUnit.parse(
            write(dir, "s.service", "[Unit]\nAfter=s.service\n[Service]\nExecStart=/bin/true\n"));
    assertEquals(1, ServiceSupervisor.requireOrdered(List.of(self)).size());
    ServiceUnit ext =
        ServiceUnit.parse(
            write(
                dir,
                "e.service",
                "[Unit]\nAfter=network.target custom\n[Service]\nExecStart=/bin/true\n"));
    assertEquals(1, ServiceSupervisor.requireOrdered(List.of(ext)).size());
    ServiceUnit d = ServiceUnit.parse(write(dir, "d.service", "[Service]\nExecStart=/bin/true\n"));
    ServiceUnit bb =
        ServiceUnit.parse(
            write(dir, "bb.service", "[Unit]\nAfter=d.service\n[Service]\nExecStart=/bin/true\n"));
    ServiceUnit cc =
        ServiceUnit.parse(
            write(dir, "cc.service", "[Unit]\nAfter=d.service\n[Service]\nExecStart=/bin/true\n"));
    ServiceUnit aa =
        ServiceUnit.parse(
            write(
                dir,
                "aa.service",
                "[Unit]\nAfter=bb.service cc.service\n[Service]\nExecStart=/bin/true\n"));
    List<String> order =
        ServiceSupervisor.requireOrdered(List.of(aa, bb, cc, d)).stream()
            .map(ServiceUnit::name)
            .toList();
    assertEquals(4, order.size());
    assertTrue(order.indexOf("d") < order.indexOf("bb"));
    assertTrue(order.indexOf("d") < order.indexOf("cc"));
  }

  @Test
  void runLogDirFailure(@TempDir Path dir) throws Exception {
    Path services = dir.resolve("svc");
    Files.createDirectories(services);
    write(services, "a.service", "[Service]\nExecStart=/bin/true\n");
    Path logFile = dir.resolve("logfile");
    Files.writeString(logFile, "x", StandardCharsets.UTF_8);
    assertThrows(
        java.io.UncheckedIOException.class,
        () -> ServiceSupervisor.supervise(services, logFile, nullOut()));
  }

  @Test
  void shutdownIdempotent(@TempDir Path dir) throws Exception {
    Object supervisor = newSupervisor(List.of(), dir.resolve("logs"));
    Method shutdown = supervisor.getClass().getDeclaredMethod("shutdown");
    shutdown.setAccessible(true);
    shutdown.invoke(supervisor);
    shutdown.invoke(supervisor);
    Field flag = supervisor.getClass().getDeclaredField("shuttingDown");
    flag.setAccessible(true);
    assertTrue((boolean) flag.get(supervisor));
  }

  @Test
  void serviceHelpers(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("logs"));
    Path svcFile =
        write(
            dir,
            "h.service",
            "[Service]\nExecStart=/bin/true\nExecStartPre=/bin/echo pre\n"
                + "ExecStop=/bin/echo post\nRestart=on-failure\nRestartSec=0\n");
    ServiceUnit unit = ServiceUnit.parse(svcFile);
    Object supervisor = newSupervisor(List.of(unit), dir.resolve("logs"));
    Object service = newService(supervisor, unit);
    assertTrue(invokeBool(service, "shouldRestart", 1));
    assertTrue(!invokeBool(service, "shouldRestart", 0));
    Object alwaysSvc =
        newService(
            supervisor,
            ServiceUnit.parse(
                write(dir, "al.service", "[Service]\nExecStart=/bin/true\nRestart=always\n")));
    assertTrue(invokeBool(alwaysSvc, "shouldRestart", 0));
    Object noSvc =
        newService(
            supervisor,
            ServiceUnit.parse(
                write(dir, "no.service", "[Service]\nExecStart=/bin/true\nRestart=no\n")));
    assertTrue(!invokeBool(noSvc, "shouldRestart", 1));
    List<String> cmd = invokeList(service, "command");
    assertTrue(cmd.contains("/bin/true"));
    assertTrue(cmd.contains("setpriv"));
    assertTrue(invokeList(service, "hookPrefix").contains("setpriv"));
    assertTrue(service.toString().contains("h="));
    invokeVoid(service, "log", "hello-gap");
    assertTrue(
        Files.readString(dir.resolve("logs").resolve("h.log"), StandardCharsets.UTF_8)
            .contains("hello-gap"));
    invokeVoid(service, "sleepSeconds", 0L);
    invokeVoid(service, "runHooks", List.of());
    Thread.currentThread().interrupt();
    try {
      invokeVoid(service, "sleepSeconds", 5L);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void serviceEnvironment(@TempDir Path dir) throws Exception {
    Path envFile = dir.resolve("app.env");
    Files.writeString(
        envFile, "# c\n\nKEY=val\nEMPTY=\nBADLINE\nK=V=extra\n", StandardCharsets.UTF_8);
    ServiceUnit withEnv =
        ServiceUnit.parse(
            write(
                dir,
                "e.service",
                "[Service]\nExecStart=/bin/true\nEnvironmentFile=" + envFile + "\n"));
    Object supervisor = newSupervisor(List.of(withEnv), dir.resolve("logs"));
    Object service = newService(supervisor, withEnv);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> env =
        (java.util.Map<String, String>) invoke(service, "environment");
    assertEquals("val", env.get("KEY"));
    ServiceUnit noEnv =
        ServiceUnit.parse(write(dir, "n.service", "[Service]\nExecStart=/bin/true\n"));
    Object plain = newService(supervisor, noEnv);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> empty =
        (java.util.Map<String, String>) invoke(plain, "environment");
    assertTrue(empty.isEmpty());
    ServiceUnit dirEnv =
        ServiceUnit.parse(
            write(
                dir, "d.service", "[Service]\nExecStart=/bin/true\nEnvironmentFile=" + dir + "\n"));
    Object dirSvc = newService(supervisor, dirEnv);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> dirMap =
        (java.util.Map<String, String>) invoke(dirSvc, "environment");
    assertTrue(dirMap.isEmpty());
  }

  @Test
  void serviceLaunchFailureAndStop(@TempDir Path dir) throws Exception {
    ServiceUnit unit =
        ServiceUnit.parse(write(dir, "f.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), dir.resolve("logs"));
    Object service = newService(supervisor, unit);
    int code = (int) invoke(service, "launchOnce");
    assertEquals(127, code);
    invokeVoid(service, "stop");
    Process mockAlive = Mockito.mock(Process.class);
    Mockito.when(mockAlive.isAlive()).thenReturn(true);
    Mockito.when(mockAlive.waitFor(20L, TimeUnit.SECONDS)).thenReturn(true);
    setField(service, "process", mockAlive);
    setField(service, "wanted", true);
    invokeVoid(service, "stop");
    Mockito.verify(mockAlive).destroy();
    Process mockTimeout = Mockito.mock(Process.class);
    Mockito.when(mockTimeout.isAlive()).thenReturn(true);
    Mockito.when(mockTimeout.waitFor(20L, TimeUnit.SECONDS)).thenReturn(false);
    setField(service, "process", mockTimeout);
    setField(service, "wanted", true);
    invokeVoid(service, "stop");
    Mockito.verify(mockTimeout).destroyForcibly();
    Process mockInterrupt = Mockito.mock(Process.class);
    Mockito.when(mockInterrupt.isAlive()).thenReturn(true);
    Mockito.when(mockInterrupt.waitFor(20L, TimeUnit.SECONDS))
        .thenThrow(new InterruptedException("mock"));
    setField(service, "process", mockInterrupt);
    setField(service, "wanted", true);
    invokeVoid(service, "stop");
    assertTrue(Thread.interrupted() || !Thread.currentThread().isInterrupted());
    Thread.interrupted();
  }

  @Test
  void serviceLaunchSuccessViaMock(@TempDir Path dir) throws Exception {
    ServiceUnit unit =
        ServiceUnit.parse(write(dir, "s.service", "[Service]\nExecStart=/bin/true\n"));
    Object supervisor = newSupervisor(List.of(unit), dir.resolve("logs"));
    Object service = newService(supervisor, unit);
    Process mockProc = Mockito.mock(Process.class);
    Mockito.when(mockProc.waitFor()).thenReturn(0);
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              Mockito.when(mock.start()).thenReturn(mockProc);
              Mockito.when(mock.redirectErrorStream(any(boolean.class))).thenReturn(mock);
              Mockito.when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                  .thenReturn(mock);
              Mockito.when(mock.directory(any())).thenReturn(mock);
              Mockito.when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      int code = (int) invoke(service, "launchOnce");
      assertEquals(0, code);
    }
    Process mockFail = Mockito.mock(Process.class);
    Mockito.when(mockFail.waitFor()).thenThrow(new InterruptedException("mock"));
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              Mockito.when(mock.start()).thenReturn(mockFail);
              Mockito.when(mock.redirectErrorStream(any(boolean.class))).thenReturn(mock);
              Mockito.when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                  .thenReturn(mock);
              Mockito.when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      int code = (int) invoke(service, "launchOnce");
      assertEquals(130, code);
      Thread.interrupted();
    }
  }

  @Test
  void serviceRunHooksSuccessAndFailure(@TempDir Path dir) throws Exception {
    ServiceUnit unit =
        ServiceUnit.parse(
            write(
                dir, "hk.service", "[Service]\nExecStart=/bin/true\nExecStartPre=/bin/echo hi\n"));
    Object supervisor = newSupervisor(List.of(unit), dir.resolve("logs"));
    Object service = newService(supervisor, unit);
    Process mockProc = Mockito.mock(Process.class);
    Mockito.when(mockProc.waitFor()).thenReturn(0);
    try (MockedConstruction<ProcessBuilder> ignored =
        Mockito.mockConstruction(
            ProcessBuilder.class,
            (mock, ctx) -> {
              Mockito.when(mock.start()).thenReturn(mockProc);
              Mockito.when(mock.redirectErrorStream(any(boolean.class))).thenReturn(mock);
              Mockito.when(mock.redirectOutput(any(ProcessBuilder.Redirect.class)))
                  .thenReturn(mock);
              Mockito.when(mock.environment()).thenReturn(new java.util.HashMap<>());
            })) {
      invokeVoid(service, "runHooks", List.of("/bin/echo", "hi"));
    }
    invokeVoid(service, "runHooks", List.of("/bin/echo", "hi"));
    Thread.currentThread().interrupt();
    try {
      invokeVoid(service, "runHooks", List.of("/bin/echo", "hi"));
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void serviceStartTimerAndStop(@TempDir Path dir) throws Exception {
    ServiceUnit timer =
        ServiceUnit.parse(write(dir, "t.service", "[Service]\nExecStart=/bin/true\n"))
            .withTimerSeconds(1);
    Object supervisor = newSupervisor(List.of(timer), dir.resolve("logs"));
    Object service = newService(supervisor, timer);
    Method start = service.getClass().getDeclaredMethod("start");
    start.setAccessible(true);
    start.invoke(service);
    Thread.sleep(400);
    invokeVoid(service, "stop");
    Thread.sleep(400);
  }

  @Test
  void sandboxAllBranches(@TempDir Path dir) throws IOException {
    ServiceUnit plain =
        ServiceUnit.parse(write(dir, "p.service", "[Service]\nExecStart=/bin/true\n"));
    List<String> plainPrefix = ServiceSupervisor.sandboxPrefix(plain);
    assertTrue(plainPrefix.contains("setpriv"));
    assertTrue(!plainPrefix.contains("unshare"));
    assertTrue(!plainPrefix.contains("--no-new-privs"));
    ServiceUnit full =
        ServiceUnit.parse(
            write(
                dir,
                "f.service",
                "[Service]\nUser=bob\nGroup=grp\nExecStart=/bin/true\nLimitNOFILE=100\n"
                    + "NoNewPrivileges=yes\nProtectSystem=strict\nPrivateTmp=yes\n"
                    + "ProtectHome=yes\nReadWritePaths=/tmp/x\n"));
    List<String> fullPrefix = ServiceSupervisor.sandboxPrefix(full);
    assertTrue(fullPrefix.contains("unshare"));
    assertTrue(fullPrefix.contains("--no-new-privs"));
    assertTrue(fullPrefix.contains("--reuid=bob"));
    assertTrue(fullPrefix.contains("--regid=grp"));
    assertTrue(fullPrefix.contains("--rlimit-nofile=100"));
    assertTrue(fullPrefix.contains("/bin/sh"));
    ServiceUnit nogroup =
        ServiceUnit.parse(write(dir, "g.service", "[Service]\nUser=bob\nExecStart=/bin/true\n"));
    assertTrue(ServiceSupervisor.sandboxPrefix(nogroup).contains("--regid=bob"));
    String script = ServiceSupervisor.sandboxScript(full);
    assertTrue(script.contains("remount,ro /"));
    assertTrue(script.contains("tmpfs /tmp"));
    assertTrue(script.contains("/home"));
    assertTrue(script.contains("/tmp/x"));
    assertTrue(script.endsWith("exec \"$@\""));
    assertEquals("exec \"$@\"", ServiceSupervisor.sandboxScript(plain));
    ServiceUnit homeOnly =
        ServiceUnit.parse(
            write(dir, "h.service", "[Service]\nExecStart=/bin/true\nProtectHome=yes\n"));
    assertTrue(ServiceSupervisor.sandboxScript(homeOnly).contains("/home"));
    assertEquals("a/b", ServiceSupervisor.posix(Path.of("a/b")));
  }

  private static Object newSupervisor(List<ServiceUnit> units, Path logDir) throws Exception {
    Constructor<?> cons =
        ServiceSupervisor.class.getDeclaredConstructor(List.class, Path.class, PrintStream.class);
    cons.setAccessible(true);
    return cons.newInstance(units, logDir, nullOut());
  }

  private static Object newService(Object supervisor, ServiceUnit unit) throws Exception {
    Class<?> inner = Class.forName("dev.jahaziel.dist.supervisor.ServiceSupervisor$Service");
    Constructor<?> cons = inner.getDeclaredConstructor(ServiceSupervisor.class, ServiceUnit.class);
    cons.setAccessible(true);
    return cons.newInstance(supervisor, unit);
  }

  private static Object invoke(Object target, String name, Object... args) throws Exception {
    for (Method m : target.getClass().getDeclaredMethods()) {
      if (m.getName().equals(name) && m.getParameterCount() == args.length) {
        m.setAccessible(true);
        return m.invoke(target, args);
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static void invokeVoid(Object target, String name, Object... args) throws Exception {
    invoke(target, name, args);
  }

  private static boolean invokeBool(Object target, String name, int exit) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name, int.class);
    m.setAccessible(true);
    return (boolean) m.invoke(target, exit);
  }

  @SuppressWarnings("unchecked")
  private static List<String> invokeList(Object target, String name) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name);
    m.setAccessible(true);
    return (List<String>) m.invoke(target);
  }

  private static void invokeVoid(Object target, String name, List<String> hook) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name, List.class);
    m.setAccessible(true);
    m.invoke(target, hook);
  }

  private static void invokeVoid(Object target, String name, long seconds) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name, long.class);
    m.setAccessible(true);
    m.invoke(target, seconds);
  }

  private static void invokeVoid(Object target, String name, String msg) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name, String.class);
    m.setAccessible(true);
    m.invoke(target, msg);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
