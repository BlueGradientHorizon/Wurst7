/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.hacks.MurderMysteryHack;
import net.wurstclient.util.ChatUtils;

public class MurderMysteryCmd extends Command
{
	public MurderMysteryCmd()
	{
		super("mm",
			"Manages current MurderMystery session information about detected murderers and detectives.",
			"", "Statistics: .mm [stat]", "Clear both lists: .mm c|clear",
			"Clear murderers or detectives list: .mm c|clear m|d");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length < 1)
		{
			stat();
			return;
		}
		
		if(args.length > 2)
			throw new CmdSyntaxError();
		
		switch(args[0].toLowerCase())
		{
			case "", "stat" -> stat();
			case "c", "clear" -> clear(args);
			default -> throw new CmdSyntaxError();
		}
	}
	
	private void stat()
	{
		MurderMysteryHack mm = WURST.getHax().murderMysteryHack;
		ChatUtils.message(mm.getMurderersCommaSeparatedEnumerationString());
		ChatUtils.message(mm.getDetectivesCommaSeparatedEnumerationString());
	}
	
	private void clear(String[] args) throws CmdException
	{
		MurderMysteryHack mm = WURST.getHax().murderMysteryHack;
		
		if(args.length < 2)
		{
			mm.clearLists(true, true);
			ChatUtils.message("Murderers and detectives lists were cleared.");
			return;
		}
		
		switch(args[1])
		{
			case "m" ->
			{
				mm.clearLists(true, false);
				ChatUtils.message("Murderers list was cleared.");
			}
			case "d" ->
			{
				mm.clearLists(false, true);
				ChatUtils.message("Detectives list was cleared.");
			}
			default -> throw new CmdSyntaxError();
		}
	}
}
