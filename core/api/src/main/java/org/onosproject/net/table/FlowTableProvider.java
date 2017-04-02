/*
 * Copyright 2014-present Open Networking Laboratory
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
package org.onosproject.net.table;

import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.net.DeviceId;
import org.onosproject.net.provider.Provider;

/**
 * Abstraction of a flow table provider.
 */
public interface FlowTableProvider extends Provider {

    /**
     * Instructs the provider to apply the specified flow tables to their
     * respective devices.
     *
     * @param flowTables one or more flow tables
     *                  throws SomeKindOfException that indicates which ones were applied and
     *                  which ones failed
     */
    void applyFlowTable(FlowTable... flowTables);

    void applyTable(DeviceId deviceId, OFFlowTable ofTable);

    /**
     * Instructs the provider to remove the specified flow tables to their
     * respective devices.
     *
     * @param flowTables one or more flow tables
     *                  throws SomeKindOfException that indicates which ones were applied and
     *                  which ones failed
     */
    void removeFlowTable(FlowTable... flowTables);

    /**
     * Removes tables by their id.
     *
     * @param id        the id to remove
     * @param flowTables one or more flow tables
     * @deprecated since 1.5.0 Falcon
     */
    @Deprecated
    void removeTablesById(ApplicationId id, FlowTable... flowTables);

    /**
     * Installs a batch of flow tables. Each flowTable is associated to an
     * operation which results in either addition, removal or modification.
     *
     * @param batch a batch of flow tables
     */
    void executeBatch(FlowTableBatchOperation batch);

}
