/**
 * Michelin CERT 2020.
 */

package com.michelin.cert.redscan;

import com.michelin.cert.redscan.utils.queueing.RabbitMqBaseConfig;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure Rabbit MQ messages.
 *
 * @author FX13982 - Maxime ESCOURBIAC
 */
@Configuration
public class RabbitMqConfig extends RabbitMqBaseConfig {

  /**
   * QUEUE_MASTERDOMAINS.
   */
  public static final String QUEUE_MASTERDOMAINS = "com.michelin.cert.deepsubjack.masterdomains";

  /**
   * Queue configuration method.
   *
   * @return Declarables.
   */
  @Bean
  public Declarables fanoutBindings() {
    Queue queue = new Queue(QUEUE_MASTERDOMAINS, false);
    FanoutExchange fanoutExchange = new FanoutExchange(FANOUT_MASTERDOMAINS_EXCHANGE_NAME, false, false);
    return new Declarables(queue, fanoutExchange, BindingBuilder.bind(queue).to(fanoutExchange));
  }
}
