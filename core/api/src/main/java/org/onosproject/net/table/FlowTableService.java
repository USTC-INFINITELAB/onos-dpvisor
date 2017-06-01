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
import org.onosproject.event.ListenerService;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;

/**
 * Service for injecting flow tables into the environment and for obtaining
 * information about flow tables already in the environment. This implements
 * semantics of a distributed authoritative flow table where the master copy
 * of the flow tables lies with the controller and the devices hold only the
 * 'cached' copy.
 */
public interface FlowTableService
    extends ListenerService<FlowTableEvent, FlowTableListener> {

    /**
     * The topic used for obtaining globally unique ids.
     */
    String FLOW_OP_TOPIC = "flow-ops-ids";

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

    /**
     * Returns the number of flow tables in the system.
     *
     * @return flow table count
     */
    int getFlowTableCount();

    /**
     * Returns the collection of flow entries applied on the specified device.
     * This will include flow tables which may not yet have been applied to
     * the device.
     *
     * @param deviceId device identifier
     * @return collection of flow tables
     */
    Iterable<FlowTable> getFlowTables(DeviceId deviceId);

    // TODO: add createFlowTable factory method and execute operations method

    /**
     * Applies the specified flow tables onto their respective devices. These
     * flow tables will be retained by the system and re-applied anytime the
     * device reconnects to the controller.
     *
     * @param flowTables one or more flow tables
     */
    void applyFlowTables(FlowTable... flowTables);

    /**
     * Applies the specified flow table onto their respective device. This
     * flow table will be retained by the system and re-applied anytime the
     * device reconnects to the controller.
     *
     * @param deviceId the device id
     * @param ofTable the flow table
     * @param appId the application id
     */
    void applyTable(DeviceId deviceId, OFFlowTable ofTable, ApplicationId appId);

    /**
     * Removes the specified flow tables from their respective devices. If the
     * device is not presently connected to the controller, these flow will
     * be removed once the device reconnects.
     *
     * @param flowTables one or more flow tables
     * throws SomeKindOfException that indicates which ones were removed and
     *                  which ones failed
     */
    void removeFlowTables(FlowTable... flowTables);

    /**
     * Removes all tables by id.
     *
     * @param appId id to remove
     */
    void removeFlowTablesById(ApplicationId appId);

    /**
     * Removes all tables by device id and table id.
     *
     * @param deviceId the device id
     * @param tableId the table id
     */
    void removeFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId);

    /**
     * Removes all flow entrys in the specified flow table.
     *
     * @param deviceId the device id
     * @param globalTableId the table id of the specified flow table
     * @param entryId the entry id in the flow table
     */
    void removeFlowEntryByEntryId(DeviceId deviceId, int globalTableId, long entryId);

    /**
     * Returns a list of tables with this application id.
     *
     * @param id the id to look up
     * @return collection of flow tables
     */
    Iterable<FlowTable> getFlowTablesById(ApplicationId id);

    /**
     * Returns a flow table with this device id and table id.
     *
     * @param deviceId the device id
     * @param tableId the table id
     * @return the flow table
     */
    FlowTable getFlowTablesByTableId(DeviceId deviceId, FlowTableId tableId);

    /**
     * Applies a batch operation of FlowTables.
     *
     *   batch operation to apply
     */
    void apply(FlowTableOperations ops);
}
