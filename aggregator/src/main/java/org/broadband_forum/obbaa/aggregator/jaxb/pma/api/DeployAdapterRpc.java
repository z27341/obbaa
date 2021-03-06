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

package org.broadband_forum.obbaa.aggregator.jaxb.pma.api;


import java.util.List;

import javax.xml.bind.JAXBException;

import org.broadband_forum.obbaa.aggregator.api.DispatchException;
import org.broadband_forum.obbaa.aggregator.jaxb.netconf.api.NetconfRpcMessage;
import org.broadband_forum.obbaa.aggregator.jaxb.pma.schema.adapter.DeployAdapter;
import org.broadband_forum.obbaa.aggregator.jaxb.utils.JaxbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public final class DeployAdapterRpc extends NetconfRpcMessage {

    public static final String MODULE_NAME = "bbf-obbaa-device-adapters";
    public static final String REVISION = "2018-08-31";
    public static final String NAMESPACE = "urn:bbf:yang:obbaa:device-adapters";

    private static final Logger LOGGER = LoggerFactory.getLogger(DeployAdapterRpc.class);

    private DeployAdapter deployAdapter;

    private DeployAdapterRpc(String netconfMessage) throws JAXBException {
        super(netconfMessage);
        parseDeployAdapter(getRpcPayloads(getRpc()));
    }

    public static DeployAdapterRpc getInstance(String netconfMessage) throws DispatchException {
        try {
            DeployAdapterRpc instance = new DeployAdapterRpc(netconfMessage);
            return instance;
        }
        catch (JAXBException ex) {
            throw new DispatchException(ex);
        }
    }

    private void parseDeployAdapter(List<Document> payloads) {
        Document payload = payloads.get(0);
        deployAdapter = unmarshalDeployAdapter(payload);
    }

    private static DeployAdapter unmarshalDeployAdapter(Document payload) {
        try {
            return JaxbUtils.unmarshal(payload, DeployAdapter.class);
        }
        catch (JAXBException ex) {
            LOGGER.error(ex.getErrorCode());
            return null;
        }
    }

    public DeployAdapter getDeployAdapter() {
        return deployAdapter;
    }
}
