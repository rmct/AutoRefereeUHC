package org.mctourney.autoreferee.commands;

import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import org.apache.commons.cli.CommandLine;

import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.AutoRefereeUHC;
import org.mctourney.autoreferee.UHCMatch;
import org.mctourney.autoreferee.util.commands.AutoRefCommand;
import org.mctourney.autoreferee.util.commands.AutoRefPermission;
import org.mctourney.autoreferee.util.commands.CommandHandler;

import java.util.Date;

public class UHCCommands implements CommandHandler
{
	AutoReferee plugin;

	public UHCCommands(Plugin plugin)
	{
		this.plugin = (AutoReferee) plugin;
	}

	@AutoRefCommand(name={"autoref", "loaduhc"}, argmax=0, options="s+g+z+t+",
		description="Load a UHC match.")
	@AutoRefPermission(console=true, nodes={"autoreferee.admin"})

	public boolean loadUHC(CommandSender sender, AutoRefMatch match, String[] args, CommandLine options)
	{
		String worldname = AutoRefereeUHC.WORLD_PREFIX + Long.toHexString(new Date().getTime());
		WorldCreator creator = WorldCreator.name(worldname);

		int size = UHCMatch.DEFAULT_SIZE;
		if (options.hasOption('z'))
		{
			String szopt = options.getOptionValue('z');
			try { size = Integer.parseInt(szopt); }
			catch (NumberFormatException e)
			{ sender.sendMessage("Not a valid size: " + szopt); }
		}

		if (options.hasOption('s'))
		{
			// parse out the provided seed (numbers are converted verbatim)
			long seed = options.getOptionValue('s').hashCode();
			try { seed = Long.parseLong(options.getOptionValue('s')); }
			catch (NumberFormatException e) {  }

			// set the seed for the world creator
			creator.seed(seed);
		}

		// TODO Custom Generator

		UHCMatch uhcMatch = new UHCMatch(Bukkit.createWorld(creator), size, true).setCreator(sender);

		// specify number of chunks to load per step
		if (options.hasOption('t'))
			try { uhcMatch.setLoadSpeed(Integer.parseInt(options.getOptionValue('t'))); }
			catch (NumberFormatException e) { e.printStackTrace(); }

		plugin.addMatch(uhcMatch);
		return true;
	}
}
