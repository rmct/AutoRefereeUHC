package org.mctourney.autoreferee;

import org.bukkit.plugin.java.JavaPlugin;
import org.mctourney.autoreferee.commands.UHCCommands;

public class AutoRefereeUHC extends JavaPlugin
{
	public static final String WORLD_PREFIX = "world-aruhc-";

	private static AutoRefereeUHC instance = null;

	public static AutoRefereeUHC getInstance()
	{ return instance; }

	@Override
	public void onEnable()
	{
		AutoRefereeUHC.instance = this;

		AutoReferee ar = AutoReferee.getInstance();

		ar.getCommandManager().registerCommands(new UHCCommands(ar), ar);

		getLogger().info(this.getName() + " enabled.");
	}

	@Override
	public void onDisable()
	{
		getLogger().info(this.getName() + " disabled.");
	}
}
