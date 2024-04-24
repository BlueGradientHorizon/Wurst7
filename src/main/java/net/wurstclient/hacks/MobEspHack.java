/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.*;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.wurstclient.settings.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"mob esp", "MobTracers", "mob tracers"})
public final class MobEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspBoxStyleSetting boxStyle = new EspBoxStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each mob.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final CheckboxSetting boxRotation = new CheckboxSetting(
		"Box rotation", "Rotate ESP box according to entity's body yaw.", true);
	
	private final CheckboxSetting lines = new CheckboxSetting("Draw lines",
		"Draw tracer lines pointing from center of screen to mob entity with corresponding direction to it.",
		false);
	
	private final CheckboxSetting dynamicBoxColor = new CheckboxSetting(
		"Dynamic boxes colors",
		"Boxes colors will change depending on distance to mob from red to green.",
		true);
	
	private final ColorSetting staticBoxColor = new ColorSetting("Boxes colors",
		"If \"Dynamic boxes colors\" is disabled, colors will be set to this static color.",
		Color.WHITE);
	
	private final CheckboxSetting dynamicLineColor = new CheckboxSetting(
		"Dynamic lines colors",
		"Lines colors will change depending on distance to mob from red to green.",
		true);
	
	private final ColorSetting staticLineColor = new ColorSetting(
		"Lines colors",
		"If \"Dynamic lines colors\" is disabled, colors will be set to this static color.",
		Color.WHITE);
	
	private final SliderSetting glowingEffectRadius = new SliderSetting(
		"Glowing effect radius",
		"Enables Minecraft's glowing effect for all visible mobs if distance for each of them in blocks is less than this value.",
		0, 0, 64, 1, SliderSetting.ValueDisplay.INTEGER);
	
	private final CheckboxSetting disableEspIfGlowing = new CheckboxSetting(
		"Disable ESP for mobs within glowing radius.",
		"Disables ESP boxes for all mobs if \"Glowing effect radius\" setting behaviour is currently applied for them.",
		false);
	
	public boolean shouldGlow(LivingEntity le)
	{
		if(glowingEffectRadius.getValue() == 0)
			return false;
		return MC.player != null && MC.player.getPos()
			.distanceTo(le.getPos()) < glowingEffectRadius.getValue();
	}
	
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterHostileSetting.genericVision(false),
			FilterNeutralSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterPassiveSetting.genericVision(false),
			FilterPassiveWaterSetting.genericVision(false),
			FilterBatsSetting.genericVision(false),
			FilterSlimesSetting.genericVision(false),
			FilterPetsSetting.genericVision(false),
			FilterVillagersSetting.genericVision(false),
			FilterZombieVillagersSetting.genericVision(false),
			FilterGolemsSetting.genericVision(false),
			FilterPiglinsSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterZombiePiglinsSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterEndermenSetting
				.genericVision(AttackDetectingEntityFilter.Mode.OFF),
			FilterShulkersSetting.genericVision(false),
			FilterAllaysSetting.genericVision(false),
			FilterInvisibleSetting.genericVision(false),
			FilterNamedSetting.genericVision(false),
			FilterArmorStandsSetting.genericVision(true));
	
	private final ArrayList<LivingEntity> mobs = new ArrayList<>();
	
	public MobEspHack()
	{
		super("MobESP");
		setCategory(Category.RENDER);
		
		addSetting(boxStyle);
		addSetting(boxSize);
		addSetting(boxRotation);
		addSetting(lines);
		addSetting(dynamicBoxColor);
		addSetting(staticBoxColor);
		addSetting(dynamicLineColor);
		addSetting(staticLineColor);
		addSetting(glowingEffectRadius);
		addSetting(disableEspIfGlowing);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		mobs.clear();
		
		Stream<LivingEntity> stream = StreamSupport
			.stream(MC.world.getEntities().spliterator(), false)
			.filter(LivingEntity.class::isInstance).map(e -> (LivingEntity)e)
			.filter(e -> !(e instanceof PlayerEntity))
			.filter(e -> !e.isRemoved() && e.getHealth() > 0);
		
		stream = entityFilters.applyTo(stream);
		
		mobs.addAll(stream.collect(Collectors.toList()));
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(lines.isChecked())
			event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		
		RegionPos region = RenderUtils.getCameraRegion();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		if(boxStyle.isEnabled())
			renderBoxes(matrixStack, partialTicks, region);
		
		if(lines.isChecked())
			renderTracers(matrixStack, partialTicks, region);
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	
	private void renderBoxes(MatrixStack matrixStack, float partialTicks,
		RegionPos region)
	{
		float extraSize = boxSize.getExtraSize();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		for(LivingEntity le : mobs)
		{
			if(disableEspIfGlowing.isChecked() && shouldGlow(le))
				continue;
			
			matrixStack.push();
			
			Vec3d lerpedPos = EntityUtils.getLerpedPos(le, partialTicks)
				.subtract(region.toVec3d());
			matrixStack.translate(lerpedPos.x, lerpedPos.y, lerpedPos.z);
			
			matrixStack.scale(le.getWidth() + extraSize,
				le.getHeight() + extraSize, le.getWidth() + extraSize);
			
			if(boxRotation.isChecked())
				matrixStack.multiply(new Quaternionf()
					.rotationY(-le.bodyYaw * MathHelper.RADIANS_PER_DEGREE));
			
			if(dynamicBoxColor.isChecked())
			{
				float f = MC.player.distanceTo(le) / 20F;
				RenderSystem.setShaderColor(2 - f, f, 0, 0.5F);
			}else
			{
				float[] c = staticBoxColor.getColorF();
				RenderSystem.setShaderColor(c[0], c[1], c[2], 0.5F);
			}
			
			Box bb = new Box(-0.5, 0, -0.5, 0.5, 1, 0.5);
			
			switch(boxStyle.getSelected())
			{
				case FILLED -> RenderUtils.drawSolidBox(bb, matrixStack);
				case EDGES -> RenderUtils.drawOutlinedBox(bb, matrixStack);
				case FILLED_WITH_EDGES ->
				{
					RenderUtils.drawSolidBox(bb, matrixStack);
					RenderUtils.drawOutlinedBox(bb, matrixStack);
				}
			}
			
			matrixStack.pop();
		}
	}
	
	private void renderTracers(MatrixStack matrixStack, float partialTicks,
		RegionPos region)
	{
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION_COLOR);
		
		Vec3d regionVec = region.toVec3d();
		Vec3d start = RotationUtils.getClientLookVec(partialTicks)
			.add(RenderUtils.getCameraPos()).subtract(regionVec);
		
		for(LivingEntity le : mobs)
		{
			Vec3d end = EntityUtils.getLerpedBox(le, partialTicks).getCenter()
				.subtract(regionVec);
			
			float r, g, b;
			
			if(dynamicLineColor.isChecked())
			{
				float f = MC.player.distanceTo(le) / 20F;
				r = MathHelper.clamp(2 - f, 0, 1);
				g = MathHelper.clamp(f, 0, 1);
				b = 0;
			}else
			{
				float[] c = staticLineColor.getColorF();
				r = c[0];
				g = c[1];
				b = c[2];
			}
			
			bufferBuilder
				.vertex(matrix, (float)start.x, (float)start.y, (float)start.z)
				.color(r, g, b, 0.5F).next();
			
			bufferBuilder
				.vertex(matrix, (float)end.x, (float)end.y, (float)end.z)
				.color(r, g, b, 0.5F).next();
		}
		
		tessellator.draw();
	}
	
	// See MinecraftClientMixin.outlineEntities(),
	// WorldRendererMixin.renderEntity()
}
