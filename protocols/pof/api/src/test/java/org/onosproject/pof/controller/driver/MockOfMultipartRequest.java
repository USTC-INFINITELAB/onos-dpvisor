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
import org.onosproject.floodlightpof.protocol.OFMultipartRequest;
import org.onosproject.floodlightpof.protocol.statistics.OFStatisticsType;
import org.onosproject.floodlightpof.protocol.OFType;

/**
 * Mock of the Open Flow stats request message.
 */
public class MockOfMultipartRequest extends OFMultipartRequest {

    byte version;
    OFType type = OFType.MULTIPART_REQUEST;
    int xid;
    short flags;

    public MockOfMultipartRequest() {
        type = OFType.MULTIPART_REQUEST;
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
    public OFStatisticsType getStatisticType() {
        return null;
    }

    @Override
    public short getFlags() {
        return flags;
    }


}
