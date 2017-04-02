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
package org.onosproject.pof.controller;

import org.onosproject.floodlightpof.protocol.OFMessage;

/**
 * Test adapter for the OpenFlow controller interface.
 */
public class PofControllerAdapter implements PofController {
    @Override
    public Iterable<PofSwitch> getSwitches() {
        return null;
    }

    @Override
    public Iterable<PofSwitch> getMasterSwitches() {
        return null;
    }

    @Override
    public Iterable<PofSwitch> getEqualSwitches() {
        return null;
    }

    @Override
    public PofSwitch getSwitch(Dpid dpid) {
        return null;
    }

    @Override
    public PofSwitch getMasterSwitch(Dpid dpid) {
        return null;
    }

    @Override
    public PofSwitch getEqualSwitch(Dpid dpid) {
        return null;
    }

    @Override
    public void monitorAllEvents(boolean monitor) {
    }

    @Override
    public void addListener(PofSwitchListener listener) {
    }

    @Override
    public void removeListener(PofSwitchListener listener) {
    }

    @Override
    public void addPacketListener(int priority, PacketListener listener) {
    }

    @Override
    public void removePacketListener(PacketListener listener) {
    }

    @Override
    public void write(Dpid dpid, OFMessage msg) {
    }

    @Override
    public void processPacket(Dpid dpid, OFMessage msg) {
    }

    @Override
    public void setRole(Dpid dpid, RoleState role) {
    }

    @Override
    public void addEventListener(PofEventListener listener) {
    }

    @Override
    public void removeEventListener(PofEventListener listener) {
    }
}
