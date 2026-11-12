package ru.tourrent.gateway;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;
import java.util.UUID;
import org.slf4j.LoggerFactory;
@Component
public class RequestIdentityFilter implements WebFilter,Ordered {
    @Override public int getOrder(){return -50;}
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String incoming=exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        String rid=incoming!=null&&incoming.matches("[A-Za-z0-9._-]{1,64}")?incoming:UUID.randomUUID().toString();
        return exchange.getPrincipal().map(p->p.getName()).defaultIfEmpty("anonymous").flatMap(owner->{
            var request=exchange.getRequest().mutate().headers(h->{h.set("X-Request-ID",rid);h.remove("X-User-ID");h.set("X-User-ID",owner);}).build();
            exchange.getResponse().getHeaders().set("X-Request-ID",rid);
            return chain.filter(exchange.mutate().request(request).build()).doFinally(signal->{
                if(request.getPath().value().startsWith("/api/"))LoggerFactory.getLogger(getClass()).info("requestId={} {} {} status={}",rid,request.getMethod(),request.getPath(),exchange.getResponse().getStatusCode());
            });
        });
    }
}
