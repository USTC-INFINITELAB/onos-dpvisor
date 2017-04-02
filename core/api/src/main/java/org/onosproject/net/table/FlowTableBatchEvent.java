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

import org.onosproject.event.AbstractEvent;
import org.onosproject.net.DeviceId;

@Deprecated
/**
 * Describes flow table batch event.
 *
 * @deprecated in Drake release - no longer a public API
 */
public final class FlowTableBatchEvent extends AbstractEvent<FlowTableBatchEvent.Type, FlowTableBatchRequest> {


    /**
     * Type of flow table events.
     */
    public enum Type {

        // Request has been forwarded to MASTER Node
        /**
         * Signifies that a batch operation has been initiated.
         */
        BATCH_OPERATION_REQUESTED,

        // MASTER Node has pushed the batch down to the Device
        // (e.g., Received barrier reply)
        /**
         * Signifies that a batch operation has completed.
         */
        BATCH_OPERATION_COMPLETED,
    }

    private final CompletedTableBatchOperation result;
    private final DeviceId deviceId;

    /**
     * Constructs a new FlowTableBatchEvent.
     *
     * @param request batch operation request
     * @param deviceId the device this batch will be processed on
     * @return event.
     */
    public static FlowTableBatchEvent requested(FlowTableBatchRequest request, DeviceId deviceId) {
        FlowTableBatchEvent event = new FlowTableBatchEvent(Type.BATCH_OPERATION_REQUESTED, request, deviceId);
        return event;
    }

    /**
     * Constructs a new FlowTableBatchEvent.
     * @param request batch operation request.
     * @param result completed batch operation result.
     * @return event.
     */
    public static FlowTableBatchEvent completed(FlowTableBatchRequest request, CompletedTableBatchOperation result) {
        FlowTableBatchEvent event = new FlowTableBatchEvent(Type.BATCH_OPERATION_COMPLETED, request, result);
        return event;
    }

    /**
     * Returns the result of this batch operation.
     * @return batch operation result.
     */
    public CompletedTableBatchOperation result() {
        return result;
    }

    /**
     * Returns the deviceId for this batch.
     * @return device id
     */
    public DeviceId deviceId() {
        return deviceId;
    }

    /**
     * Creates an event of a given type and for the specified flow TABLE batch.
     *
     * @param type    flow table batch event type
     * @param request event flow table batch subject
     * @param result  the result of the batch operation
     */
    private FlowTableBatchEvent(Type type, FlowTableBatchRequest request, CompletedTableBatchOperation result) {
        super(type, request);
        this.result = result;
        this.deviceId = result.deviceId();
    }

    /**
     * Creates an event of a given type and for the specified flow table batch.
     *
     * @param type      flow table batch event type
     * @param request   event flow table batch subject
     * @param deviceId  the device id for this batch
     */
    private FlowTableBatchEvent(Type type, FlowTableBatchRequest request, DeviceId deviceId) {
        super(type, request);
        this.result = null;
        this.deviceId = deviceId;
    }
}
