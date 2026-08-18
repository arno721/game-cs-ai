package com.arno721.armorstandgrabber.chest;

import java.util.List;

public record ContainerDescriptor(
    int syncId,
    String title,
    String handlerClassName,
    List<Integer> containerSlotIds
) {
    public ContainerDescriptor {
        containerSlotIds = List.copyOf(containerSlotIds);
    }
}
