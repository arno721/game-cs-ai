package com.arno721.armorstandgrabber.session;

public enum RetrievalPhase {
    Idle,
    PrepareSlot,
    WaitSwitchDelay,
    RotateForInteraction,
    Interact,
    WaitItem,
    FinishOverflow,
    WaitItemDelay,
    Complete,
    Abort
}
