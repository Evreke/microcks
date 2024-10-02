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

import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.headers.MQRFH2;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.compat.jms.internal.JMSC;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Set;

/**
 * IBM MQ implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class IBMMQProducerManager {
  
  /** Get a JBoss logging logger. */
  private final Logger logger = Logger.getLogger(getClass());

  MQQueue queue;
  
  @ConfigProperty(name = "ibmmq.server")
  String ibmmqServer;

  @ConfigProperty(name = "ibmmq.queue-name")
  String ibmmqQueueName;

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
      queue = createQueue();
    } catch (Exception e) {
      logger.errorf("Error creating MQ queue client %s", ibmmqServer);
      logger.errorf("Connection exception %s", e.getMessage());
    }
  }

  /**
   *
   * A new MQueue initialized with configuration properties for read and write
   * @return A new MQueue initialized with configuration properties.
   * @throws Exception in case of connection failure
   */
  protected MQQueue createQueue() throws Exception {
    MQEnvironment.hostname = ibmmqServer;
    MQEnvironment.port = ibmmqPort;
    MQEnvironment.channel = ibmmqChannel;
    MQEnvironment.userID = ibmmqUser;
    MQEnvironment.password = ibmmqPassword;
    MQQueueConnectionFactory connectionFactory = new MQQueueConnectionFactory();

    connectionFactory.setQueueManager(queueManagerName);
    connectionFactory.setTransportType(JMSC.MQJMS_TP_CLIENT_MQ_TCPIP);
    connectionFactory.setConnectionNameList(ibmmqServer);
    connectionFactory.createQueueConnection(ibmmqUser, ibmmqPassword);


    MQQueueManager queueManager = new MQQueueManager(queueManagerName);

    int openOptions = CMQC.MQOO_OUTPUT;

    logger.infof("Connecting to IBM MQ with user %s", ibmmqUser);
    return queueManager.accessQueue(ibmmqQueueName, openOptions);
  }
  
  public void sendMessage(String queueName, String message, MQRFH2 headers) {
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
    return ibmmqQueueName;
  }

  public MQRFH2 renderEventMessageHeaders(TemplateEngine templateEngine, Set<Header> headers) {
    if (headers != null && !headers.isEmpty()) {
      var ibmmqHeaders = new MQRFH2();
      var defaultFolder = "usr";

      for (io.github.microcks.domain.Header header : headers) {

        header.getValues().stream().findFirst().ifPresent( headerValue ->
          {
            if (headerValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
              try {
                setHeader(templateEngine, header.getName(), ibmmqHeaders, defaultFolder, headerValue);
              } catch (Throwable t) {
                logger.error("Failing at evaluating template " + headerValue, t);
                trySettingHeader(header, ibmmqHeaders, defaultFolder, headerValue);
              }
            } else {
              trySettingHeader(header, ibmmqHeaders, defaultFolder, headerValue);
            }
          }
        );
      }

      return ibmmqHeaders;
    }
    return null;
  }

  private void setHeader(
    TemplateEngine templateEngine,
    String headerName,
    MQRFH2 ibmmqHeaders,
    String defaultFolder,
    String headerValue
  ) throws IOException {
    ibmmqHeaders.setFieldValue(defaultFolder, headerName, templateEngine.getValue(headerValue));
  }

  private void trySettingHeader(Header header, MQRFH2 ibmmqHeaders, String defaultFolder, String firstValue) {
    try {
      ibmmqHeaders.setFieldValue(defaultFolder, header.getName(), firstValue);
    } catch (IOException e) {
      logger.errorf("Failed setting header %s", header.getName());
      throw new RuntimeException("Failed setting header", e);
    }
  }


}
