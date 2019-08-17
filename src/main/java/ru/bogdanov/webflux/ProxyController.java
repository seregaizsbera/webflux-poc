package ru.bogdanov.webflux;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/proxy")
public class ProxyController {
    private WebClient client;

    public ProxyController() {
        this.client = WebClient.create();
    }

    @PostMapping(path = "/break")
    public Mono<String> proxyBreak(@RequestBody String input) {
        return doBusiness(input, "break");
    }

    @PostMapping(path = "/block")
    public Mono<String> proxyBlock(@RequestBody String input) {
        return doBusiness(input, "block");
    }

    private Mono<String> doBusiness(String input, String mode) {
        return client.post()
                .uri(String.format("http://localhost:8080/%s/", mode))
                .body(BodyInserters.fromObject(input))
                .exchange()
                .flatMap(response -> response.bodyToMono(String.class));
    }
}
