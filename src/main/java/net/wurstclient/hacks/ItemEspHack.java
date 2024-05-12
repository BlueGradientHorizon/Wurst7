/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.wurstclient.settings.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspBoxStyleSetting boxStyle = new EspBoxStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each item.\n"
			+ "\u00a7lFancy\u00a7r mode shows larger boxes that look better.");
	
	private final CheckboxSetting lines = new CheckboxSetting("Draw lines",
		"Draw tracer lines pointing from center of screen to item entity with corresponding direction to it.",
		false);
	
	private final ItemListSetting itemsListFilter = new ItemListSetting(
		"Items filter",
		"ESP box will be rendered only for item entities containing items from this item list. No items means select all item entities.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Items will be highlighted in this color.", Color.YELLOW);
	
	private final CheckboxSetting showFilteredOutItems = new CheckboxSetting(
		"Render ESP boxes for filtered out items",
		"If enabled, all items which were filtered out earlier by \"Items filter\" will be rendered but with ability to set different color.",
		false);
	
	private final ColorSetting filteredOutItemsColor =
		new ColorSetting("Color for filtered out items",
			"Filtered out items will be highlighted in this color.", Color.RED);
	
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	
	public ItemEspHack()
	{
		super("ItemESP");
		setCategory(Category.RENDER);
		
		addSetting(boxStyle);
		addSetting(boxSize);
		addSetting(lines);
		addSetting(itemsListFilter);
		addSetting(color);
		addSetting(showFilteredOutItems);
		addSetting(filteredOutItemsColor);
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
		items.clear();
		for(Entity entity : MC.world.getEntities())
			if(entity instanceof ItemEntity)
				items.add((ItemEntity)entity);
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
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
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
	
	private boolean shouldBeFiltered(ItemEntity e)
	{
		List<String> itemsListNames = itemsListFilter.getItemNames();
		if(!itemsListNames.isEmpty())
		{
			Item item = e.getStack().getItem();
			String itemName = Registries.ITEM.getId(item).toString();
			
			return !itemsListNames.contains(itemName);
		}
		return false;
	}
	
	private void renderBoxes(MatrixStack matrixStack, float partialTicks,
		RegionPos region)
	{
		float extraSize = boxSize.getExtraSize();
		
		for(ItemEntity e : items)
		{
			boolean ifFilterApplies = shouldBeFiltered(e);
			
			if(ifFilterApplies && !showFilteredOutItems.isChecked())
				continue;
			
			matrixStack.push();
			
			Vec3d lerpedPos = EntityUtils.getLerpedPos(e, partialTicks)
				.subtract(region.toVec3d());
			matrixStack.translate(lerpedPos.x, lerpedPos.y, lerpedPos.z);
			
			matrixStack.scale(e.getWidth() + extraSize,
				e.getHeight() + extraSize, e.getWidth() + extraSize);
			
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			float[] colorF = ifFilterApplies ? filteredOutItemsColor.getColorF()
				: color.getColorF();
			RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.5F);
			
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
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		float[] colorF = color.getColorF();
		RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.5F);
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		Vec3d regionVec = region.toVec3d();
		Vec3d start = RotationUtils.getClientLookVec(partialTicks)
			.add(RenderUtils.getCameraPos()).subtract(regionVec);
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		for(ItemEntity e : items)
		{
			if(shouldBeFiltered(e))
				continue;
			
			Vec3d end = EntityUtils.getLerpedBox(e, partialTicks).getCenter()
				.subtract(regionVec);
			
			bufferBuilder
				.vertex(matrix, (float)start.x, (float)start.y, (float)start.z)
				.next();
			bufferBuilder
				.vertex(matrix, (float)end.x, (float)end.y, (float)end.z)
				.next();
		}
		tessellator.draw();
	}
}
