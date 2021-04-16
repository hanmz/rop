package com.tencent.tdmq.handlers.rocketmq.utils;

import com.google.common.base.Joiner;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;
import org.apache.pulsar.common.naming.TopicDomain;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.NamespaceUtil;

/**
 * RocketMQTopic maintains two topic name, one is the original topic name, the other is the full topic name used in
 * Pulsar.
 * We shouldn't use the original topic name directly in RoP source code. Instead, we should
 * 1. getOriginalName() when read a RocketMQ request from client or write a RocketMQ response to client.
 * 2. getFullName() when access Pulsar resources.
 */
public class RocketMQTopic {

    private static final char TENANT_NAMESPACE_SEP = '|';
    private static final char ROCKETMQ_NAMESPACE_TOPIC_SEP = NamespaceUtil.NAMESPACE_SEPARATOR;
    private static final TopicDomain domain = TopicDomain.persistent;
    private static String defaultTenant = "rocketmq";
    private static String defaultNamespace = "public";
    private static String metaTenant = "rocketmq";
    private static String metaNamespace = "__rocketmq";
    @Getter
    private TopicName pulsarTopicName;
    private String rocketmqTenant = Strings.EMPTY;
    private String rocketmqNs = Strings.EMPTY;

    //rocketmq topicname => namespace%originaltopic   namespace%DLQ%originaltopic  originaltopic %DLQ%originaltopic
    public RocketMQTopic(String defaultTenant, String defaultNamespace, String rmqTopicName) {
        String prefix = NamespaceUtil.getNamespaceFromResource(rmqTopicName);
        if (Strings.isNotBlank(prefix)) {
            if (prefix.indexOf(TENANT_NAMESPACE_SEP) > 0) {
                this.rocketmqTenant = prefix.substring(0, prefix.indexOf(TENANT_NAMESPACE_SEP));
                this.rocketmqNs = prefix.substring(prefix.indexOf(TENANT_NAMESPACE_SEP) + 1);
            } else {
                this.rocketmqNs = prefix;
            }
        }
        String realTenant = Strings.isNotBlank(this.rocketmqTenant) ? this.rocketmqTenant : defaultTenant;
        String realNs = Strings.isNotBlank(this.rocketmqNs) ? this.rocketmqNs : defaultNamespace;
        this.pulsarTopicName = TopicName
                .get(domain.name(), realTenant, realNs, NamespaceUtil.withoutNamespace(rmqTopicName));
    }

    public RocketMQTopic(String rmqTopicName) {
        this(defaultTenant, defaultNamespace, rmqTopicName);
    }

    public final static void init(String metaTenant, String metaNamespace, String defaultTenant,
            String defaultNamespace) {
        RocketMQTopic.defaultTenant = defaultTenant;
        RocketMQTopic.defaultNamespace = defaultNamespace;
        RocketMQTopic.metaTenant = metaTenant;
        RocketMQTopic.metaNamespace = metaNamespace;
    }

    public final static String getPulsarOrigNoDomainTopic(String rmqTopic) {
        return new RocketMQTopic(rmqTopic).getOrigNoDomainTopicName();
    }

    public final static String getPulsarMetaNoDomainTopic(String rmqTopic) {
        return new RocketMQTopic(rmqTopic).getMetaNoDomainTopic();
    }

    public final static String getPulsarDefaultNoDomainTopic(String rmqTopic) {
        return new RocketMQTopic(rmqTopic).getDefaultNoDomainTopic();
    }

    public String getRocketDLQTopic() {
        if (Strings.isBlank(rocketmqTenant) && Strings.isBlank(rocketmqNs)) {
            return MixAll.DLQ_GROUP_TOPIC_PREFIX + pulsarTopicName.getLocalName();
        } else if (Strings.isBlank(rocketmqTenant) && Strings.isNotBlank(rocketmqNs)) {
            return MixAll.DLQ_GROUP_TOPIC_PREFIX + rocketmqNs + ROCKETMQ_NAMESPACE_TOPIC_SEP + pulsarTopicName
                    .getLocalName();
        } else {
            return MixAll.DLQ_GROUP_TOPIC_PREFIX + ROCKETMQ_NAMESPACE_TOPIC_SEP + pulsarTopicName.getLocalName();
        }
    }

    public String getMetaNoDomainTopic() {
        return Joiner.on('/').join(metaTenant, metaNamespace, pulsarTopicName.getLocalName());
    }

    public String getDefaultNoDomainTopic() {
        return Joiner.on('/').join(defaultTenant, defaultNamespace, pulsarTopicName.getLocalName());
    }

    public String getOrigNoDomainTopicName() {
        return Joiner.on('/').join(pulsarTopicName.getTenant(), pulsarTopicName.getNamespacePortion(),
                pulsarTopicName.getLocalName());
    }

    public String getPulsarFullName() {
        return this.pulsarTopicName.toString();
    }

    public String getPartitionName(int partition) {
        if (partition < 0) {
            throw new IllegalArgumentException("Invalid partition " + partition + ", it should be non-negative number");
        }
        return this.pulsarTopicName.getPartition(partition).toString();
    }

    public TopicName getPartitionTopicName(int partition) {
        if (partition < 0) {
            throw new IllegalArgumentException("Invalid partition " + partition + ", it should be non-negative number");
        }
        return this.pulsarTopicName.getPartition(partition);
    }

}

