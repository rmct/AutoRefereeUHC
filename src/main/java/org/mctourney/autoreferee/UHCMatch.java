package org.mctourney.autoreferee;

import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;

import org.mctourney.autoreferee.regions.AutoRefRegion;
import org.mctourney.autoreferee.regions.CuboidRegion;

public class UHCMatch extends AutoRefMatch
{
	// size of a default UHC match in blocks
	public static final int DEFAULT_SIZE = 1024;

	// convert players who are eliminated into spectators?
	protected boolean losersBecomeSpectators = false;

	// is this world currently accepting players (preload complete?)
	protected boolean acceptingPlayers = false;

	// match creator - receives updates on load progress
	private CommandSender creator = null;

	// world region (all players share this region)
	private AutoRefRegion matchRegion = null;

	// world generation task
	private WorldPregenerationTask worldgen = null;

	/**
	 * Reason for world pregeneration being paused.
	 */
	private enum PauseReason
	{
		MEMORY, USER;
	}

	private class WorldPregenerationTask extends BukkitRunnable
	{
		private static final int CHUNKS_PER_STEP = 4;

		public static final int MINIMUM_MEMORY = 10 * 1024; // 10 kb

		private static final int PRELOAD_CHUNK_RADIUS = 3;

		private PauseReason pause = null;

		private int totalChunks = 0;

		public int speed = CHUNKS_PER_STEP;

		public long startTime = 0L;

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
			{
				Chunk chunk = w.getChunkAt(x, z);
				this.chunkQueue.add(chunk);
			}

			this.totalChunks = this.chunkQueue.size();

			// sort chunks on distance to spawn
			Collections.sort(chunkQueue, new ChunkSorter());
			this.startTime = System.currentTimeMillis();
		}

		public WorldPregenerationTask(AutoRefRegion region)
		{
			CuboidRegion bound = region.getBoundingCuboid();

			World w = bound.world;

			// get extremes of the bounding region
			Chunk bmin = w.getChunkAt(bound.getMinimumPoint());
			Chunk bmax = w.getChunkAt(bound.getMaximumPoint());

			for (int x = bmin.getX(); x <= bmax.getX(); ++x)
			for (int z = bmin.getZ(); z <= bmax.getZ(); ++z)
			{
				Chunk chunk = w.getChunkAt(x, z);
				this.chunkQueue.add(chunk);
			}

			this.totalChunks = this.chunkQueue.size();

			// sort chunks on distance to spawn
			Collections.sort(chunkQueue, new ChunkSorter());
			this.startTime = System.currentTimeMillis();
		}

		public void setPaused(boolean pause)
		{ this.pause = pause ? PauseReason.USER : null; }

		@Override
		public void run()
		{
			// just quit immediately if paused by user
			if (pause == PauseReason.USER) return;

			if (Runtime.getRuntime().freeMemory() < MINIMUM_MEMORY)
			{
				// notify the users that we are pausing the world generation
				if (pause != PauseReason.MEMORY) this.notify(
					ChatColor.DARK_GRAY + "Waiting for additional memory.");

				pause = PauseReason.MEMORY;
				return;
			}

			if (pause != null) this.notify(
				ChatColor.DARK_GRAY + "Resuming world generation...");
			pause = null;

			// load a batch of chunks
			int prc_before = (this.totalChunks - this.chunkQueue.size()) * 100 / this.totalChunks;
			for (int i = CHUNKS_PER_STEP; i > 0 && this.chunkQueue.size() > 0; --i)
			{
				// load and unload a chunk to force it to generate
				Chunk chunk = this.chunkQueue.removeFirst();
				chunk.load(true); chunk.unload();

				if (!UHCMatch.this.acceptingPlayers)
				{
					int distance = Math.max(Math.abs(chunk.getX()), Math.abs(chunk.getZ()));
					if (this.chunkQueue.isEmpty()) UHCMatch.this.worldPreloadComplete();
				}
			}

			float taken = (System.currentTimeMillis() - startTime) / 1000.0f;
			float workremaining = this.chunkQueue.size() / (float) this.totalChunks;
			int sec = (int) Math.floor(taken * workremaining / (1.0f - workremaining));

			int prc_after = (this.totalChunks - this.chunkQueue.size()) * 100 / this.totalChunks;
			String update = String.format("%d%% chunks generated (~%02d:%02d remaining)", prc_after, sec/60, sec%60);
			if (prc_after / 10 != prc_before / 10) this.notify(ChatColor.GRAY + update);
		}

		private void notify(String message)
		{
			message += String.format(" [%d KB remaining]", Runtime.getRuntime().freeMemory());
			UHCMatch.this.broadcast(message);

			// if the creator is not yet in this world, send update directly
			if (UHCMatch.this.creator instanceof Player &&
				((Player) UHCMatch.this.creator).getWorld() != UHCMatch.this.getWorld())
					UHCMatch.this.creator.sendMessage(message);
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

		this.addStartRegion(new CuboidRegion(this.getWorld(),
			-20, 20, -20, 20, 0, this.getWorld().getMaxHeight()));
		this.setWorldSpawn(new Location(this.getWorld(),
			0, this.getWorld().getHighestBlockYAt(0, 0), 0));

		this.addRegion(this.matchRegion = new CuboidRegion(this.getWorld(),
			-size/2, size/2, -size/2, size/2, 0, this.getWorld().getMaxHeight()));
	}

	@Override
	protected void loadWorldConfiguration()
	{
	}

	@Override
	public void saveWorldConfiguration()
	{
	}

	public UHCMatch setCreator(CommandSender creator)
	{ this.creator = creator; return this; }

	public UHCMatch setLoadSpeed(int speed)
	{ if (this.worldgen != null) this.worldgen.speed = speed; return this; }

	public void pauseWorldGen()
	{ if (this.worldgen != null) this.worldgen.setPaused(true); }

	public void unpauseWorldGen()
	{ if (this.worldgen != null) this.worldgen.setPaused(false); }

	private void worldPreloadComplete()
	{
		if (this.creator instanceof Player)
			this.joinMatch((Player) this.creator);
		this.acceptingPlayers = true;

		this.worldgen.cancel();
		this.worldgen = null;
	}
}
