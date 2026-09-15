package dev.rocks.infinitecraft.client;

import com.mojang.math.Quadrant;
import dev.rocks.infinitecraft.client.discovery.DiscoveryBookClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Predicate;

/** Select baked terrain geometry once per chunk rebuild, including Sodium's rendering path. */
public final class FusionCrafterModels implements ClientModInitializer {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("rocks_infinite_craft", "block/fusion_crafter");

    @Override
    public void onInitializeClient() {
        SpecialItemsTabClient.initialize();
        DiscoveryBookClient.initialize();
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelOnLoad().register((original, context) -> {
            if (!context.state().is(Blocks.CRAFTER)) return original;
            var orientation = context.state().getValue(BlockStateProperties.ORIENTATION);
            // Match vanilla crafter blockstate rotations, including upward/downward placements.
            var x = switch (orientation.front()) {
                case DOWN -> Quadrant.R90;
                case UP -> Quadrant.R270;
                default -> Quadrant.R0;
            };
            var horizontal = orientation.front().getAxis().isVertical() ? orientation.top() : orientation.front();
            int turns = switch (horizontal) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
            if (orientation.front() == Direction.UP) turns = (turns + 2) % 4;
            var variant = new Variant(MODEL).withXRot(x).withYRot(Quadrant.values()[turns]);
            return new BlockStateModel.UnbakedRoot() {
                @Override
                public void resolveDependencies(ResolvableModel.Resolver resolver) {
                    original.resolveDependencies(resolver);
                    variant.resolveDependencies(resolver);
                }
                @Override
                public Object visualEqualityGroup(BlockState state) { return original.visualEqualityGroup(state); }
                @Override
                public BlockStateModel bake(BlockState state, ModelBaker baker) {
                    return new StationModel(original.bake(state, baker), new SingleVariant(variant.bake(baker)));
                }
            };
        }));
    }

    private static final class StationModel extends WrapperBlockStateModel {
        private final BlockStateModel fusion;
        StationModel(BlockStateModel vanilla, BlockStateModel fusion) {
            super(vanilla);
            this.fusion = fusion;
        }

        private BlockStateModel selected(BlockAndTintGetter level, BlockPos pos) {
            return Boolean.TRUE.equals(level.getBlockEntityRenderData(pos)) ? fusion : wrapped;
        }
        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                                        RandomSource random, Predicate<Direction> cullTest) {
            selected(level, pos).emitQuads(emitter, level, pos, state, random, cullTest);
        }
        @Override
        public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return selected(level, pos).createGeometryKey(level, pos, state, random);
        }
        @Override
        public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            return selected(level, pos).particleMaterial(level, pos, state);
        }
        @Override
        public int materialFlags() { return wrapped.materialFlags() | fusion.materialFlags(); }
        @Override
        public boolean hasMaterialFlag(int flag) { return (materialFlags() & flag) != 0; }
        @Override
        public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return selected(level, pos).materialFlags(level, pos, state, random);
        }
        @Override
        public boolean hasMaterialFlag(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, int flag) {
            return (materialFlags(level, pos, state, random) & flag) != 0;
        }
    }
}
