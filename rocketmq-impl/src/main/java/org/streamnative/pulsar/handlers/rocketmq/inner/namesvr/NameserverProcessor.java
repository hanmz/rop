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

package org.streamnative.pulsar.handlers.rocketmq.inner.namesvr;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.rocketmq.common.constant.PermName.PERM_READ;
import static org.apache.rocketmq.common.constant.PermName.PERM_WRITE;
import static org.apache.rocketmq.common.protocol.RequestCode.GET_ROUTEINTO_BY_TOPIC;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.pulsar.broker.loadbalance.impl.ModularLoadManagerImpl;
import org.apache.pulsar.broker.loadbalance.impl.ModularLoadManagerWrapper;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.policies.data.loadbalancer.AdvertisedListener;
import org.apache.pulsar.policies.data.loadbalancer.LocalBrokerData;
import org.apache.rocketmq.common.help.FAQUrl;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.streamnative.pulsar.handlers.rocketmq.RocketMQProtocolHandler;
import org.streamnative.pulsar.handlers.rocketmq.RocketMQServiceConfiguration;
import org.streamnative.pulsar.handlers.rocketmq.inner.RocketMQBrokerController;
import org.streamnative.pulsar.handlers.rocketmq.utils.PulsarUtil;
import org.streamnative.pulsar.handlers.rocketmq.utils.RocketMQTopic;
import org.testng.collections.Sets;

/**
 * Nameserver processor.
 */
@Slf4j
public class NameserverProcessor implements NettyRequestProcessor {

    public static final Pattern BROKER_ADDER_PAT = Pattern.compile("([^/:]+:)(\\d+)");
    /**
     * Differentiate the source network type of client requests according to different ports.
     */
    private static final Map<String, String> PORT_LISTENER_NAME_MAP = Maps.newHashMap();
    private final RocketMQBrokerController brokerController;
    private final RocketMQServiceConfiguration config;
    private final MQTopicManager mqTopicManager;
    private final int servicePort;

    public NameserverProcessor(RocketMQBrokerController brokerController) {
        this.brokerController = brokerController;
        this.config = brokerController.getServerConfig();
        this.mqTopicManager = brokerController.getTopicConfigManager();
        this.servicePort = RocketMQProtocolHandler.getListenerPort(config.getRocketmqListeners());

        String rocketmqListenerPortMap = config.getRocketmqListenerPortMap();
        String[] parts = rocketmqListenerPortMap.split(",");
        for (String part : parts) {
            String[] arr = part.split(":");
            PORT_LISTENER_NAME_MAP.put(arr[0].trim(), arr[1].trim());
        }
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request)
            throws Exception {
        if (ctx != null) {
            log.debug("receive request, {} {} {}",
                    request.getCode(),
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()),
                    request);
        }

        switch (request.getCode()) {
            case RequestCode.PUT_KV_CONFIG:
            case RequestCode.GET_KV_CONFIG:
            case RequestCode.DELETE_KV_CONFIG:
            case RequestCode.QUERY_DATA_VERSION:
                // TODO return queryBrokerTopicConfig(ctx, request);
            case RequestCode.REGISTER_BROKER:
            case RequestCode.UNREGISTER_BROKER:
            case GET_ROUTEINTO_BY_TOPIC:
                // TODO return this.getRouteInfoByTopic(ctx, request);
                return handleTopicMetadata(ctx, request);
            case RequestCode.GET_BROKER_CLUSTER_INFO:  // 需要 | 管控端需要
                return this.getBrokerClusterInfo(ctx, request);
            case RequestCode.WIPE_WRITE_PERM_OF_BROKER:
            case RequestCode.GET_ALL_TOPIC_LIST_FROM_NAMESERVER:
                // return getAllTopicListFromNameserver(ctx, request);
            case RequestCode.DELETE_TOPIC_IN_NAMESRV:
                //return deleteTopicInNamesrv(ctx, request);
            case RequestCode.GET_KVLIST_BY_NAMESPACE:
            case RequestCode.GET_TOPICS_BY_CLUSTER:
                //return this.getTopicsByCluster(ctx, request);
            case RequestCode.GET_SYSTEM_TOPIC_LIST_FROM_NS:
                //return this.getSystemTopicListFromNs(ctx, request);
            case RequestCode.GET_UNIT_TOPIC_LIST:
                //return this.getUnitTopicList(ctx, request);
            case RequestCode.GET_HAS_UNIT_SUB_TOPIC_LIST:
                //return this.getHasUnitSubTopicList(ctx, request);
            case RequestCode.GET_HAS_UNIT_SUB_UNUNIT_TOPIC_LIST:
                //return this.getHasUnitSubUnUnitTopicList(ctx, request);
            case RequestCode.UPDATE_NAMESRV_CONFIG:
            case RequestCode.GET_NAMESRV_CONFIG:
            default:
                break;
        }
        return null;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    protected RemotingCommand handleTopicMetadata(ChannelHandlerContext ctx, RemotingCommand request)
            throws Exception {
        checkNotNull(request);
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);
        final GetRouteInfoRequestHeader requestHeader =
                (GetRouteInfoRequestHeader) request.decodeCommandCustomHeader(GetRouteInfoRequestHeader.class);
        TopicRouteData topicRouteData = new TopicRouteData();
        List<BrokerData> brokerDatas = new ArrayList<>();
        List<QueueData> queueDatas = new ArrayList<>();
        topicRouteData.setBrokerDatas(brokerDatas);
        topicRouteData.setQueueDatas(queueDatas);

        String clusterName = config.getClusterName();

        // 如果主题名和clusterName相同，返回集群中任意一个节点到客户端。这里为了兼容客户端创建主题操作
        if (clusterName.equals(requestHeader.getTopic())) {
            try {
                PulsarAdmin adminClient = brokerController.getBrokerService().pulsar().getAdminClient();
                List<String> brokers = adminClient.brokers().getActiveBrokers(clusterName);
                String randomBroker = brokers.get(new Random().nextInt(brokers.size()));
                String rmqBrokerAddress = parseBrokerAddress(randomBroker, servicePort);
                BrokerData brokerData = new BrokerData();
                HashMap<Long, String> brokerAddrs = Maps.newHashMap();
                brokerAddrs.put(0L, rmqBrokerAddress);
                brokerData.setBrokerAddrs(brokerAddrs);
                brokerDatas.add(brokerData);
                byte[] content = topicRouteData.encode();
                response.setBody(content);
                response.setCode(ResponseCode.SUCCESS);
                response.setRemark(null);
                return response;
            } catch (Exception e) {
                log.error("Cluster [{}] get route info failed", clusterName, e);
                response.setCode(ResponseCode.SYSTEM_ERROR);
                response.setRemark(null);
                return response;
            }
        }

        String listenerName = getListenerName(ctx);

        // 根据传入的请求获取指定的topic
        String requestTopic = requestHeader.getTopic();
        if (Strings.isNotBlank(requestTopic)) {
            RocketMQTopic mqTopic = new RocketMQTopic(requestTopic);
            Map<Integer, InetSocketAddress> topicBrokerAddr =
                    mqTopicManager.getTopicBrokerAddr(mqTopic.getPulsarTopicName(), Strings.EMPTY);
            try {
                if (topicBrokerAddr != null && topicBrokerAddr.size() > 0) {
                    Set<String> brokerNames = Sets.newHashSet();
                    topicBrokerAddr.forEach((i, addr) -> brokerNames.add(addr.getHostName()));

                    for (String brokerName : brokerNames) {
                        String ropBrokerAddress = getBrokerAddressByListenerName(brokerName, listenerName);

                        HashMap<Long, String> brokerAddrs = new HashMap<>();
                        brokerAddrs.put(0L, ropBrokerAddress);
                        BrokerData brokerData = new BrokerData(clusterName, brokerName, brokerAddrs);
                        brokerDatas.add(brokerData);
                        topicRouteData.setBrokerDatas(brokerDatas);

                        QueueData queueData = new QueueData();
                        queueData.setBrokerName(brokerName);
                        queueData.setReadQueueNums(topicBrokerAddr.size());
                        queueData.setWriteQueueNums(topicBrokerAddr.size());
                        queueData.setPerm(PERM_WRITE | PERM_READ);
                        queueDatas.add(queueData);
                        topicRouteData.setQueueDatas(queueDatas);
                    }

                    byte[] content = topicRouteData.encode();
                    response.setBody(content);
                    response.setCode(ResponseCode.SUCCESS);
                    response.setRemark(null);
                    return response;
                }
            } catch (Exception ex) {
                log.warn("fetch topic address of topic[{}] error.", requestTopic, ex);
            }
        }

        response.setCode(ResponseCode.TOPIC_NOT_EXIST);
        response.setRemark("No topic route info in name server for the topic: " + requestHeader.getTopic()
                + FAQUrl.suggestTodo(FAQUrl.APPLY_TOPIC_URL));
        return response;
    }

    public String parseBrokerAddress(String brokerAddress, int port) {
        // pulsar://localhost:6650
        if (null == brokerAddress) {
            log.error("The brokerAddress is null, please check.");
            return "";
        }
        Matcher matcher = BROKER_ADDER_PAT.matcher(brokerAddress);
        String result = brokerAddress;
        if (matcher.find()) {
            result = matcher.group(1) + servicePort;
        }
        return result;
    }

    /**
     * Get cluster info according to cluster name.
     */
    private RemotingCommand getBrokerClusterInfo(ChannelHandlerContext ctx, RemotingCommand request) {
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);

        String clusterName = config.getClusterName();
        try {
            PulsarAdmin adminClient = brokerController.getBrokerService().pulsar().getAdminClient();
            List<String> brokers = adminClient.brokers().getActiveBrokers(clusterName);

            HashMap<String, BrokerData> brokerAddrTable = Maps.newHashMap();
            Set<String> brokerNames = Sets.newHashSet();
            for (String broker : brokers) {
                String rmqBrokerAddress = parseBrokerAddress(broker, servicePort);
                String brokerName = PulsarUtil.getBrokerHost(broker);

                HashMap<Long, String> brokerAddrs = Maps.newHashMap();
                brokerAddrs.put(0L, rmqBrokerAddress);
                brokerAddrTable.put(brokerName, new BrokerData(clusterName, brokerName, brokerAddrs));

                brokerNames.add(brokerName);
            }

            HashMap<String, Set<String>> clusterAddrTable = Maps.newHashMap();
            clusterAddrTable.put(clusterName, brokerNames);

            ClusterInfo clusterInfoSerializeWrapper = new ClusterInfo();
            clusterInfoSerializeWrapper.setBrokerAddrTable(brokerAddrTable);
            clusterInfoSerializeWrapper.setClusterAddrTable(clusterAddrTable);

            response.setBody(clusterInfoSerializeWrapper.encode());

            response.setCode(ResponseCode.SUCCESS);
            response.setRemark(null);
            return response;
        } catch (Exception e) {
            log.error("ClusterName [{}] getBrokerClusterInfo failed", clusterName, e);
        }

        response.setCode(ResponseCode.SYSTEM_ERROR);
        response.setRemark(null);
        return response;
    }

    /**
     * Analyze the request local receiving port, and identify the client network type according to the
     * port [type mapping relationship is specified by the configuration item rocketmqListenerPortMap].
     */
    private String getListenerName(ChannelHandlerContext ctx) {
        String localAddress = ctx.channel().localAddress().toString();
        String localPort = localAddress.substring(localAddress.indexOf(":") + 1);
        return PORT_LISTENER_NAME_MAP.get(localPort);
    }

    private String getBrokerAddressByListenerName(String host, String listenerName) {
        ModularLoadManagerImpl modularLoadManager = getModularLoadManagerImpl();

        List<String> brokers = Lists.newArrayList(modularLoadManager.getAvailableBrokers());
        if (brokers.isEmpty()) {
            log.info("GetBrokerAddressByListenerName not found broker");
            return Joiner.on(":").join(host, servicePort);
        }
        String brokerAddress = brokers.get(0);
        String port = brokerAddress.substring(brokerAddress.indexOf(":") + 1).trim();
        brokerAddress = Joiner.on(":").join(host, port);

        LocalBrokerData localBrokerData = modularLoadManager.getBrokerLocalData(brokerAddress);
        if (localBrokerData == null) {
            log.info("GetBrokerAddressByListenerName not found localBrokerData, host: {}", host);
            return Joiner.on(":").join(host, servicePort);
        }

        AdvertisedListener advertisedListener = localBrokerData.getAdvertisedListeners().get(listenerName);
        if (advertisedListener == null) {
            log.info("GetBrokerAddressByListenerName not found advertisedListener, listenerName: {}", listenerName);
            return Joiner.on(":").join(host, servicePort);
        }

        return advertisedListener.getBrokerServiceUrl().toString().replaceAll("pulsar://", "");
    }

    private ModularLoadManagerImpl getModularLoadManagerImpl() {

        return (ModularLoadManagerImpl) ((ModularLoadManagerWrapper) this.brokerController.getBrokerService()
                .getPulsar().getLoadManager().get()).getLoadManager();
    }
}
