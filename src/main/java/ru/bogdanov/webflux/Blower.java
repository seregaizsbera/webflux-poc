package ru.bogdanov.webflux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
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
            int poolSize = 25;
            int index = 0;
            if (args.length > index) {
                poolSize = Integer.valueOf(args[index++]);
            }
            String mode = "break";
            if (args.length > index) {
                mode = args[index++];
            }
            if (index > args.length) {
                System.err.printf("Extra parameters found.%n");
                System.exit(1);
                return;
            }
            launch(poolSize, mode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void launch(int poolSize, String mode) throws InterruptedException, IOException {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch cnt = new CountDownLatch(poolSize);
        Context context = new Context();
        Executors.newScheduledThreadPool(1, Blower::makeDaemon)
                .scheduleAtFixedRate(context::dump,0L, MONITORING_PERIOD_MS, TimeUnit.MILLISECONDS);
        URL url = new URL(String.format("http://localhost:8080/%s/", mode));
        for (int i = 0; i < poolSize; i++) {
            Thread.sleep(10L);
            final int threadId = i;
            pool.submit(() -> {
                context.workerStared();
                try {
                    while (true) {
                        try {
                            context.beforeRequest();
                            sendRequest(url);
                            context.success();
                        } finally {
                            context.afterRequest();
                        }
                        try {
                            Thread.sleep(2000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    System.err.printf("%s%n", e);
                } finally {
                    System.out.printf("%d -> exited%n", threadId);
                    cnt.countDown();
                    context.workerStopped();
                }
            });
        }
        cnt.await();
        pool.shutdown();
    }

    private static void sendRequest(URL url) throws IOException {
        String request = UUID.randomUUID().toString();
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(request.getBytes(StandardCharsets.UTF_8));
        }
        //noinspection UnnecessarySemicolon
        try (
            InputStream in = connection.getInputStream();
            Reader input = new InputStreamReader(in, StandardCharsets.UTF_8);
            BufferedReader buf = new BufferedReader(input);
        ) {
            buf.lines().forEach(s -> {});
        }
    }

    private static Thread makeDaemon(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Context {
        private final DateTimeFormatter dateTimeFormat = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .withZone(ZoneOffset.UTC);
        private final AtomicLong lastCompleted = new AtomicLong();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicLong completed = new AtomicLong();
        private final AtomicInteger live = new AtomicInteger();
        private final AtomicInteger exited = new AtomicInteger();
        private final Instant start = Instant.now();

        void beforeRequest() { pending.incrementAndGet(); }
        void afterRequest() { pending.decrementAndGet(); }
        void success() { completed.incrementAndGet(); }
        void workerStared() { live.incrementAndGet(); }
        void workerStopped() {
            live.decrementAndGet();
            exited.incrementAndGet();
        }

        void dump() {
            long currentCompleted = completed.get();
            double rps = (currentCompleted - lastCompleted.get()) * 1000 / (double) MONITORING_PERIOD_MS;
            double avg = currentCompleted * 1000 / (double) Duration.between(start, Instant.now()).toMillis();
            lastCompleted.set(currentCompleted);
            System.out.printf("[%s] pending: %3d, completed: %5d (live: %4d; exited: %3d) %6.2f rps (%6.2f rps)%n",
                    dateTimeFormat.format(Instant.now()),
                    pending.get(),
                    completed.get(),
                    live.get(),
                    exited.get(),
                    rps,
                    avg);
        }
    }
}
