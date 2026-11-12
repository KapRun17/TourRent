package ru.tourrent.shared;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class BrokerConfiguration {
    public static final String EXCHANGE="tourrent.events";
    @Bean Declarables topology() {
        var ex=new TopicExchange(EXCHANGE,true,false);
        var dead=new TopicExchange("tourrent.dead",true,false);
        var failures=new Queue("tourrent.failures",true);
        var logistics=QueueBuilder.durable("logistics.rentals").deadLetterExchange("tourrent.dead").build();
        var rentals=QueueBuilder.durable("rentals.logistics").deadLetterExchange("tourrent.dead").build();
        var releases=QueueBuilder.durable("catalog.releases").deadLetterExchange("tourrent.dead").build();
        return new Declarables(ex,dead,failures,logistics,rentals,releases,
            BindingBuilder.bind(failures).to(dead).with("#"),
            BindingBuilder.bind(logistics).to(ex).with("rental.confirmed"),
            BindingBuilder.bind(logistics).to(ex).with("rental.cancelled"),
            BindingBuilder.bind(logistics).to(ex).with("rental.return_requested"),
            BindingBuilder.bind(rentals).to(ex).with("logistics.#"),
            BindingBuilder.bind(releases).to(ex).with("rental.cancelled"),
            BindingBuilder.bind(releases).to(ex).with("rental.completed"));
    }
}
