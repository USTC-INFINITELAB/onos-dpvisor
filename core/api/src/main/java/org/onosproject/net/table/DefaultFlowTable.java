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

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.net.DeviceId;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultFlowTable implements FlowTable {

    private final DeviceId deviceId;
    private final OFFlowTable flowTable;
    private final long created;

    private final FlowTableId id;

    private final Short appId;

    /**
     * Creates a flow table.
     *
     * @param table the flow table
     */
    public DefaultFlowTable(FlowTable table) {
        this.deviceId = table.deviceId();
        this.flowTable = table.flowTable();
        this.appId = table.appId();
        this.id = table.id();
        this.created = System.currentTimeMillis();
    }

    /**
     * Creates a flow table.
     *
     * @param deviceId the device id
     * @param tableId the table id
     * @param flowTable the openflow table
     */
    private DefaultFlowTable(DeviceId deviceId, FlowTableId tableId, OFFlowTable flowTable) {

        this.deviceId = deviceId;
        this.flowTable = flowTable;

        this.appId = (short) (tableId.value() >>> 48);
        this.id = tableId;
        this.created = System.currentTimeMillis();

    }

    /**
     * Creates a flow table specified with the device id, table
     * id and application id.
     *
     * @param deviceId the device id
     * @param tableId the table id
     * @param appId the application id
     * @param flowTable the openflow table
     */
    private DefaultFlowTable(DeviceId deviceId, FlowTableId tableId, ApplicationId appId, OFFlowTable flowTable) {

        this.deviceId = deviceId;
        this.flowTable = flowTable;

        this.appId = appId.id();
        this.id = tableId;
        this.created = System.currentTimeMillis();

    }

    @Override
    public FlowTableId id() {
        return id;
    }

    @Override
    public short appId() {
        return appId;
    }


    @Override
    public DeviceId deviceId() {
        return deviceId;
    }

    @Override
    public OFFlowTable flowTable() {
        return flowTable;
    }

    @Override
    /*
     * The priority and statistics can change on a given treatment and selector
     *
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public int hashCode() {
        return Objects.hash(deviceId, flowTable, id);
    }

    //FIXME do we need this method in addition to hashCode()?
    private int hash() {
        return Objects.hash(deviceId, flowTable, id);
    }

    @Override
    /*
     * The priority and statistics can change on a given treatment and selector
     *
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DefaultFlowTable) {
            DefaultFlowTable that = (DefaultFlowTable) obj;
            return Objects.equals(deviceId, that.deviceId) &&
                    Objects.equals(flowTable, that.flowTable) &&
                    Objects.equals(id, that.id);
        }
        return false;
    }

    @Override
    public boolean exactMatch(FlowTable table) {
        return this.equals(table) &&
                Objects.equals(this.id, table.id());
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", Long.toHexString(id.value()))
                .add("deviceId", deviceId)
                .add("OFFlowTable", flowTable)
                .add("created", created)
                .toString();
    }


    @Beta
    public long created() {
        return created;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements FlowTable.Builder {

        private FlowTableId tableId;
        private ApplicationId appId;
        private DeviceId deviceId;
        private OFFlowTable flowTable = null;


        @Override
        public FlowTable.Builder withCookie(long cookie) {
            this.tableId = FlowTableId.valueOf(cookie);
            return this;
        }

        @Override
        public FlowTable.Builder fromApp(ApplicationId appID) {
            this.appId = appID;
            return this;
        }


        @Override
        public FlowTable.Builder forDevice(DeviceId deviceID) {
            this.deviceId = deviceID;
            return this;
        }

        @Override
        public FlowTable.Builder forTable(int tableID) {
            this.tableId = FlowTableId.valueOf(tableID);
            return this;
        }

        @Override
        public FlowTable.Builder withFlowTable(OFFlowTable flowTaBle) {
            this.flowTable = flowTaBle;
            return this;
        }

        @Override
        public FlowTable build() {
            FlowTableId localTableId;

            checkArgument((appId != null), "Either an application" +
                    " id or a cookie must be supplied");
            checkNotNull(tableId, "tableId cannot be null");

            checkNotNull(flowTable, "flowTable cannot be null");

            checkNotNull(deviceId, "Must refer to a device");

            return new DefaultFlowTable(deviceId, tableId, appId, flowTable);
        }


        private FlowTableId computeTableId(ApplicationId appID) {
            return FlowTableId.valueOf((((long) appID.id()) << 48)
                    | (hash() & 0xffffffffL));
        }

        private int hash() {
            Funnel<OFFlowTable> tableFunnel = (from, into) -> from.toString();

            HashFunction hashFunction = Hashing.murmur3_32();
            HashCode hashCode = hashFunction.newHasher()
                    .putString(deviceId.toString(), Charsets.UTF_8)
                    .putObject(flowTable, tableFunnel)
                    .putLong(tableId.value())
                    .hash();

            return hashCode.asInt();
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("table id", Long.toHexString(tableId.value()))
                    .add("deviceId", deviceId)
                    .add("ApplicationId", appId.id())
                    .add("OFFlowTable", flowTable)
                    .toString();
        }
    }

}
