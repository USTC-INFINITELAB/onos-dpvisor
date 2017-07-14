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
package org.onosproject.provider.pof.flow.impl;

import org.onosproject.floodlightpof.protocol.OFFlowMod;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.OFMatchX;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.PofCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.PofAction;
import org.onosproject.net.flow.instructions.PofInstruction;
import org.onosproject.net.table.FlowTable;
import org.onosproject.net.table.FlowTableId;
import org.onosproject.net.table.FlowTableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;


/**
 * Flow mod builder for OpenFlow 1.3+.
 */
public class FlowModBuilderVer20 extends FlowModBuilder {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final int OFPCML_NO_BUFFER = 0xffff;

    private TrafficSelector selector;
    private TrafficTreatment treatment;
    private FlowRule flowRule;
    private FlowTable flowTable;
    private DeviceId deviceId;
    private int flowEntryId;
    protected FlowTableStore flowTableStore;

//
    /**
     * Constructor for a flow mod builder for OpenFlow 1.3.
     *
     * @param flowRule the flow rule to transform into a flow mod
     * @param factory the OpenFlow factory to use to build the flow mod
     * @param xid the transaction ID
     * @param driverService the device driver service
     */
    protected FlowModBuilderVer20(FlowRule flowRule, BasicFactory factory,
                                  FlowTableStore flowTableStore,
                                  Optional<Long> xid, Optional<DriverService> driverService) {
        super(flowRule, factory, xid, driverService);

        this.flowRule = flowRule;
        this.selector = flowRule.selector();
        this.treatment = flowRule.treatment();
        this.deviceId = flowRule.deviceId();
        this.flowEntryId = (int) flowRule.id().value();
        this.flowTableStore = flowTableStore;
        FlowTable flowtable = flowTableStore.getFlowTableInternal(flowRule.deviceId(), FlowTableId
                .valueOf(flowRule.tableId()));
        if (flowtable == null) {
            log.info("++++ flow table is null");
        }
        this.flowTable = flowtable;
    }


    @Override
    public OFFlowMod buildFlowAdd() {
        List<OFMatchX> matchXList = buildMatch();
        List<OFInstruction> insList = buildInstruction();

        OFTableType tableType = null;

        if (flowTable == null) {
            log.error("++++ flow table is null");
        }
        
        tableType = flowTable.flowTable().getTableType();
        if ((tableType == OFTableType.OF_LINEAR_TABLE && flowTable.flowTable()
                .getMatchFieldNum() != 0 && matchXList != null && matchXList.size() != 0)
                || (tableType != OFTableType.OF_LINEAR_TABLE && (flowTable.flowTable().getMatchFieldNum() == 0
                || matchXList.isEmpty() || matchXList.size() != flowTable.flowTable().getMatchFieldNum()))
                || insList.size() == 0 || insList == null) {
            //return FLOWENTRYID_INVALID;
            log.error("ERROR in check table in buildFlowAdd1");
            return null;
        }

        if (null != matchXList) {
            int totalFiledLength = 0;
            for (OFMatchX matchX : matchXList) {
                if (matchX == null) {
                    log.error("ERROR in check table in buildFlowAdd2");
                    return null;
                }
                //log.info("OFMatchX: " + matchX.toString());
                totalFiledLength += matchX.getLength();
            }
            if (totalFiledLength != flowTable.flowTable().getKeyLength()) {
                log.error("ERROR in check table in buildFlowAdd3, totalFiledLength: {}, getKeyLength: {}",
                        totalFiledLength, flowTable.flowTable().getKeyLength());
                return null;
            }
        }

        //add flow entry
        return addFlowEntry(deviceId, flowTable.id(), matchXList, insList, (short) flowRule().priority(), true);
    }

    private OFFlowMod addFlowEntry(DeviceId deviceID, FlowTableId tableId,
                                   List<OFMatchX> matchList,
                                   List<OFInstruction> instructionList,
                                   short priority, boolean counterEnable) {

        //log.info("buildver20.addflowentry");
        OFTableType tableType = flowTable.flowTable().getTableType();
        //byte smallFlowTableId = flowTableStore.parseToSmallTableId(deviceID, (byte) globalTableId.value());

        OFFlowMod newFlowEntry = new OFFlowMod();
        newFlowEntry.setTableId((byte)tableId.value());
        newFlowEntry.setTableType(tableType);
        newFlowEntry.setIndex(flowEntryId);

        newFlowEntry.setMatchFieldNum((byte) matchList.size());
        newFlowEntry.setMatchList(matchList);
        newFlowEntry.setInstructionNum((byte) instructionList.size());
        newFlowEntry.setInstructionList(instructionList);
        newFlowEntry.setPriority(priority);
        newFlowEntry.setCommand((byte) OFFlowMod.OFFlowEntryCmd.OFPFC_ADD.ordinal());
        newFlowEntry.setLengthU(OFFlowMod.MAXIMAL_LENGTH);

        flowTableStore.addFlowEntry(deviceID, flowTable.id(), this.flowRule);
        //log.info("buildver20. newflowentry:{}", newFlowEntry.toString());
        return newFlowEntry;
    }

    @Override
    public OFFlowMod buildFlowMod() {
        List<OFMatchX> matchXList = buildMatch();
        List<OFInstruction> insList = buildInstruction();

        OFTableType tableType;
        tableType = flowTable.flowTable().getTableType();

        if ((tableType == OFTableType.OF_LINEAR_TABLE && flowTable.flowTable().getMatchFieldNum() != 0
                && !matchXList.isEmpty()) || (tableType != OFTableType.OF_LINEAR_TABLE
                && (flowTable.flowTable().getMatchFieldNum() == 0
                || matchXList.size() != flowTable.flowTable()
                .getMatchFieldNum())) || insList.size() == 0 || insList == null) {
            //return FLOWENTRYID_INVALID;
            log.error("ERROR in check table in buildFlowAdd4");
            return null;
        }

        if (null != matchXList) {
            int totalFiledLength = 0;
            for (OFMatchX matchX : matchXList) {
                if (matchX == null) {
                    log.error("ERROR in check table in buildFlowAdd5");
                    return null;
                }
                totalFiledLength += matchX.getLength();
            }
            if (totalFiledLength != flowTable.flowTable().getKeyLength()) {
                log.error("ERROR in check table in buildFlowAdd6");
                return null;
            }
        }

        //mod flow entry
        return modFlowEntry(deviceId, (int) flowRule()
                .id().value(), matchXList, insList, (short) flowRule().priority(), true);

    }

    private OFFlowMod modFlowEntry(DeviceId deviceID, int flowEntryID,
                                   List<OFMatchX> matchList,
                                   List<OFInstruction> instructionList,
                                   short priority, boolean counterEnable) {
        FlowRule oldFlowEntry = flowTableStore.getFlowEntries(deviceID, flowTable.id())
                .get(flowEntryID);


        OFFlowMod oldFlowMod = new OFFlowMod();
        oldFlowMod.setTableId((byte) oldFlowEntry.tableId());
        oldFlowMod.setTableType(flowTable.flowTable().getTableType());
        oldFlowMod.setIndex(flowEntryID);

        oldFlowMod.setLengthU(OFFlowMod.MAXIMAL_LENGTH);

        oldFlowMod.setMatchFieldNum((byte) matchList.size());
        oldFlowMod.setMatchList(matchList);
        oldFlowMod.setInstructionNum((byte) instructionList.size());
        oldFlowMod.setInstructionList(instructionList);
        oldFlowMod.setPriority(priority);
        oldFlowMod.setCommand((byte) OFFlowMod.OFFlowEntryCmd.OFPFC_MODIFY.ordinal());

        flowTableStore.modifyFlowEntry(deviceID, flowTable.id(), oldFlowEntry);

        return oldFlowMod;
    }

    @Override
    public OFFlowMod buildFlowDel() {

        //del flow entry
        log.info("delFlowEntry : {}", deviceId.toString());
        log.info("delFlowEntry : {}", flowTable.id().value());
        log.info("delFlowEntry : {}", flowRule().id().value());
        return delFlowEntry(deviceId, (int) flowRule().id().value());
    }


    private OFFlowMod delFlowEntry(DeviceId deviceID, int flowEntryID) {

        log.info("delFlowEntry deviceId: {}, Table ID: {}, entry ID: {}", deviceID, flowTable
                .id(), flowEntryID);

        FlowRule oldFlowEntry = flowTableStore.getFlowEntries(deviceID, flowTable.id())
                .get(flowEntryID);

        OFFlowMod oldFlowMod = new OFFlowMod();
        oldFlowMod.setTableId((byte) oldFlowEntry.tableId());
        oldFlowMod.setTableType(flowTable.flowTable().getTableType());
        oldFlowMod.setIndex(flowEntryID);
        oldFlowMod.setLengthU(OFFlowMod.MAXIMAL_LENGTH);
        oldFlowMod.setCommand((byte) OFFlowMod.OFFlowEntryCmd.OFPFC_DELETE.ordinal());

        flowTableStore.deleteFlowEntry(deviceID, flowTable.id(), flowEntryID);
        return oldFlowMod;
    }
    /**
     * To MatchX.
     * @return OFMatchX
     */
    private OFMatchX toMatchX(PofCriterion pc) {

        OFMatch20 match20 = new OFMatch20();
        match20.setFieldName(pc.fieldName());
        match20.setFieldId(pc.fieldId());
        match20.setOffset(pc.offset());
        match20.setLength(pc.length());

        OFMatchX matchX = new OFMatchX(match20, pc.value(), pc.mask());

        return matchX;
    }


    /**
     * Builds the match for the flow mod.
     *
     * @return the match
     */
    // CHECKSTYLE IGNORE MethodLength FOR NEXT 300 LINES
    protected List<OFMatchX> buildMatch() {
        List<OFMatchX> matchXList = new ArrayList<>();
        if (flowTable.flowTable().getTableType() != OFTableType.OF_LINEAR_TABLE) {
//            log.info("++++ flowTable: " + flowTable.toString());
            int matchFieldNum = flowTable.flowTable().getMatchFieldNum();
            List<OFMatch20> matchFieldList = flowTable.flowTable().getMatchFieldList();
            Set<Criterion> criterions = selector.criteria();

            Criterion criterion = criterions.iterator().next();

            if (criterion instanceof PofCriterion) {
                PofCriterion pofCriterion = (PofCriterion) criterion;
                List<Criterion> list = pofCriterion.list();
                if (matchFieldNum != list.size()) {
                    log.error("match field number in the entry should be {}", matchFieldNum);
                    return new ArrayList<>();
                }
                for (int i = 0; i < matchFieldNum; i++) {
                    PofCriterion pc = (PofCriterion) list.get(i);
                    OFMatchX matchX = toMatchX(pc);
                    if (matchX == null) {
                        log.error("parse matchX error");
                        return new ArrayList<>();
                    }
                    OFMatch20 matchFieldInTable = matchFieldList.get(i);
                    if (matchX.getFieldId() == OFMatch20.METADATA_FIELD_ID && matchFieldInTable.getFieldName() != null) {
                        if (!matchFieldInTable.getFieldName().equalsIgnoreCase(matchX.getFieldName())) {
                            log.error("matchX[" + i + "] should be metadata[name= " + matchFieldInTable
                                    .getFieldName() + "]");
                           return new ArrayList<>();
                        }
                    } else {
                        if (matchFieldInTable.getFieldId() != matchX.getFieldId()) {
                            log.error("matchX[" + i + "] should be field[id= " + matchFieldInTable.getFieldId() + "]");
                            return new ArrayList<>();
                        }
                    }

                    matchXList.add(matchX);
                }

            }
        }

        return matchXList;
    }



    private List<OFInstruction> buildInstruction() {
        if (treatment == null) {
            return Collections.emptyList();
        }

        List<Instruction> ins = treatment.allInstructions();
        List<OFAction> ofActions = new LinkedList<>();
        List<OFInstruction> ofIns = new LinkedList<>();

        for (Instruction i : ins) {
            switch (i.type()) {
                case POFACTION:
                    PofAction pa = (PofAction) i;
                    ofActions.add(pa.action());
                    break;
                case POFINSTRUCTION:
                    PofInstruction pi = (PofInstruction) i;
                    ofIns.add(pi.instruction());
                    break;

                default:
                    log.warn("Instruction type {} not yet implemented.", i.type());
            }
        }
        //log.info("buildInstruction: return instrustions.");
        return ofIns;
    }

}
