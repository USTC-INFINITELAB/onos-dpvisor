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
package org.onosproject.codec.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.table.DeviceOFTableType;
import org.onosproject.net.table.DefaultFlowTable;
import org.onosproject.net.table.FlowTable;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.net.table.FlowTableStore;

import java.util.ArrayList;
import java.util.stream.IntStream;
import static org.onlab.util.Tools.nullIsIllegal;

/**
 * Flow table JSON codec.
 */
public final class FlowTableCodec extends JsonCodec<FlowTable> {

    private static final String APP_ID = "appId";
    private static final String TABLE_SIZE = "tableSize";
    private static final String TABLE_NAME = "tableName";
    private static final String TABLE_TYPE = "tableType";
    private static final String FIELD_NAME = "fieldName";
    protected static final String FIELD_ID = "fieldId";
    protected static final String OFFSET = "offset";
    protected static final String LENGTH = "length";
    private static final String DEVICE_ID = "deviceId";
    private static final String SELECTOR = "selector";
    private static final String CRITERIA = "criteria";
    private static final String MISSING_MEMBER_MESSAGE =
            " member is required in FlowRule";
    public static final String REST_APP_ID = "org.onosproject.rest";

    public static class TableStoreResource extends AbstractWebResource {
        public FlowTableStore getTableStore() {
            final FlowTableStore tableStore = get(FlowTableStore.class);
            return tableStore;
        }

    }

    private static final TableStoreResource tableStore = new TableStoreResource();


    @Override
    public FlowTable decode(ObjectNode json, CodecContext context) {
        if (json == null || !json.isObject()) {
            return null;
        }

        FlowTable.Builder resultBuilder = new DefaultFlowTable.Builder();
        OFFlowTable ofFlowTable = new OFFlowTable();
        DeviceId deviceId = DeviceId.deviceId(json.get(DEVICE_ID).asText());

        FlowTableTypeCodec tableTypeCodec = new FlowTableTypeCodec(json
                .get(TABLE_TYPE).asText());
        OFTableType tableType = tableTypeCodec.getFlowTbleType();
        byte tableId = (byte) tableStore.getTableStore()
                .getNewGlobalFlowTableId(deviceId, tableType);

        ObjectNode selectorJson = get(json, SELECTOR);
        JsonNode criteriaJson = selectorJson.get(CRITERIA);
        ArrayList<OFMatch20> match20List = new ArrayList<OFMatch20>();
        IntStream.range(0, criteriaJson.size())
                .forEach(i -> match20List.add(
                        match20Codec(get(criteriaJson, i))));
        CoreService coreService = context.getService(CoreService.class);
        JsonNode appIdJson = json.get(APP_ID);
        String appId = appIdJson != null ? appIdJson.asText() : REST_APP_ID;

        resultBuilder.fromApp(coreService
                .registerApplication(REST_APP_ID));


        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setTableName(json.get(TABLE_NAME).asText());
        ofFlowTable.setTableSize(json.get(TABLE_SIZE).asInt());
        ofFlowTable.setTableType(tableType);
        resultBuilder.withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId);

        return resultBuilder.build();
    }

    public OFMatch20 match20Codec(JsonNode json) {
        short fieldId = (short) nullIsIllegal(json.get(FIELD_ID),
                FIELD_ID + MISSING_MEMBER_MESSAGE).asInt();
        short offset = (short) nullIsIllegal(json.get(OFFSET),
                OFFSET + MISSING_MEMBER_MESSAGE).asInt();
        short length = (short) nullIsIllegal(json.get(LENGTH),
                LENGTH + MISSING_MEMBER_MESSAGE).asInt();
        String fieldName = nullIsIllegal(json.get(FIELD_NAME),
                FIELD_ID + MISSING_MEMBER_MESSAGE).asText();
        OFMatch20 ofMatch20 = new OFMatch20();
        ofMatch20.setFieldId(fieldId);
        ofMatch20.setFieldName(fieldName);
        ofMatch20.setLength(length);
        ofMatch20.setOffset(offset);
        return ofMatch20;
    }
}
