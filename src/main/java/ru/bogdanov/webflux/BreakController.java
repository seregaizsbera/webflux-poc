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

    @PostMapping(path = "/break")
    public Mono<String> theBreak(@RequestBody String input) {
        long delay = makeUpDelay();
        return Mono.just(makeResponse(input, delay))
                .delayElement(Duration.ofMillis(delay));
    }

    @PostMapping(path = "/block")
    public Mono<String> theBlock(@RequestBody String input) {
        long delay = makeUpDelay();
        return Mono.just(makeResponse(input, delay))
                .map(s -> sleep(delay, s));
    }

    private static <T> T sleep(long delay, T value) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }

    private static String makeResponse(String input, long delay) {
        return String.format("%s -> %.3fs", input, delay / 1000.);
    }

    private static long makeUpDelay() {
        return ThreadLocalRandom.current().nextLong(2000L, 4000L);
    }
}
