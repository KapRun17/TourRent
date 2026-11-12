package ru.tourrent.gateway;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class GatewayConfiguration {
    @Bean RouteLocator routes(RouteLocatorBuilder b){return b.routes()
        .route("catalog",r->r.path("/api/equipment/**").uri("lb://catalog-service"))
        .route("logistics",r->r.path("/api/deliveries/**").uri("lb://logistics-service"))
        .route("rentals",r->r.path("/api/rentals/**").uri("lb://rental-service")).build();}
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http){return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(a->a.pathMatchers("/","/index.html","/app.js","/style.css","/favicon.svg","/ui-config","/actuator/health/**").permitAll().pathMatchers("/api/**").authenticated().anyExchange().denyAll())
        .oauth2ResourceServer(o->o.jwt(j->{})).build();}
    @Bean ReactiveJwtDecoder jwtDecoder(@Value("${tourrent.auth-internal:http://keycloak:8080}") String base,@Value("${tourrent.auth-public:http://localhost:8180}") String issuer){
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(base+"/realms/tourrent/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer+"/realms/tourrent"));return decoder;
    }
    @Bean @LoadBalanced WebClient.Builder webClientBuilder(){return WebClient.builder();}
}
