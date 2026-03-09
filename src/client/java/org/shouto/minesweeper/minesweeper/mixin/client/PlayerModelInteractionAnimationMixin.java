package org.shouto.minesweeper.minesweeper.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.shouto.minesweeper.minesweeper.client.MinesweeperClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelInteractionAnimationMixin {
    @Shadow public ModelPart rightSleeve;
    @Shadow public ModelPart leftSleeve;
    @Shadow public ModelPart jacket;
    @Shadow public ModelPart rightPants;
    @Shadow public ModelPart leftPants;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void minesweeper$applyInteractionPose(AvatarRenderState state, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        MinesweeperClientState.PlayerInteractionAnimationSnapshot snapshot =
                MinesweeperClientState.getPlayerInteractionAnimation(state.id, client.level.getGameTime());
        if (snapshot == null) {
            return;
        }

        HumanoidModelAccessorMixin humanoid = (HumanoidModelAccessorMixin) (Object) this;
        ModelPart body = humanoid.minesweeper$getBody();
        ModelPart rightArm = humanoid.minesweeper$getRightArm();
        ModelPart leftArm = humanoid.minesweeper$getLeftArm();
        ModelPart rightLeg = humanoid.minesweeper$getRightLeg();
        ModelPart leftLeg = humanoid.minesweeper$getLeftLeg();

        float progress = snapshot.progress();
        switch (snapshot.kind()) {
            case INTERACT -> applyInteractPose(body, rightArm, leftArm, rightLeg, leftLeg, progress);
            case FLAG_PICKUP -> applyFlagPickupPose(body, rightArm, leftArm, rightLeg, leftLeg, progress);
        }

        syncOverlayParts(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    private static void applyInteractPose(
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg,
            float progress
    ) {
        float reach = holdBlend(progress, 0.24F, 0.84F);

        body.xRot = Mth.lerp(reach, body.xRot, 0.42F);
        rightArm.xRot = Mth.lerp(reach, rightArm.xRot, -2.10F);
        rightArm.yRot = Mth.lerp(reach, rightArm.yRot, 0.24F);
        rightArm.zRot = Mth.lerp(reach, rightArm.zRot, 0.06F);
        leftArm.xRot = Mth.lerp(reach, leftArm.xRot, 0.30F);
        leftArm.yRot = Mth.lerp(reach, leftArm.yRot, -0.08F);
        rightLeg.xRot = Mth.lerp(reach, rightLeg.xRot, -0.32F);
        leftLeg.xRot = Mth.lerp(reach, leftLeg.xRot, -0.24F);
    }

    private static void applyFlagPickupPose(
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg,
            float progress
    ) {
        float grab = holdBlend(progress, 0.20F, 0.88F);
        float pulse = Mth.sin(progress * (float) Math.PI) * 0.08F;

        body.xRot = Mth.lerp(grab, body.xRot, 0.25F + pulse);
        rightArm.xRot = Mth.lerp(grab, rightArm.xRot, -1.65F + pulse);
        leftArm.xRot = Mth.lerp(grab, leftArm.xRot, -1.65F + pulse);
        rightArm.yRot = Mth.lerp(grab, rightArm.yRot, 0.32F);
        leftArm.yRot = Mth.lerp(grab, leftArm.yRot, -0.32F);
        rightArm.zRot = Mth.lerp(grab, rightArm.zRot, 0.08F);
        leftArm.zRot = Mth.lerp(grab, leftArm.zRot, -0.08F);
        rightLeg.xRot = Mth.lerp(grab, rightLeg.xRot, -0.20F);
        leftLeg.xRot = Mth.lerp(grab, leftLeg.xRot, -0.20F);
    }

    private static float holdBlend(float progress, float easeInEnd, float easeOutStart) {
        float value;
        if (progress <= easeInEnd) {
            value = progress / easeInEnd;
        } else if (progress >= easeOutStart) {
            value = (1.0F - progress) / (1.0F - easeOutStart);
        } else {
            value = 1.0F;
        }

        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private void syncOverlayParts(
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg
    ) {
        copyPartPose(rightArm, this.rightSleeve);
        copyPartPose(leftArm, this.leftSleeve);
        copyPartPose(body, this.jacket);
        copyPartPose(rightLeg, this.rightPants);
        copyPartPose(leftLeg, this.leftPants);
    }

    private static void copyPartPose(ModelPart source, ModelPart target) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }
}
