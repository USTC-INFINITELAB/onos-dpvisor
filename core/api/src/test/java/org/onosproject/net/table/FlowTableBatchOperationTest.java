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

import java.util.LinkedList;

import org.junit.Test;
import org.onosproject.net.intent.IntentTestsMocks;

import com.google.common.testing.EqualsTester;

/**
 * Unit tests for flow rule batch classes.
 */
public class FlowTableBatchOperationTest {

    /**
     * Tests the equals(), hashCode() and toString() methods.
     */
    @Test
    public void testEquals() {
        final FlowTable rule = new IntentTestsMocks.MockFlowTable(1);
        final FlowTableBatchEntry entry1 = new FlowTableBatchEntry(
                FlowTableBatchEntry.FlowTableOperation.ADD, rule);
        final FlowTableBatchEntry entry2 = new FlowTableBatchEntry(
                FlowTableBatchEntry.FlowTableOperation.MODIFY, rule);
        final FlowTableBatchEntry entry3 = new FlowTableBatchEntry(
                FlowTableBatchEntry.FlowTableOperation.REMOVE, rule);
        final LinkedList<FlowTableBatchEntry> ops1 = new LinkedList<>();
        ops1.add(entry1);
        final LinkedList<FlowTableBatchEntry> ops2 = new LinkedList<>();
        ops1.add(entry2);
        final LinkedList<FlowTableBatchEntry> ops3 = new LinkedList<>();
        ops3.add(entry3);

        final FlowTableBatchOperation operation1 = new FlowTableBatchOperation(ops1, null, 0);
        final FlowTableBatchOperation sameAsOperation1 = new FlowTableBatchOperation(ops1, null, 0);
        final FlowTableBatchOperation operation2 = new FlowTableBatchOperation(ops2, null, 0);
        final FlowTableBatchOperation operation3 = new FlowTableBatchOperation(ops3, null, 0);

        new EqualsTester()
                .addEqualityGroup(operation1, sameAsOperation1)
                .addEqualityGroup(operation2)
                .addEqualityGroup(operation3)
                .testEquals();
    }
}
