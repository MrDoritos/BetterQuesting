package betterquesting.client.gui.editors.rewards;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import betterquesting.api.client.gui.GuiScreenThemed;
import betterquesting.api.client.gui.controls.GuiButtonThemed;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.enums.EnumPacketAction;
import betterquesting.api.enums.EnumSaveType;
import betterquesting.api.misc.IFactory;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.RenderUtils;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeNative;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.rewards.RewardRegistry;
import com.google.gson.JsonObject;

@SideOnly(Side.CLIENT)
public class GuiRewardEditor extends GuiScreenThemed implements IVolatileScreen, INeedsRefresh
{
	private List<IFactory<? extends IReward>> rewardTypes = new ArrayList<IFactory<? extends IReward>>();
	private List<Integer> rewardIDs = new ArrayList<Integer>();
	private IQuest quest;
	private int qID = -1;
	
	// TODO: Upgrade to scrolling lists
	private int leftScroll = 0;
	private int rightScroll = 0;
	private int maxRows = 0;
	
	public GuiRewardEditor(GuiScreen parent, IQuest quest)
	{
		super(parent, I18n.format("betterquesting.title.edit_rewards", I18n.format(quest.getUnlocalisedName())));
		this.quest = quest;
		this.qID = QuestDatabase.INSTANCE.getKey(quest);
	}
	
	@Override
	public void initGui()
	{
		super.initGui();
		
		rewardTypes = RewardRegistry.INSTANCE.getAll();
		rewardIDs = quest.getRewards().getAllKeys();
		
		maxRows = (sizeY - 64)/20;
		int btnWidth = sizeX/2 - 16;
		
		// Left main buttons
		for(int i = 0; i < maxRows; i++)
		{
			GuiButtonThemed btn = new GuiButtonThemed(this.buttonList.size(), guiLeft + 36, guiTop + 32 + (i*20), btnWidth - 36, 20, "NULL", true);
			this.buttonList.add(btn);
		}
		
		// Left delete buttons
		for(int i = 0; i < maxRows; i++)
		{
			GuiButtonThemed btn = new GuiButtonThemed(this.buttonList.size(), guiLeft + 16, guiTop + 32 + (i*20), 20, 20, "" + TextFormatting.RED + TextFormatting.BOLD + "x", true);
			this.buttonList.add(btn);
		}
		
		// Right main buttons
		for(int i = 0; i < maxRows; i++)
		{
			GuiButtonThemed btn = new GuiButtonThemed(this.buttonList.size(), guiLeft + sizeX/2 + 8, guiTop + 32 + (i*20), btnWidth - 16, 20, "NULL", true);
			this.buttonList.add(btn);
		}
		
		RefreshColumns();
	}
	
	@Override
	public void refreshGui()
	{
		IQuest tmp = QuestDatabase.INSTANCE.getValue(qID);
		
		if(tmp == null)
		{
			mc.displayGuiScreen(parent);
			return;
		}
		
		this.quest = tmp;
		this.rewardIDs = quest.getRewards().getAllKeys();
		RefreshColumns();
	}
	
	@Override
	public void drawScreen(int mx, int my, float partialTick)
	{
		super.drawScreen(mx, my, partialTick);
		
		GlStateManager.color(1F, 1F, 1F, 1F);
		mc.renderEngine.bindTexture(currentTheme().getGuiTexture());
		
		// Left scroll bar
		this.drawTexturedModalRect(guiLeft + sizeX/2 - 16, this.guiTop + 32, 248, 0, 8, 20);
		int s = 20;
		while(s < (maxRows - 1) * 20)
		{
			this.drawTexturedModalRect(guiLeft + sizeX/2 - 16, this.guiTop + 32 + s, 248, 20, 8, 20);
			s += 20;
		}
		this.drawTexturedModalRect(guiLeft + sizeX/2 - 16, this.guiTop + 32 + s, 248, 40, 8, 20);
		this.drawTexturedModalRect(guiLeft + sizeX/2 - 16, this.guiTop + 32 + (int)Math.max(0, s * (float)leftScroll/(rewardIDs.size() - maxRows)), 248, 60, 8, 20);
		
		// Right scroll bar
		this.drawTexturedModalRect(guiLeft + sizeX - 24, this.guiTop + 32, 248, 0, 8, 20);
		s = 20;
		while(s < (maxRows - 1) * 20)
		{
			this.drawTexturedModalRect(guiLeft + sizeX - 24, this.guiTop + 32 + s, 248, 20, 8, 20);
			s += 20;
		}
		this.drawTexturedModalRect(guiLeft + sizeX - 24, this.guiTop + 32 + s, 248, 40, 8, 20);
		this.drawTexturedModalRect(guiLeft + sizeX - 24, this.guiTop + 32 + (int)Math.max(0, s * (float)rightScroll/(rewardTypes.size() - maxRows)), 248, 60, 8, 20);
		
		RenderUtils.DrawLine(width/2, guiTop + 32, width/2, guiTop + sizeY - 32, 2F, getTextColor());
	}
	
	@Override
	public void actionPerformed(GuiButton button)
	{
		super.actionPerformed(button);
		
		int n1 = button.id - 1; // Reward index
		int n2 = n1/maxRows; // Reward listing (0 = quest, 1 = quest delete, 2 = registry)
		int n3 = n1%maxRows + leftScroll; // Quest list index
		int n4 = n1%maxRows + rightScroll; // Registry list index
		
		if(n2 == 0) // Edit reward
		{
			if(n3 >= 0 && n3 < rewardIDs.size())
			{
				IReward reward = quest.getRewards().getValue(rewardIDs.get(n3));
				GuiScreen editor = reward.getRewardEditor(this, quest);
				
				if(editor != null)
				{
					mc.displayGuiScreen(editor);
				} else
				{
					mc.displayGuiScreen(new GuiRewardEditDefault(this, reward));
				}
			}
		} else if(n2 == 1) // Delete reward
		{
			if(!(n3 < 0 || n3 >= rewardIDs.size()))
			{
				quest.getRewards().removeKey(rewardIDs.get(n3));
				SendChanges();
			}
		} else if(n2 == 2) // Add reward
		{
			if(!(n4 < 0 || n4 >= rewardTypes.size()))
			{
				quest.getRewards().add(RewardRegistry.INSTANCE.createReward(rewardTypes.get(n4).getRegistryName()), quest.getRewards().nextKey());
				SendChanges();
			}
		}
	}
	
	@Override
	public void mouseScroll(int mx, int my, int scroll)
	{
		super.mouseScroll(mx, my, scroll);
        
        if(scroll != 0 && isWithin(mx, my, this.guiLeft, this.guiTop, sizeX/2, sizeY))
        {
    		leftScroll = Math.max(0, MathHelper.clamp_int(leftScroll + scroll, 0, rewardIDs.size() - maxRows));
    		RefreshColumns();
        }
        
        if(scroll != 0 && isWithin(mx, my, this.guiLeft + sizeX/2, this.guiTop, sizeX/2, sizeY))
        {
        	rightScroll = Math.max(0, MathHelper.clamp_int(rightScroll + scroll, 0, rewardTypes.size() - maxRows));
        	RefreshColumns();
        }
	}
	
	public void SendChanges()
	{
		JsonObject base = new JsonObject();
		base.add("config", quest.writeToJson(new JsonObject(), EnumSaveType.CONFIG));
		base.add("progress", quest.writeToJson(new JsonObject(), EnumSaveType.PROGRESS));
		NBTTagCompound tags = new NBTTagCompound();
		tags.setInteger("action", EnumPacketAction.EDIT.ordinal()); // Action: Update data
		tags.setInteger("questID", QuestDatabase.INSTANCE.getKey(quest));
		tags.setTag("data", NBTConverter.JSONtoNBT_Object(base, new NBTTagCompound()));
		PacketSender.INSTANCE.sendToServer(new QuestingPacket(PacketTypeNative.QUEST_EDIT.GetLocation(), tags));
	}
	
	public void RefreshColumns()
	{
    	rightScroll = Math.max(0, MathHelper.clamp_int(rightScroll, 0, rewardTypes.size() - maxRows));
		leftScroll = Math.max(0, MathHelper.clamp_int(leftScroll, 0, rewardIDs.size() - maxRows));
		
		List<GuiButton> btnList = this.buttonList;
		
		for(int i = 1; i < btnList.size(); i++)
		{
			GuiButton btn = btnList.get(i);
			int n1 = i - 1; // Reward index
			int n2 = n1/maxRows; // Reward listing (0 = quest, 1 = quest delete, 2 = registry)
			int n3 = n1%maxRows + leftScroll; // Quest list index
			int n4 = n1%maxRows + rightScroll; // Registry list index
			
			if(n2 == 0) // Edit reward
			{
				if(n3 < 0 || n3 >= rewardIDs.size())
				{
					btn.displayString = "NULL";
					btn.visible = btn.enabled = false;
				} else
				{
					btn.visible = btn.enabled = true;
					btn.displayString = I18n.format(quest.getRewards().getValue(rewardIDs.get(n3)).getUnlocalisedName());
				}
			} else if(n2 == 1) // Delete reward
			{
				btn.visible = btn.enabled = !(n3 < 0 || n3 >= rewardIDs.size());
			} else if(n2 == 2) // Add reward
			{
				if(n4 < 0 || n4 >= rewardTypes.size())
				{
					btn.displayString = "NULL";
					btn.visible = btn.enabled = false;
				} else
				{
					btn.visible = btn.enabled = true;
					btn.displayString = rewardTypes.get(n4).getRegistryName().toString();
				}
			}
		}
	}
}
