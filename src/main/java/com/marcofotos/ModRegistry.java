package com.marcofotos;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MarcoFotos.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MarcoFotos.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MarcoFotos.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MarcoFotos.MODID);

    public static final RegistryObject<Block> MARCO_BLOCK = BLOCKS.register("marco_foto",
            () -> new MarcoFotoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Item> MARCO_ITEM = ITEMS.register("marco_foto",
            () -> new MarcoFotoItem(MARCO_BLOCK.get(), new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> IMAGEN_ITEM = ITEMS.register("imagen",
            () -> new ImagenItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<MarcoFotoBlockEntity>> MARCO_BE = BLOCK_ENTITIES.register("marco_foto",
            () -> BlockEntityType.Builder.of(MarcoFotoBlockEntity::new, MARCO_BLOCK.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("principal",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Marco de Fotos"))
                    .icon(() -> new ItemStack(IMAGEN_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(MARCO_ITEM.get());
                        out.accept(IMAGEN_ITEM.get());
                    })
                    .build());
}
