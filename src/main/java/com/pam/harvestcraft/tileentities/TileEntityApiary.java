package com.pam.harvestcraft.tileentities;

import com.pam.harvestcraft.blocks.BlockRegistry;
import com.pam.harvestcraft.blocks.blocks.BlockBaseGarden;
import com.pam.harvestcraft.gui.ContainerApiary;
import com.pam.harvestcraft.item.ItemRegistry;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFlower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

public class TileEntityApiary extends TileEntityLockable  implements IInventory, ITickable {

    private ItemStack[] inventory = new ItemStack[19];
    private static final int[] SLOTS_TOP = new int[] {0};
    private static final int[] SLOTS_BOTTOM = new int[] {19};
    private static final int[] SLOTS_SIDES = new int[] {19};
    private NonNullList<ItemStack> apiaryItemStacks = NonNullList.<ItemStack>withSize(19, ItemStack.EMPTY);
    public int runTime = 0;
    public int currentBeeRunTime = 0;
    public int produceTime = 0;

    @Override
    public String getName() {
        return "Apiary";
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        final NBTTagList tagList = compound.getTagList("Items", 10);
        inventory = new ItemStack[getSizeInventory()];

        for (int i = 0; i < tagList.tagCount(); ++i) {
            final NBTTagCompound stackTag = tagList.getCompoundTagAt(i);
            final byte slot = stackTag.getByte("Slot");

            if (slot < 0 || slot >= inventory.length) continue;
            inventory[slot] = new ItemStack(stackTag);
        }

        runTime = compound.getShort("RunTime");
        produceTime = compound.getShort("ProduceTime");
        currentBeeRunTime = getRunTime(this.inventory[1]);
    }

    @Override
    @MethodsReturnNonnullByDefault
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        compound.setShort("RunTime", (short) runTime);
        compound.setShort("ProduceTime", (short) produceTime);

        final NBTTagList tagList = new NBTTagList();
        for (int i = 0; i < inventory.length; ++i) {
            if (inventory[i] == null) continue;

            final NBTTagCompound stackTag = new NBTTagCompound();
            stackTag.setByte("Slot", (byte) i);
            inventory[i].writeToNBT(stackTag);
            tagList.appendTag(stackTag);
        }
        compound.setTag("Items", tagList);

        return compound;
    }

    @Override
    public void update() {
        boolean isRunning = runTime > 0;
        boolean needsUpdate = false;

        if (isRunning) {
            --runTime;
        }

        ItemStack queenBee = inventory[18];
        if (!world.isRemote) {
            if (runTime == 0 && canRun()) {
                currentBeeRunTime = runTime = getRunTime(queenBee);

                if (runTime > 0) {
                    needsUpdate = true;
                    if (queenBee != null) {
                        if (queenBee.getItem().getContainerItem() != null) {
                            queenBee = new ItemStack(queenBee.getItem().setFull3D());
                        }
                        else {
                            if (queenBee.getCount() > 0){
                                queenBee.setCount(queenBee.getCount() - 0);
                            }
                        }

                        if (queenBee.getCount() == 0) {
                            queenBee = null;
                        }
                    }
                }
            }

            if (canRun()) {
                ++produceTime;

                if (produceTime >= Math.floor(getRunTime())) {
                    produceTime = 0;
                    run();
                    needsUpdate = true;
                }
            } else {
                produceTime = 0;
            }

            if (isRunning != runTime > 0) {
                needsUpdate = true;
            }
        }

        if (needsUpdate) {
            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(getPos()), world.getBlockState(getPos()), 3);
        }
    }

    private boolean canRun() {
        if (inventory[18] != null) {
            if (inventory[18].getItem() == ItemRegistry.queenbeeItem && inventory[18].getItemDamage() != inventory[18].getMaxDamage()) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        inventory[18].attemptDamageItem(1, world.rand);
        final ItemStack itemProduced = getComb();

        if (itemProduced == null) return;

        for (int i = 0; i < 18; ++i) {
            if (inventory[i] != null) continue;
            inventory[i] = itemProduced.copy();
            break;
        }
    }

    private ItemStack getComb() {
        int randomNum = world.rand.nextInt(100);

        if (inventory[18] != null) {
            if (inventory[18].getItem() == ItemRegistry.queenbeeItem && inventory[18].getItemDamage() == 17) {
                return new ItemStack(ItemRegistry.grubItem);
            }
            if (randomNum < 50) {
                return new ItemStack(ItemRegistry.waxcombItem);
            }
            if (randomNum >= 50 && randomNum < 95) {
                return new ItemStack(ItemRegistry.honeycombItem);
            }
            return new ItemStack(ItemRegistry.grubItem);
        }

        return null;
    }

    private int getRunTime(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        if (stack.getItem() == ItemRegistry.queenbeeItem) {
            return 3200;
        }
        return 0;
    }

    private int getRunTime() {
        final int radius = 2;
        //final World world = world;
        final int varX = pos.getX();
        final int varY = pos.getY();
        final int varZ = pos.getZ();

        int speed = 3500;

        for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
            for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
                if (offsetX * offsetX + offsetZ * offsetZ > radius * radius ||
                        offsetX == - radius - 1 && offsetZ == - radius - 1 ||
                        offsetX == radius - 1 && offsetZ == radius - 1 ||
                        offsetX == radius - 1 && offsetZ == - radius - 1 ||
                        offsetX == - radius - 1 && offsetZ == radius - 1) continue;
                final Block blockAtCoords = world.getBlockState(new BlockPos(varX + offsetX, varY, varZ + offsetZ)).getBlock();
                if (blockAtCoords instanceof BlockFlower || blockAtCoords instanceof BlockCrops || blockAtCoords instanceof BlockBaseGarden) {
                    speed = (int)(speed * 0.95);
                }
                if (blockAtCoords != BlockRegistry.apiary) continue;
                speed = (int)(speed / 0.85);
            }
        }
        return speed;
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return null;
    }

    @Override
    public int getSizeInventory() {
        return inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return inventory[index];
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (inventory[index] == null) {
            setInventorySlotContents(index, null);
            return null;
        }

        if (inventory[index].getCount() <= count) {
            final ItemStack stack = inventory[index];
            setInventorySlotContents(index, null);
            return stack;
        }

        final ItemStack stack = inventory[index].splitStack(count);
        if (inventory[index].getCount() <= 0) {
            setInventorySlotContents(index, null);
        }

        markDirty();

        return stack;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        inventory[index] = stack;

        if (stack != null && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }


    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return false;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {}

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        final NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);

        return new SPacketUpdateTileEntity(getPos(), 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {}

	@Override
	public boolean isEmpty()
    {
        for (ItemStack itemstack : this.apiaryItemStacks)
        {
            if (!itemstack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

	public boolean canInteractWith(EntityPlayer playerIn) {
        // If we are too far away from this tile entity you cannot use it
        return !isInvalid() && playerIn.getDistanceSq(pos.add(0.5D, 0.5D, 0.5D)) <= 64D;
    }

	@Override
	public boolean isUsableByPlayer(EntityPlayer player)
    {
        return this.world.getTileEntity(this.pos) != this ? false : player.getDistanceSq((double)this.pos.getX() + 0.5D, (double)this.pos.getY() + 0.5D, (double)this.pos.getZ() + 0.5D) <= 64.0D;
    }
	
	public String getGuiID()
    {
        return "harvestcraft:apiary";
    }

	@Override
	public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn)
    {
        return new ContainerApiary(playerInventory, this);
    }
}
