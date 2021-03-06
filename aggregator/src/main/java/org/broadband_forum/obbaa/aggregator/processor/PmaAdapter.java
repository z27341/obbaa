/*
 * Copyright 2018 Broadband Forum
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

package org.broadband_forum.obbaa.aggregator.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.broadband_forum.obbaa.adapter.handler.DeviceAdapterActionHandler;
import org.broadband_forum.obbaa.aggregator.api.Aggregator;
import org.broadband_forum.obbaa.aggregator.api.DeviceConfigProcessor;
import org.broadband_forum.obbaa.aggregator.api.DispatchException;
import org.broadband_forum.obbaa.aggregator.api.GlobalRequestProcessor;
import org.broadband_forum.obbaa.aggregator.jaxb.netconf.api.NetconfRpcMessage;
import org.broadband_forum.obbaa.aggregator.jaxb.netconf.schema.rpc.RpcOperationType;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.api.DeployAdapterRpc;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.api.PmaDeviceConfigRpc;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.api.PmaYangLibraryRpc;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.api.UndeployAdapterRpc;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.schema.deviceconfig.PmaDeviceConfigAlign;
import org.broadband_forum.obbaa.netconf.api.client.NetconfClientInfo;
import org.broadband_forum.obbaa.pma.DeviceModelDeployer;
import org.broadband_forum.obbaa.pma.PmaRegistry;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PMA Adapter.
 */
public class PmaAdapter implements DeviceConfigProcessor, GlobalRequestProcessor {

    private static final String DEVICE_DPU = "DPU";
    private static final Logger LOGGER = LoggerFactory.getLogger(PmaAdapter.class);

    Aggregator m_aggregator;
    PmaRegistry m_pmaRegistry;
    DeviceModelDeployer m_deviceModelDeployer;
    DeviceAdapterActionHandler m_deviceAdapterActionHandler;

    /**
     * PMA dependents Aggregator for message dispatch.
     *
     * @param aggregator          Aggregator component
     * @param actionHandler       Device Adapter Action Handler
     * @param pmaRegistry         PMA Registry
     * @param deviceModelDeployer PMA Model deploy
     */
    public PmaAdapter(Aggregator aggregator, PmaRegistry pmaRegistry,
                      DeviceModelDeployer deviceModelDeployer, DeviceAdapterActionHandler actionHandler) {
        m_aggregator = aggregator;
        m_pmaRegistry = pmaRegistry;
        m_deviceModelDeployer = deviceModelDeployer;
        m_deviceAdapterActionHandler = actionHandler;
    }

    /**
     * Initialize the PMA Adapter.
     */
    public void init() {
        registerGlobalProcessors();
        registerDeviceConfigProcessor();
    }

    /**
     * Destroy resource of PMA Adapter.
     */
    public void destroy() {
        try {
            getAggregator().removeProcessor((GlobalRequestProcessor) this);
            getAggregator().removeProcessor((DeviceConfigProcessor) this);
        }
        catch (DispatchException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    /**
     * Register global processor which functions supported by PMA.
     */
    private void registerGlobalProcessors() {
        try {
            getAggregator().addProcessor(buildGlobalProcessor(), this);
        }
        catch (DispatchException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    /**
     * Build module identifiers as a global processor of PMA.
     *
     * @return Module identifiers
     */
    private Set<ModuleIdentifier> buildGlobalProcessor() {
        Set<ModuleIdentifier> moduleIdentifiers = new HashSet<>();
        moduleIdentifiers.addAll(buildPmaYangLibraryModules());
        moduleIdentifiers.addAll(buildDeployAdapterYangModules());

        return moduleIdentifiers;
    }

    /**
     * Build PMA Action modules.
     *
     * @return Module identifiers
     */
    private Set<ModuleIdentifier> buildPmaYangLibraryModules() {
        Set<ModuleIdentifier> moduleIdentifiers = new HashSet<>();

        ModuleIdentifier moduleIdentifier = NetconfMessageUtil.buildModuleIdentifier(PmaYangLibraryRpc.MODULE_NAME,
                PmaYangLibraryRpc.NAMESPACE, PmaYangLibraryRpc.REVISION);
        moduleIdentifiers.add(moduleIdentifier);

        return moduleIdentifiers;
    }

    private Set<ModuleIdentifier> buildDeployAdapterYangModules() {
        Set<ModuleIdentifier> moduleIdentifiers = new HashSet<>();

        ModuleIdentifier moduleIdentifier = NetconfMessageUtil.buildModuleIdentifier(DeployAdapterRpc.MODULE_NAME,
                DeployAdapterRpc.NAMESPACE, DeployAdapterRpc.REVISION);
        moduleIdentifiers.add(moduleIdentifier);

        return moduleIdentifiers;
    }

    /**
     * Build PMA device config YANG modules.
     *
     * @return Module identifiers
     */
    private Set<ModuleIdentifier> buildPmaDeviceConfigModules() {
        Set<ModuleIdentifier> moduleIdentifiers = new HashSet<>();

        ModuleIdentifier moduleIdentifier = NetconfMessageUtil.buildModuleIdentifier(PmaDeviceConfigRpc.MODULE_NAME,
                PmaDeviceConfigRpc.NAMESPACE, PmaDeviceConfigRpc.REVISION);
        moduleIdentifiers.add(moduleIdentifier);

        return moduleIdentifiers;
    }

    /**
     * Register device config processor which functions supported by PMA.
     */
    private void registerDeviceConfigProcessor() {
        try {
            Set<ModuleIdentifier> moduleIdentifiers = getDeviceModelDeployer().getAllModuleIdentifiers();
            moduleIdentifiers.addAll(buildPmaDeviceConfigModules());
            getAggregator().addProcessor(DEVICE_DPU, moduleIdentifiers, this);
        }
        catch (DispatchException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    @Override
    public String processRequest(String deviceName, String netconfRequest) throws DispatchException {
        try {
            NetconfRpcMessage netconfRpcMessage = NetconfRpcMessage.getInstance(netconfRequest);
            if (netconfRpcMessage.getOnlyOneTopXmlns().equals(PmaDeviceConfigRpc.NAMESPACE)) {
                return processPmaDeviceConfigRequest(deviceName, netconfRpcMessage);
            }

            return getPmaRegistry().executeNC(deviceName, netconfRequest);
        }
        catch (IllegalArgumentException | IllegalStateException | ExecutionException ex) {
            throw new DispatchException(ex);
        }
    }

    @Override
    public String processRequest(NetconfClientInfo clientInfo, String netconfRequest) throws DispatchException {
        NetconfRpcMessage netconfRpcMessage = NetconfRpcMessage.getInstance(netconfRequest);
        if (netconfRpcMessage.getOnlyOneTopXmlns().equals(PmaYangLibraryRpc.NAMESPACE)) {
            return processPmaYangLibraryRequest(netconfRpcMessage);
        } else if (netconfRpcMessage.getOnlyOneTopXmlns().equals(DeployAdapterRpc.NAMESPACE)) {
            return processDeployOrUndeployAdapterRequest(netconfRpcMessage);
        }

        throw DispatchException.buildNotSupport();
    }

    private String processDeployOrUndeployAdapterRequest(NetconfRpcMessage netconfRpcMessage) throws DispatchException {
        if (!netconfRpcMessage.getRpc().getRpcOperationType().equals(RpcOperationType.ACTION)) {
            return netconfRpcMessage.buildRpcReplyError(DispatchException.NOT_SUPPORT);
        }
        DeployAdapterRpc deployAdapterRpc = DeployAdapterRpc.getInstance(netconfRpcMessage.getOriginalMessage());
        if (deployAdapterRpc.getDeployAdapter() != null) {
            deployAdapter(deployAdapterRpc);
        }
        UndeployAdapterRpc undeployAdapterRpc = UndeployAdapterRpc.getInstance(netconfRpcMessage.getOriginalMessage());
        if (undeployAdapterRpc.getUndeployAdapter() != null) {
            undeployAdapter(undeployAdapterRpc);
        }

        return netconfRpcMessage.buildRpcReplyOk();
    }

    private void deployAdapter(DeployAdapterRpc deployAdapterRpc) throws DispatchException {
        try {
            m_deviceAdapterActionHandler.deployRpc(deployAdapterRpc.getDeployAdapter().getDeploy().getAdapterArchive());
        } catch (Exception e) {
            throw new DispatchException(e);
        }
    }

    private void undeployAdapter(UndeployAdapterRpc deployAdapterRpc) throws DispatchException {
        try {
            m_deviceAdapterActionHandler.undeploy(deployAdapterRpc.getUndeployAdapter().getUndeploy().getAdapterArchive());
        } catch (Exception e) {
            throw new DispatchException(e);
        }
    }

    /**
     * Process the request of YANG module reloading.
     *
     * @param netconfRpcMessage Request
     * @return Response message
     */
    private String processPmaYangLibraryRequest(NetconfRpcMessage netconfRpcMessage) throws DispatchException {
        if (!netconfRpcMessage.getRpc().getRpcOperationType().equals(RpcOperationType.ACTION)) {
            return netconfRpcMessage.buildRpcReplyError(DispatchException.NOT_SUPPORT);
        }

        PmaYangLibraryRpc pmaYangLibraryRpc = PmaYangLibraryRpc.getInstance(netconfRpcMessage.getOriginalMessage());
        reloadYangLibrary(pmaYangLibraryRpc);

        return netconfRpcMessage.buildRpcReplyOk();
    }

    /**
     * Reload YANG library of PMA.
     *
     * @param pmaYangLibraryRpc Request
     */
    private void reloadYangLibrary(PmaYangLibraryRpc pmaYangLibraryRpc) throws DispatchException {
        if (pmaYangLibraryRpc.getPmaYangLibrary().getReload() == null) {
            return;
        }

        //Just support reload YANG library
        List<String> responses = getPmaRegistry().reloadDeviceModel();
        for (String response : responses) {
            LOGGER.info("reloadYangLibrary: {}", response);
        }

        redeployYangLibrary();
    }

    /**
     * Redeploy YANG library of PMA.
     *
     * @throws DispatchException Exception
     */
    private void redeployYangLibrary() throws DispatchException {
        Set<ModuleIdentifier> moduleIdentifiers = getDeviceModelDeployer().getAllModuleIdentifiers();
        getAggregator().addProcessor("DPU", moduleIdentifiers, this);
    }

    /**
     * Process the request of configuration align in PMA.
     *
     * @param deviceName        Device name
     * @param netconfRpcMessage Request
     * @return Response message
     * @throws DispatchException Exception
     */
    private String processPmaDeviceConfigRequest(String deviceName, NetconfRpcMessage netconfRpcMessage)
            throws DispatchException {
        PmaDeviceConfigRpc pmaDeviceConfigRpc = PmaDeviceConfigRpc.getInstance(netconfRpcMessage.getOriginalMessage());

        switch (netconfRpcMessage.getRpc().getRpcOperationType()) {
            case GET:
                return processPmaDeviceConfigGet(deviceName, pmaDeviceConfigRpc);
            case ACTION:
                return processPmaDeviceConfigAction(deviceName, pmaDeviceConfigRpc);
            default:
                throw DispatchException.buildNotSupport();
        }
    }

    /**
     * Process request of alignment state for a device.
     *
     * @param deviceName         Device name
     * @param pmaDeviceConfigRpc Request
     * @return Response
     * @throws DispatchException Exception
     */
    private String processPmaDeviceConfigGet(String deviceName, PmaDeviceConfigRpc pmaDeviceConfigRpc)
            throws DispatchException {
        //TODO : there is no api of PMA to query the state
        pmaDeviceConfigRpc.getPmaDeviceConfig().setAlignmentState("aligned");

        return pmaDeviceConfigRpc.buildRpcReplyDataResponse();
    }

    /**
     * Process request of device config for a device.
     *
     * @param deviceName         Device name
     * @param pmaDeviceConfigRpc Request
     * @return Response
     * @throws DispatchException Exception
     */
    private String processPmaDeviceConfigAction(String deviceName, PmaDeviceConfigRpc pmaDeviceConfigRpc)
            throws DispatchException {
        try {
            PmaDeviceConfigAlign align = pmaDeviceConfigRpc.getPmaDeviceConfig().getPmaDeviceConfigAlign();
            if ((align != null) && (align.getForce() != null)) {
                getPmaRegistry().forceAlign(deviceName);
            } else {
                getPmaRegistry().align(deviceName);
            }
            return pmaDeviceConfigRpc.buildRpcReplyOk();
        }
        catch (ExecutionException ex) {
            throw new DispatchException(ex);
        }
    }

    /**
     * Get namespace of the request.
     *
     * @param netconfRpcMessage Request
     * @return Namespace
     * @throws DispatchException Exception
     */
    private String getRequestNamespace(NetconfRpcMessage netconfRpcMessage) throws DispatchException {
        return netconfRpcMessage.getOnlyOneTopXmlns();
    }

    /**
     * Get Aggregator component.
     *
     * @return Aggregator
     */
    public Aggregator getAggregator() {
        return m_aggregator;
    }

    /**
     * Get PMA registry component.
     *
     * @return Component
     */
    public PmaRegistry getPmaRegistry() {
        return m_pmaRegistry;
    }

    /**
     * Get component of PMA device model deployer.
     *
     * @return Component
     */
    public DeviceModelDeployer getDeviceModelDeployer() {
        return m_deviceModelDeployer;
    }
}
