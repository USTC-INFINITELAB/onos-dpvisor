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

package org.onosproject.net.table;

import org.onosproject.net.driver.HandlerBehaviour;

import java.util.Collection;

/**
 * Flow rule programmable device behaviour.
 */
public interface FlowTableProgrammable extends HandlerBehaviour {

    /**
     * Retrieves the collection of flow table entries currently installed on the device.
     *
     * @return collection of flow tables
     */
    Collection<FlowTable> getFlowTables();

    /**
     * Applies the specified collection of flow tables to the device.
     *
     * @param tables flow tables to be added
     * @return collection of flow tables that were added successfully
     */
    Collection<FlowTable> applyFlowTables(Collection<FlowTable> tables);

    /**
     * Removes the specified collection of flow tables from the device.
     *
     * @param tables flow tables to be removed
     * @return collection of flow tables that were removed successfully
     */
    Collection<FlowTable> removeFlowtables(Collection<FlowTable> tables);

}
