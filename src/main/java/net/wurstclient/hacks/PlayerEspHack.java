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

import net.wurstclient.settings.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
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
import net.wurstclient.settings.filters.FilterInvisibleSetting;
import net.wurstclient.settings.filters.FilterSleepingSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"player esp", "PlayerTracers", "player tracers"})
public final class PlayerEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspBoxStyleSetting boxStyle = new EspBoxStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each player.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");
	
	private final CheckboxSetting boxRotation = new CheckboxSetting(
		"Box rotation", "Rotate ESP box according to entity's body yaw.", true);
	
	private final CheckboxSetting lines = new CheckboxSetting("Draw lines",
		"Draw tracer lines pointing from center of screen to player entity with corresponding direction to it.",
		false);
	
	private final CheckboxSetting dynamicBoxColor = new CheckboxSetting(
		"Dynamic boxes colors",
		"Boxes colors will change depending on distance to another player from red to green.",
		true);
	
	private final ColorSetting staticBoxColor = new ColorSetting("Boxes colors",
		"If \"Dynamic boxes colors\" is disabled, colors will be set to this static color.",
		Color.WHITE);
	
	private final CheckboxSetting dynamicLineColor = new CheckboxSetting(
		"Dynamic lines colors",
		"Lines colors will change depending on distance to another player from red to green.",
		true);
	
	private final ColorSetting staticLineColor = new ColorSetting(
		"Lines colors",
		"If \"Dynamic lines colors\" is disabled, colors will be set to this static color.",
		Color.WHITE);
	
	private final SliderSetting glowingEffectRadius = new SliderSetting(
		"Glowing effect radius",
		"Enables Minecraft's glowing effect for all visible players if distance for each of them in blocks is less than this value.",
		0, 0, 64, 1, SliderSetting.ValueDisplay.INTEGER);
	
	private final CheckboxSetting disableEspIfGlowing = new CheckboxSetting(
		"Disable ESP for players within glowing radius.",
		"Disables ESP boxes for all players if \"Glowing effect radius\" setting behaviour is currently applied for them.",
		false);
	
	public boolean shouldGlow(PlayerEntity pe)
	{
		if(glowingEffectRadius.getValue() == 0)
			return false;
		return MC.player != null && MC.player.getPos()
			.distanceTo(pe.getPos()) < glowingEffectRadius.getValue();
	}
	
	private final EntityFilterList entityFilters = new EntityFilterList(
		new FilterSleepingSetting("Won't show sleeping players.", false),
		new FilterInvisibleSetting("Won't show invisible players.", false));
	
	private final ArrayList<PlayerEntity> players = new ArrayList<>();
	
	public PlayerEspHack()
	{
		super("PlayerESP");
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
	public void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		PlayerEntity player = MC.player;
		ClientWorld world = MC.world;
		
		players.clear();
		Stream<AbstractClientPlayerEntity> stream = world.getPlayers()
			.parallelStream().filter(e -> !e.isRemoved() && e.getHealth() > 0)
			.filter(e -> e != player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> Math.abs(e.getY() - MC.player.getY()) <= 1e6);
		
		stream = entityFilters.applyTo(stream);
		
		players.addAll(stream.collect(Collectors.toList()));
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
		
		// draw boxes
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
		
		for(PlayerEntity pe : players)
		{
			if(disableEspIfGlowing.isChecked() && shouldGlow(pe))
				continue;
			
			matrixStack.push();
			
			Vec3d lerpedPos = EntityUtils.getLerpedPos(pe, partialTicks)
				.subtract(region.toVec3d());
			matrixStack.translate(lerpedPos.x, lerpedPos.y, lerpedPos.z);
			
			matrixStack.scale(pe.getWidth() + extraSize,
				pe.getHeight() + extraSize, pe.getWidth() + extraSize);
			
			if(boxRotation.isChecked())
				matrixStack.multiply(new Quaternionf()
					.rotationY(-pe.bodyYaw * MathHelper.RADIANS_PER_DEGREE));
			
			// set color
			if(WURST.getFriends().contains(pe.getName().getString()))
				RenderSystem.setShaderColor(0, 0, 1, 0.5F);
			else
			{
				if(dynamicBoxColor.isChecked())
				{
					float f = MC.player.distanceTo(pe) / 20F;
					RenderSystem.setShaderColor(2 - f, f, 0, 0.5F);
				}else
				{
					float[] c = staticBoxColor.getColorF();
					RenderSystem.setShaderColor(c[0], c[1], c[2], 0.5F);
				}
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
		
		for(PlayerEntity pe : players)
		{
			Vec3d end = EntityUtils.getLerpedBox(pe, partialTicks).getCenter()
				.subtract(regionVec);
			
			float r, g, b;
			
			if(WURST.getFriends().contains(pe.getName().getString()))
			{
				r = 0;
				g = 0;
				b = 1;
				
			}else
			{
				if(dynamicLineColor.isChecked())
				{
					float f = MC.player.distanceTo(pe) / 20F;
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
