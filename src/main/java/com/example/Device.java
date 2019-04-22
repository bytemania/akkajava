package com.example;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.FI;

import java.util.Optional;

public class Device extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;
    final String deviceId;

    public Device(String groupId, String deviceId) {
        this.groupId = groupId;
        this.deviceId = deviceId;
    }

    public static Props props(String groupId, String deviceId){
        return Props.create(Device.class, ()-> new Device(groupId, deviceId));
    }

    public static final class RecordTemperature {
        final long requestId;
        final double value;

        public RecordTemperature(long requestId, double value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    public static final class TemperatureRecorded {
        final long requestId;

        public TemperatureRecorded(long requestId){
            this.requestId = requestId;
        }
    }

    public static final class ReadTemperature {
        final long requestId;

        public ReadTemperature(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class RespondTemperature {
        final long requestId;
        final Optional<Double> value;

        public RespondTemperature(long requestId, Optional<Double> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    Optional<Double> lastTemperatureReading = Optional.empty();

    @Override
    public void preStart() {
        log.info("Device actor {}-{} started", groupId, deviceId);
    }

    @Override
    public void postStop() {
        log.info("Device actor {}-{} stopped", groupId, deviceId);
    }

    @Override
    public Receive createReceive() {

        FI.UnitApply<DeviceManager.RequestTrackDevice> trackDeviceApply =  r -> {
            if (groupId.equals(r.groupId) && deviceId.equals(r.deviceId)) {
                getSender().tell(new DeviceManager.DeviceRegistered(), getSelf());
            } else {
                log.warning("Ignoring TrackDevice request for {}-{}. This actor is responsible for {}-{}.",
                        r.groupId, r.deviceId, groupId, deviceId);
            }
        };

        FI.UnitApply<RecordTemperature> recordTemperatureApply =  r -> {
            log.info("Recorded temperature reading {} with {}", r.value, r.requestId);
            lastTemperatureReading = Optional.of(r.value);
            getSender().tell(new TemperatureRecorded(r.requestId), getSelf());
        };

        FI.UnitApply<ReadTemperature> readTemperatureApply =  r ->
                getSender().tell(new RespondTemperature(r.requestId, lastTemperatureReading), getSelf());

        return receiveBuilder()
                .match(DeviceManager.RequestTrackDevice.class, trackDeviceApply)
                .match(RecordTemperature.class, recordTemperatureApply)
                .match(ReadTemperature.class, readTemperatureApply)
        .build();
    }

}
