package ru.tourrent.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class RequestIdentityFilterTest {
    @Test void authenticatedIdentityReplacesClientHeader(){
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/rentals").header("X-User-ID","victim").header("X-Request-ID","trace-123"));
        var authenticated=exchange.mutate().principal(Mono.just(new UsernamePasswordAuthenticationToken("actual-user","unused"))).build();
        new RequestIdentityFilter().filter(authenticated,e->{assertEquals("actual-user",e.getRequest().getHeaders().getFirst("X-User-ID"));assertEquals("trace-123",e.getRequest().getHeaders().getFirst("X-Request-ID"));return Mono.empty();}).block();
    }
    @Test void unsuitableRequestIdentifierIsReplaced(){
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/rentals").header("X-Request-ID","unsafe identifier with spaces"));
        new RequestIdentityFilter().filter(exchange,e->{String id=e.getRequest().getHeaders().getFirst("X-Request-ID");assertNotNull(id);assertTrue(id.matches("[a-f0-9-]{36}"));return Mono.empty();}).block();
    }
}
