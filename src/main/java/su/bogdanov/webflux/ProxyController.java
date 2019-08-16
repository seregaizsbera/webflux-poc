package su.bogdanov.webflux;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    ProxyController() {
        this.client = WebClient.create();
    }

    @PostMapping(path = "/")
    public Mono<String> proxy(@RequestBody String input) {
        return client.post()
                .uri("http://localhost:8080/break/")
                .body(BodyInserters.fromObject(input))
                .exchange()
                .flatMap(response -> response.bodyToMono(String.class));
    }
}
