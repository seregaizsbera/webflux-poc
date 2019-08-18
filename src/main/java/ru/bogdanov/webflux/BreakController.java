package ru.bogdanov.webflux;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/")
public class BreakController {
    private static final long MIN_DELAY_MS = 2000L;
    private static final long MAX_DELAY_MS = 4000L;

    @PostMapping(path = "/break")
    public Mono<String> theBreak(@RequestBody String input) {
        long delay = makeUpDelayMs();
        return Mono.just(makeResponse(input, delay))
                .delayElement(Duration.ofMillis(delay));
    }

    @PostMapping(path = "/block")
    public Mono<String> theBlock(@RequestBody String input) {
        long delayMs = makeUpDelayMs();
        return Mono.just(makeResponse(input, delayMs))
                .map(s -> sleep(delayMs, s));
    }

    private static <T> T sleep(long delayMs, T value) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }

    private static String makeResponse(String input, long delayMs) {
        //noinspection MagicNumber
        return String.format("%s -> %.3fs", input, delayMs / 1000.);
    }

    private static long makeUpDelayMs() {
        return ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS);
    }
}
