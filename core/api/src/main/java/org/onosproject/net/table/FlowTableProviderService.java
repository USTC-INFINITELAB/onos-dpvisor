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

import org.onosproject.net.DeviceId;
import org.onosproject.net.provider.ProviderService;

/**
 * Service through which flow table providers can inject information into
 * the core.
 */
public interface FlowTableProviderService extends ProviderService<FlowTableProvider> {

    /**
     * Signals that a flow table that was previously installed has been removed.
     *
     * @param flowTable removed flow table
     */
    void flowTableRemoved(FlowTable flowTable);

    /**
     * Pushes the collection of flow tables currently applied on the given
     * device.
     *
     * @param deviceId device identifier
     * @param flowTables collection of flow tables
     */
    void pushTableMetrics(DeviceId deviceId, Iterable<FlowTableEntry> flowTables);

    /**
     * Pushes the collection of flow tables currently applied on the given
     * device without flowMissing process.
     *
     * @param deviceId device identifier
     * @param flowTables collection of flow tables
     */
    void pushTableMetricsWithoutFlowMissing(DeviceId deviceId, Iterable<FlowTableEntry> flowTables);

    /**
     * Indicates to the core that the requested batch operation has
     * been completed.
     *
     * @param batchId the batch which was processed
     * @param operation the resulting outcome of the operation
     */
    void batchOperationCompleted(long batchId, CompletedTableBatchOperation operation);
}
