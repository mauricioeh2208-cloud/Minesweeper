package org.shouto.minesweeper.minesweeper.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.shouto.minesweeper.minesweeper.client.model.MineExplosionGeoModel;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.HashMap;
import java.util.Map;

public class MineExplosionEntityRenderer extends GeoEntityRenderer<MineExplosionEntity, MineExplosionEntityRenderer.MineExplosionRenderState> {
    private static final int FRAME_COUNT = 8;

    public MineExplosionEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new MineExplosionGeoModel());
        this.shadowRadius = 0.0F;
        this.withScale(1.8F);
    }

    @Override
    public MineExplosionRenderState createRenderState(MineExplosionEntity animatable, Void relatedObject) {
        return new MineExplosionRenderState(animatable.getOrCreateFallbackManager());
    }

    @Override
    public void captureDefaultRenderState(
            MineExplosionEntity animatable,
            Void relatedObject,
            MineExplosionRenderState renderState,
            float partialTick
    ) {
        super.captureDefaultRenderState(animatable, relatedObject, renderState, partialTick);

        renderState.addGeckolibData(DataTickets.ANIMATABLE_MANAGER, animatable.getOrCreateFallbackManager());
        renderState.addGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID, getInstanceId(animatable, relatedObject));
        renderState.addGeckolibData(DataTickets.TICK, (double) animatable.tickCount + partialTick);
    }

    @Override
    public void addRenderData(
            MineExplosionEntity animatable,
            Void relatedObject,
            MineExplosionRenderState renderState,
            float partialTick
    ) {
        long instanceId = getInstanceId(animatable, relatedObject);

        renderState.addGeckolibData(
                DataTickets.ANIMATABLE_MANAGER,
                animatable.getAnimatableInstanceCache().getManagerForId(instanceId)
        );
        renderState.addGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID, instanceId);
        renderState.addGeckolibData(DataTickets.PARTIAL_TICK, partialTick);
        renderState.addGeckolibData(DataTickets.TICK, (double) animatable.tickCount + partialTick);
    }

    @Override
    public void adjustModelBonesForRender(
            RenderPassInfo<MineExplosionRenderState> renderPassInfo,
            BoneSnapshots snapshots
    ) {
        int visibleFrame = getVisibleFrame(renderPassInfo.renderState().getAnimatableAge());

        for (int frameIndex = 1; frameIndex <= FRAME_COUNT; frameIndex++) {
            boolean visible = frameIndex == visibleFrame;

            snapshots.ifPresent("frame_" + frameIndex, snapshot -> snapshot
                    .skipRender(!visible)
                    .skipChildrenRender(!visible)
                    .setScale(1.0F, 1.0F, 1.0F));
        }
    }

    private static int getVisibleFrame(double animationAge) {
        double clampedAge = Math.max(0.0D, Math.min(MineExplosionEntity.ANIMATION_DURATION_TICKS - 0.001D, animationAge));
        double progress = clampedAge / MineExplosionEntity.ANIMATION_DURATION_TICKS;

        return Math.min(FRAME_COUNT, (int) (progress * FRAME_COUNT) + 1);
    }

    public static final class MineExplosionRenderState extends EntityRenderState implements GeoRenderState {
        private final AnimatableManager<?> fallbackManager;
        private final Map<DataTicket<?>, Object> geckoData = new HashMap<>();

        public MineExplosionRenderState(AnimatableManager<?> fallbackManager) {
            this.fallbackManager = fallbackManager;
            this.geckoData.put(DataTickets.ANIMATABLE_MANAGER, fallbackManager);
        }

        @Override
        public Map<DataTicket<?>, Object> getDataMap() {
            return geckoData;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <D> D getGeckolibData(DataTicket<D> dataTicket) {
            if (dataTicket == DataTickets.ANIMATABLE_MANAGER || DataTickets.ANIMATABLE_MANAGER.equals(dataTicket)) {
                return (D) this.fallbackManager;
            }

            return GeoRenderState.super.getGeckolibData(dataTicket);
        }
    }
}
