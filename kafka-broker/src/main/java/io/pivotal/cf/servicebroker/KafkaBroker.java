/**
 Copyright (C) 2016-Present Pivotal Software, Inc. All rights reserved.

 This program and the accompanying materials are made available under
 the terms of the under the Apache License, Version 2.0 (the "License”);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package io.pivotal.cf.servicebroker;

import io.pivotal.ecosystem.servicebroker.model.LastOperation;
import io.pivotal.ecosystem.servicebroker.model.ServiceBinding;
import io.pivotal.ecosystem.servicebroker.model.ServiceInstance;
import io.pivotal.ecosystem.servicebroker.service.DefaultServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Example service broker. Can be used as a template for creating custom service brokers
 * by adding your code in the appropriate methods. For more information on the CF service broker
 * lifecycle and API, please see See <a href="https://docs.cloudfoundry.org/services/api.html">here.</a>
 * <p>
 * This class extends DefaultServiceImpl, which has no-op implementations of the methods. This means
 * that if, for instance, your broker does not support binding you can just delete the binding methods below
 * (in other words, you do not need to implement your own no-op implementations).
 */
@Service
@Slf4j
class KafkaBroker extends DefaultServiceImpl {

    public static final String TOPIC_NAME_KEY = "topicName";

    public KafkaBroker(Environment env, KafkaClient client) {
        super();
        this.env = env;
        this.client = client;
    }

    private Environment env;

    private KafkaClient client;

    /**
     * Add code here and it will be run during the create-service process. This might include
     * calling back to your underlying service to create users, schemas, fire up environments, etc.
     *
     * @param instance service instance data passed in by the cloud connector. Clients can pass additional json
     *                 as part of the create-service request, which will show up as key value pairs in instance.parameters.
     */
    @Override
    public LastOperation createInstance(ServiceInstance instance) {
        try {
            Object name = instance.getParameters().get(TOPIC_NAME_KEY);
            if (name == null) {
                name = instance.getId();
                instance.getParameters().put(TOPIC_NAME_KEY, name);
            }

            log.info("creating topic: " + name.toString());
            client.createTopic(name.toString());
            return new LastOperation(LastOperation.CREATE, LastOperation.SUCCEEDED, "created.");
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            return new LastOperation(LastOperation.CREATE, LastOperation.FAILED, t.getMessage());
        }
    }

    /**
     * Code here will be called during the delete-service instance process. You can use this to de-allocate resources
     * on your underlying service, delete user accounts, destroy environments, etc.
     *
     * @param instance service instance data passed in by the cloud connector.
     */
    @Override
    public LastOperation deleteInstance(ServiceInstance instance) {
      try {
          log.info("de-provisioning service instance which is a kafka topic: " + instance.getId());

          //call out to kafka to delete the topic
          String topic = instance.getParameters().get(TOPIC_NAME_KEY).toString();
          client.deleteTopic(topic);
          String msg = "kafka-broker: " + topic + " deleted.";
          log.info(msg);
          return new LastOperation(LastOperation.DELETE, LastOperation.SUCCEEDED, "deleted.");
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            return new LastOperation(LastOperation.DELETE, LastOperation.FAILED, t.getMessage());
        }
    }

    /**
     * Code here will be called during the update-service process. You can use this to modify
     * your service instance.
     *
     * @param instance service instance data passed in by the cloud connector.
     */
    @Override
    public LastOperation updateInstance(ServiceInstance instance) {
        log.info("updating broker user: " + instance.getId());
        return new LastOperation(LastOperation.UPDATE, LastOperation.SUCCEEDED, "updated.");
    }

    /**
     * Called during the bind-service process. This is a good time to set up anything on your underlying service specifically
     * needed by an application, such as user accounts, rights and permissions, application-specific environments and connections, etc.
     * <p>
     * Services that do not support binding should set '"bindable": false,' within their catalog.json file. In this case this method
     * can be safely deleted in your implementation.
     *
     * @param instance service instance data passed in by the cloud connector.
     * @param binding  binding data passed in by the cloud connector. Clients can pass additional json
     *                 as part of the bind-service request, which will show up as key value pairs in binding.parameters. Brokers
     *                 can, as part of this method, store any information needed for credentials and unbinding operations as key/value
     *                 pairs in binding.properties
     */
    @Override
    public LastOperation createBinding(ServiceInstance instance, ServiceBinding binding) {
        // use app guid to send bind request
        //don't need to talk to kafka, just return credentials.
        log.info("binding app: " + binding.getAppGuid() + " to topic: " + instance.getParameters().get(TOPIC_NAME_KEY));
        return new LastOperation(LastOperation.BIND, LastOperation.SUCCEEDED, "bound.");
    }

    /**
     * Called during the unbind-service process. This is a good time to destroy any resources, users, connections set up during the bind process.
     *
     * @param instance service instance data passed in by the cloud connector.
     * @param binding  binding data passed in by the cloud connector.
     */
    @Override
    public LastOperation deleteBinding(ServiceInstance instance, ServiceBinding binding) {
        log.info("unbinding app: " + binding.getAppGuid() + " from topic: " + instance.getParameters().get(TOPIC_NAME_KEY));
        return new LastOperation(LastOperation.UNBIND, LastOperation.SUCCEEDED, "unbound.");
    }

    /**
     * Bind credentials that will be returned as the result of a create-binding process. The format and values of these credentials will
     * depend on the nature of the underlying service. For more information and some examples, see
     * <a href=https://docs.cloudfoundry.org/services/binding-credentials.html>here.</a>
     * <p>
     * This method is called after the create-binding method: any information stored in binding.properties in the createBinding call
     * will be available here, along with any custom data passed in as json parameters as part of the create-binding process by the client.
     *
     * @param instance service instance data passed in by the cloud connector.
     * @param binding  binding data passed in by the cloud connector.
     * @return credentials, as a series of key/value pairs
     */
    @Override
    public Map<String, Object> getCredentials(ServiceInstance instance, ServiceBinding binding) {
        log.info("returning credentials.");

        try {
            Map<String, Object> m = new HashMap<>();
            m.put("hostname", client.getBootstrapServers());
            m.put(TOPIC_NAME_KEY, instance.getParameters().get(TOPIC_NAME_KEY));

            String uri = "kafka://" + m.get("hostname") + "/" + m.get(TOPIC_NAME_KEY);
            m.put("uri", uri);
            return m;
        } catch (Throwable t) {
            throw new KafkaBrokerException(t);
        }
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
