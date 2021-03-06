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

package org.broadband_forum.obbaa.device.adapter;

public interface AdapterSpecificConstants {

    String ADAPTER_XML_PATH = "/model/adapter.xml";
    String YANG_PATH = "/yang";

    String UNABLE_TO_GET_ADAPTER_DETAILS_TYPE = "Unable to get type, interface-version, model and vendor in specified "
            + "device adapter definition archive";
    String ADAPTER_DEFINITION_ARCHIVE_NOT_FOUND_ERROR = "Device adapter definition archive not found in the staging area";
    String DASH = "-";
}
