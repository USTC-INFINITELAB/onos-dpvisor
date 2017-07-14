/*
 * Copyright 2015-present Open Networking Laboratory
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
package org.onosproject.store.serializers.custom;

import org.onosproject.floodlightpof.protocol.OFMatch;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.action.OFActionOutput;
import org.onosproject.floodlightpof.protocol.action.OFActionType;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionType;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableMod;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.table.DefaultFlowTableEntry;
import org.onosproject.net.table.DeviceOFTableType;
import org.onosproject.net.table.DeviceTableId;
import org.onosproject.net.flow.criteria.PofCriterion;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.flow.instructions.PofAction;
import org.onosproject.net.flow.instructions.PofInstruction;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableEntry;
import org.onosproject.net.table.FlowTableId;

import org.onosproject.store.impl.MastershipBasedTimestamp;
import org.onosproject.store.impl.Timestamped;
import org.onosproject.store.primitives.ConsistentMapBackedJavaMap;
import org.onosproject.store.service.WallClockTimestamp;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onlab.util.KryoNamespace;

public final class DistributedStoreSerializers {

    public static final int STORE_CUSTOM_BEGIN = KryoNamespaces.BEGIN_USER_CUSTOM_ID + 100;

    /**
     * KryoNamespace which can serialize ON.lab misc classes.
     */
    public static final KryoNamespace STORE_COMMON = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .nextId(KryoNamespaces.BEGIN_USER_CUSTOM_ID)
            .register(Timestamped.class)
            .register(new MastershipBasedTimestampSerializer(), MastershipBasedTimestamp.class)
            .register(WallClockTimestamp.class)
            .register(DefaultFlowTableEntry.class)
            .register(FlowTableBatchEntry.class)
            .register(FlowTableBatchEntry.FlowTableOperation.class)
            .register(FlowTableBatchOperation.class)
            .register(DefaultFlowTable.class)
            .register(OFFlowTable.class)
            .register(OFTableMod.OFTableModCmd.class)
            .register(OFMatch20.class)
            .register(OFTableType.class)
            .register(FlowTableId.class)
            .register(OFMatch.class)
            .register(DeviceOFTableType.class)
            .register(DeviceTableId.class)
            .register(FlowTableEntry.FlowTableState.class)
            .register(ConsistentMapBackedJavaMap.class)
            .build();

    // avoid instantiation
    private DistributedStoreSerializers() {}
}
