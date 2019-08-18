package ru.bogdanov.webflux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class Blower {
    private static final long MONITORING_PERIOD_MS = 5000L;
    private static final long THREAD_STARTUP_DELAY_MS = 10L;
    private static final long DELAY_BETWEEN_REQUESTS_MS = 2000L;
    private static final int DEFAULT_THREAD_POOL_SIZE = 25;

    private Blower() {}

    public static void main(String args[]) {
        try {
            int poolSize = DEFAULT_THREAD_POOL_SIZE;
            int index = 0;
            if (args.length > index) {
                poolSize = Integer.parseInt(args[index++]);
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
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private static void launch(int poolSize, String mode) throws InterruptedException, IOException {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        Context context = new Context(poolSize);
        Executors.newScheduledThreadPool(1, Blower::makeDaemon)
                .scheduleAtFixedRate(context::dump, 0L, MONITORING_PERIOD_MS, TimeUnit.MILLISECONDS);
        URL url = new URL(String.format("http://localhost:8080/%s/", mode));
        for (int i = 0; i < poolSize; i++) {
            Thread.sleep(THREAD_STARTUP_DELAY_MS);
            pool.submit(new Worker(context, i, url));
        }
        context.waitForCompletion();
        pool.shutdown();
    }

    private static Thread makeDaemon(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Worker implements Runnable {
        private final Context context;
        private final int threadId;
        private final URL url;

        Worker(Context context, int threadId, URL url) {
            this.context = context;
            this.threadId = threadId;
            this.url = url;
        }

        @Override
        public void run() {
            context.workerStared();
            try {
                while (true) {
                    try {
                        context.beforeRequest();
                        sendRequest();
                        context.success();
                    } finally {
                        context.afterRequest();
                    }
                    try {
                        Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.err.printf("%s%n", e);
            } finally {
                System.out.printf("%d -> exited%n", threadId);
                context.workerStopped();
            }
        }

        private void sendRequest() throws IOException {
            String request = UUID.randomUUID().toString();
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(request.getBytes(StandardCharsets.UTF_8));
            }
            try (BufferedReader buf = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                buf.lines().forEach(s -> {});
            }
        }
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
        private final CountDownLatch cnt;

        Context(int poolSize) {
            this.cnt = new CountDownLatch((poolSize));
        }

        void beforeRequest() { pending.incrementAndGet(); }
        void afterRequest() { pending.decrementAndGet(); }
        void success() { completed.incrementAndGet(); }
        void workerStared() { live.incrementAndGet(); }

        void workerStopped() {
            live.decrementAndGet();
            exited.incrementAndGet();
            cnt.countDown();
        }

        void waitForCompletion() throws InterruptedException {
            cnt.await();
        }

        void dump() {
            long currentCompleted = completed.get();
            double rps = (currentCompleted - lastCompleted.get()) * 1000L / (double) MONITORING_PERIOD_MS;
            double avg = currentCompleted * 1000L / (double) Duration.between(start, Instant.now()).toMillis();
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
