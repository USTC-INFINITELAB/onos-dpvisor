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
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.table.DeviceTableId;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.net.table.FlowTableStore;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onlab.util.Tools.nullIsIllegal;

/**
 * Flow rule JSON codec.
 */
public final class FlowRuleCodec extends JsonCodec<FlowRule> {

    private static final String PRIORITY = "priority";
    private static final String TIMEOUT = "timeout";
    private static final String IS_PERMANENT = "isPermanent";
    private static final String APP_ID = "appId";
    private static final String TABLE_ID = "tableId";
    private static final String DEVICE_ID = "deviceId";
    private static final String TREATMENT = "treatment";
    private static final String SELECTOR = "selector";
    private static final String CRITERIA = "criteria";
    private static final String INSTRUCTIONS = "instructions";
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
    public ObjectNode encode(FlowRule flowRule, CodecContext context) {
        checkNotNull(flowRule, "Flow rule cannot be null");

        CoreService service = context.getService(CoreService.class);
        ApplicationId appId = service.getAppId(flowRule.appId());
        String strAppId = (appId == null) ? "<none>" : appId.name();

        final ObjectNode result = context.mapper().createObjectNode()
                .put("id", Long.toString(flowRule.id().value()))
                .put("tableId", flowRule.tableId())
                .put("appId", strAppId)
                .put("priority", flowRule.priority())
                .put("timeout", flowRule.timeout())
                .put("isPermanent", flowRule.isPermanent())
                .put("deviceId", flowRule.deviceId().toString());

        if (flowRule.treatment() != null) {
            final JsonCodec<TrafficTreatment> treatmentCodec =
                    context.codec(TrafficTreatment.class);
            result.set("treatment", treatmentCodec.encode(flowRule.treatment(), context));
        }

        if (flowRule.selector() != null) {
            final JsonCodec<TrafficSelector> selectorCodec =
                    context.codec(TrafficSelector.class);
            result.set("selector", selectorCodec.encode(flowRule.selector(), context));
        }

        return result;
    }

    @Override
    public FlowRule decode(ObjectNode json, CodecContext context) {
        if (json == null || !json.isObject()) {
            return null;
        }

        FlowRule.Builder resultBuilder = new DefaultFlowRule.Builder();
        DeviceId deviceid = DeviceId.deviceId(json.get(DEVICE_ID).asText());
        CoreService coreService = context.getService(CoreService.class);
        JsonNode appIdJson = json.get(APP_ID);
        String appId = appIdJson != null ? appIdJson.asText() : REST_APP_ID;
        if (!(json.get(DEVICE_ID).asText().substring(0, 3).equals("pof"))) {
            resultBuilder.fromApp(coreService
                    .registerApplication(REST_APP_ID));
        }

        if (json.get(DEVICE_ID).asText().substring(0, 3).equals("pof")) {
            long newFlowEntryId = tableStore.getTableStore()
                    .getNewFlowEntryId(deviceid, json.get(TABLE_ID).asInt());
            resultBuilder.withCookie(newFlowEntryId);
        }

        int priority = nullIsIllegal(json.get(PRIORITY),
                PRIORITY + MISSING_MEMBER_MESSAGE).asInt();
        resultBuilder.withPriority(priority);

        boolean isPermanent = nullIsIllegal(json.get(IS_PERMANENT),
                IS_PERMANENT + MISSING_MEMBER_MESSAGE).asBoolean();
        if (isPermanent) {
            resultBuilder.makePermanent();
        } else {
            resultBuilder.makeTemporary(nullIsIllegal(json.get(TIMEOUT),
                    TIMEOUT
                            + MISSING_MEMBER_MESSAGE
                            + " if the flow is temporary").asInt());
        }

        JsonNode tableIdJson = json.get(TABLE_ID);
        if (tableIdJson != null) {
            resultBuilder.forTable(tableIdJson.asInt());
        }

        DeviceId deviceId = DeviceId.deviceId(nullIsIllegal(json.get(DEVICE_ID),
                DEVICE_ID + MISSING_MEMBER_MESSAGE).asText());
        resultBuilder.forDevice(deviceId);

        ObjectNode treatmentJson = get(json, TREATMENT);
        if (treatmentJson != null) {
            if (json.get(DEVICE_ID).asText().substring(0, 3).equals("pof")) {
                resultBuilder.withTreatment(pofTreatmentCodec(treatmentJson, context));
            } else {
            JsonCodec<TrafficTreatment> treatmentCodec =
                    context.codec(TrafficTreatment.class);
            resultBuilder.withTreatment(treatmentCodec.decode(treatmentJson, context));
            }
        }

        ObjectNode selectorJson = get(json, SELECTOR);
        if (selectorJson != null) {
            if (json.get(DEVICE_ID).asText().substring(0, 3).equals("pof")) {
                resultBuilder.withSelector(pofSelectorCodec(selectorJson, context));
            } else {
                JsonCodec<TrafficSelector> selectorCodec =
                        context.codec(TrafficSelector.class);
                resultBuilder.withSelector(selectorCodec.decode(selectorJson, context));
            }
        }
        return resultBuilder.build();
    }

    public TrafficSelector pofSelectorCodec (ObjectNode json, CodecContext context) {
        final JsonCodec<Criterion> criterionCodec =
                context.codec(Criterion.class);

        JsonNode criteriaJson = json.get(CRITERIA);
        ArrayList<Criterion> entryList = new ArrayList<Criterion>();
        TrafficSelector.Builder builder = DefaultTrafficSelector.builder();
        if (criteriaJson != null) {
            IntStream.range(0, criteriaJson.size())
                    .forEach(i -> entryList.add(
                            criterionCodec.decode(get(criteriaJson, i),
                                    context)));
            builder.add(Criteria.matchOffsetLength(entryList));
        }
        return builder.build();
    }

    public TrafficTreatment pofTreatmentCodec (ObjectNode json, CodecContext context) {
        final JsonCodec<Instruction> instructionsCodec =
                context.codec(Instruction.class);
        JsonNode instructionsJson = json.get(INSTRUCTIONS);
        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        if (instructionsJson != null) {
            IntStream.range(0, instructionsJson.size())
                    .forEach(i -> builder.add(
                            instructionsCodec.decode(get(instructionsJson, i),
                                    context)));
        }
        return builder.build();
    }
}
