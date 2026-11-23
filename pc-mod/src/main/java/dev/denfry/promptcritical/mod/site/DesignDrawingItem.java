package dev.denfry.promptcritical.mod.site;

import net.minecraft.world.item.Item;

/** Чертёж типового проекта: прикладывается к стройплощадке и задаёт, что на ней строят. */
public class DesignDrawingItem extends Item {
    private final String planId;

    public DesignDrawingItem(Properties props, String planId) {
        super(props);
        this.planId = planId;
    }

    public String planId() {
        return planId;
    }
}
