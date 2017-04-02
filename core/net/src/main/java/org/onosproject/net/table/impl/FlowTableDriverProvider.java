/*
 * Copyright 2016-present Open Networking Laboratory
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

package org.onosproject.net.table.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.table.CompletedTableBatchOperation;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableBatchEntry;
import org.onosproject.net.table.FlowTableBatchOperation;
import org.onosproject.net.table.FlowTableProgrammable;
import org.onosproject.net.table.FlowTableProvider;
import org.onosproject.net.table.FlowTableProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.ImmutableSet.copyOf;
import static org.onosproject.net.table.FlowTableBatchEntry.FlowTableOperation.*;
/**
 * Driver-based table rule provider.
 */
class FlowTableDriverProvider extends AbstractProvider implements FlowTableProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // Perhaps to be extracted for better reuse as we deal with other.
    public static final String SCHEME = "default";
    public static final String PROVIDER_NAME = "org.onosproject.provider";

    FlowTableProviderService providerService;
    private DeviceService deviceService;
    private MastershipService mastershipService;

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> poller = null;

    /**
     * Creates a new fallback flow rule provider.
     */
    FlowTableDriverProvider() {
        super(new ProviderId(SCHEME, PROVIDER_NAME));
    }

    /**
     * Initializes the provider with necessary supporting services.
     *
     * @param providerSerVice   flow rule provider service
     * @param deviceSerVice     device service
     * @param masterShipService mastership service
     * @param pollFrequency     flow entry poll frequency
     */
    void init(FlowTableProviderService providerSerVice,
              DeviceService deviceSerVice, MastershipService masterShipService,
              int pollFrequency) {
        this.providerService = providerSerVice;
        this.deviceService = deviceSerVice;
        this.mastershipService = masterShipService;

        if (poller != null && !poller.isCancelled()) {
            poller.cancel(false);
        }

        poller = executor.scheduleAtFixedRate(this::pollFlowEntries, pollFrequency,
                                              pollFrequency, TimeUnit.SECONDS);
    }

    @Override
    public void applyFlowTable(FlowTable... flowTables) {
        rulesByDevice(flowTables).asMap().forEach(this::applyFlowTables);
    }

    @Override
    public void applyTable(DeviceId deviceId, OFFlowTable ofTable) {
        log.info("+++++ FlowTableDriverProvider.applyTable");
        //do nothing
        //added by hdy
    }

    @Override
    public void removeFlowTable(FlowTable... flowTables) {
        rulesByDevice(flowTables).asMap().forEach(this::removeFlowTables);
    }

    @Override
    public void removeTablesById(ApplicationId id, FlowTable... flowTables) {
        removeFlowTable(flowTables);
    }

    @Override
    public void executeBatch(FlowTableBatchOperation batch) {
        ImmutableList.Builder<FlowTable> toAdd = ImmutableList.builder();
        ImmutableList.Builder<FlowTable> toRemove = ImmutableList.builder();
        for (FlowTableBatchEntry fbe : batch.getOperations()) {
            if (fbe.operator() == ADD || fbe.operator() == MODIFY) {
                toAdd.add(fbe.target());
            } else if (fbe.operator() == REMOVE) {
                toRemove.add(fbe.target());
            }
        }

        ImmutableList<FlowTable> rulesToAdd = toAdd.build();
        ImmutableList<FlowTable> rulesToRemove = toRemove.build();

        Collection<FlowTable> added = applyFlowTables(batch.deviceId(), rulesToAdd);
        Collection<FlowTable> removed = removeFlowTables(batch.deviceId(), rulesToRemove);

        Set<FlowTable> failedRules = Sets.union(Sets.difference(copyOf(rulesToAdd), copyOf(added)),
                                               Sets.difference(copyOf(rulesToRemove), copyOf(removed)));
        CompletedTableBatchOperation status =
                new CompletedTableBatchOperation(failedRules.isEmpty(), failedRules, batch.deviceId());
        providerService.batchOperationCompleted(batch.id(), status);
    }

    private Multimap<DeviceId, FlowTable> rulesByDevice(FlowTable[] flowTables) {
        // Sort the flow rules by device id
        Multimap<DeviceId, FlowTable> rulesByDevice = LinkedListMultimap.create();
        for (FlowTable rule : flowTables) {
            /*Device id*/
            rulesByDevice.put(DeviceId.deviceId("pof:1"), rule);
        }
        return rulesByDevice;
    }

    private Collection<FlowTable> applyFlowTables(DeviceId deviceId, Collection<FlowTable> flowTables) {
        FlowTableProgrammable programmer = getFlowTableProgrammable(deviceId);
        return programmer != null ? programmer.applyFlowTables(flowTables) : ImmutableList.of();
    }

    private Collection<FlowTable> removeFlowTables(DeviceId deviceId, Collection<FlowTable> flowTables) {
        FlowTableProgrammable programmer = getFlowTableProgrammable(deviceId);
        return programmer != null ? programmer.removeFlowtables(flowTables) : ImmutableList.of();
    }

    private FlowTableProgrammable getFlowTableProgrammable(DeviceId deviceId) {
        Device device = deviceService.getDevice(deviceId);
        if (device.is(FlowTableProgrammable.class)) {
            return device.as(FlowTableProgrammable.class);
        } else {
            log.debug("Device {} is not flow rule programmable", deviceId);
            return null;
        }
    }


    private void pollFlowEntries() {
        deviceService.getAvailableDevices().forEach(device -> {
            if (mastershipService.isLocalMaster(device.id()) && device.is(FlowTableProgrammable.class)) {
//                providerService.pushFlowMetrics(device.id(),
//                                                device.as(FlowTableProgrammable.class).getFlowTables());
            }
        });
    }

}
