/**
 * Michelin CERT 2020.
 */

package com.michelin.cert.redscan;

import com.michelin.cert.redscan.utils.datalake.DatalakeStorageException;
import com.michelin.cert.redscan.utils.models.Severity;
import com.michelin.cert.redscan.utils.models.Vulnerability;
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
 * @author FX13982 - Maxime ESCOURBIAC
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
  @RabbitListener(queues = {RabbitMqConfig.QUEUE_DOMAINS})
  public void receiveMessage(String message) {
    LogManager.getLogger(ScanApplication.class).info(String.format("Start subjack : %s", message));
    try {
      
      //Execute Subjack.
      OsCommandExecutor osCommandExecutor = new OsCommandExecutor();
      StreamGobbler streamGobbler = osCommandExecutor.execute(String.format("/root/go/bin/subjack -c /root/go/src/github.com/certmichelin/subjack/fingerprints.json -a -m -d %s ", message));

      if (streamGobbler != null) {
        LogManager.getLogger(ScanApplication.class).info(String.format("Subjack terminated with status : %d", streamGobbler.getExitStatus()));

        //Convert the stream output.
        if (streamGobbler.getStandardOutputs() != null) {
          if (streamGobbler.getStandardOutputs().length != 0) {
            for (Object object : streamGobbler.getStandardOutputs()) {
              String result = ((String) object).replaceAll("\u001B\\[[;\\d]*m", "");
              LogManager.getLogger(ScanApplication.class).info(String.format("Subjack output : %s", result));
              if (result.startsWith("[")) { //Remove potential error.
                String[] tmp = result.split(" ");
                StringBuilder subjackOutput = new StringBuilder();
                for (int i = 0; i < tmp.length - 1; i++) {
                  subjackOutput.append(tmp[i]).append(" ");
                }
                datalakeConfig.upsertDomainField(message, "subjack", subjackOutput);

                //Send the vulnerability.
                Vulnerability vulnerability = new Vulnerability(Vulnerability.generateId("redscan-subjack",message,"subjack"), 
                        Severity.HIGH, 
                        String.format("[%s] Subdomain potentially takeoverable", message), 
                        String.format("The domain %s is potentially takeoverable : %s", message, subjackOutput), 
                        message, 
                        "redscan-subjack");
                rabbitTemplate.convertAndSend(RabbitMqConfig.FANOUT_VULNERABILITIES_EXCHANGE_NAME, "", vulnerability.toJson());
              }
            }
          } else {
            datalakeConfig.upsertDomainField(message, "subjack", "None");
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
