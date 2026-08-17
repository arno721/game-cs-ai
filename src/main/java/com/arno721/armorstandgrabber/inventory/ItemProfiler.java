package com.arno721.armorstandgrabber.inventory;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.HoneyBlock;
import net.minecraft.block.IceBlock;
import net.minecraft.block.SlimeBlock;
import net.minecraft.block.SoulSandBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.item.WindChargeItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ItemProfiler {
    public ItemProfile profile(ItemStack stack, SlotRef slot, Set<String> blacklist) {
        Item item = stack.getItem();
        String itemId = Registries.ITEM.getId(item).toString();
        boolean blacklisted = blacklist.contains(itemId);
        boolean hotbar = slot.region() == InventoryRegion.PLAYER_HOTBAR;
        int stableSlot = slot.slotId();
        int durability = stack.isDamageable() ? Math.max(0, stack.getMaxDamage() - stack.getDamage()) : 0;
        List<ItemFacet> facets = new ArrayList<>();

        if (stack.isIn(ItemTags.SWORDS)) {
            add(facets, ItemType.SWORD, ItemScore.weapon(100, 0, 0, durability, hotbar, stableSlot), 40, 0);
            add(facets, ItemType.WEAPON, ItemScore.weapon(100, 0, 0, durability, hotbar, stableSlot), 60, 0);
        }
        if (item instanceof AxeItem || stack.isIn(ItemTags.AXES)) {
            add(facets, ItemType.AXE, ItemScore.tool(90, false, 0, durability, hotbar, stableSlot), 30, 0);
            add(facets, ItemType.TOOL, ItemScore.tool(90, false, 0, durability, hotbar, stableSlot), 70, 0);
            add(facets, ItemType.WEAPON, ItemScore.weapon(85, 0, 0, durability, hotbar, stableSlot), 65, 0);
        }
        if (stack.isIn(ItemTags.PICKAXES)) {
            add(facets, ItemType.PICKAXE, ItemScore.tool(100, false, 0, durability, hotbar, stableSlot), 20, 0);
            add(facets, ItemType.TOOL, ItemScore.tool(100, false, 0, durability, hotbar, stableSlot), 70, 0);
        }
        if (stack.isIn(ItemTags.SHOVELS)) {
            add(facets, ItemType.SHOVEL, ItemScore.tool(80, false, 0, durability, hotbar, stableSlot), 25, 0);
            add(facets, ItemType.TOOL, ItemScore.tool(80, false, 0, durability, hotbar, stableSlot), 70, 0);
        }
        if (stack.isIn(ItemTags.HOES)) {
            add(facets, ItemType.HOE, ItemScore.tool(70, false, 0, durability, hotbar, stableSlot), 25, 0);
            add(facets, ItemType.TOOL, ItemScore.tool(70, false, 0, durability, hotbar, stableSlot), 70, 0);
        }
        if (item instanceof TridentItem || stack.isIn(ItemTags.SPEARS)) {
            add(facets, ItemType.SPEAR, ItemScore.weapon(95, 0, 0, durability, hotbar, stableSlot), 30, 0);
            add(facets, ItemType.WEAPON, ItemScore.weapon(95, 0, 0, durability, hotbar, stableSlot), 60, 0);
        }
        if (item instanceof MaceItem) {
            add(facets, ItemType.MACE, ItemScore.weapon(92, 0, 0, durability, hotbar, stableSlot), 30, 0);
            add(facets, ItemType.WEAPON, ItemScore.weapon(92, 0, 0, durability, hotbar, stableSlot), 60, 0);
        }
        if (item instanceof BowItem) add(facets, ItemType.BOW, ItemScore.generic(100, hotbar, stableSlot), 30, 0);
        if (item instanceof CrossbowItem) add(facets, ItemType.CROSSBOW, ItemScore.generic(100, hotbar, stableSlot), 30, 0);
        if (item instanceof FishingRodItem) add(facets, ItemType.ROD, ItemScore.generic(100, hotbar, stableSlot), 30, 0);
        if (item instanceof ShieldItem) add(facets, ItemType.SHIELD, ItemScore.generic(100, hotbar, stableSlot), 20, 0);

        if (stack.isIn(ItemTags.ARROWS)) add(facets, ItemType.ARROW, ItemScore.generic(1, hotbar, stableSlot), 100, 1);
        if (item instanceof EnderPearlItem) {
            add(facets, ItemType.PEARL, ItemScore.generic(100, hotbar, stableSlot), 25, 0);
            add(facets, ItemType.THROWABLE, ItemScore.generic(100, hotbar, stableSlot), 80, 1);
        } else if (item instanceof SnowballItem || item instanceof EggItem || item instanceof WindChargeItem) {
            add(facets, ItemType.THROWABLE, ItemScore.generic(50, hotbar, stableSlot), 80, 1);
        }

        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            double priority = item == Items.ENCHANTED_GOLDEN_APPLE ? 1000 : 900;
            add(facets, ItemType.GAPPLE, ItemScore.generic(priority, hotbar, stableSlot), 15, 0);
        }

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food != null) {
            double ratio = food.nutrition() == 0 ? 0 : food.saturation() / food.nutrition();
            double score = (item == Items.ENCHANTED_GOLDEN_APPLE ? 10_000 : item == Items.GOLDEN_APPLE ? 9_000 : 0)
                + ratio * 100 + food.nutrition() * 10 + food.saturation();
            add(facets, ItemType.FOOD, ItemScore.generic(score, hotbar, stableSlot), 90, food.nutrition());
        }

        if (item instanceof PotionItem) {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null) {
                ItemScore potionScore = potionScore(item, contents, hotbar, stableSlot);
                if (potionScore != null) add(facets, ItemType.POTION, potionScore, 50, 0);
            }
        }

        if (item == Items.WATER_BUCKET) {
            add(facets, ItemType.WATER, ItemScore.generic(100, hotbar, stableSlot), 40, 1);
            add(facets, ItemType.BUCKET, ItemScore.generic(100, hotbar, stableSlot), 90, 0);
        } else if (item == Items.LAVA_BUCKET) {
            add(facets, ItemType.LAVA, ItemScore.generic(100, hotbar, stableSlot), 40, 1);
            add(facets, ItemType.BUCKET, ItemScore.generic(100, hotbar, stableSlot), 90, 0);
        } else if (item == Items.MILK_BUCKET) {
            add(facets, ItemType.MILK, ItemScore.generic(100, hotbar, stableSlot), 40, 1);
            add(facets, ItemType.BUCKET, ItemScore.generic(100, hotbar, stableSlot), 90, 0);
        }

        if (stack.isIn(ItemTags.TRIMMABLE_ARMOR)) {
            add(facets, ItemType.ARMOR, ItemScore.generic(durability, false, stableSlot), 10, 0);
        }

        if (item instanceof BlockItem blockItem && isStableFullBlock(blockItem.getBlock())) {
            add(facets, ItemType.BLOCK, ItemScore.generic(stack.getCount(), hotbar, stableSlot), 100, 1);
        }

        String compatibilityKey = itemId + "|" + stack.getComponents();
        return new ItemProfile(
            slot,
            itemId,
            compatibilityKey,
            stack.getCount(),
            stack.getMaxCount(),
            blacklisted,
            facets
        );
    }

    private static ItemScore potionScore(Item item, PotionContentsComponent contents, boolean hotbar, int slot) {
        int bestTier = -1;
        int bestAmplifier = 0;
        int totalDuration = 0;
        boolean hasAny = false;
        for (StatusEffectInstance effect : contents.getEffects()) {
            hasAny = true;
            if (PotionTier.isHarmful(effect.getEffectType())) return null;
            bestTier = Math.max(bestTier, PotionTier.of(effect.getEffectType()).rank());
            bestAmplifier = Math.max(bestAmplifier, effect.getAmplifier());
            totalDuration += Math.max(0, effect.getDuration());
        }
        if (!hasAny) return null;
        int typePreference = item instanceof SplashPotionItem ? 3 : item instanceof LingeringPotionItem ? 1 : 2;
        return new ItemScore(bestTier, bestAmplifier, typePreference, totalDuration, hotbar, slot);
    }

    private static boolean isStableFullBlock(Block block) {
        if (block instanceof FallingBlock || block instanceof BlockEntityProvider) return false;
        if (block instanceof SlimeBlock || block instanceof HoneyBlock || block instanceof IceBlock || block instanceof SoulSandBlock) return false;
        return Block.isShapeFullCube(block.getDefaultState().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
    }

    private static void add(
        List<ItemFacet> facets,
        ItemType type,
        ItemScore score,
        int allocationPriority,
        int unitsPerItem
    ) {
        facets.add(new ItemFacet(type, score, allocationPriority, unitsPerItem));
    }
}
