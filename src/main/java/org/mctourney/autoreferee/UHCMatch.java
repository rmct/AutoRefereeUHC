package org.mctourney.autoreferee;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;

import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;

public class UHCMatch extends AutoRefMatch
{
	// size of a default
	public static final int DEFAULT_SIZE = 1024;

	protected boolean losersBecomeSpectators = false;

	protected boolean acceptingPlayers = false;

	private WorldPregenerationTask worldgen = null;

	public class WorldPregenerationTask extends BukkitRunnable
	{
		private static final int CHUNKS_PER_STEP = 1;

		private int totalChunks = 0;

		public CommandSender recipient = null;

		private LinkedList<Chunk> chunkQueue = Lists.newLinkedList();

		// compares distance of chunk to spawn, places chunks nearer spawn earlier in queue
		public class ChunkSorter implements Comparator<Chunk>
		{
			public int compare(Chunk a, Chunk b)
			{
				int amax = Math.max(Math.abs(a.getX()), Math.abs(a.getZ()));
				int bmax = Math.max(Math.abs(b.getX()), Math.abs(b.getZ()));
				if (amax != bmax) return amax - bmax;

				int amin = Math.min(Math.abs(a.getX()), Math.abs(a.getZ()));
				int bmin = Math.min(Math.abs(b.getX()), Math.abs(b.getZ()));
				return amin - bmin;
			}
		}

		public WorldPregenerationTask(World w, int radius)
		{
			for (int x = -radius; x <= radius; ++x)
			for (int z = -radius; z <= radius; ++z)
				this.chunkQueue.add(w.getChunkAt(x, z));

			this.totalChunks = this.chunkQueue.size();

			// sort chunks on distance to spawn
			Collections.sort(chunkQueue, new ChunkSorter());
		}

		private WorldPregenerationTask(AutoRefRegion region)
		{
			CuboidRegion bound = region.getBoundingCuboid();

			World w = bound.world;

			// get extremes of the bounding region
			Chunk bmin = w.getChunkAt(bound.getMinimumPoint());
			Chunk bmax = w.getChunkAt(bound.getMaximumPoint());

			for (int x = bmin.getX(); x <= bmax.getX(); ++x)
			for (int z = bmin.getZ(); z <= bmax.getZ(); ++z)
				this.chunkQueue.add(w.getChunkAt(x, z));

			this.totalChunks = this.chunkQueue.size();

			// sort chunks on distance to spawn
			Collections.sort(chunkQueue, new ChunkSorter());
		}

		@Override
		public void run()
		{
			// load a batch of chunks
			for (int i = CHUNKS_PER_STEP; i > 0 && this.chunkQueue.size() > 0; --i)
				this.chunkQueue.removeFirst().load(true);

			int percent = (this.totalChunks - this.chunkQueue.size()) * 100 / this.totalChunks;
			String update = String.format("%d%% chunks generated", percent);
			UHCMatch.this.broadcast(ChatColor.DARK_GRAY + update);

			if (this.recipient != null && (!(this.recipient instanceof Player) 
				|| ((Player) this.recipient).getWorld() != UHCMatch.this.getWorld()))
					this.recipient.sendMessage(ChatColor.DARK_GRAY + update);
		}
	}

	public UHCMatch(World world, int size, boolean tmp)
	{
		super(world, tmp);

		this.setRespawnMode(RespawnMode.DISALLOW);

		// to compute chunk radius, take size in blocks, divide by 16 (to get size in
		// chunks), then divide by 2 to convert diameter to radius.
		this.worldgen = new WorldPregenerationTask(world, size/32);
		this.worldgen.runTaskTimer(AutoRefereeUHC.getInstance(), 0L, 20L);
	}

	public void setNotificationRecipient(CommandSender recp)
	{ if (this.worldgen != null) this.worldgen.recipient = recp; }
}
