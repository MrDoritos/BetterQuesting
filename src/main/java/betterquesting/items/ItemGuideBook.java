package betterquesting.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import betterquesting.core.BetterQuesting;

public class ItemGuideBook extends Item
{
	public ItemGuideBook()
	{
		this.setUnlocalizedName("betterquesting.guide");
		this.setCreativeTab(BetterQuesting.tabQuesting);
	}

    /**
     * Called whenever this item is equipped and the right mouse button is pressed. Args: itemStack, world, entityPlayer
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand)
    {
    	if(world.isRemote && hand == EnumHand.MAIN_HAND)
    	{
    		player.openGui(BetterQuesting.instance, 1, world, 0, 0, 0);
    	}
    	
        return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
    }
	
	@Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack)
    {
		return true;
    }
}
