package org.shouto.minesweeper.minesweeper.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HumanoidModel.class)
public interface HumanoidModelAccessorMixin {
    @Accessor("body")
    ModelPart minesweeper$getBody();

    @Accessor("rightArm")
    ModelPart minesweeper$getRightArm();

    @Accessor("leftArm")
    ModelPart minesweeper$getLeftArm();

    @Accessor("rightLeg")
    ModelPart minesweeper$getRightLeg();

    @Accessor("leftLeg")
    ModelPart minesweeper$getLeftLeg();
}
