/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.minion.async.producer;

import com.ibm.mq.MQException;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.headers.MQMD;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Set;

/**
 * IBM MQ implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class IBMMQProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private MQQueueManager mqQueueManager;

   @ConfigProperty(name = "ibmmq.server")
   String ibmmqServer;

   @ConfigProperty(name = "ibmmq.username", defaultValue = "microcks-async-minion")
   String ibmmqUser;

   @ConfigProperty(name = "ibmmq.channel")
   String ibmmqChannel;

   @ConfigProperty(name = "ibmmq.password")
   String ibmmqPassword;

   @ConfigProperty(name = "ibmmq.port")
   int ibmmqPort;

   @ConfigProperty(name = "ibmmq.connection-manager-name")
   String queueManagerName;


   @PostConstruct
   protected void create() throws Exception {
      try {
         mqQueueManager = createQueueManager();
      } catch (Exception e) {
         logger.errorf("Error creating MQ queue client %s", ibmmqServer);
         logger.errorf("Connection exception %s", e.getMessage());
      }
   }

   /**
    *
    * A new MQQueueManager initialized for further connections creation
    * @return A new MQQueueManager.
    * @throws Exception in case of connection failure
    */
   protected MQQueueManager createQueueManager() {
      Hashtable<String, Object> properties = new Hashtable<>();
      properties.put("QMGR", queueManagerName);
      properties.put("HOST_NAME", ibmmqServer);
      properties.put("PORT", ibmmqPort);
      properties.put("CHANNEL", ibmmqChannel);
      properties.put("CONNECTION_MODE", "1");
      properties.put("USERID", ibmmqUser);
      properties.put("PASSWORD", ibmmqPassword);

      logger.info("Creating MQ queue manager");
      try {
         return new MQQueueManager(queueManagerName, properties);
      } catch (MQException e) {
         logger.errorf("Unable to initialize  %s", ibmmqServer);
         throw new RuntimeException(e);
      }
   }

   public void sendMessage(String queueName, String message, MQMD headers) {
      MQQueue queue;

      try {
         queue = mqQueueManager.accessQueue(queueName, CMQC.MQOO_OUTPUT);
         if (queue == null) {
            throw new RuntimeException("Queue " + queueName + " not found");
         }

      } catch (MQException | RuntimeException e) {
         logger.errorf(e, "Error accessing MQ queue %s", queueName);
         throw new RuntimeException(e);
      }

      MQMessage mqMessage = new com.ibm.mq.MQMessage();

      try {
         mqMessage.write(message.getBytes());
         queue.put(mqMessage, new com.ibm.mq.MQPutMessageOptions());
      } catch (IOException ioe) {
         logger.warnf("Error creating MQ message, ignoring it", ioe.getMessage());
      } catch (MQException mqe) {
         logger.warnf("Error sending MQ message, ignoring it", mqe.getMessage());
      }
   }

   public String getQueueName(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);
      operationName = operationName.replace('/', '-');

      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName;
   }

   public MQMD renderEventMessageHeaders(TemplateEngine templateEngine, Set<Header> headers) {
      MQMD mqmd = new MQMD();
      return mqmd;
   }
}
