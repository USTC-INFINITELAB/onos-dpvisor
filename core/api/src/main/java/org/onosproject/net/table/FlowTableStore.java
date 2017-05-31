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

import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.store.Store;

import java.util.List;
import java.util.Map;

/**
 * Manages inventory of flow tables; not intended for direct use.
 */
public interface FlowTableStore extends Store<FlowTableBatchEvent, FlowTableStoreDelegate> {

    /**
     * Returns the number of flow table in the store.
     *
     * @return number of flow tables
     */
    int getFlowTableCount();

    /**
     * Returns the stored flow table.
     *
     * @param table the table to look for
     * @return a flow table
     */
    FlowTable getFlowTable(FlowTable table);

    /**
     * Returns the stored flow table associated with a device and table id .
     *
     * @param deviceId the device id
     *  @param flowTableId the table id
     * @return a flow table
     */
    FlowTable getFlowTableInternal(DeviceId deviceId, FlowTableId flowTableId);

    /**
     * Returns the flow tables associated with a device.
     *
     * @param deviceId the device ID
     * @return the flow tables
     */
    Iterable<FlowTable> getFlowTables(DeviceId deviceId);

    /**
     * // TODO: Better description of method behavior.
     * Stores a new flow table without generating events.
     *
     * @param table the flow table to add
     * @deprecated in Cardinal Release
     */
    @Deprecated
    void storeFlowTable(FlowTable table);

    /**
     * Stores a batch of flow tables.
     *
     * @param operation batch of flow tables.
     *           A batch can contain flow tables for a single device only.
     *
     */
    void storeBatch(FlowTableBatchOperation operation);

    /**
     * Invoked on the completion of a storeBatch operation.
     *
     * @param event flow table batch event
     */
    void batchOperationComplete(FlowTableBatchEvent event);

    /**
     * Marks a flow table for deletion. Actual deletion will occur
     * when the provider indicates that the flow has been removed.
     *
     * @param table the flow table to delete
     */
    void deleteFlowTable(FlowTable table);

    /**
     * Stores a new flow table, or updates an existing table.
     *
     * @param table the flow table to add or update
     * @return flow_added event, or null if just an update
     */
    FlowTableEvent addOrUpdateFlowTable(FlowTable table);

    /**
     * @param table the flow table to remove
     * @return flow_removed event, or null if nothing removed
     */
    FlowTableEvent removeFlowTable(FlowTable table);

    /**
     * Marks a flow table as PENDING_ADD during retry.
     *
     * Emits flow_update event if the state is changed
     *
     * @param table the flow table that is retrying
     * @return flow_updated event, or null if nothing updated
     */
    FlowTableEvent pendingFlowTable(FlowTable table);

    /**
     * Removes all flow tables of given device from store.
     *
     * @param deviceId device id
     */
    void purgeFlowTable(DeviceId deviceId);

    /**
     * Removes switch associated with device id in store.
     *
     * @param deviceId device id
     */
    void removeSwitchStore(DeviceId deviceId);

    /**
     * Initializes switch store associated with device id.
     *
     * @param deviceId device id
     */
    void initializeSwitchStore(DeviceId deviceId);

    /**
     * Adds flow table resource.
     *
     * @param deviceId device id
     * @param of table resource information of switch
     */
    void setFlowTableNoBase(DeviceId deviceId, OFFlowTableResource of);

    Map<OFTableType, Byte> getFlowTableNoBaseMap(DeviceId deviceId);

    /**
     * Returns the table id of the specified flow table associated with
     * device id and flow table type.
     *
     * @param deviceId  the device id
     * @param type the flow table type
     * @return flow table id
     */
    int getNewGlobalFlowTableId(DeviceId deviceId, OFTableType type);

    /**
     * Returns the flow entry id in the specified flow table.
     *
     * @param deviceId the device id
     * @param tableId the table id
     * @return flow entry id
     */
    int getNewFlowEntryId(DeviceId deviceId, int tableId);

    Map<OFTableType, Byte> getFlowTableNoMap(DeviceId deviceId);

    Map<OFTableType, List<Byte>> getFreeFlowTableIDListMap(DeviceId deviceId);

    /**
     * Returns the number of flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     *
     * @return number of flow entrys
     */
    int getFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId);

    /**
     * Increases the number of flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     *
     * @return number of flow entrys
     */
    void addFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId);

    /**
     * Decreases the number of flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     */
    void deleteFlowEntryCount(DeviceId deviceId, FlowTableId flowTableId);


    List<Integer> getFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId);

    /**
     * Adds the entry id of a flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     * @param flowEntryId the entry id
     */
    void addFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId);

    /**
     * Deletes the entry id of a flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     * @param flowEntryId the entry id
     */
    void deleteFreeFlowEntryIds(DeviceId deviceId, FlowTableId flowTableId, Integer flowEntryId);

    /**
     * Returns the flow entry id and flow entry.
     * @param deviceId the device id
     * @param flowTableId the table id
     *
     * @return flow entry id and flow entry
     */
    Map<Integer, FlowRule> getFlowEntries(DeviceId deviceId, FlowTableId flowTableId);

    /**
     * Adds flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     * @param flowRule the entry id
     */
    void addFlowEntry(DeviceId deviceId, FlowTableId flowTableId, FlowRule flowRule);

    /**
     * Modifies flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     * @param flowRule the entry id
     */
    void modifyFlowEntry(DeviceId deviceId, FlowTableId flowTableId, FlowRule flowRule);

    /**
     * Deletes flow entry in the specified flow table.
     * @param deviceId the device id
     * @param flowTableId the table id
     * @param flowEntryId the entry id
     */
    void deleteFlowEntry(DeviceId deviceId, FlowTableId flowTableId, int flowEntryId);

    /**
     * Returns the small table id associated with the specified flow table.
     * @param deviceId the device id
     * @param globalTableId the global table id
     *
     * @return small table id
     */
    byte parseToSmallTableId(DeviceId deviceId, int globalTableId);

    /**
     * Returns the global table id in the specified device.
     * @param deviceId the device id
     * @param tableType the table type
     * @param smallTableId the table id in a particular type of pof table
     *
     * @return global table id
     */
    byte parseToGlobalTableId(DeviceId deviceId, OFTableType tableType, byte smallTableId);

}
