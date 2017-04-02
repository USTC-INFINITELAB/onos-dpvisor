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

/**
 * Represents a generalized match &amp; action pair to be applied to an
 * infrastructure device.
 */
public interface FlowTable {

    /**
     * Returns the ID of this flow table.
     *
     * @return the table ID
     */
    FlowTableId id();

    /**
     * Returns the application id of this flow table.
     *
     * @return an applicationId
     */
    short appId();

    /**
     * Returns the identity of the device where this flow table applies.
     *
     * @return device identifier
     */
    DeviceId deviceId();

    /**
     * Return the flow table.
     *
     * @return the flow table
     */
    OFFlowTable flowTable();

    /**
     * {@inheritDoc}
     *
     * Equality for flow tables only considers 'match equality'. This means that
     * two flow tables with the same match conditions will be equal, regardless
     * of the treatment or other characteristics of the flow table.
     *
     * @param   obj   the reference object with which to compare.
     * @return  {@code true} if this object is the same as the obj
     *          argument; {@code false} otherwise.
     */
    boolean equals(Object obj);

    /**
     * Returns whether this flow table is an exact match to the flow table given
     * in the argument.
     * <p>
     * Exact match means that deviceId, priority, selector,
     * tableId, flowId and treatment are equal. Note that this differs from
     * the notion of object equality for flow tables, which does not consider the
     * flowId or treatment when testing equality.
     * </p>
     *
     * @param table other table to match against
     * @return true if the tables are an exact match, otherwise false
     */
    boolean exactMatch(FlowTable table);

    /**
     * A flowtable builder.
     */
    interface Builder {

        /**
         * Assigns a cookie value to this flowtable. Mutually exclusive with the
         * fromApp method. This method is intended to take a cookie value from
         * the dataplane and not from the application.
         *
         * @param cookie a long value
         * @return this
         */
        Builder withCookie(long cookie);

        /**
         * Assigns the application that built this flow table to this object.
         * The short value of the appId will be used as a basis for the
         * cookie value computation. It is expected that application use this
         * call to set their application id.
         *
         * @param appId an application id
         * @return this
         */
        Builder fromApp(ApplicationId appId);

        /**
         * Sets the deviceId for this flow table.
         *
         * @param deviceId a device id
         * @return this
         */
        Builder forDevice(DeviceId deviceId);

        /**
         * Sets the table id for this flow table. Default value is 0.
         *
         * @param tableId an integer
         * @return this
         */
        Builder forTable(int tableId);

        /**
         * Define a flow table or a protocol type for flow entries.
         *
         * @param flowTable a Pof flow table
         * @return this
         */
        Builder withFlowTable(OFFlowTable flowTable);

        /**
         * Builds a flow table object.
         *
         * @return a flow table.
         */
        FlowTable build();

    }
}
