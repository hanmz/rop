/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.streamnative.pulsar.handlers.rocketmq.utils;

import static org.apache.pulsar.common.naming.TopicName.PARTITIONED_TOPIC_SUFFIX;

import com.google.common.base.Joiner;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicDomain;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Topic name utils.
 */
public class TopicNameUtils {

    public static TopicName pulsarTopicName(MessageQueue topicPartition, NamespaceName namespace) {
        return pulsarTopicName(topicPartition.getTopic(), topicPartition.getQueueId(), namespace);
    }

    public static TopicName pulsarTopicName(MessageQueue topicPartition) {
        return pulsarTopicName(topicPartition.getTopic(), topicPartition.getQueueId());
    }

    private static TopicName pulsarTopicName(String topic, int partitionIndex) {
        return TopicName.get(topic + PARTITIONED_TOPIC_SUFFIX + partitionIndex);
    }

    public static TopicName pulsarTopicName(String topic, NamespaceName namespace) {
        return TopicName.get(TopicDomain.persistent.value(), namespace, topic);
    }

    public static TopicName pulsarTopicName(String topic) {
        return TopicName.get(topic);
    }

    public static TopicName pulsarTopicName(String topic, int partitionIndex, NamespaceName namespace) {
        if (topic.startsWith(TopicDomain.persistent.value())) {
            topic = topic.replace(TopicDomain.persistent.value() + "://", "");
        }

        if (topic.contains(namespace.getNamespaceObject().toString())) {
            topic = topic.replace(namespace.getNamespaceObject().toString() + "/", "");
        }
        return TopicName.get(TopicDomain.persistent.value(),
                namespace,
                topic + PARTITIONED_TOPIC_SUFFIX + partitionIndex);
    }

    public static String getTopicNameWithoutPartitions(TopicName topicName) {
        String localName = topicName.getPartitionedTopicName();
        if (localName.contains(PARTITIONED_TOPIC_SUFFIX)) {
            return localName.substring(0, localName.lastIndexOf(PARTITIONED_TOPIC_SUFFIX));
        } else {
            return localName;
        }
    }

    // get local name without partition part
    public static String getTopicNameFromPulsarTopicName(TopicName topicName) {
        // remove partition part
        String localName = topicName.getPartitionedTopicName();
        // remove persistent://tenant/ns
        return TopicName.get(localName).getLocalName();
    }

    public static String getNoDomainTopicName(TopicName topicName) {
        return Joiner.on("/").join(topicName.getTenant(), topicName.getNamespacePortion(), topicName.getLocalName());
    }

}
