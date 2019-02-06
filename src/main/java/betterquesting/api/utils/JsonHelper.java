package betterquesting.api.utils;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.placeholders.PlaceholderConverter;
import betterquesting.api2.utils.BQThreadedIO;
import com.google.gson.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.Future;

/**
 * Used to read JSON data with pre-made checks for null entries and casting.
 * Missing entries will return a default/blank value instead of null without
 * editing the parent JSON.<br>
 * In the event the requested item, fluid or entity is missing, a place holder will be substituted
 */
public class JsonHelper
{
	private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	public static JsonArray GetArray(JsonObject json, String id)
	{
		if(json == null)
		{
			return new JsonArray();
		}
		
		if(json.has(id) && json.get(id).isJsonArray())
		{
			return json.get(id).getAsJsonArray();
		} else
		{
			return new JsonArray();
		}
	}
	
	public static JsonObject GetObject(JsonObject json, String id)
	{
		if(json == null)
		{
			return new JsonObject();
		}
		
		if(json.has(id) && json.get(id).isJsonObject())
		{
			return json.get(id).getAsJsonObject();
		} else
		{
			return new JsonObject();
		}
	}
	
	public static String GetString(JsonObject json, String id, String def)
	{
		if(json == null)
		{
			return def;
		}
		
		if(json.has(id) && json.get(id).isJsonPrimitive() && json.get(id).getAsJsonPrimitive().isString())
		{
			return json.get(id).getAsString();
		} else
		{
			return def;
		}
	}
	
	public static Number GetNumber(JsonObject json, String id, Number def)
	{
		if(json == null)
		{
			return def;
		}
		
		if(json.has(id) && json.get(id).isJsonPrimitive())
		{
			try
			{
				return json.get(id).getAsNumber();
			} catch(Exception e)
			{
				return def;
			}
		} else
		{
			return def;
		}
	}
	
	public static boolean GetBoolean(JsonObject json, String id, boolean def)
	{
		if(json == null)
		{
			return def;
		}
		
		if(json.has(id) && json.get(id).isJsonPrimitive())
		{
			try
			{
				return json.get(id).getAsBoolean();
			} catch(Exception e)
			{
				return def;
			}
		} else
		{
			return def;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static ArrayList<JsonElement> GetUnderlyingArray(JsonArray array)
	{
		try
		{
			Field field = JsonArray.class.getDeclaredField("elements");
			field.setAccessible(true);
			
			return (ArrayList<JsonElement>)field.get(array);
		} catch(Exception e)
		{
			QuestingAPI.getLogger().log(Level.ERROR, "Unable to retrieve underlying JsonArray:", e);
		}
		
		return null;
	}
	
	public static void ClearCompoundTag(NBTTagCompound tag)
	{
		if(tag == null)
		{
			return;
		}
		
		ArrayList<String> list = new ArrayList<>(tag.getKeySet());
		for(String key : list)
		{
			tag.removeTag(key);
		}
	}
	
	public static JsonObject ReadFromFile(File file)
	{
		Future<JsonObject> task = BQThreadedIO.INSTANCE.enqueue(() -> {
			if(file == null || !file.exists())
			{
				return new JsonObject();
			}
			
			try(InputStreamReader fr = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
			{
				return GSON.fromJson(fr, JsonObject.class);
			} catch(Exception e)
			{
				QuestingAPI.getLogger().log(Level.ERROR, "An error occured while loading JSON from file:", e);
				
				int i = 0;
				File bkup = new File(file.getParent(), "malformed_" + file.getName() + i + ".json");
				
				while(bkup.exists())
				{
					i++;
					bkup = new File(file.getParent(), "malformed_" + file.getName() + i + ".json");
				}
				
				QuestingAPI.getLogger().log(Level.ERROR, "Creating backup at: " + bkup.getAbsolutePath());
				CopyPaste(file, bkup);
				
				return new JsonObject(); // Just a safety measure against NPEs
			}
		});
		
		try
		{
			return task.get(); // Wait for other scheduled file ops to finish
		} catch(Exception e)
		{
		    QuestingAPI.getLogger().error("Unable to read from file " + file, e);
			return new JsonObject();
		}
	}
	
	@SuppressWarnings("ResultOfMethodCallIgnored")
    public static void WriteToFile(File file, JsonObject jObj)
	{
	    final File tmp = new File(file.getAbsolutePath() + ".tmp");
	    
		BQThreadedIO.INSTANCE.enqueue(() -> {
			try
			{
	            if(tmp.exists())
                {
                    tmp.delete();
                } else if(tmp.getParentFile() != null)
                {
                    tmp.getParentFile().mkdirs();
                }
                
                tmp.createNewFile();
			} catch(Exception e)
			{
				QuestingAPI.getLogger().error("An error occured while saving JSON to file:", e);
				return;
			}
			
			try(OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))
			{
			    // Attempt writing
				GSON.toJson(jObj, fw);
				fw.flush();
			} catch(Exception e)
			{
				QuestingAPI.getLogger().error("An error occured while saving JSON to file (File write):", e);
				return;
			}
			
			try(InputStreamReader fr = new InputStreamReader(new FileInputStream(tmp), StandardCharsets.UTF_8))
            {
				// Readback what we wrote to validate it
                GSON.fromJson(fr, JsonObject.class);
            } catch(Exception e)
            {
				QuestingAPI.getLogger().error("An error occured while saving JSON to file (Validation check):", e);
				return;
            }
			
			try
            {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch(Exception e)
            {
				QuestingAPI.getLogger().error("An error occured while saving JSON to file (Temp copy):", e);
            }
		});
	}
	
	public static void CopyPaste(File fileIn, File fileOut)
	{
		if(!fileIn.exists()) return;
		
		try
		{
		    Files.copy(fileIn.toPath(), fileOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch(Exception e)
		{
			QuestingAPI.getLogger().log(Level.ERROR, "Failed copy paste", e);
		}
	}
	
	public static String makeFileNameSafe(String s)
	{
		for(char c : ChatAllowedCharacters.ILLEGAL_FILE_CHARACTERS)
		{
			s = s.replace(c, '_');
		}
		
		return s;
	}
	
	public static boolean isItem(NBTTagCompound json)
	{
		if(json != null && json.hasKey("id") && json.hasKey("Count", 99) && json.hasKey("Damage", 99))
		{
			if(json.hasKey("id", 8))
			{
				 return Item.REGISTRY.containsKey(new ResourceLocation(json.getString("id")));
			} else
			{
				return Item.REGISTRY.getObjectById(json.getInteger("id")) != null;
			}
		}
		
		return false;
	}
	
	public static boolean isFluid(NBTTagCompound json)
	{
		return json != null && json.hasKey("FluidName", 8) && json.hasKey("Amount", 99) && FluidRegistry.getFluid(json.getString("FluidName")) != null;
	}
	
	public static boolean isEntity(NBTTagCompound tags)
	{
		return tags.hasKey("id") && EntityList.isRegistered(new ResourceLocation(tags.getString("id")));
	}
	
	/**
	 * Converts a JsonObject to an ItemStack. May return a placeholder if the correct mods are not installed</br>
	 * This should be the standard way to load items into quests in order to retain all potential data
	 */
	public static BigItemStack JsonToItemStack(NBTTagCompound nbt)
	{
		if(nbt == null || !nbt.hasKey("id"))
		{
			return new BigItemStack(Blocks.STONE);
		}
		
		String jID;
		int count = nbt.getInteger("Count");
		String oreDict = nbt.getString("OreDict");
		int damage = nbt.hasKey("Damage", 99) ? nbt.getInteger("Damage") : -1;
		damage = damage >= 0? damage : OreDictionary.WILDCARD_VALUE;
		
		Item item;
		
		if(nbt.hasKey("id", 99))
		{
			int id = nbt.getInteger("id");
			item = Item.REGISTRY.getObjectById(id); // Old format (numbers)
			jID = "" + id;
		} else
		{
			jID = nbt.getString("id");
			item = Item.REGISTRY.getObject(new ResourceLocation(jID)); // New format (names)
		}
		
		NBTTagCompound tags = null;
		if(nbt.hasKey("tag", 10))
		{
			tags = nbt.getCompoundTag("tag");
		}
		
		return PlaceholderConverter.convertItem(item, jID, count, damage, oreDict, tags);
	}
	
	/**
	 * Use this for quests instead of converter NBT because this doesn't use ID numbers
	 */
	public static NBTTagCompound ItemStackToJson(BigItemStack stack, NBTTagCompound json)
	{
		if(stack == null)
		{
			return json;
		}
		
		ResourceLocation iRes = Item.REGISTRY.getNameForObject(stack.getBaseStack().getItem());
		json.setString("id", iRes == null ? "" : iRes.toString());
		json.setInteger("Count", stack.stackSize);
		json.setString("OreDict", stack.oreDict);
		json.setInteger("Damage", stack.getBaseStack().getItemDamage());
		if(stack.HasTagCompound())
		{
			json.setTag("tag", stack.GetTagCompound());
		}
		return json;
	}
	
	public static FluidStack JsonToFluidStack(NBTTagCompound json)
	{
		String name = json.hasKey("FluidName", 8) ? json.getString("FluidName") : "water";
		int amount = json.getInteger("Amount");
		NBTTagCompound tags = null;
		
		if(json.hasKey("Tag", 10))
		{
			tags = json.getCompoundTag("Tag");
		}
		
		Fluid fluid = FluidRegistry.getFluid(name);
		
		return PlaceholderConverter.convertFluid(fluid, name, amount, tags);
	}
	
	public static NBTTagCompound FluidStackToJson(FluidStack stack, NBTTagCompound json)
	{
		if(stack == null)
		{
			return json;
		}
		
		json.setString("FluidName", FluidRegistry.getFluidName(stack));
		json.setInteger("Amount", stack.amount);
		if(stack.tag != null)
		{
			json.setTag("Tag", stack.tag);
		}
		return json;
	}
	
	public static Entity JsonToEntity(NBTTagCompound tags, World world)
	{
		Entity entity = null;
		
		if(tags.hasKey("id") && EntityList.isRegistered(new ResourceLocation(tags.getString("id"))))
		{
			entity = EntityList.createEntityFromNBT(tags, world);
		}
		
		return PlaceholderConverter.convertEntity(entity, world, tags);
	}
	
	@Deprecated
	public static Entity JsonToEntity(NBTTagCompound tags, World world, boolean allowPlaceholder)
	{
		return JsonToEntity(tags, world);
	}
	
	public static NBTTagCompound EntityToJson(Entity entity, NBTTagCompound json)
	{
		if(entity == null)
		{
			return json;
		}
		
		NBTTagCompound tags = new NBTTagCompound();
		entity.writeToNBTOptional(tags);
		String id = EntityList.getEntityString(entity);
		tags.setString("id", id != null ? id : ""); // Some entities don't write this to file in certain cases
		json.merge(tags);
		return json;
	}
}
