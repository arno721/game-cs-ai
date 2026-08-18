package com.arno721.armorstandgrabber.inventory;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

public enum PotionTier {
    F(0), D(1), C(2), B(3), A(4), S(5);

    private final int rank;

    PotionTier(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static PotionTier of(RegistryEntry<StatusEffect> effect) {
        if (effect.equals(StatusEffects.INSTANT_HEALTH)) return S;
        if (effect.equals(StatusEffects.REGENERATION)
            || effect.equals(StatusEffects.RESISTANCE)
            || effect.equals(StatusEffects.FIRE_RESISTANCE)
            || effect.equals(StatusEffects.HEALTH_BOOST)
            || effect.equals(StatusEffects.ABSORPTION)) return A;
        if (effect.equals(StatusEffects.SPEED)
            || effect.equals(StatusEffects.STRENGTH)
            || effect.equals(StatusEffects.SLOW_FALLING)
            || effect.equals(StatusEffects.INVISIBILITY)) return B;
        if (effect.equals(StatusEffects.SATURATION)
            || effect.equals(StatusEffects.WATER_BREATHING)
            || effect.equals(StatusEffects.JUMP_BOOST)
            || effect.equals(StatusEffects.HASTE)
            || effect.equals(StatusEffects.NIGHT_VISION)) return C;
        if (effect.equals(StatusEffects.LUCK)) return D;
        return F;
    }

    public static boolean isHarmful(RegistryEntry<StatusEffect> effect) {
        return effect.equals(StatusEffects.SLOWNESS)
            || effect.equals(StatusEffects.MINING_FATIGUE)
            || effect.equals(StatusEffects.INSTANT_DAMAGE)
            || effect.equals(StatusEffects.NAUSEA)
            || effect.equals(StatusEffects.BLINDNESS)
            || effect.equals(StatusEffects.HUNGER)
            || effect.equals(StatusEffects.WEAKNESS)
            || effect.equals(StatusEffects.POISON)
            || effect.equals(StatusEffects.WITHER)
            || effect.equals(StatusEffects.GLOWING)
            || effect.equals(StatusEffects.LEVITATION)
            || effect.equals(StatusEffects.UNLUCK)
            || effect.equals(StatusEffects.BAD_OMEN)
            || effect.equals(StatusEffects.DARKNESS);
    }
}
