package su.bogdanov.webflux;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

@RestController
@RequestMapping("/break")
public class BreakController {

    @PostMapping(path = "/")
    public Mono<String> theBreak(@RequestBody String input) {
        long delay = ThreadLocalRandom.current().nextLong(3000, 10000);
        return Mono.just(String.format("%s -> %d", input, delay))
                .delayElement(Duration.ofMillis(delay));
    }
}
