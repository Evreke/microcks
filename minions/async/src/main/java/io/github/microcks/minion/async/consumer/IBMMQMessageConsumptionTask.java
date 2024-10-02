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
package io.github.microcks.minion.async.consumer;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import io.github.microcks.minion.async.AsyncTestSpecification;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IBMMQMessageConsumptionTask implements MessageConsumptionTask {

   /**
    * Get a JBoss logging logger.
    */
   private final Logger logger = Logger.getLogger(getClass());

   /**
    * The string for Regular Expression that helps validating acceptable endpoints.
    */
   public static final String ENDPOINT_PATTERN_STRING = "ibmmq://(?<host>[^:]):(?<port>\\d+)/(?<queueManager>.+)?/(\\?(?<mqChannelName>.+))?/(?<destination>.+)";
   /**
    * The Pattern for matching groups within the endpoint regular expression.
    */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   private final AsyncTestSpecification specification;

   private MQQueueManager queueManager;

   private String queueName;

   public IBMMQMessageConsumptionTask(AsyncTestSpecification testSpecification) {
      this.specification = testSpecification;
   }

   @Override
   public void close() throws IOException {
      throw new RuntimeException("TO BE DONE");
   }

   /**
    * Convenient static method for checking if this implementation will accept endpoint.
    *
    * @param endpointUrl The endpoint URL to validate
    * @return True if endpointUrl can be used for connecting and consuming on endpoint
    */
   public static boolean acceptEndpoint(String endpointUrl) {
      return endpointUrl != null && endpointUrl.matches(ENDPOINT_PATTERN_STRING);
   }

   @Override
   public List<ConsumedMessage> call() throws Exception {
      if (queueManager == null) {
         initQueueManager();
      }

      MQMessage message = new MQMessage();
      MQGetMessageOptions options = new MQGetMessageOptions();
      MQQueue queue = null;
      List<ConsumedMessage> consumedMessages = new ArrayList<>();

      try {
         queue = queueManager.accessQueue(queueName, CMQC.MQOO_INPUT_AS_Q_DEF);

         if (queue != null) {
            queue.get(message, options);

            ConsumedMessage consumedMessage = new ConsumedMessage();
            consumedMessage.setReceivedAt(System.currentTimeMillis());
            consumedMessage.setHeaders(new HashSet<>());
            consumedMessage.setPayload(message.readStringOfByteLength(message.getMessageLength()).getBytes());

            consumedMessages.add(consumedMessage);
         }

         logger.infof("Message received: %s", message.readStringOfByteLength(message.getMessageLength()));
      } catch (MQException e) {
         logger.errorf("Error receiving message: %s", e.getMessage());
      } finally {
         if (queue != null) {
            queue.close();
         }
      }
      return consumedMessages;
   }

   private void initQueueManager() {
      try {
         AsyncAPISchemaValidator.getJsonNodeForSchema(specification.getAsyncAPISpec());
      } catch (IOException e) {
         logger.errorf("Retrieval of AsyncAPI schema for validation fails for {%s}", specification.getTestResultId());
      }

      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      matcher.find();

      queueName = matcher.group("destination");

      Hashtable<String, Object> properties = new Hashtable<>();
      properties.put("QMGR", matcher.group("queueManager")); // Имя менеджера очередей
      properties.put("HOST_NAME", matcher.group("host")); // Хост
      properties.put("PORT", Integer.parseInt(matcher.group("port"))); // Порт
      properties.put("CHANNEL", matcher.group("mqChannelName")); // Канал
      properties.put("CONNECTION_MODE", "1"); // 1 - клиентский режим
      properties.put("USERID", specification.getSecret().getUsername()); // Имя пользователя
      properties.put("PASSWORD", specification.getSecret().getPassword()); // Пароль

      try {
         queueManager = new MQQueueManager(matcher.group("queueManager"), properties);
      } catch (MQException e) {
         logger.errorf("Error creating MQ queue manager: %s", e.getMessage());
      }

   }

}
