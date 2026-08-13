package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorMaterial;
import org.geysermc.hydraulic.ext.ArmorItemExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorItem.class)
public class ArmorItemMixin implements ArmorItemExt {
    @Unique
    private Holder<ArmorMaterial> armorMaterial;

    @Unique
    private ArmorItem.Type armorType;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void onInitFinish(Holder<ArmorMaterial> armorMaterial, ArmorItem.Type armorType, Item.Properties properties, CallbackInfo ci) {
        this.armorMaterial = armorMaterial;
        this.armorType = armorType;
    }

    @Override
    public Holder<ArmorMaterial> material() {
        return armorMaterial;
    }

    @Override
    public ArmorItem.Type type() {
        return armorType;
    }

    @Override
    public int protection() {
        return armorMaterial.value().getDefense(armorType);
    }
}
