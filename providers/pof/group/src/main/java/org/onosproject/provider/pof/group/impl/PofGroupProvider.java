package org.onosproject.provider.pof.group.impl;

import java.util.*;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.floodlightpof.protocol.OFGroupMod;
import org.onosproject.floodlightpof.protocol.OFMessage;
import org.onosproject.floodlightpof.protocol.OFPortStatus;
import org.onosproject.floodlightpof.protocol.table.OFFlowTableResource;
import org.onosproject.floodlightpof.protocol.factory.BasicFactory;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.group.*;
import org.onosproject.net.provider.AbstractProvider;

import org.onosproject.pof.controller.*;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.onosproject.core.GroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.group.DefaultGroup;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupOperation;
import org.onosproject.net.group.GroupOperation.GroupMsgErrorCode;
import org.onosproject.net.group.GroupOperations;
import org.onosproject.net.group.GroupProvider;
import org.onosproject.net.group.GroupProviderRegistry;
import org.onosproject.net.group.GroupProviderService;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.group.StoredGroupBucketEntry;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;

import static org.onlab.util.Tools.getIntegerProperty;
import static org.slf4j.LoggerFactory.getLogger;

@Component(immediate = true)
public class PofGroupProvider extends AbstractProvider implements GroupProvider {
    private final Logger log = getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PofController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

    private GroupProviderService providerService;
    static final int POLL_INTERVAL = 10;


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService cfgService;

    private static final int DEFAULT_POLL_INTERVAL = 10;
    private static final String COMPONENT = "org.onosproject.provider.pof.group.impl.PofGroupProvider";
    private static final String GROUP_POLL_INTERVAL_CONST = "groupPollInterval";

    @Property(name = "groupPollInterval", intValue = DEFAULT_POLL_INTERVAL,
            label = "Frequency (in seconds) for polling group statistics")
    private int groupPollInterval = DEFAULT_POLL_INTERVAL;


    private final InternalGroupProvider listener = new InternalGroupProvider();

    private static final AtomicLong XID_COUNTER = new AtomicLong(1);


    private final Map<GroupId, GroupOperation> pendingGroupOperations =
            Maps.newConcurrentMap();

    /* Map<Group ID, Transaction ID> */
    private final Map<GroupId, Long> pendingXidMaps = Maps.newConcurrentMap();

    public PofGroupProvider() {

        super(new ProviderId("pof", "org.onosproject.provider.pof.group"));
    }

    private boolean isGroupSupported(PofSwitch sw) {
        return true;
    }

    @Activate
    public void activate() {
        log.info("provider register now");
        cfgService.registerProperties(getClass());
        providerService = providerRegistry.register(this);
        controller.addListener(listener);
        controller.addEventListener(listener);

        for (PofSwitch sw : controller.getSwitches()) {
            if (isGroupSupported(sw)){}
        }

        log.info("Started");
    }
    @Deactivate
    public void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        providerRegistry.unregister(this);
        providerService = null;
        log.info("Stopped");
    }
    @Modified
    public void modified(ComponentContext context) {
        log.info("Modifield");
    }

    private void modifyPollInterval() {


    }
    @Override
    public void performGroupOperation(DeviceId deviceId, GroupOperations groupOps) {

        final Dpid dpid = Dpid.dpid(deviceId.uri());
        PofSwitch sw = controller.getSwitch(dpid);
        for (GroupOperation groupOperation : groupOps.operations()) {
            if (sw == null) {
                log.error("SW {} is not found", dpid);
                return;
            }
            final Long groupModXid = XID_COUNTER.getAndIncrement();
            GroupModBuilder builder = null;
            if (driverService == null) {
                builder = GroupModBuilder.builder(groupOperation.buckets(),
                        groupOperation.groupId(),
                        groupOperation.groupType(),
                        sw.factory(),
                        Optional.of(groupModXid));
            } else {

                builder = GroupModBuilder.builder(groupOperation.buckets(),
                        groupOperation.groupId(),
                        groupOperation.groupType(),
                        sw.factory(),
                        Optional.of(groupModXid),
                        Optional.of(driverService));
            }
            OFGroupMod groupMod = null;
            switch (groupOperation.opType()) {
                case ADD:

                    groupMod = builder.buildGroupAdd();
                    break;
                case MODIFY:
                    groupMod = builder.buildGroupMod();
                    break;
                case DELETE:
                    groupMod = builder.buildGroupDel();
                    break;
                default:
                    log.error("Unsupported Group operation");
                    return;
            }
            log.info("The groupMod : {}",groupMod.toString());
            sw.sendMsg(groupMod);
            GroupId groudId = new GroupId(groupMod.getGroupId());
//            pendingGroupOperations.put(groudId, groupOperation);
//            pendingXidMaps.put(groudId, groupModXid);
        }
    }
    private class InternalGroupProvider
            implements PofSwitchListener, PofEventListener {


        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            if (providerService == null) {
                return;
            }
            DeviceId deviceId = DeviceId.deviceId(Dpid.uri(dpid));
            //TODO unsupported
        }

        @Override
        public void switchAdded(Dpid dpid) {
            PofSwitch sw = controller.getSwitch(dpid);
        }

        @Override
        public void handleConnectionUp(Dpid dpid) {
            return;
        }

        @Override
        public void switchRemoved(Dpid dpid) {
            return;
        }

        @Override
        public void switchChanged(Dpid dpid) {
            return;
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
            return;
        }

        @Override
        public void setTableResource(Dpid dpid, OFFlowTableResource msg) {
            return;
        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested, RoleState response) {
            return;
        }
    }
}
