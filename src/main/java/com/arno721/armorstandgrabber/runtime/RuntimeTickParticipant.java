package com.arno721.armorstandgrabber.runtime;

public interface RuntimeTickParticipant {
    RuntimeOwner runtimeOwner();
    void onRuntimeTick();
}
