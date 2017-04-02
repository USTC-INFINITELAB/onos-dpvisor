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

import org.junit.Test;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRuleTest;
import org.onosproject.net.intent.IntentTestsMocks;

import com.google.common.testing.EqualsTester;
import org.slf4j.Logger;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.onlab.junit.ImmutableClassChecker.assertThatClassIsImmutableBaseClass;
import static org.onosproject.net.NetTestTools.APP_ID;
import static org.onosproject.net.NetTestTools.did;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Unit tests for the default flow rule class.
 */
public class DefaultFlowTableTest {
    private static final Logger log = getLogger(DefaultFlowRuleTest.class);

    private static byte[] b = new byte[3];
    private static DeviceId deviceId = DeviceId.deviceId("pof:001");
    private static long tableId = 1;

    final FlowTable flowTable1 = new IntentTestsMocks.MockFlowTable(tableId);
    final FlowTable sameAsFlowTable1 = new IntentTestsMocks.MockFlowTable(tableId);
    final DefaultFlowTable defaultFlowTable1 = new DefaultFlowTable(flowTable1);
    final DefaultFlowTable sameAsDefaultFlowTable1 = new DefaultFlowTable(sameAsFlowTable1);

    /**
     * Checks that the DefaultFlowTable class is immutable but can be inherited
     * from.
     */
    @Test
    public void testImmutability() {
        assertThatClassIsImmutableBaseClass(DefaultFlowTable.class);
    }

    /**
     * Tests the equals, hashCode and toString methods using Guava EqualsTester.
     */
    @Test
    public void testEquals() {
        log.info("defaultFlowTable1 :{}, sameAsDefaultFlowTable1 :{}", defaultFlowTable1
                .toString(), sameAsDefaultFlowTable1.toString());
        new EqualsTester()
                .addEqualityGroup(defaultFlowTable1, sameAsDefaultFlowTable1)
                .testEquals();
    }

    /**
     * Tests creation of a DefaultFlowTable using a FlowTable constructor.
     */
    @Test
    public void testCreationFromFlowTable() {
        assertThat(defaultFlowTable1.deviceId(), is(flowTable1.deviceId()));
        assertThat(defaultFlowTable1.appId(), is(flowTable1.appId()));
        assertThat(defaultFlowTable1.id(), is(flowTable1.id()));

    }

    /**
     * Tests creation of a DefaultFlowTable using a FlowId constructor.
     */

    @Test
    public void testCreationWithFlowId() {
        final FlowTable rule =
                DefaultFlowTable.builder()
                        .forDevice(did("1"))
                        .forTable(1)
                        .withFlowTable(ofFlowTable((byte) 1))
                        .withCookie((long) 1)
                        .fromApp(APP_ID)
                        .build();

        assertThat(rule.deviceId(), is(did("1")));
    }

    /**
     * Tests the creation of a DefaultFlowTable using an AppId constructor.
     */
    @Test
    public void testCreationWithAppId() {
        final FlowTable rule =
                DefaultFlowTable.builder()
                        .forDevice(did("1"))
                        .forTable(1)
                        .withFlowTable(ofFlowTable((byte) 1))
                        .withCookie((long) 1)
                        .fromApp(APP_ID)
                        .build();

        assertThat(rule.deviceId(), is(did("1")));
    }

    /**
     * Tests flow ID is consistent.
     */
    @Test
    public void testCreationWithConsistentFlowId() {
        final FlowTable rule1 =
                DefaultFlowTable.builder()
                        .forDevice(did("1"))
                        .forTable(1)
                        .withFlowTable(ofFlowTable((byte) 1))
                        .fromApp(APP_ID)
                        .build();

        final FlowTable rule2 =
                DefaultFlowTable.builder()
                        .forDevice(did("1"))
                        .forTable(1)
                        .withFlowTable(ofFlowTable((byte) 1))
                        .fromApp(APP_ID)
                        .build();

        new EqualsTester().addEqualityGroup(rule1.id(), rule2.id()).testEquals();
    }


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
}
