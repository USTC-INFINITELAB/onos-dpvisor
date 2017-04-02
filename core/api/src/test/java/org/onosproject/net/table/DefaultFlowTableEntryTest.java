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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.intent.IntentTestsMocks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.onosproject.net.NetTestTools.APP_ID;
import static org.onosproject.net.NetTestTools.did;

/**
 * Unit tests for the DefaultFlowTableEntry class.
 */
public class DefaultFlowTableEntryTest {


    private OFFlowTable ofFlowTable(byte smallTableId) {

        OFMatch20 ofMatch20 = new OFMatch20();
        ofMatch20.setFieldId((short) 1);
        ofMatch20.setFieldName("test");
        ofMatch20.setOffset((short) 0);
        ofMatch20.setLength((short) 48);

        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        match20List.add(ofMatch20);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(smallTableId);
        ofFlowTable.setTableName("FirstEntryTable");
        ofFlowTable.setTableSize(128);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setMatchFieldList(match20List);
        return ofFlowTable;
    }

    private  DefaultFlowTableEntry makeFlowEntry(int uniqueValue) {

        FlowTable rule = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable((byte) 1))
                .forTable(uniqueValue)
                .withCookie(uniqueValue)
                .forDevice(did("id" + Integer.toString(uniqueValue)))
                .fromApp(APP_ID)
                .build();

        return new DefaultFlowTableEntry(rule, FlowTableEntry.FlowTableState.ADDED,
                uniqueValue, uniqueValue, uniqueValue);
    }

    final DefaultFlowTableEntry defaultFlowTableEntry1 = makeFlowEntry(1);
    final DefaultFlowTableEntry sameAsDefaultFlowTableEntry1 = makeFlowEntry(1);
    final DefaultFlowTableEntry defaultFlowTableEntry2 = makeFlowEntry(2);



    /**
     * Tests the equals, hashCode and toString methods using Guava EqualsTester.
     */
//    @Test
//    public void testEquals() {
//        new EqualsTester()
//                .addEqualityGroup(defaultFlowTableEntry1, sameAsDefaultFlowTableEntry1)
//                .addEqualityGroup(defaultFlowTableEntry2)
//                .testEquals();
//    }

    /**
     * Tests the construction of a default flow entry from a device id.
     */
    @Test
    public void testDeviceBasedObject() {
        assertThat(defaultFlowTableEntry1.deviceId(), is(did("id1")));
        assertThat(defaultFlowTableEntry1.life(), is(1L));
        assertThat(defaultFlowTableEntry1.packets(), is(1L));
        assertThat(defaultFlowTableEntry1.bytes(), is(1L));
        assertThat(defaultFlowTableEntry1.state(), is(FlowTableEntry.FlowTableState.ADDED));
        assertThat(defaultFlowTableEntry1.lastSeen(),
                greaterThan(System.currentTimeMillis() -
                        TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)));
    }

    /**
     * Tests the setters on a default flow entry object.
     */
    @Test
    public void testSetters() {
        final DefaultFlowTableEntry entry = makeFlowEntry(1);

        entry.setLastSeen();
        entry.setState(FlowTableEntry.FlowTableState.PENDING_REMOVE);
        entry.setPackets(11);
        entry.setBytes(22);
        entry.setLife(33);

        assertThat(entry.deviceId(), is(did("id1")));
        assertThat(entry.life(), is(33L));
        assertThat(entry.packets(), is(11L));
        assertThat(entry.bytes(), is(22L));
        assertThat(entry.state(), is(FlowTableEntry.FlowTableState.PENDING_REMOVE));
        assertThat(entry.lastSeen(),
                greaterThan(System.currentTimeMillis() -
                        TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)));
    }

    /**
     * Tests a default flow rule built for an error.
     */
    @Test
    public void testErrorObject() {
        final DefaultFlowTableEntry errorEntry =
                new DefaultFlowTableEntry(defaultFlowTableEntry1,
                        111,
                        222);
        assertThat(errorEntry.errType(), is(111));
        assertThat(errorEntry.errCode(), is(222));
        assertThat(errorEntry.state(), is(FlowTableEntry.FlowTableState.FAILED));
        assertThat(errorEntry.lastSeen(),
                greaterThan(System.currentTimeMillis() -
                        TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)));
    }

    /**
     * Tests a default flow entry constructed from a flow rule.
     */
    @Test
    public void testFlowBasedObject() {
        final DefaultFlowTableEntry entry =
                new DefaultFlowTableEntry(new IntentTestsMocks.MockFlowTable(1));
        assertThat(entry.appId(), is((short) 0));
        assertThat(entry.lastSeen(),
                greaterThan(System.currentTimeMillis() -
                        TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)));
    }

    /**
     * Tests a default flow entry constructed from a flow rule plus extra
     * parameters.
     */
    @Test
    public void testFlowBasedObjectWithParameters() {
        final DefaultFlowTableEntry entry =
                new DefaultFlowTableEntry(defaultFlowTableEntry1,
                        FlowTableEntry.FlowTableState.REMOVED,
                        101, 102, 103);
        assertThat(entry.state(), is(FlowTableEntry.FlowTableState.REMOVED));
        assertThat(entry.life(), is(101L));
        assertThat(entry.packets(), is(102L));
        assertThat(entry.bytes(), is(103L));
        assertThat(entry.lastSeen(),
                greaterThan(System.currentTimeMillis() -
                        TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS)));
    }

    public class TestApplicationId extends DefaultApplicationId {
        public TestApplicationId(int id, String name) {
            super(id, name);
        }
    }
}
