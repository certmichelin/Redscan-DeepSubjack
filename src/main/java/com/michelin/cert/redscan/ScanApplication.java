/*
 * Copyright 2021 Michelin CERT (https://cert.michelin.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.michelin.cert.redscan;

import com.michelin.cert.redscan.utils.datalake.DatalakeStorageException;
import com.michelin.cert.redscan.utils.system.OsCommandExecutor;
import com.michelin.cert.redscan.utils.system.StreamGobbler;

import org.apache.logging.log4j.LogManager;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RedScan scanner main class.
 *
 * @author Maxime ESCOURBIAC
 */
@SpringBootApplication
public class ScanApplication {

  private final RabbitTemplate rabbitTemplate;

  @Autowired
  private DatalakeConfig datalakeConfig;

  /**
   * Constructor to init rabbit template. Only required if pushing data to queues
   *
   * @param rabbitTemplate Rabbit template.
   */
  public ScanApplication(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * RedScan Main methods.
   *
   * @param args Application arguments.
   */
  public static void main(String[] args) {
    SpringApplication.run(ScanApplication.class, args);
  }

  /**
   * Message executor.
   *
   * @param message Message received.
   */
  @RabbitListener(queues = {RabbitMqConfig.QUEUE_MASTERDOMAINS})
  public void receiveMessage(String message) {
    LogManager.getLogger(ScanApplication.class).info(String.format("Start deepsubjack : %s", message));
    OsCommandExecutor osCommandExecutor = new OsCommandExecutor();
    try {

      //Execute subfinder.
      LogManager.getLogger(ScanApplication.class).info(String.format("Start subfinder : %s", message));
      StreamGobbler subfinderStreamGobbler = osCommandExecutor.execute(String.format("subfinder -silent -d %s", message));
      if (subfinderStreamGobbler != null) {
        LogManager.getLogger(ScanApplication.class).info(String.format("Subfinder terminated with status : %d", subfinderStreamGobbler.getExitStatus()));

        //Convert the stream output to Host List.
        for (Object subdomain : subfinderStreamGobbler.getStandardOutputs()) {
          LogManager.getLogger(ScanApplication.class).info(String.format("Start subjack : %s", subdomain.toString()));
          
          StreamGobbler subjackStreamGobbler = osCommandExecutor.execute(String.format("/root/go/bin/subjack -c /root/go/src/github.com/certmichelin/subjack/fingerprints.json -a -m -d %s ", subdomain.toString()));

          if (subjackStreamGobbler.getStandardOutputs() != null) {
            if (subjackStreamGobbler.getStandardOutputs().length != 0) {
              for (Object subjackSubdomain : subjackStreamGobbler.getStandardOutputs()) {
                String result = ((String) subjackSubdomain).replaceAll("\u001B\\[[;\\d]*m", "");
                LogManager.getLogger(ScanApplication.class).info(String.format("Subjack output : %s", result));
                if (result.startsWith("[")) { //Remove potential error.
                  datalakeConfig.createDomain(subdomain.toString(), message);
                  rabbitTemplate.convertAndSend(RabbitMqConfig.FANOUT_DOMAINS_EXCHANGE_NAME, "", subdomain.toString());
                }
              }
            }
          }
        }
      }
    } catch (DatalakeStorageException ex) {
      LogManager.getLogger(ScanApplication.class).error(String.format("Datalake Storage Exception : %s", ex.getMessage()));
    } catch (Exception ex) {
      LogManager.getLogger(ScanApplication.class).error(String.format("Exception : %s", ex.getMessage()));
    }
  }

}
