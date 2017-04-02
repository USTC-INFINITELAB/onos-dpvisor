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
package org.onosproject.provider.pof.flow.impl;

import org.onosproject.floodlightpof.protocol.OFFlowMod;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.table.FlowTableStore;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builder for OpenFlow flow mods based on FlowRules.
 */
public abstract class FlowModBuilder {

    private final Logger log = getLogger(getClass());

    //private final OFFactory factory;
    //wenjian
    private final BasicFactory factory;
    //wenjian
    private final FlowRule flowRule;
    private final TrafficSelector selector;
    protected final Long xid;
    protected final Optional<DriverService> driverService;
    protected final DeviceId deviceId;

    /**
     * Creates a new flow mod builder.
     *
     * @param flowRule the flow rule to transform into a flow mod
     * @param factory the OpenFlow factory to use to build the flow mod
     * @param xid the transaction ID
     * @param driverService the device driver service
     * @return the new flow mod builder
     */
    public static FlowModBuilder builder(FlowRule flowRule,
                                         BasicFactory factory,
                                         FlowTableStore simpleFlowTableStore,
                                         Optional<Long> xid,
                                         Optional<DriverService> driverService) {
        /*wenjian
        switch (factory.getVersion()) {
        case OF_10:
            return new FlowModBuilderVer10(flowRule, factory, xid, driverService);
        case OF_13:
            return new FlowModBuilderVer20(flowRule, factory, xid, driverService);
        default:
            throw new UnsupportedOperationException(
                    "No flow mod builder for protocol version " + factory.getVersion());
        }
        wenjian*/
        return new FlowModBuilderVer20(flowRule, factory, simpleFlowTableStore, xid, driverService);
    }

    /**
     * Constructs a flow mod builder.
     *
     * @param flowRule the flow rule to transform into a flow mod
     * @param factory the OpenFlow factory to use to build the flow mod
     * @param driverService the device driver service
     * @param xid the transaction ID
     */
    protected FlowModBuilder(FlowRule flowRule, BasicFactory factory, Optional<Long> xid,
                             Optional<DriverService> driverService) {
        this.factory = factory;
        this.flowRule = flowRule;
        this.selector = flowRule.selector();
        this.xid = xid.orElse(0L);
        this.driverService = driverService;
        this.deviceId = flowRule.deviceId();
    }

    /**
     * Builds an ADD flow mod.
     *
     * @return the flow mod
     */
    public abstract OFFlowMod buildFlowAdd();

    /**
     * Builds a MODIFY flow mod.
     *
     * @return the flow mod
     */
    public abstract OFFlowMod buildFlowMod();

    /**
     * Builds a DELETE flow mod.
     *
     * @return the flow mod
     */
    public abstract OFFlowMod buildFlowDel();

    /**
     * Returns the flow rule for this builder.
     *
     * @return the flow rule
     */
    protected FlowRule flowRule() {
        return flowRule;
    }

    /**
     * Returns the factory used for building OpenFlow constructs.
     *
     * @return the factory
     */
    protected BasicFactory factory() {
        return factory;
    }



}
