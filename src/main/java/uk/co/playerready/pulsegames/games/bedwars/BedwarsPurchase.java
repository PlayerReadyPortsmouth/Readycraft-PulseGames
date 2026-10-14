package uk.co.playerready.pulsegames.games.bedwars;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Prepare a complete finite purchase before touching the real inventory. */
final class BedwarsPurchase {
    private BedwarsPurchase() {}
    static boolean buy(PlayerInventory inventory,Material currency,int cost,ItemStack product) {
        if(cost<=0 || product==null || product.getAmount()<=0) return false;
        ItemStack[] plan=new ItemStack[36]; int funds=0;
        for(int i=0;i<plan.length;i++) {
            ItemStack item=inventory.getItem(i); plan[i]=item==null?null:item.clone();
            if(item!=null && item.getType()==currency && !item.hasItemMeta()) funds+=item.getAmount();
        }
        if(funds<cost) return false;
        int remainingCost=cost;
        for(int i=0;i<plan.length && remainingCost>0;i++) if(plan[i]!=null && plan[i].getType()==currency && !plan[i].hasItemMeta()) {
            int paid=Math.min(plan[i].getAmount(),remainingCost); remainingCost-=paid;
            if(paid==plan[i].getAmount()) plan[i]=null; else plan[i].setAmount(plan[i].getAmount()-paid);
        }
        int remaining=product.getAmount(),limit=Math.max(1,product.getMaxStackSize());
        for(int i=0;i<plan.length && remaining>0;i++) if(plan[i]!=null && plan[i].isSimilar(product)) {
            int add=Math.min(remaining,Math.max(0,limit-plan[i].getAmount()));
            plan[i].setAmount(plan[i].getAmount()+add); remaining-=add;
        }
        for(int i=0;i<plan.length && remaining>0;i++) if(plan[i]==null || plan[i].getType()==Material.AIR) {
            int add=Math.min(remaining,limit); plan[i]=product.clone(); plan[i].setAmount(add); remaining-=add;
        }
        if(remaining!=0) return false;
        for(int i=0;i<plan.length;i++) inventory.setItem(i,plan[i]);
        return true;
    }
    static int count(PlayerInventory inventory,Material material) {
        int count=0; for(int i=0;i<36;i++) { var item=inventory.getItem(i); if(item!=null && item.getType()==material) count+=item.getAmount(); }
        return count;
    }
}
