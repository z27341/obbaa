Aggregator Component
========

<a id="aggregator" />

The \"Aggregator\" software component provides the interface between the
request processing components in the BAA Core and the NBI Adapters. The
interface exposed by the Aggregator toward the NBI Adapters is a java interface.
By providing this interface, the Aggregator adds architectural value by:

-   Masking the differences in various protocols within the NBI layer.
    Decoupling the Northbound protocol from the BAA Core

-   Hiding the specific implementation of the BAA Core processing
    components

-   Assisting in improving the availability of BAA Core processing
    components

The functionality provided by the Aggregator includes the ability to:

1.  Redirect requests coming from the NBI Adapter to appropriate request
    processing component within the BAA Core

2.  Forward notifications to SDN-C/OSS elements via the NBI Adapter

**Tip:** Requests and notifications that are directed to the BAA Core layer for administration of the BAA layer and management and control of devices use the YANG module \"bbf-obbaa-network-manager.yang\". When manipulating the managed device, requests and notifications use the mount point \"yangmnt:mount-point managed-device\" named \"root\" for that managed device.

High-Level Request and Notification Flows
-----------------------------------------

### Request Flows

1.  The NBI Adpater forwards user requests by calling the Aggregator
    interface(**dispatchRequest**). The Aggregator, by nature, uses
    NETCONF primitives but isn\'t a NETCONF server.

    a.  The NBI Adapter needs to translate their requests into NETCONF
        primitives before they can forward it to Aggregator.

2.  The Aggregator classifies the request either as device management
    and service configuration request, then forwards them to the
    Persistent Management Agent (PMA) or Device Manager (DM) components
    respectively for further processing.

    a.  The Aggregator uses the namespace (\"xmlns\") of the NETCONF
        request message and the device type device type to determine how
        to distribute the message.

    b.  The Aggregator stores the capability set from the processing
        components in order to determine which component to distribute
        the request. The capability sets are described by the YANG
        model\'s schema path, mount point, device type.

    c.  The DM and PMA components implement a unified
        interface(**DeviceManagementProcessor/DeviceConfigProcessor/GlobalRequestProcessor**)
        that is called by the Aggregator.

    d.  The Aggregator does not depend on DM, PMA components or other
        new request processing components as the Aggregator forwards
        messages based on capability sets of registered processor
        instances.

    e.  If the request is used to query information from the
        schema-mount, the Aggregator returns the result based on the
        YANG module library.

    f.  When the request includes the mount-point of the managed device
        (yangmnt:mount-point \"root\"), the Aggregator extracts the
        requests from the mount-point - creating, using the extracted data,
        a request and fowards the extracted request to the request processor 
        component that provides that capability. When the component that processes 
        the request returns the result, the Aggregator packages the result to the mount point.

### Notification Flows

1.  The DM/PMA components publish notifications to the Aggregator
    component by calling Aggregator\'s
    interface(**publishNotification**).

    a.  The notification message must be in NETCONF format, which is
        based on the YANG model description.

    b.  The Aggregator packages the respective notification to
        mount-point \'root\' for the specified managed device.

2.  The Aggregator forwards the notification to the NBI Adapter
    component.

    a.  The notification message must be in NETCONF format, which is
        based on the YANG model description.

    b.  The Aggregator forwards the notification to one or more
        registered NBI Adapters using the unified
        interface(**NotificationProcessor**) implemented by the NBI
        Adapter.

3.  The Aggregator provides a registration interface(**AddProcessor**) in
    order to forward notifications to the NBI Adapter component.

    a.  NBI Adapter needs to register itself to the Aggregator when
        the NBI Adapter is started.

    b.  When a NBI Adapter is destroyed, the destruction flow needs to
        call the API(**RemoveProcessor**) to delete the registration
        relationship.

4.  The Aggregator provides the DM/PMA with function registration
    interface(**AddProcessor**).

    a.  The DM, PMA and other new Processor components need to register
        themselves to the Aggregator when the processing component is
        started.

    b.  The Processing component needs to re-register after updating
        their YANG model.

    c.  Destruction of the Processor needs to call the
        API(**RemoveProcessor**) to delete the registration
        relationship.

Design Details
==============

The following is the high level interface of the Aggregator component.

```
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

package org.broadband_forum.obbaa.aggregator.api;

import java.util.Set;

import org.broadband_forum.obbaa.netconf.api.client.NetconfClientInfo;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;

/**
 * BAA Core API for NBI. It's used for message forwarding and notification listening.
 * The maximum number of request processors and notification processors is 100
 */
public interface Aggregator {

    /**
     * Provide a unified API to NBI for request message forwarding.
     *
     * @param clientInfo Client Info
     * @param netconfRequest Message defined with YANG from NBI.
     * @return Result
     * @throws DispatchException Dispatch exception
     */
    String dispatchRequest(NetconfClientInfo clientInfo, String netconfRequest) throws DispatchException;

    /**
     * Provide a unified API for notification publication.
     *
     * @param notificationMessage Notification message
     * @throws DispatchException Dispatch exception
     */
    void publishNotification(String notificationMessage) throws DispatchException;

    /**
     * Provide a unified API to PMA/DM for notification publish.
     *
     * @param deviceName          Device name
     * @param notificationMessage Notification message
     * @throws DispatchException Dispatch exception
     */
    void publishNotification(String deviceName, String notificationMessage) throws DispatchException;

    /**
     * Provide a unified API to message processor(YANG library...) register themselves.
     *
     * @param moduleIdentifiers      YANG model paths
     * @param globalRequestProcessor The instance of request processor
     * @throws DispatchException Dispatch exception
     */
    void addProcessor(Set<ModuleIdentifier> moduleIdentifiers,
                      GlobalRequestProcessor globalRequestProcessor) throws DispatchException;

    /**
     * Provide a unified API to message processor(PMA/DM) register themselves.
     *
     * @param deviceType            Device type
     * @param moduleIdentifiers     YANG model paths
     * @param deviceConfigProcessor The instance of message processor
     * @throws DispatchException Dispatch exception
     */
    void addProcessor(String deviceType, Set<ModuleIdentifier> moduleIdentifiers, DeviceConfigProcessor deviceConfigProcessor)
        throws DispatchException;

    /**
     * Provide a unified API to NAI for notification provider registry.
     *
     * @param notificationProcessor NAI notification processor
     * @throws DispatchException Dispatch exception
     */
    void addProcessor(NotificationProcessor notificationProcessor) throws DispatchException;

    /**
     * Provide a unified API to message processor(YANG library...) unregister themselves.
     *
     * @param globalRequestProcessor The instance of message processor like Schema-mount etc.
     * @throws DispatchException Dispatch exception
     */
    void removeProcessor(GlobalRequestProcessor globalRequestProcessor) throws DispatchException;

    /**
     * Provide a unified API to message processor(PMA/DM) unregister themselves.
     *
     * @param deviceConfigProcessor The instance of message processor like Device Manager etc.
     * @throws DispatchException Dispatch exception
     */
    void removeProcessor(DeviceConfigProcessor deviceConfigProcessor) throws DispatchException;

    /**
     * Provide a unified API to message processor(PMA/DM) unregister themselves.
     *
     * @param notificationProcessor The instance of message processor like Device Manager etc.
     * @throws DispatchException Dispatch exception
     */
    void removeProcessor(NotificationProcessor notificationProcessor) throws DispatchException;

    /**
     * Provide a unified API to message processor(PMA/DM) unregister themselves.
     *
     * @param deviceType            Device type
     * @param moduleIdentifiers     YANG model namespaces
     * @param deviceConfigProcessor The instance of message processor
     * @throws DispatchException Dispatch exception
     */
    void removeProcessor(String deviceType, Set<ModuleIdentifier> moduleIdentifiers, DeviceConfigProcessor deviceConfigProcessor)
        throws DispatchException;

    /**
     * Register device management processor.
     *
     * @param deviceManagementProcessor Device manager processor
     */
    void registerDeviceManager(DeviceManagementProcessor deviceManagementProcessor);

    /**
     * Unregister device management processor.
     */
    void unregisterDeviceManager();

    /**
     * Get all capabilities of all processors.
     *
     * @return Capabilities
     */
    Set<ProcessorCapability> getProcessorCapabilities();

    /**
     * Get all modules of all processors.
     *
     * @return All of the modules
     */
    Set<ModuleIdentifier> getModuleIdentifiers();

    /**
     * Get the YANG module capabilities of OB-BAA system.
     *
     * @return YANG modules
     */
    Set<String> getSystemCapabilities();
}
```

Requests and notifications are sent within the context of the mounted
schema for the BAA layer. The BAA layer\'s schema is defined in the YANG module 
\"bbf-obbaa-network-manager.yang\".

```
<schema-mounts xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-schema-mount">
  <mount-point>
    <module>bbf-obbaa-network-manager</module>
    <name>root</name>
      <module-set>
        <name>olt-device</name>
        <module>
        ...
        </module>
      </module-set>
      <module-set>
        <name>dpu-device</name>
        <module>
        ...
        </module>
      </module-set>
    </use-schema>
  </mount-point>
</schema-mounts>
```

Dispatching Requests
--------------------

The interface **dispatchRequest** is used to forward messages received
from the NBI Adapter to registered request processing components (e.g.,
DM, PMA).

When the Device Management and PMA components are started, they register the
request processor by calling the Aggregator\'s **addProcessor** API
where the request processing components register their capabilities by
schema path of YANG model. The Aggregator forwards the request to the DM or
PMA through the \"**xmlns**\" attributes in the request message.

Request message example:
```
<rpc message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
	<action">
		<network-manager xmlns="urn:bbf:yang:obbaa:network-manager">
			<managed-devices>
				<device>
					<name>deviceA</name>
					<root>
						<system xmlns="urn:ietf:params:xml:ns:yang:ietf-system">
							<restart/>
						</system>
					</root>
				</device>
			</managed-devices>
		</network-manager>
	</action>
</rpc>
```

In this request the managed device is found using the
"\<name\>deviceA\</name\>" leaf node. YANG model\'s namespace is found
from the requests namespace within the \"root\" mount-point (i.e.,
xmlns=\"urn:ietf:params:xml:ns:yang:ietf-system\"). The Aggregator then
uses these elements to forward the request to a registered processing
component.

Forward message example:

```
<rpc message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
  <action">
      <system xmlns="urn:ietf:params:xml:ns:yang:ietf-system">
        <restart/>
      </system>
  </action>
</rpc>
```

Response message example:

```

<!-- Successful -->
<?xml version="1.0"encoding="UTF-8"?>
   <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1520491011757">
   <ok />
</rpc-reply>

<!-- Error -->
<?xml version="1.0" encoding="UTF-8"?>
<rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1524537494121">
  <rpc-error>
    <error-type>application</error-type>
    <error-tag>bad-element</error-tag>
    <error-severity>error</error-severity>
    <error-message xml:lang="en">Value "sss" is invalid</error-message>
    <error-info>
      <bad-element>sort</bad-element>
    </error-info>
  </rpc-error>
</rpc-reply>

<!-- Query -->
<rpc message-id="101"
     xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
	<action xmlns="urn:ietf:params:xml:ns:yang:1">
		<network-manager xmlns="urn:bbf:yang:obbaa:network-manager">
			<managed-devices>
				<device>
					<name>device1</name>
					<root>
						<system xmlns="urn:ietf:params:xml:ns:yang:ietf-system">
							<address>2001:db8::3</address>
						</system>
					</root>
				</device>
			</managed-devices>
		</network-manager>
	</action>
</rpc>
```

Request processors (e.g., DM, PMA) provide a consistent interface that
is used by the Aggregator component. These interfaces allow new or
specialized request processors to be implemented without needing to
modify the Aggregator. In this release two (2) request processing
components have been implemented the DM and PMA components.

The following is the interface that the DM component exposes toward the
Aggregator:

```
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

package org.broadband_forum.obbaa.aggregator.api;

/**
 * Device Management Request processor.
 * Device management is mainly used for adding and deleting devices and other actions.
 */
public interface DeviceManagementProcessor extends GlobalRequestProcessor {
    /**
     * Get device type from device name.
     *
     * @param deviceName Device type
     * @return Device name
     * @throws DispatchException Error
     */
    String getDeviceTypeByDeviceName(String deviceName) throws DispatchException;
}
```

The following is the interface that the PMA component exposes toward the Aggregator:
**Tip:** The PMA component provides a capabilities specific to a managed device. As such the interface exposed by the PMA processor includes an attribute for the managed device to be passed by the Aggregator.

```
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

package org.broadband_forum.obbaa.aggregator.api;

/**
 * Implemented by PMA for device service configuration request process.
 * Device configuration is mainly used for service configuration like VLAN etc.
 */
public interface DeviceConfigProcessor {
    /**
     * Provide a unified API for netconfRequest processing from Aggregator.
     *
     * @param deviceName     Unique identifier of the device
     * @param netconfRequest Message of NETCONF request etc
     * @return Result
     * @throws DispatchException exception
     */
    String processRequest(String deviceName, String netconfRequest) throws DispatchException;
}
```

All request processing component's interface is subclassed from the GlobalRequestProcessor which is defined below:

```
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

package org.broadband_forum.obbaa.aggregator.api;

import org.broadband_forum.obbaa.netconf.api.client.NetconfClientInfo;

/**
 * Implemented by some common system config component for request process.
 */
public interface GlobalRequestProcessor {
    /**
     * Provide a unified API for netconfRequest processing from Aggregator.
     *
     *
     * @param clientInfo Client info
     * @param netconfRequest Message of NetConf request etc
     * @return Result
     * @throws DispatchException exception
     */
    String processRequest(NetconfClientInfo clientInfo, String netconfRequest) throws DispatchException;
}
```

Publishing Notifications
------------------------

The Aggregator component supports publishing the notification message to
the NBI Adapters using the from components who call the Aggregator\'s
**publishNotification** API. Once the Aggregator component receives a
notification it forwards the notification to NBI Adapters that have
registered with the Aggregator using the NBI Adapter\'s
**publishNotification** API.

The following is the interface that NBI Adapter\'s implement to receive
notifications:

```
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

package org.broadband_forum.obbaa.aggregator.api;

/**
 * Implemented by NB Adapter for notification process.
 */
public interface NotificationProcessor {
    /**
     * Provide a unified API to PMA/DM for notification sending.
     *
     * @param notificationMessage Notification message
     */
    void publishNotification(String notificationMessage) throws DispatchException;
}
```

YANG modules used by the Aggregator Component
=============================================

bbf-obbaa-network-manager
-------------------------

The network-manager YANG module is the top-level modules that represents
the BAA layer. The module is used for the administration of the BAA
layer as management and control of managed devices.

Each managed device is contained within the managed-devices list with
the device\'s data model represented as \"root\" mount-point for that
managed device in the list.

```

module bbf-obbaa-network-manager {
  yang-version 1.1;
  namespace "urn:bbf:yang:obbaa:network-manager";
  prefix network-manager;

  import ietf-inet-types {
    prefix inet;
  }
  import ietf-yang-types {
    prefix yang;
  }
  import ietf-yang-schema-mount {
    prefix yangmnt;
  }
  import ietf-yang-library {
    prefix yanglib;
    revision-date 2016-06-21;
  }

  organization
    "broadband_forum";
  contact
    "https://www.broadband-forum.org";
  description
    "YANG module supporting network management.";

  revision 2018-05-07 {
    description
      "Initial revision.";
    reference "broadband_forum";
  }

  grouping connection-grouping {
    leaf connection-model {
      type enumeration {
        enum call-home;
        enum direct;
      }
      description
        "whether the connection is call-home or direct.";
    }
    choice protocol {
      mandatory true;
      container password-auth {
        when "../connection-model = 'direct'";
        container authentication {
          leaf address {
            type inet:ip-address;
            description
              "Device IP address.";
            mandatory true;
          }
          leaf management-port {
            type uint32;
            description
              "The management port of the device.";
          }
          leaf user-name {
            type string;
            description
              "The user name of the device.";
          }
          leaf password {
            type string;
            description
              "The password of the user.";
          }
        }
      }
      leaf duid {
        description
          "A globally unique value for a DUID (DHCP Unique Identifier)
           as defined in RFC 3315.";
        type string {
          length "1..128";
        }
        when "../connection-model = 'call-home'";
      }
    }
  }

  grouping device-details{
    description
      "These four leafs collectively determine one module-set/one adapter.";
    leaf type {
      type string;
      description
        "The type of device. Identifies the type of access node like OLT/ONT/DPU etc";
    }
    leaf interface-version {
      type string;
      description
        "The interface version of the device , which uniquely identifies the yang-modules set & revision supported by the device";
    }
    leaf model {
      type string;
      description
        "The model of device. Identifies the hardware variant of the device.
         Example 4LT/8LT card numbers etc";
    }
    leaf vendor {
      type string;
      description
        "The vendor of device.Eg Nokia/Huawei";
    }
    leaf push-pma-configuration-to-device {
       type boolean;
       default true;
       description
         "By default, push the PMA configuration to the device when the device connects for the first time. This is done since PMA in OB-BAA is the master of configurations. Configure this attribute as false using <edit-config> to turn-off this feature and upload device configuration to PMA.
          When this attribute is set to false, it will be automatically reset to true after the device configuration is successfully uploaded to PMA.";
    }
  }

  grouping management-grouping {
    uses device-details;
    container device-connection {
      uses connection-grouping;
    }
    container device-state {
      config false;
      leaf configuration-alignment-state {
        type string;
      }
      container connection-state {
        leaf connected {
          type boolean;
          description
            "The connection state of device.";
        }
        leaf connection-creation-time {
          type yang:date-and-time;
        }
        leaf-list device-capability {
          type string;
        }
      }
    }
  }

  grouping notification-grouping {
    notification device-state-change {
      description
        "Device state changed";
      leaf event {
        type enumeration {
          enum online;
          enum offline;
        }
      }
    }
  }
  container network-manager {

      container managed-devices {
        description
          "The managed devices and device communication settings.";
        list device {
          key "name";
          leaf name {
            type string;
            description
              "The name of device.";
          }
          container device-management {
            uses management-grouping;
          }
          container device-notification {
            uses notification-grouping;
          }
          container root {
            yangmnt:mount-point "root";
            description
              "Root for models supported per device.";
          }
        }
      }

      container new-devices {
        config false;
        list new-device {
          key "duid";
          leaf duid {
            description
              "A globally unique value for a DUID (DHCP Unique Identifier)
               as defined in RFC 3315.";
            type string {
              length "1..128";
            }
          }
          leaf-list device-capability {
              type string;
          }
        }
      }

      container device-adapters {
        config false;
        leaf device-adapter-count {
          description
           "Total number of device-adapters deployed";
          type string;
          config false;
        }
        list device-adapter {
          key "type interface-version model vendor";
          description
           "List of device-adapters containing yang modules along with supported deviations and features.
            An device-adapter is uniquely identified by its type, version, model and vendor.";

          uses device-details;

          container yang-modules {
            description
            "The list yang modules supported by the device-adapter";
            uses yanglib:module-list;
          }
        }
      }
}
}

```

Note:
1.  The current version does not support device management and service
    configuration in the same message at the same time.

ietf-yang-schema-mount
----------------------

The managed device\'s data is maintained in the managed device container
using the \"root\" mount-point.

```
module ietf-yang-schema-mount {

    yang-version 1.1;

    namespace
      "urn:ietf:params:xml:ns:yang:ietf-yang-schema-mount";

    prefix yangmnt;

    import ietf-inet-types {
      prefix inet;
      reference
        "RFC 6991: Common YANG Data Types";


    }
    import ietf-yang-types {
      prefix yang;
      reference
        "RFC 6991: Common YANG Data Types";


    }
    import ietf-yang-library {
      prefix yanglib;
      reference
        "RFC 7895: YANG Module Library";


    }

    organization
      "IETF NETMOD (NETCONF Data Modeling Language) Working Group";

    contact
      "WG Web:   <https://tools.ietf.org/wg/netmod/>
     WG List:  <mailto:netmod@ietf.org>

     Editor:   Martin Bjorklund
               <mailto:mbj@tail-f.com>

     Editor:   Ladislav Lhotka
               <mailto:lhotka@nic.cz>";

    description
      "This module defines a YANG extension statement that can be used
     to incorporate data models defined in other YANG modules in a
     module. It also defines operational state data that specify the
     overall structure of the data model.

     Copyright (c) 2017 IETF Trust and the persons identified as
     authors of the code. All rights reserved.

     Redistribution and use in source and binary forms, with or
     without modification, is permitted pursuant to, and subject to
     the license terms contained in, the Simplified BSD License set
     forth in Section 4.c of the IETF Trust's Legal Provisions
     Relating to IETF Documents
     (https://trustee.ietf.org/license-info).

     The key words 'MUST', 'MUST NOT', 'REQUIRED', 'SHALL', 'SHALL
     NOT', 'SHOULD', 'SHOULD NOT', 'RECOMMENDED', 'MAY', and
     'OPTIONAL' in the module text are to be interpreted as described
     in RFC 2119 (https://tools.ietf.org/html/rfc2119).

     This version of this YANG module is part of RFC XXXX
     (https://tools.ietf.org/html/rfcXXXX); see the RFC itself for
     full legal notices.";

    revision "2017-10-09" {
      description "Initial revision.";
      reference
        "RFC XXXX: YANG Schema Mount";

    }


    extension mount-point {
      argument "label" {
        yin-element false;
      }
      description
        "The argument 'label' is a YANG identifier, i.e., it is of the
       type 'yang:yang-identifier'.

       The 'mount-point' statement MUST NOT be used in a YANG
       version 1 module, neither explicitly nor via a 'uses'
       statement.

       The 'mount-point' statement MAY be present as a substatement
       of 'container' and 'list', and MUST NOT be present elsewhere.
       There MUST NOT be more than one 'mount-point' statement in a
       given 'container' or 'list' statement.

       If a mount point is defined within a grouping, its label is
       bound to the module where the grouping is used.

       A mount point defines a place in the node hierarchy where
       other data models may be attached. A server that implements a
       module with a mount point populates the
       /schema-mounts/mount-point list with detailed information on
       which data models are mounted at each mount point.

       Note that the 'mount-point' statement does not define a new
       data node.";
    }

    grouping mount-point-list {
      description
        "This grouping is used inside the 'schema-mounts' container and
       inside the 'schema' list.";
      list mount-point {
        key "module label";
        description
          "Each entry of this list specifies a schema for a particular
         mount point.

         Each mount point MUST be defined using the 'mount-point'
         extension in one of the modules listed in the corresponding
         YANG library instance with conformance type 'implement'. The
         corresponding YANG library instance is:

         - standard YANG library state data as defined in RFC 7895,
           if the 'mount-point' list is a child of 'schema-mounts',

         - the contents of the sibling 'yanglib:modules-state'
           container, if the 'mount-point' list is a child of
           'schema'.";
        leaf module {
          type yang:yang-identifier;
          description
            "Name of a module containing the mount point.";
        }

        leaf label {
          type yang:yang-identifier;
          description
            "Label of the mount point defined using the 'mount-point'
           extension.";
        }

        leaf config {
          type boolean;
          default "true";
          description
            "If this leaf is set to 'false', then all data nodes in the
           mounted schema are read-only (config false), regardless of
           their 'config' property.";
        }

        choice schema-ref {
          mandatory true;
          description
            "Alternatives for specifying the schema.";
          leaf inline {
            type empty;
            description
              "This leaf indicates that the server has mounted
             'ietf-yang-library' and 'ietf-schema-mount' at the mount
             point, and their instantiation (i.e., state data
             containers 'yanglib:modules-state' and 'schema-mounts')
             provides the information about the mounted schema.";
          }
          list use-schema {
            key "name";
            min-elements 1;
            description
              "Each entry of this list contains a reference to a schema
             defined in the /schema-mounts/schema list.";
            leaf name {
              type leafref {
                path
                  "/schema-mounts/schema/name";
              }
              description
                "Name of the referenced schema.";
            }

            leaf-list parent-reference {
              type yang:xpath1.0;
              description
                "Entries of this leaf-list are XPath 1.0 expressions
               that are evaluated in the following context:

               - The context node is the node in the parent data tree
                 where the mount-point is defined.

               - The accessible tree is the parent data tree
                 *without* any nodes defined in modules that are
                 mounted inside the parent schema.

               - The context position and context size are both equal
                 to 1.

               - The set of variable bindings is empty.

               - The function library is the core function library
                 defined in [XPath] and the functions defined in
                 Section 10 of [RFC7950].

               - The set of namespace declarations is defined by the
                 'namespace' list under 'schema-mounts'.

               Each XPath expression MUST evaluate to a nodeset
               (possibly empty). For the purposes of evaluating XPath
               expressions whose context nodes are defined in the
               mounted schema, the union of all these nodesets
               together with ancestor nodes are added to the
               accessible data tree.";
            }
          }  // list use-schema
        }  // choice schema-ref
      }  // list mount-point
    }  // grouping mount-point-list

    container schema-mounts {
      config false;
      description
        "Contains information about the structure of the overall
       mounted data model implemented in the server.";
      list namespace {
        key "prefix";
        description
          "This list provides a mapping of namespace prefixes that are
         used in XPath expressions of 'parent-reference' leafs to the
         corresponding namespace URI references.";
        leaf prefix {
          type yang:yang-identifier;
          description "Namespace prefix.";
        }

        leaf uri {
          type inet:uri;
          description
            "Namespace URI reference.";
        }
      }  // list namespace

      uses mount-point-list;

      list schema {
        key "name";
        description
          "Each entry specifies a schema that can be mounted at a mount
         point.  The schema information consists of two parts:

         - an instance of YANG library that defines YANG modules used
           in the schema,

         - mount-point list with content identical to the top-level
           mount-point list (this makes the schema structure
           recursive).";
        leaf name {
          type string;
          description
            "Arbitrary name of the schema entry.";
        }

        uses yanglib:module-list;

        uses mount-point-list;
      }  // list schema
    }  // container schema-mounts
  }  // module ietf-yang-schema-mount
```

ietf-yang-library
-----------------

module ietf-yang-library {
     namespace "urn:ietf:params:xml:ns:yang:ietf-yang-library";
     prefix "yanglib";

     import ietf-yang-types {
       prefix yang;
     }
     import ietf-inet-types {
       prefix inet;
     }

     organization
       "IETF NETCONF (Network Configuration) Working Group";

     contact
       "WG Web:   <https://datatracker.ietf.org/wg/netconf/>
        WG List:  <mailto:netconf@ietf.org>

        WG Chair: Mehmet Ersue
                  <mailto:mehmet.ersue@nsn.com>

        WG Chair: Mahesh Jethanandani
                  <mailto:mjethanandani@gmail.com>

        Editor:   Andy Bierman
                  <mailto:andy@yumaworks.com>

        Editor:   Martin Bjorklund
                  <mailto:mbj@tail-f.com>

        Editor:   Kent Watsen
                  <mailto:kwatsen@juniper.net>";

     description
       "This module contains monitoring information about the YANG
        modules and submodules that are used within a YANG-based
        server.

        Copyright (c) 2016 IETF Trust and the persons identified as
        authors of the code.  All rights reserved.

        Redistribution and use in source and binary forms, with or
        without modification, is permitted pursuant to, and subject
        to the license terms contained in, the Simplified BSD License
        set forth in Section 4.c of the IETF Trust's Legal Provisions
        Relating to IETF Documents
        (http://trustee.ietf.org/license-info).

        This version of this YANG module is part of RFC 7895; see
        the RFC itself for full legal notices.";

     revision 2016-06-21 {
       description
         "Initial revision.";
       reference
         "RFC 7895: YANG Module Library.";
     }

     /*
      * Typedefs
      */

     typedef revision-identifier {
       type string {
         pattern '\d{4}-\d{2}-\d{2}';
       }
       description
         "Represents a specific date in YYYY-MM-DD format.";
     }

     /*
      * Groupings
      */

     grouping module-list {
       description
         "The module data structure is represented as a grouping
          so it can be reused in configuration or another monitoring
          data structure.";

       grouping common-leafs {
         description
           "Common parameters for YANG modules and submodules.";

         leaf name {
           type yang:yang-identifier;
           description
             "The YANG module or submodule name.";
         }
         leaf revision {
           type union {
             type revision-identifier;
             type string { length 0; }
           }
           description
             "The YANG module or submodule revision date.
              A zero-length string is used if no revision statement
              is present in the YANG module or submodule.";
         }
       }

       grouping schema-leaf {
         description
           "Common schema leaf parameter for modules and submodules.";

         leaf schema {
           type inet:uri;
           description
             "Contains a URL that represents the YANG schema
              resource for this module or submodule.

              This leaf will only be present if there is a URL
              available for retrieval of the schema for this entry.";
         }
       }

       list module {
         key "name revision";
         description
           "Each entry represents one revision of one module
            currently supported by the server.";

         uses common-leafs;
         uses schema-leaf;

         leaf namespace {
           type inet:uri;
           mandatory true;
           description
             "The XML namespace identifier for this module.";
         }
         leaf-list feature {
           type yang:yang-identifier;
           description
             "List of YANG feature names from this module that are
              supported by the server, regardless of whether they are
              defined in the module or any included submodule.";
         }
         list deviation {
           key "name revision";
           description
             "List of YANG deviation module names and revisions
              used by this server to modify the conformance of
              the module associated with this entry.  Note that
              the same module can be used for deviations for
              multiple modules, so the same entry MAY appear
              within multiple 'module' entries.

              The deviation module MUST be present in the 'module'
              list, with the same name and revision values.
              The 'conformance-type' value will be 'implement' for
              the deviation module.";
           uses common-leafs;
         }
         leaf conformance-type {
           type enumeration {
             enum implement {
               description
                 "Indicates that the server implements one or more
                  protocol-accessible objects defined in the YANG module
                  identified in this entry.  This includes deviation
                  statements defined in the module.

                  For YANG version 1.1 modules, there is at most one
                  module entry with conformance type 'implement' for a
                  particular module name, since YANG 1.1 requires that,
                  at most, one revision of a module is implemented.

                  For YANG version 1 modules, there SHOULD NOT be more
                  than one module entry for a particular module name.";
             }
             enum import {
               description
                 "Indicates that the server imports reusable definitions
                  from the specified revision of the module but does
                  not implement any protocol-accessible objects from
                  this revision.

                  Multiple module entries for the same module name MAY
                  exist.  This can occur if multiple modules import the
                  same module but specify different revision dates in
                  the import statements.";
             }
           }
           mandatory true;
           description
             "Indicates the type of conformance the server is claiming
              for the YANG module identified by this entry.";
         }
         list submodule {
           key "name revision";
           description
             "Each entry represents one submodule within the
              parent module.";
           uses common-leafs;
           uses schema-leaf;
         }
       }
     }

     /*
      * Operational state data nodes
      */

     container modules-state {
       config false;
       description
         "Contains YANG module monitoring information.";

       leaf module-set-id {
         type string;
         mandatory true;
         description
           "Contains a server-specific identifier representing
            the current set of modules and submodules.  The
            server MUST change the value of this leaf if the
            information represented by the 'module' list instances
            has changed.";
       }

       uses module-list;
     }

     /*
      * Notifications
      */

     notification yang-library-change {
       description
         "Generated when the set of modules and submodules supported
          by the server has changed.";
       leaf module-set-id {
         type leafref {
           path "/yanglib:modules-state/yanglib:module-set-id";
         }
         mandatory true;
         description
           "Contains the module-set-id value representing the
            set of modules and submodules supported at the server at
            the time the notification is generated.";
       }
     }
}
```

Component Diagram
=================

Peripheral components
---------------------

The following is the component diagram with the component that interact
with the Aggregator component.

<p align="center">
 <img width="400px" height="400px" src="{{site.url}}/aggregator/aggregator_component.png">
</p>

Class diagram
-------------

The following is the class diagram for classes relevant (implemented or
uses) to the Aggregator component.

<p align="center">
 <img width="800px" height="800px" src="{{site.url}}/aggregator/aggregator_class.png">
</p>

Sequence diagram
----------------

The following is a sequence diagram that describes the important
interactions of the Aggregator component.

<p align="center">
 <img width="1000px" height="700px" src="{{site.url}}/aggregator/aggregator_seq.png">
</p>

Adaptation of request processors
================================

This section provides guidelines for how to implement request processor
used by the Aggregator component.

Device Management Adapter
-------------------------

The adapter is used for dispatching requests to the DM component using
the \'bbf-obbaa-network-manager\' YANG module.

Code example:

```
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

import java.util.HashMap;
import java.util.Map;

import org.broadband_forum.obbaa.aggregator.api.Aggregator;
import org.broadband_forum.obbaa.aggregator.api.DeviceManagementProcessor;
import org.broadband_forum.obbaa.aggregator.api.DispatchException;
import org.broadband_forum.obbaa.netconf.api.client.NetconfClientInfo;
import org.broadband_forum.obbaa.netconf.api.messages.DocumentToPojoTransformer;
import org.broadband_forum.obbaa.netconf.api.messages.NetConfResponse;
import org.broadband_forum.obbaa.netconf.api.util.NetconfMessageBuilderException;
import org.broadband_forum.obbaa.netconf.api.util.NetconfResources;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.NetconfServer;
import org.w3c.dom.Document;

public class DeviceManagerAdapter implements DeviceManagementProcessor {
    Aggregator m_aggregator;
    NetconfServer m_dmNetconfServer;
    Map<String, DeviceAdapterInfo> m_deviceTypeMap;

    public DeviceManagerAdapter(Aggregator aggregator) {
        m_aggregator = aggregator;
        m_deviceTypeMap = new HashMap<>();
    }

    public void init() {
        m_aggregator.registerDeviceManager(this);
    }

    public void destroy() {
        m_aggregator.unregisterDeviceManager();
    }

    public NetconfServer getDmNetconfServer() {
        return m_dmNetconfServer;
    }

    public void setDmNetconfServer(NetconfServer dmNetconfServer) {
        m_dmNetconfServer = dmNetconfServer;
    }

    @Override
    public String processRequest(NetconfClientInfo clientInfo, String netconfRequest) throws DispatchException {
        Document document = AggregatorMessage.stringToDocument(netconfRequest);
        String messageId = NetconfMessageUtil.getMessageIdFromRpcDocument(document);
        NetConfResponse response = deviceManagement(clientInfo, document);
        response.setMessageId(messageId);

        //Device name is invalid in this processor
        return response.responseToString();
    }

    private NetConfResponse deviceManagement(NetconfClientInfo netconfClientInfo, Document document) throws DispatchException {
        String typeOfNetconfRequest = NetconfMessageUtil.getTypeOfNetconfRequest(document);
        NetConfResponse response = new NetConfResponse();

        try {
            switch (typeOfNetconfRequest) {
                case NetconfResources.DELETE_CONFIG:
                    m_dmNetconfServer.onDeleteConfig(netconfClientInfo, DocumentToPojoTransformer.getDeleteConfig(document), response);
                    break;

                case NetconfResources.EDIT_CONFIG:
                    m_dmNetconfServer.onEditConfig(netconfClientInfo, DocumentToPojoTransformer.getEditConfig(document), response);
                    break;

                case NetconfResources.GET:
                    m_dmNetconfServer.onGet(netconfClientInfo, DocumentToPojoTransformer.getGet(document), response);
                    break;

                case NetconfResources.GET_CONFIG:
                    m_dmNetconfServer.onGetConfig(netconfClientInfo, DocumentToPojoTransformer.getGetConfig(document), response);
                    break;

                default:
                    // Does not support
                    throw new DispatchException("Does not support the operation.");
            }
        } catch (NetconfMessageBuilderException ex) {
            throw new DispatchException(ex);
        }
        return response;
    }

    public void removeDeviceAdptInfo(String deviceName) {
        m_deviceTypeMap.remove(deviceName);
    }

    public void updateDeviceAdptInfo(String deviceName, DeviceAdapterInfo deviceAdapterInfo) {
        if (m_deviceTypeMap.get(deviceName) == null) {
            m_deviceTypeMap.put(deviceName, deviceAdapterInfo);
            return;
        }

        m_deviceTypeMap.replace(deviceName, deviceAdapterInfo);
    }

    private DeviceAdapterInfo getDeviceAdptInfo(String deviceName) {
        try {
            return m_deviceTypeMap.get(deviceName);
        } catch (NullPointerException | ClassCastException ex) {
            return new DeviceAdapterInfo();
        }
    }

    @Override
    public String getDeviceTypeByDeviceName(String deviceName) {
        DeviceAdapterInfo deviceAdapterInfo = getDeviceAdptInfo(deviceName);
        if (deviceAdapterInfo == null) {
            return null;
        }

        return deviceAdapterInfo.getType();
    }
}
```

PMA Adapter
-----------

The adapter is used for dispatching requests to the PMA component using
the schema-mount point \'root\' from the \"bbf-obbaa-network-manager\"
YANG module.

PMA adapter implements the follow functions:

1.  Registering the capability (based on the YANG model of device
    configuration) of the PMA component to the Aggregator

2.  Forwarding the request from the Aggregator to the PMA component

3.  Exposing functionality to manipulate the YANG library and managing
    the state of a managed device. The interface includes:

    a.  Deploy device-adapter

    b.  Undeploy device-adapter

    c.  Force align the NETCONF configuration of a device

    d.  Query the align-state of one device

4.  Send notification of YANG library change

Based on these features, it is recommended to implement the following
YANG modules:

```
module bbf-obbaa-pma-yang-library {
    yang-version 1.1;
    namespace "urn:bbf:yang:obbaa:pma-yang-library";
    prefix pma-yang-library;
    
    import ietf-inet-types {
        prefix inet;
    }
   
    organization
        "broadband_forum";

    contact
        "https://www.broadband-forum.org";

    description
        "PMA YANG library management.";

    revision 2018-06-15 {
        description
            "Initial revision.";
        reference
            "broadband_forum";
    }
    
    container pma-yang-library {
        description "YANG library of PMA.";
        
        action reload {
            description "Reload the YANG library of PMA.";
        }
    }
}
```

YANG module for the device adapters:

```
module bbf-obbaa-device-adapters {
  yang-version 1.1;
  namespace "urn:bbf:yang:obbaa:device-adapters";
  prefix device-adapters;

  organization
     "broadband_forum";
   contact
     "https://www.broadband-forum.org";
   description
     "YANG module supporting device adapters for OB-BAA.";

  revision 2018-08-31 {
   description
     "Initial revision.";
   reference "broadband_forum";
  }

  container deploy-adapter {
     action deploy {
         input {
             leaf adapter-archive {
                 type string;
             }
         }
     }
  }

  container undeploy-adapter {
       action undeploy {
           input {
               leaf adapter-archive {
                   type string;
               }
           }
       }
    }
}
``` 

The PMA adapter installs the following YANG module to the
network-manager in order to force the alignment of a managed device.

```

module bbf-obbaa-pma-device-config {
    yang-version 1.1;
    namespace "urn:bbf:yang:obbaa:pma-device-config";
    prefix pma-align;
    
    import ietf-inet-types {
        prefix inet;
    }

    organization
        "broadband_forum";

    contact
        "https://www.broadband-forum.org";

    description
        "Align the configuration of device.";

    revision 2018-06-15 {
        description
            "Initial revision.";
        reference
            "broadband_forum";
    }
    
    identity align-type {
        description "Type of align requested of the component.";
    }

    container pma-device-config {
        description "Device configuration of PMA.";
        
        action align {
            description "Align device configuration of PMA.";
        
            input {
                leaf force {
                    type identityref {
                        base align-type;
                    }
                    description "Force align the device configuration.";
                }
            }
        }
    
        leaf alignment-state {
            type enumeration {
                enum aligned;
                enum mis-aligned;
                enum in-error;
            }
            config false;
            description "The leaf indicates whether the device configuration is aligned with the configuration of its PMA";
        }
    }
}
```

Code example:

```
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
```

[<--Architecture](../architecture)
