package su.bogdanov.webflux;

import java.io.BufferedReader;
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
import java.util.stream.Collectors;

public class Blower {

    public static void main(String args[]) {
        try {
            final int poolSize = Integer.valueOf(args[0]);
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            DateTimeFormatter dateTimeFormat = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);
            CountDownLatch cnt = new CountDownLatch(poolSize);
            for (int i = 0; i < poolSize; i++) {
                final int threadId = i;
                pool.submit(() -> {
                    try {
                        while (true) {
                            Instant t1 = Instant.now();
                            String request = UUID.randomUUID().toString();
                            URLConnection connection = new URL("http://localhost:8081/proxy/").openConnection();
                            connection.setDoOutput(true);
                            connection.setRequestProperty("Content-Type", "text/plain");
                            try (OutputStream out = connection.getOutputStream()) {
                                out.write(request.getBytes(StandardCharsets.UTF_8));
                            }
                            String response;
                            try (
                                    InputStream in = connection.getInputStream();
                                    Reader input = new InputStreamReader(in, StandardCharsets.UTF_8);
                                    BufferedReader buf = new BufferedReader(input);
                            ) {
                                response = buf.lines().collect(Collectors.joining("\n"));
                            }
                            Instant t2 = Instant.now();
                            Duration d = Duration.between(t1, t2);
                            System.out.printf("%3d [%s] %s -> %s (%d ms)%n", threadId, dateTimeFormat.format(Instant.now()), request, response, d.toMillis());
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        System.out.printf("%d -> exited%n", threadId);
                        cnt.countDown();
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
