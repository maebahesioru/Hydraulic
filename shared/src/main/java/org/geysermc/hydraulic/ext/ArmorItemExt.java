package org.geysermc.hydraulic.ext;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;

public interface ArmorItemExt {
    Holder<ArmorMaterial> material();

    ArmorItem.Type type();

    int protection();
}
