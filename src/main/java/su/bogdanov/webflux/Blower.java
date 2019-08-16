package su.bogdanov.webflux;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Blower {
    private static final long MONITORING_PERIOD_MS = 5000L;

    public static void main(String args[]) {
        try {
            final int poolSize = Integer.valueOf(args[0]);
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            DateTimeFormatter dateTimeFormat = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);
            CountDownLatch cnt = new CountDownLatch(poolSize);
            AtomicInteger pending = new AtomicInteger();
            AtomicLong total = new AtomicLong();
            AtomicLong lastTotal = new AtomicLong(total.get());
            AtomicInteger live = new AtomicInteger();
            AtomicInteger exited = new AtomicInteger();
            Instant t1 = Instant.now();
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
                    () -> {
                        long currentTotal = total.get();
                        double rps = (currentTotal - lastTotal.get()) * 1000 / (double) MONITORING_PERIOD_MS;
                        double avg = currentTotal * 1000 / (double) Duration.between(t1, Instant.now()).toMillis();
                        lastTotal.set(currentTotal);
                        System.out.printf("[%s] pending: %3d, total: %5d (live: %3d; exited: %3d) %.2f rps (%.2f rps)%n", dateTimeFormat.format(Instant.now()), pending.get(), total.get(), live.get(), exited.get(), rps, avg);
                    },
                    MONITORING_PERIOD_MS,
                    MONITORING_PERIOD_MS, TimeUnit.MILLISECONDS);
            for (int i = 0; i < poolSize; i++) {
                Thread.sleep(10L);
                final int threadId = i;
                pool.submit(() -> {
                    live.incrementAndGet();
                    try {
                        while (true) {
                            // Instant t1 = Instant.now();
                            String request = UUID.randomUUID().toString();
                            URLConnection connection = new URL("http://localhost:8081/proxy/").openConnection();
                            connection.setDoOutput(true);
                            connection.setRequestProperty("Content-Type", "text/plain");
                            try (OutputStream out = connection.getOutputStream()) {
                                out.write(request.getBytes(StandardCharsets.UTF_8));
                            }
                            pending.incrementAndGet();
                            // String response;
                            try (
                                    InputStream in = connection.getInputStream();
                                    Reader input = new InputStreamReader(in, StandardCharsets.UTF_8);
                                    BufferedReader buf = new BufferedReader(input)
                            ) {
                                // response = buf.lines().collect(Collectors.joining("\n"));
                                buf.lines().forEach(s -> {});
                            }
                            /* int p = */ pending.decrementAndGet();
                            total.incrementAndGet();
                            // Instant t2 = Instant.now();
                            // Duration d = Duration.between(t1, t2);
                            // System.out.printf("%3d [%s] %s -> %s (%d ms) %d%n", threadId, dateTimeFormat.format(Instant.now()), request, response, d.toMillis(), p);
                            Thread.sleep(2000L);
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        System.out.printf("%d -> exited%n", threadId);
                        cnt.countDown();
                        live.decrementAndGet();
                        exited.incrementAndGet();
                    }
                });
            }
            cnt.await();
            pool.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
