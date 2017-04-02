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

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.onosproject.event.AbstractEventTest;
import org.onosproject.net.intent.IntentTestsMocks;

import com.google.common.testing.EqualsTester;

/**
 * Unit Tests for the FlowRuleEvent class.
 */
public class FlowTableEventTest extends AbstractEventTest {

    @Test
    public void testEquals() {


        final FlowTable flowTable1 = new IntentTestsMocks.MockFlowTable(1);
        final FlowTable flowTable2 = new IntentTestsMocks.MockFlowTable(2);
        final long time = 123L;
        final FlowTableEvent event1 =
                new FlowTableEvent(FlowTableEvent.Type.TABLE_ADDED, flowTable1, time);
        final FlowTableEvent sameAsEvent1 =
                new FlowTableEvent(FlowTableEvent.Type.TABLE_ADDED, flowTable1, time);
        final FlowTableEvent event2 =
                new FlowTableEvent(FlowTableEvent.Type.TABLE_ADD_REQUESTED,
                        flowTable2, time);

        // Equality for events is based on Object, these should all compare
        // as different.
        new EqualsTester()
                .addEqualityGroup(event1)
                .addEqualityGroup(sameAsEvent1)
                .addEqualityGroup(event2)
                .testEquals();
    }

    /**
     * Tests the constructor where a time is passed in.
     */
    @Test
    public void testTimeConstructor() {
        final long time = 123L;
        final FlowTable flowTable = new IntentTestsMocks.MockFlowTable(1);
        final FlowTableEvent event =
                new FlowTableEvent(FlowTableEvent.Type.TABLE_REMOVE_REQUESTED, flowTable, time);
        validateEvent(event, FlowTableEvent.Type.TABLE_REMOVE_REQUESTED, flowTable, time);
    }

    /**
     * Tests the constructor with the default time value.
     */
    @Test
    public void testConstructor() {
        final long time = System.currentTimeMillis();
        final FlowTable flowTable = new IntentTestsMocks.MockFlowTable(1);
        final FlowTableEvent event =
                new FlowTableEvent(FlowTableEvent.Type.TABLE_UPDATED, flowTable);
        validateEvent(event, FlowTableEvent.Type.TABLE_UPDATED, flowTable, time,
                time + TimeUnit.SECONDS.toMillis(30));
    }
}
