/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.wurstclient.WurstClient;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
	@Inject(at = @At("HEAD"),
		method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z",
		cancellable = true)
	private void onHasBlindnessOrDarkness(Camera camera,
		CallbackInfoReturnable<Boolean> ci)
	{
		if(WurstClient.INSTANCE.getHax().antiBlindHack.isEnabled())
			ci.setReturnValue(false);
	}
	
	@Inject(method = "renderEntity", at = @At("HEAD"))
	private void renderEntity(Entity entity, double cameraX, double cameraY,
		double cameraZ, float tickDelta, MatrixStack matrices,
		VertexConsumerProvider vertexConsumers, CallbackInfo ci)
	{
		if(vertexConsumers instanceof OutlineVertexConsumerProvider p)
			if(entity instanceof PlayerEntity pe
				&& WurstClient.INSTANCE.getHax().playerEspHack.shouldGlow(pe))
				p.setColor(255, 255, 255, 255);
			else if(entity instanceof LivingEntity le
				&& WurstClient.INSTANCE.getHax().mobEspHack.shouldGlow(le))
				p.setColor(255, 255, 255, 255);
	}
}
