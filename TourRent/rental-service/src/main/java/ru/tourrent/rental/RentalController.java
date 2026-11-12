package ru.tourrent.rental;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
@RestController
public class RentalController {
    private final RentalService service;
    public RentalController(RentalService service){this.service=service;}
    @GetMapping("/api/rentals")
    public Mono<List<Map<String,Object>>> list(@RequestHeader("X-User-ID") String owner){return Mono.fromCallable(()->service.list(owner)).subscribeOn(Schedulers.boundedElastic());}
    @GetMapping("/api/rentals/{id}")
    public Mono<Map<String,Object>> one(@PathVariable UUID id,@RequestHeader("X-User-ID") String owner){return Mono.fromCallable(()->service.one(id,owner)).subscribeOn(Schedulers.boundedElastic());}
    @PostMapping("/api/rentals") @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String,Object>> create(@Valid @RequestBody RentalService.Create body,@RequestHeader("X-User-ID") String owner,@RequestHeader("X-Request-ID") String rid){return Mono.fromCallable(()->service.create(body,owner,rid)).subscribeOn(Schedulers.boundedElastic());}
    @PostMapping("/api/rentals/{id}/{action:cancel|return}")
    public Mono<Map<String,Object>> action(@PathVariable UUID id,@PathVariable String action,@RequestHeader("X-User-ID") String owner,@RequestHeader("X-Request-ID") String rid){return Mono.fromCallable(()->service.action(id,action,owner,rid)).subscribeOn(Schedulers.boundedElastic());}
    @GetMapping(value="/api/rentals/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String,Object>>> events(@PathVariable UUID id,@RequestHeader("X-User-ID") String owner,@RequestHeader(value="Last-Event-ID",defaultValue="0") long after){
        return Mono.fromCallable(()->service.one(id,owner)).subscribeOn(Schedulers.boundedElastic()).flatMapMany(ignored->Flux.defer(()->{
            var cursor=new AtomicLong(Math.max(0,after));
            return Flux.interval(Duration.ZERO,Duration.ofSeconds(1)).onBackpressureLatest()
                .concatMap(t->Mono.fromCallable(()->service.history(id,cursor.get())).subscribeOn(Schedulers.boundedElastic()))
                .flatMapIterable(rows->rows)
                .doOnNext(row->cursor.set(((Number)row.get("sequence")).longValue()))
                .map(row->ServerSentEvent.builder(row).id(String.valueOf(row.get("sequence"))).event((String)row.get("status")).build())
                .takeUntil(e->Set.of("COMPLETED","CANCELLED").contains(e.event()));
        }));
    }
    @GetMapping("/internal/diagnostics")
    public Mono<Map<String,Object>> diagnostics(){return Mono.fromCallable(service::diagnostics).subscribeOn(Schedulers.boundedElastic());}
}
