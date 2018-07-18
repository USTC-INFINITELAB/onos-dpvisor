package org.onosproject.provider.pof.group.impl;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onosproject.core.GroupId;
import org.onosproject.floodlightpof.protocol.OFBucket;
import org.onosproject.floodlightpof.protocol.OFGroupMod;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.floodlightpof.protocol.factory.OFBucketFactory;
import org.onosproject.floodlightpof.protocol.instruction.OFInstruction;
import org.onosproject.floodlightpof.protocol.instruction.OFInstructionApplyActions;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.driver.DefaultDriverData;
import org.onosproject.net.driver.DefaultDriverHandler;
import org.onosproject.net.driver.Driver;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.*;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;

import org.slf4j.Logger;

import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;



public class GroupModBuilder {

    private GroupBuckets buckets;
    private GroupId groupId;
    private GroupDescription.Type type;
    private OFBucketFactory factory;
    private Long xid;
    private Optional<DriverService> driverService;

    private final Logger log = getLogger(getClass());

    private static final int OFPCML_NO_BUFFER = 0xffff;



    private List<TrafficTreatment> trafficTreatments = new LinkedList<>();
    private GroupModBuilder(GroupBuckets buckets, GroupId groupId,
                            GroupDescription.Type type, OFBucketFactory factory,
                            Optional<Long> xid) {
        this.buckets = buckets;
        this.groupId = groupId;
        this.type = type;
        this.factory = factory;
        this.xid = xid.orElse((long) 0);
    }

    private List<TrafficTreatment> getTrafficTreatments() {
        ListIterator<GroupBucket> bucketListIterator = buckets.buckets().listIterator();
        return null;
    }

    private GroupModBuilder(GroupBuckets buckets, GroupId groupId,
                            GroupDescription.Type type, OFBucketFactory factory,
                            Optional<Long> xid, Optional<DriverService> driverService) {
        this.buckets = buckets;
        this.groupId = groupId;
        this.type = type;
        this.factory = factory;
        this.xid = xid.orElse((long) 0);
        this.driverService = driverService;
    }
    /**
     * Creates a builder for GroupMod.
     *
     * @param buckets GroupBuckets object
     * @param groupId Group Id to create
     * @param type Group type
     * @param factory OFFactory object
     * @param xid transaction ID
     * @return GroupModBuilder object
     */
    public static GroupModBuilder builder(GroupBuckets buckets, GroupId groupId,
                                          GroupDescription.Type type, OFBucketFactory factory,
                                          Optional<Long> xid) {

        return new GroupModBuilder(buckets, groupId, type, factory, xid);
    }

    /**
     * Creates a builder for GroupMod.
     *
     * @param buckets GroupBuckets object
     * @param groupId Group Id to create
     * @param type Group type
     * @param factory OFFactory object
     * @param xid transaction ID
     * @param driverService driver Service
     * @return GroupModBuilder object
     */
    public static GroupModBuilder builder(GroupBuckets buckets, GroupId groupId,
                                          GroupDescription.Type type, OFBucketFactory factory,
                                          Optional<Long> xid, Optional<DriverService> driverService) {

        return new GroupModBuilder(buckets, groupId, type, factory, xid, driverService);
    }

    /**
     * Creates a GroupMod.
     *
     * @return OFGroupMod
     */
    public OFGroupMod buildGroupAdd() {
        OFGroupMod ofGroupMod = new OFGroupMod();
        List<OFBucket> ofBucketList = new LinkedList<>();
        for (GroupBucket groupBucket : buckets.buckets()) {
            ofBucketList.add(buildOFBucket(groupBucket));

        }
        ofGroupMod.setBucketFactory(factory);
        ofGroupMod.setBucketList(ofBucketList);
        ofGroupMod.setBucketNum((byte) ofBucketList.size());
        ofGroupMod.setCommand(OFGroupMod.OFGroupModCmd.OFPGC_ADD);
        ofGroupMod.setCounterId(0);
        ofGroupMod.setSlotId((byte)0);
        ofGroupMod.setGroupId(groupId.id());

        ofGroupMod.setGroupType((byte) (getPOFGroupType(type).ordinal()));

        return ofGroupMod;
    }

    /**
     * Creates a GroupMod.
     *
     * @return  OFGroupMod object
     */
    public OFGroupMod buildGroupDel() {

        OFGroupMod ofGroupMod = new OFGroupMod();
        List<OFBucket> ofBucketList = new LinkedList<>();
        for (GroupBucket groupBucket : buckets.buckets()) {
            ofBucketList.add(buildOFBucket(groupBucket));

        }
        ofGroupMod.setBucketFactory(factory);
        ofGroupMod.setBucketList(ofBucketList);
//        ofGroupMod.setBucketNum((byte) ofBucketList.size());
        ofGroupMod.setBucketNum((byte) 0x00);    // tsf: for del_group, no need to parse buckets in data plane
        ofGroupMod.setCommand(OFGroupMod.OFGroupModCmd.OFPGC_DELETE);
        ofGroupMod.setCounterId(0);
        ofGroupMod.setSlotId((byte)0);
        ofGroupMod.setGroupId(groupId.id());

        ofGroupMod.setGroupType((byte) (getPOFGroupType(type).ordinal()));

        return ofGroupMod;
    }

    /**
     * Creates a GroupMod.
     *
     * @return OFGroupMod
     */

    public OFGroupMod buildGroupMod() {
        OFGroupMod ofGroupMod = new OFGroupMod();
        List<OFBucket> ofBucketList = new LinkedList<>();
        for (GroupBucket groupBucket : buckets.buckets()) {
            ofBucketList.add(buildOFBucket(groupBucket));

        }
        ofGroupMod.setBucketFactory(factory);
        ofGroupMod.setBucketList(ofBucketList);
        ofGroupMod.setBucketNum((byte) ofBucketList.size());
        ofGroupMod.setCommand(OFGroupMod.OFGroupModCmd.OFPGC_MODIFY);
        ofGroupMod.setCounterId(0);
        ofGroupMod.setSlotId((byte)0);
        ofGroupMod.setGroupId(groupId.id());

        ofGroupMod.setGroupType((byte) type.ordinal());

        return ofGroupMod;
    }

    /**
     * Creates a GroupMod.
     *
     * @return OFGroupMod
     */
    private OFBucket buildOFBucket(GroupBucket groupBucket) {
        TrafficTreatment trafficTreatment = groupBucket.treatment();
        if (trafficTreatment == null) {

            return null;
        }
        List<OFAction> actionList = new LinkedList<>();

        List<Instruction> instructionList = trafficTreatment.allInstructions();

        for (Instruction i : instructionList) {
            if (i.type() == Instruction.Type.POFACTION) {
                log.info("GroupMod : this is pof-actions in group");
                PofAction pofAction = (PofAction)i;
                actionList.add(pofAction.action());

            } else if (i.type() == Instruction.Type.POFINSTRUCTION) {
                log.info("GroupMod : this is pof-instructions in group");

                if (i instanceof DefaultPofInstructions.PofInstructionApplyActions) {


                    OFInstructionApplyActions ofInstructionApplyActions
                            = (OFInstructionApplyActions)
                            ((DefaultPofInstructions.PofInstructionApplyActions) i)
                            .instruction();
                    List<OFAction> applyActionList = ofInstructionApplyActions.getActionList();

                    actionList.addAll(applyActionList);
                }
            } else {
                log.warn("actions or instructions type {} not yet supported", i.type());
            }

        }

        short weight = groupBucket.weight();
        short watchSlotId = 0;
        byte watchPort = 0;
        int watchGroup = 0;
        if (groupBucket.watchGroup() != null && type == GroupDescription.Type.FAILOVER) {

            watchPort = (byte)groupBucket.watchPort().toLong();
        }
        if (groupBucket.watchPort() != null && type == GroupDescription.Type.FAILOVER) {
            watchGroup = groupBucket.watchGroup().id();

        }
        short actionNum = (short) actionList.size();
        return new OFBucket(actionNum, weight, watchSlotId, watchPort, watchGroup, actionList);
    }

    private OFGroupMod.OFGroupType getPOFGroupType(GroupDescription.Type groupType) {
        switch (groupType) {
            case INDIRECT:
                return OFGroupMod.OFGroupType.OFPGT_INDIRECT;
            case SELECT:
                return OFGroupMod.OFGroupType.OFPGT_SELECT;
            case FAILOVER:
                return OFGroupMod.OFGroupType.OFPGT_FF;
            case ALL:
                return OFGroupMod.OFGroupType.OFPGT_ALL;
            default:
                log.error("Unsupported group type : {}", groupType);
                break;
        }
        return null;
    }


}
