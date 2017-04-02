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
package org.onosproject.pof.controller.driver;

import org.jboss.netty.buffer.ChannelBuffer;
import org.onosproject.floodlightpof.protocol.OFFlowMod;
import org.onosproject.floodlightpof.protocol.OFType;

/**
 * Mock of the Open Flow flow mod message.
 */
public class MockOfFlowMod extends OFFlowMod {

    byte version;
    OFType type;
    int xid;
    long cookie;
    long cookieMask;
    byte tableId;
    byte command;

    public MockOfFlowMod() {
        type = OFType.FLOW_MOD;
    }

    @Override
    public OFType getType() {
        return type;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    @Override
    public int getXid() {
        return xid;
    }

    @Override
    public void writeTo(ChannelBuffer channelBuffer) { }



    @Override
    public long getCookie() {
        return cookie;
    }

    @Override
    public long getCookieMask() throws UnsupportedOperationException {
        return cookieMask;
    }

    @Override
    public byte getTableId() throws UnsupportedOperationException {
        return tableId;
    }

    @Override
    public byte getCommand() {
        return command;
    }

    @Override
    public short getIdleTimeout() {
        return 0;
    }

    @Override
    public short getHardTimeout() {
        return 0;
    }

    @Override
    public short getPriority() {
        return 0;
    }


}
