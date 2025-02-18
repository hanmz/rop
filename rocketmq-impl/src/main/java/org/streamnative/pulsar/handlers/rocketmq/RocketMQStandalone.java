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

package org.streamnative.pulsar.handlers.rocketmq;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.beust.jcommander.Parameter;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.pulsar.PulsarStandalone;
import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.ServiceConfigurationUtils;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.functions.worker.WorkerConfig;
import org.apache.pulsar.functions.worker.WorkerService;
import org.apache.pulsar.transaction.coordinator.TransactionCoordinatorID;
import org.apache.pulsar.zookeeper.LocalBookkeeperEnsemble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ standalone server.
 */
public class RocketMQStandalone implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PulsarStandalone.class);

    PulsarService broker;
    PulsarAdmin admin;
    LocalBookkeeperEnsemble bkEnsemble;
    ServiceConfiguration config;
    WorkerService fnWorkerService;
    @Parameter(names = {"-c", "--config"}, description = "Configuration file path", required = true)
    private String configFile;
    @Parameter(names = {"--wipe-data"}, description = "Clean up previous ZK/BK data")
    private boolean wipeData = false;
    @Parameter(names = {"--num-bookies"}, description = "Number of local Bookies")
    private int numOfBk = 1;
    @Parameter(names = {"--zookeeper-port"}, description = "Local zookeeper's port")
    private int zkPort = 2181;
    @Parameter(names = {"--bookkeeper-port"}, description = "Local bookies base port")
    private int bkPort = 3181;
    @Parameter(names = {"--zookeeper-dir"}, description = "Local zooKeeper's data directory")
    private String zkDir = "data/standalone/zookeeper";
    @Parameter(names = {"--bookkeeper-dir"}, description = "Local bookies base data directory")
    private String bkDir = "data/standalone/bookkeeper";
    @Parameter(names = {"--no-broker"}, description = "Only start ZK and BK services, no broker")
    private boolean noBroker = false;
    @Parameter(names = {"--only-broker"}, description = "Only start Pulsar broker service (no ZK, BK)")
    private boolean onlyBroker = false;
    @Parameter(names = {"-nfw", "--no-functions-worker"}, description = "Run functions worker with Broker")
    private boolean noFunctionsWorker = false;
    @Parameter(names = {"-fwc", "--functions-worker-conf"}, description = "Configuration file for Functions Worker")
    private String fnWorkerConfigFile =
            Paths.get("").toAbsolutePath().normalize().toString() + "/conf/functions_worker.yml";
    @Parameter(names = {"-nss", "--no-stream-storage"}, description = "Disable stream storage")
    private boolean noStreamStorage = false;
    @Parameter(names = {"--stream-storage-port"}, description = "Local bookies stream storage port")
    private int streamStoragePort = 4181;
    @Parameter(names = {"-a", "--advertised-address"}, description = "Standalone broker advertised address")
    private String advertisedAddress = null;
    @Parameter(names = {"-h", "--help"}, description = "Show this help message")
    private boolean help = false;

    /**
     * This method gets a builder to build an embedded pulsar instance
     * i.e.
     * <pre>
     * <code>
     * PulsarStandalone pulsarStandalone = PulsarStandalone.builder().build();
     * pulsarStandalone.start();
     * pulsarStandalone.stop();
     * </code>
     * </pre>
     *
     * @return PulsarStandaloneBuilder instance
     */
    public static RocketMQStandaloneBuilder builder() {
        return RocketMQStandaloneBuilder.instance();
    }

    public void setBroker(PulsarService broker) {
        this.broker = broker;
    }

    public void setBkEnsemble(LocalBookkeeperEnsemble bkEnsemble) {
        this.bkEnsemble = bkEnsemble;
    }

    public void setFnWorkerService(WorkerService fnWorkerService) {
        this.fnWorkerService = fnWorkerService;
    }

    public ServiceConfiguration getConfig() {
        return config;
    }

    public void setConfig(ServiceConfiguration config) {
        this.config = config;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public boolean isWipeData() {
        return wipeData;
    }

    public void setWipeData(boolean wipeData) {
        this.wipeData = wipeData;
    }

    public int getNumOfBk() {
        return numOfBk;
    }

    public void setNumOfBk(int numOfBk) {
        this.numOfBk = numOfBk;
    }

    public int getZkPort() {
        return zkPort;
    }

    public void setZkPort(int zkPort) {
        this.zkPort = zkPort;
    }

    public int getBkPort() {
        return bkPort;
    }

    public void setBkPort(int bkPort) {
        this.bkPort = bkPort;
    }

    public String getZkDir() {
        return zkDir;
    }

    public void setZkDir(String zkDir) {
        this.zkDir = zkDir;
    }

    public String getBkDir() {
        return bkDir;
    }

    public void setBkDir(String bkDir) {
        this.bkDir = bkDir;
    }

    public boolean isNoBroker() {
        return noBroker;
    }

    public void setNoBroker(boolean noBroker) {
        this.noBroker = noBroker;
    }

    public boolean isOnlyBroker() {
        return onlyBroker;
    }

    public void setOnlyBroker(boolean onlyBroker) {
        this.onlyBroker = onlyBroker;
    }

    public boolean isNoFunctionsWorker() {
        return noFunctionsWorker;
    }

    public void setNoFunctionsWorker(boolean noFunctionsWorker) {
        this.noFunctionsWorker = noFunctionsWorker;
    }

    public String getFnWorkerConfigFile() {
        return fnWorkerConfigFile;
    }

    public void setFnWorkerConfigFile(String fnWorkerConfigFile) {
        this.fnWorkerConfigFile = fnWorkerConfigFile;
    }

    public boolean isNoStreamStorage() {
        return noStreamStorage;
    }

    public void setNoStreamStorage(boolean noStreamStorage) {
        this.noStreamStorage = noStreamStorage;
    }

    public int getStreamStoragePort() {
        return streamStoragePort;
    }

    public void setStreamStoragePort(int streamStoragePort) {
        this.streamStoragePort = streamStoragePort;
    }

    public String getAdvertisedAddress() {
        return advertisedAddress;
    }

    public void setAdvertisedAddress(String advertisedAddress) {
        this.advertisedAddress = advertisedAddress;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public void start() throws Exception {

        if (config == null) {
            throw new RuntimeException("The config file is null");
        }

        log.debug("--- setup PulsarStandaloneStarter ---");

        if (!this.isOnlyBroker()) {
            ServerConfiguration bkServerConf = new ServerConfiguration();
            bkServerConf.loadConf(new File(configFile).toURI().toURL());

            // Start LocalBookKeeper
            bkEnsemble = new LocalBookkeeperEnsemble(
                    this.getNumOfBk(), this.getZkPort(), this.getBkPort(), this.getStreamStoragePort(), this.getZkDir(),
                    this.getBkDir(), this.isWipeData(), "127.0.0.1");
            bkEnsemble.startStandalone(bkServerConf, !this.isNoStreamStorage());
        }

        if (this.isNoBroker()) {
            return;
        }

        // initialize the functions worker
        if (!this.isNoFunctionsWorker()) {
            WorkerConfig workerConfig;
            if (isBlank(this.getFnWorkerConfigFile())) {
                workerConfig = new WorkerConfig();
            } else {
                workerConfig = WorkerConfig.load(this.getFnWorkerConfigFile());
            }
            // worker talks to local broker
            if (this.isNoStreamStorage()) {
                // only set the state storage service url when state is enabled.
                workerConfig.setStateStorageServiceUrl(null);
            } else if (workerConfig.getStateStorageServiceUrl() == null) {
                workerConfig.setStateStorageServiceUrl("bk://127.0.0.1:" + this.getStreamStoragePort());
            }
            config.getWebServicePort()
                    .map(port -> workerConfig.setWorkerPort(port));
            config.getWebServicePortTls()
                    .map(port -> workerConfig.setWorkerPortTls(port));

            String hostname = ServiceConfigurationUtils.getDefaultOrConfiguredAddress(
                    config.getAdvertisedAddress());
            workerConfig.setWorkerHostname(hostname);
            workerConfig.setWorkerPort(config.getWebServicePort().get());
            workerConfig.setWorkerId(
                    "c-" + config.getClusterName()
                            + "-fw-" + hostname
                            + "-" + workerConfig.getWorkerPort());
            // inherit broker authorization setting
            workerConfig.setAuthenticationEnabled(config.isAuthenticationEnabled());
            workerConfig.setAuthenticationProviders(config.getAuthenticationProviders());

            workerConfig.setAuthorizationEnabled(config.isAuthorizationEnabled());
            workerConfig.setAuthorizationProvider(config.getAuthorizationProvider());
            workerConfig.setConfigurationStoreServers(config.getConfigurationStoreServers());
            workerConfig.setZooKeeperSessionTimeoutMillis(config.getZooKeeperSessionTimeoutMillis());
            workerConfig.setZooKeeperOperationTimeoutSeconds(config.getZooKeeperOperationTimeoutSeconds());

            workerConfig.setTlsAllowInsecureConnection(config.isTlsAllowInsecureConnection());
            workerConfig.setTlsEnableHostnameVerification(false);
            workerConfig.setBrokerClientTrustCertsFilePath(config.getTlsTrustCertsFilePath());

            // client in worker will use this config to authenticate with broker
            workerConfig.setBrokerClientAuthenticationPlugin(config.getBrokerClientAuthenticationPlugin());
            workerConfig.setBrokerClientAuthenticationParameters(config.getBrokerClientAuthenticationParameters());

            // inherit super users
            workerConfig.setSuperUserRoles(config.getSuperUserRoles());

            fnWorkerService = new WorkerService(workerConfig);
        }

        // Start Broker
        broker = new PulsarService(config,
                Optional.ofNullable(fnWorkerService),
                (exitCode) -> {
                    log.info("Halting standalone process with code {}", exitCode);
                    Runtime.getRuntime().halt(exitCode);
                });
        broker.start();

        if (config.isTransactionCoordinatorEnabled()) {
            broker.getTransactionMetadataStoreService().addTransactionMetadataStore(TransactionCoordinatorID.get(0));
        }

        if (!config.isTlsEnabled()) {
            URL webServiceUrl = new URL(
                    String.format("http://%s:%d", config.getAdvertisedAddress(), config.getWebServicePort().get()));
            admin = PulsarAdmin.builder().serviceHttpUrl(webServiceUrl.toString()).authentication(
                    config.getBrokerClientAuthenticationPlugin(), config.getBrokerClientAuthenticationParameters())
                    .build();
        } else {
            URL webServiceUrlTls = new URL(
                    String.format("https://%s:%d", config.getAdvertisedAddress(), config.getWebServicePortTls().get()));
            PulsarAdminBuilder builder = PulsarAdmin.builder()
                    .serviceHttpUrl(webServiceUrlTls.toString())
                    .authentication(
                            config.getBrokerClientAuthenticationPlugin(),
                            config.getBrokerClientAuthenticationParameters());

            // set trust store if needed.
            if (config.isBrokerClientTlsEnabled()) {
                if (config.isBrokerClientTlsEnabledWithKeyStore()) {
                    builder.useKeyStoreTls(true)
                            .tlsTrustStoreType(config.getBrokerClientTlsTrustStoreType())
                            .tlsTrustStorePath(config.getBrokerClientTlsTrustStore())
                            .tlsTrustStorePassword(config.getBrokerClientTlsTrustStorePassword());
                } else {
                    builder.tlsTrustCertsFilePath(config.getBrokerClientTrustCertsFilePath());
                }
                builder.allowTlsInsecureConnection(config.isTlsAllowInsecureConnection());
            }

            admin = builder.build();
            log.info("Build pulsar admin client: {}", admin.getServiceUrl());
        }

        log.debug("--- setup completed ---");
    }

    @Override
    public void close() {
        try {
            if (fnWorkerService != null) {
                fnWorkerService.stop();
            }

            if (broker != null) {
                broker.close();
            }

            if (bkEnsemble != null) {
                bkEnsemble.stop();
            }
        } catch (Exception e) {
            log.error("Shutdown failed: {}", e.getMessage(), e);
        }
    }
}
