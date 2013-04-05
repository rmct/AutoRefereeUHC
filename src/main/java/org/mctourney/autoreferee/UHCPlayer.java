package org.mctourney.autoreferee;

import org.bukkit.entity.Player;

public class UHCPlayer extends AutoRefPlayer
{
	public UHCPlayer(Player player)
	{
		super(player, null);
	}

	public UHCPlayer(String name)
	{
		super(name, null);
	}
}
