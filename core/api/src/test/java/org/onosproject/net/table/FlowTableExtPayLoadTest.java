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
package org.onosproject.net.table;

import static org.onlab.junit.ImmutableClassChecker.assertThatClassIsImmutable;

import org.junit.Test;

import com.google.common.testing.EqualsTester;

/**
 * Test for FlowTableExtPayLoad.
 */
public class FlowTableExtPayLoadTest {
    final byte[] b = new byte[3];
    final byte[] b1 = new byte[5];
    final FlowTableExtPayLoad payLoad1 = FlowTableExtPayLoad.flowTableExtPayLoad(b);
    final FlowTableExtPayLoad sameAsPayLoad1 = FlowTableExtPayLoad.flowTableExtPayLoad(b);
    final FlowTableExtPayLoad payLoad2 = FlowTableExtPayLoad.flowTableExtPayLoad(b1);

    /**
     * Checks that the FlowTableExtPayLoad class is immutable.
     */
    @Test
    public void testImmutability() {
        assertThatClassIsImmutable(FlowTableExtPayLoad.class);
    }

    /**
     * Checks the operation of equals(), hashCode() and toString() methods.
     */
    @Test
    public void testEquals() {
        new EqualsTester()
                .addEqualityGroup(payLoad1, sameAsPayLoad1)
                .addEqualityGroup(payLoad2)
                .testEquals();
    }
}
