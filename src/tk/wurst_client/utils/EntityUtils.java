/*
 * Copyright � 2014 - 2016 | Wurst-Imperium | All rights reserved.
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package tk.wurst_client.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import tk.wurst_client.WurstClient;

public class EntityUtils
{
	public static boolean lookChanged;
	public static float yaw;
	public static float pitch;
	
	public static final TargetSettings DEFAULT_SETTINGS = new TargetSettings();
	private static final List<Entity> loadedEntities =
		Minecraft.getMinecraft().theWorld.loadedEntityList;
	
	public synchronized static void faceEntityClient(EntityLivingBase entity)
	{
		float[] rotations = getRotationsNeeded(entity);
		if(rotations != null)
		{
			Minecraft.getMinecraft().thePlayer.rotationYaw = limitAngleChange(
				Minecraft.getMinecraft().thePlayer.prevRotationYaw,
				rotations[0], 55);
			Minecraft.getMinecraft().thePlayer.rotationPitch = rotations[1];
		}
	}
	
	public synchronized static boolean faceEntityPacket(Entity entity)
	{
		float[] rotations = getRotationsNeeded(entity);
		if(rotations != null)
		{
			yaw = limitAngleChange(yaw, rotations[0], 30);
			pitch = rotations[1];
			return yaw == rotations[0];
		}
		return true;
	}
	
	public static float[] getRotationsNeeded(Entity entity)
	{
		if(entity == null)
			return null;
		double diffX = entity.posX - Minecraft.getMinecraft().thePlayer.posX;
		double diffY;
		if(entity instanceof EntityLivingBase)
		{
			EntityLivingBase entityLivingBase = (EntityLivingBase)entity;
			diffY =
				entityLivingBase.posY + entityLivingBase.getEyeHeight() * 0.9
					- (Minecraft.getMinecraft().thePlayer.posY
						+ Minecraft.getMinecraft().thePlayer.getEyeHeight());
		}else
			diffY = (entity.boundingBox.minY + entity.boundingBox.maxY) / 2.0D
				- (Minecraft.getMinecraft().thePlayer.posY
					+ Minecraft.getMinecraft().thePlayer.getEyeHeight());
		double diffZ = entity.posZ - Minecraft.getMinecraft().thePlayer.posZ;
		double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);
		float yaw =
			(float)(Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
		float pitch = (float)-(Math.atan2(diffY, dist) * 180.0D / Math.PI);
		return new float[]{
			Minecraft.getMinecraft().thePlayer.rotationYaw
				+ MathHelper.wrapDegrees(
					yaw - Minecraft.getMinecraft().thePlayer.rotationYaw),
			Minecraft.getMinecraft().thePlayer.rotationPitch
				+ MathHelper.wrapDegrees(
					pitch - Minecraft.getMinecraft().thePlayer.rotationPitch)};
		
	}
	
	public final static float limitAngleChange(final float current,
		final float intended, final float maxChange)
	{
		float change = intended - current;
		if(change > maxChange)
			change = maxChange;
		else if(change < -maxChange)
			change = -maxChange;
		return current + change;
	}
	
	public static int getDistanceFromMouse(Entity entity)
	{
		float[] neededRotations = getRotationsNeeded(entity);
		if(neededRotations != null)
		{
			float neededYaw =
				Minecraft.getMinecraft().thePlayer.rotationYaw
					- neededRotations[0],
				neededPitch = Minecraft.getMinecraft().thePlayer.rotationPitch
					- neededRotations[1];
			float distanceFromMouse = MathHelper
				.sqrt_float(neededYaw * neededYaw + neededPitch * neededPitch);
			return (int)distanceFromMouse;
		}
		return -1;
	}
	
	public static boolean isCorrectEntity(Entity en)
	{
		return isCorrectEntity(en, DEFAULT_SETTINGS);
	}
	
	public static boolean isCorrectEntity(Entity en, TargetSettings settings)
	{
		// non-entities
		if(en == null)
			return false;
		
		// dead entities
		if(en instanceof EntityLivingBase && (((EntityLivingBase)en).isDead
			|| ((EntityLivingBase)en).getHealth() <= 0))
			return false;
		
		// entities outside the range
		if(Minecraft.getMinecraft().thePlayer.getDistanceToEntity(en) > settings
			.getRange())
			return false;
		
		// entities outside the FOV
		if(settings.getFOV() < 360F
			&& getDistanceFromMouse(en) > settings.getFOV() / 2F)
			return false;
		
		// entities behind walls
		if(!settings.targetBehindWalls()
			&& !Minecraft.getMinecraft().thePlayer.canEntityBeSeen(en))
			return false;
		
		// friends
		if(!settings.targetFriends()
			&& WurstClient.INSTANCE.friends.contains(en.getName()))
			return false;
		
		// players
		if(en instanceof EntityPlayer)
		{
			// normal players
			if(!settings.targetPlayers())
			{
				if(!((EntityPlayer)en).isPlayerSleeping()
					&& !((EntityPlayer)en).isInvisible())
					return false;
				
				// sleeping players
			}else if(!settings.targetSleepingPlayers())
			{
				if(((EntityPlayer)en).isPlayerSleeping())
					return false;
				
				// invisible players
			}else if(!settings.targetInvisiblePlayers())
			{
				if(((EntityPlayer)en).isInvisible())
					return false;
			}
			
			// team players
			if(settings.targetTeams() && !checkName(
				((EntityPlayer)en).getDisplayName().getFormattedText(),
				settings.getTeamColors()))
				return false;
			
			// the user
			if(en == Minecraft.getMinecraft().thePlayer)
				return false;
			
			// Freecam entity
			if(((EntityPlayer)en).getName()
				.equals(Minecraft.getMinecraft().thePlayer.getName()))
				return false;
			
			// mobs
		}else if(en instanceof EntityLiving)
		{
			// invisible mobs
			if(((EntityLiving)en).isInvisible())
			{
				if(!settings.targetInvisibleMobs())
					return false;
				
				// animals
			}else if((en instanceof EntityAgeable
				|| en instanceof EntityAmbientCreature
				|| en instanceof EntityWaterMob))
			{
				if(!settings.targetAnimals())
					return false;
				
				// monsters
			}else if((en instanceof EntityMob || en instanceof EntitySlime
				|| en instanceof EntityFlying))
			{
				if(!settings.targetMonsters())
					return false;
				
				// golems
			}else if(en instanceof EntityGolem)
			{
				if(!settings.targetGolems())
					return false;
				
				// other mobs
			}else
				return false;
			
			// team mobs
			if(settings.targetTeams() && ((EntityLiving)en).hasCustomName()
				&& !checkName(((EntityLiving)en).getCustomNameTag(),
					settings.getTeamColors()))
				return false;
			
			// other entities
		}else
			return false;
		
		return true;
	}
	
	private static boolean checkName(String name, boolean[] teamColors)
	{
		// check colors
		String[] colors = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
			"a", "b", "c", "d", "e", "f"};
		boolean hasKnownColor = false;
		for(int i = 0; i < 16; i++)
			if(name.contains("�" + colors[i]))
			{
				hasKnownColor = true;
				if(teamColors[i])
					return true;
			}
		
		// no known color => white
		return !hasKnownColor && teamColors[15];
	}
	
	public static ArrayList<Entity> getValidEntities(TargetSettings settings)
	{
		ArrayList<Entity> closeEntities = new ArrayList<>();
		
		for(Entity entity : loadedEntities)
		{
			if(isCorrectEntity(entity, settings))
				closeEntities.add(entity);
			
			if(closeEntities.size() >= 64)
				break;
		}
		
		return closeEntities;
	}
	
	public static Entity getClosestEntity(TargetSettings settings)
	{
		Minecraft mc = Minecraft.getMinecraft();
		Entity closestEntity = null;
		
		for(Entity entity : mc.theWorld.loadedEntityList)
			if(isCorrectEntity(entity, settings) && (closestEntity == null
				|| mc.thePlayer.getDistanceToEntity(entity) < mc.thePlayer
					.getDistanceToEntity(closestEntity)))
				closestEntity = entity;
			
		return closestEntity;
	}
	
	public static Entity getClosestEntityOtherThan(Entity otherEntity,
		TargetSettings settings)
	{
		Minecraft mc = Minecraft.getMinecraft();
		Entity closestEnemy = null;
		
		for(Entity entity : mc.theWorld.loadedEntityList)
			if(isCorrectEntity(entity, settings) && entity != otherEntity
				&& (closestEnemy == null
					|| mc.thePlayer.getDistanceToEntity(entity) < mc.thePlayer
						.getDistanceToEntity(closestEnemy)))
				closestEnemy = entity;
			
		return closestEnemy;
	}
	
	public static Entity getEntityWithName(String name, TargetSettings settings)
	{
		for(Entity entity : loadedEntities)
			if(isCorrectEntity(entity, settings)
				&& entity.getName().equalsIgnoreCase(name))
				return entity;
			
		return null;
	}
	
	public static Entity getEntityWithId(UUID id, TargetSettings settings)
	{
		for(Entity entity : loadedEntities)
			if(isCorrectEntity(entity, settings)
				&& entity.getUniqueID().equals(id))
				return entity;
			
		return null;
	}
	
	public static class TargetSettings
	{
		public boolean targetFriends()
		{
			return false;
		}
		
		public boolean targetBehindWalls()
		{
			return false;
		}
		
		public float getRange()
		{
			return Float.POSITIVE_INFINITY;
		}
		
		public float getFOV()
		{
			return 360F;
		}
		
		public boolean targetPlayers()
		{
			return WurstClient.INSTANCE.special.targetSpf.players.isChecked();
		}
		
		public boolean targetAnimals()
		{
			return WurstClient.INSTANCE.special.targetSpf.animals.isChecked();
		}
		
		public boolean targetMonsters()
		{
			return WurstClient.INSTANCE.special.targetSpf.monsters.isChecked();
		}
		
		public boolean targetGolems()
		{
			return WurstClient.INSTANCE.special.targetSpf.golems.isChecked();
		}
		
		public boolean targetSleepingPlayers()
		{
			return WurstClient.INSTANCE.special.targetSpf.sleepingPlayers
				.isChecked();
		}
		
		public boolean targetInvisiblePlayers()
		{
			return WurstClient.INSTANCE.special.targetSpf.invisiblePlayers
				.isChecked();
		}
		
		public boolean targetInvisibleMobs()
		{
			return WurstClient.INSTANCE.special.targetSpf.invisibleMobs
				.isChecked();
		}
		
		public boolean targetTeams()
		{
			return WurstClient.INSTANCE.special.targetSpf.teams.isChecked();
		}
		
		public boolean[] getTeamColors()
		{
			return WurstClient.INSTANCE.special.targetSpf.teamColors
				.getSelected();
		}
	}
}
