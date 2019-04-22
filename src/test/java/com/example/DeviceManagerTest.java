package com.example;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DeviceManagerTest {

    private static ActorSystem system;

    @BeforeAll
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterAll
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testRegisterGroupAndDeviceActor() {
        TestKit probe = new TestKit(system);
        ActorRef manager = system.actorOf(DeviceManager.props(), "manager");

        manager.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef group1 = probe.getLastSender();

        manager.tell(new DeviceManager.RequestTrackDevice("group2", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef group2 = probe.getLastSender();
        assertNotEquals(group1, group2);
    }

    @Test
    public void testListActiveDevices() {
        TestKit probe = new TestKit(system);
        ActorRef manager = system.actorOf(DeviceManager.props());

        manager.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        manager.tell(new DeviceManager.RequestTrackDevice("group2", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        manager.tell(new DeviceManager.RequestGroupList(0L), probe.getRef());
        DeviceManager.ReplyGroups reply = probe.expectMsgClass(DeviceManager.ReplyGroups.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("group1", "group2").collect(Collectors.toSet()), reply.groupIdToActor.keySet());
    }

    @Test
    public void testListActiveDevicesAfterOneShutDown() {
        TestKit probe = new TestKit(system);
        ActorRef manager = system.actorOf(DeviceManager.props());

        manager.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        manager.tell(new DeviceManager.RequestTrackDevice("group2", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        manager.tell(new DeviceManager.RequestGroupList(0L), probe.getRef());
        DeviceManager.ReplyGroups reply = probe.expectMsgClass(DeviceManager.ReplyGroups.class);
        assertEquals(0L, reply.requestId);
        assertEquals(Stream.of("group1", "group2").collect(Collectors.toSet()), reply.groupIdToActor.keySet());

        ActorRef toShutDown = reply.groupIdToActor.get("group1");

        probe.watch(toShutDown);
        toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(toShutDown);

        probe.awaitAssert(() -> {
            manager.tell(new DeviceManager.RequestGroupList(1L), probe.getRef());
            DeviceManager.ReplyGroups r = probe.expectMsgClass(DeviceManager.ReplyGroups.class);
            assertEquals(1L, r.requestId);
            assertEquals(Stream.of("group2").collect(Collectors.toSet()), r.groupIdToActor.keySet());
            return null;
        });
    }

}
