/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

public final class EspBoxStyleSetting
	extends EnumSetting<EspBoxStyleSetting.EspBoxStyle>
{
	public EspBoxStyleSetting()
	{
		super("Box style", "How object's box will be visually looking.",
			EspBoxStyle.values(), EspBoxStyle.EDGES);
	}
	
	public EspBoxStyleSetting(EspBoxStyle selected)
	{
		super("Box style", "How object's box will be visually looking.",
			EspBoxStyle.values(), selected);
	}
	
	public EspBoxStyleSetting(String name, String description,
		EspBoxStyle selected)
	{
		super(name, description, EspBoxStyle.values(), selected);
	}
	
	public boolean isEnabled()
	{
		return getSelected().isEnabled;
	}
	
	public enum EspBoxStyle
	{
		DISABLED("Disabled", false),
		FILLED("Filled", true),
		EDGES("Edges", true),
		FILLED_WITH_EDGES("Filled with edges", true);
		
		private final String name;
		private final boolean isEnabled;
		
		EspBoxStyle(String name, boolean isEnabled)
		{
			this.name = name;
			this.isEnabled = isEnabled;
		}
		
		public boolean isEnabled()
		{
			return isEnabled;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
