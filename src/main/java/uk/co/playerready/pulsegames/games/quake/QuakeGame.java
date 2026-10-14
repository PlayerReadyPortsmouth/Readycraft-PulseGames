package uk.co.playerready.pulsegames.games.quake;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
import uk.co.playerready.pulsegames.core.bot.*;
import uk.co.playerready.pulsegames.core.game.*;
import uk.co.playerready.pulsegames.core.util.Text;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Pack-independent arena shooter: authoritative rays, shared human/bot actions. */
public final class QuakeGame extends MiniGame implements BotMovement {
    public enum Weapon {
        RAILGUN(Material.IRON_HOE, "Railgun", 32, 100, 24, 0),
        SCATTERGUN(Material.GOLDEN_HOE, "Scattergun", 12, 40, 9, .16);
        final Material item; final String label; final int range, damage, cooldown; final double spread;
        Weapon(Material item,String label,int range,int damage,int cooldown,double spread) {
            this.item=item;this.label=label;this.range=range;this.damage=damage;this.cooldown=cooldown;this.spread=spread;
        }
    }
    private final Map<UUID,Weapon> weapons=new HashMap<>();
    private final Map<UUID,Integer> health=new HashMap<>();
    private final Map<String,Long> cooldowns=new HashMap<>();
    private final Map<UUID,Long> protectedUntil=new HashMap<>(), botReady=new HashMap<>();
    private final Map<UUID,Location> goals=new HashMap<>();
    private final LongSupplier clock;
    private final class Menu implements InventoryHolder {
        final QuakeGame source=QuakeGame.this; final UUID owner; Inventory inventory;
        Menu(UUID owner){this.owner=owner;}
        public Inventory getInventory(){return inventory;}
    }
    public QuakeGame(GameInstance game){this(game,System::nanoTime);}
    QuakeGame(GameInstance game,LongSupplier clock){super(game);this.clock=clock;}
    @Override public int startFreezeSeconds(){return 8;}
    @Override public boolean environmentalDamage(){return false;}
    @Override public boolean respawnable(){return true;}
    @Override public int respawnDelaySeconds(){return 2;}
    @Override public int timeLimitSeconds(){return Math.max(60,Math.min(900,game.arena().settings().getInt("time-limit",300)));}
    int target(){return Math.max(1,Math.min(100,game.arena().settings().getInt("target-kills",20)));}
    private boolean active(Player p){return game.state()==GameState.RUNNING && !game.frozen() && game.isActive(p) && p.getWorld()==game.world();}
    @Override public void onStart(){
        for(Player p:game.alivePlayers()){
            weapons.put(p.getUniqueId(),game.isBot(p) && ThreadLocalRandom.current().nextBoolean()?Weapon.SCATTERGUN:Weapon.RAILGUN);
            equip(p);
            protectedUntil.put(p.getUniqueId(),clock.getAsLong()+10_000_000_000L);
            if(!game.isBot(p)) game.runLater(1,()->{if(game.state()==GameState.RUNNING && game.isActive(p))open(p);});
        }
        game.broadcast("<aqua>Quake! Choose your weapon. First side to "+target()+" kills wins. Right-click to fire, dash or jump.");
    }
    private static ItemStack item(Material material,String name){
        ItemStack item=new ItemStack(material);item.editMeta(m->m.displayName(Text.mm(name)));return item;
    }
    private void equip(Player p){
        p.getInventory().clear();health.put(p.getUniqueId(),100);
        protectedUntil.put(p.getUniqueId(),clock.getAsLong()+2_000_000_000L);
        Weapon weapon=weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN);
        p.getInventory().setItem(0,item(weapon.item,"<aqua>"+weapon.label+" · right-click to fire"));
        p.getInventory().setItem(1,item(Material.FEATHER,"<green>Dash · 6s cooldown"));
        p.getInventory().setItem(2,item(Material.RABBIT_FOOT,"<green>Jump boost · 8s cooldown"));
        p.getInventory().setItem(4,item(Material.COMPASS,"<yellow>Choose weapon · during spawn protection"));
        p.getInventory().setHeldItemSlot(0);
    }
    private boolean protectedSpawn(Player p){return clock.getAsLong()<protectedUntil.getOrDefault(p.getUniqueId(),0L);}
    private void open(Player p){
        if(game.state()!=GameState.RUNNING||!game.isActive(p)||!protectedSpawn(p))return;
        Menu menu=new Menu(p.getUniqueId());menu.inventory=Bukkit.createInventory(menu,9,Text.mm("<aqua>Quake · choose weapon"));
        menu.inventory.setItem(2,item(Weapon.RAILGUN.item,"<aqua>Railgun · one hit · 1.2s reload"));
        menu.inventory.setItem(6,item(Weapon.SCATTERGUN.item,"<gold>Scattergun · 3 rays · close range"));p.openInventory(menu.inventory);
    }
    @Override public void onInventoryClick(Player p,InventoryClickEvent e){
        e.setCancelled(true);
        if(!(e.getView().getTopInventory().getHolder() instanceof QuakeGame.Menu menu)||menu.source!=this||!menu.owner.equals(p.getUniqueId())
                ||game.state()!=GameState.RUNNING||!game.isActive(p)||!protectedSpawn(p))return;
        Weapon chosen=e.getRawSlot()==2?Weapon.RAILGUN:e.getRawSlot()==6?Weapon.SCATTERGUN:null;
        if(chosen!=null){weapons.put(p.getUniqueId(),chosen);p.getInventory().setItem(0,item(chosen.item,"<aqua>"+chosen.label+" · right-click to fire"));p.closeInventory();}
    }
    @Override public void onInventoryDrag(Player p,InventoryDragEvent e){e.setCancelled(true);}
    @Override public void onInteract(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||e.getItem()==null)return;
        e.setCancelled(true);Player p=e.getPlayer();Material type=e.getItem().getType();
        if(type==Material.COMPASS)open(p);
        else if(type==Material.FEATHER)ability(p,false);
        else if(type==Material.RABBIT_FOOT)ability(p,true);
        else if(type==weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN).item)fire(p);
    }
    private boolean ready(Player p,String action,int ticks){
        String key=p.getUniqueId()+":"+action;long now=clock.getAsLong();
        if(now<cooldowns.getOrDefault(key,0L))return false;
        cooldowns.put(key,now+ticks*50_000_000L);return true;
    }
    boolean ability(Player p,boolean jump){
        if(!active(p)||!ready(p,jump?"jump":"dash",jump?160:120))return false;
        Vector v=p.getLocation().getDirection().setY(0);
        if(v.lengthSquared()>0)v.normalize().multiply(jump?.4:1.3);
        v.setY(jump?.85:.18);p.setVelocity(v);p.setCooldown(jump?Material.RABBIT_FOOT:Material.FEATHER,jump?160:120);return true;
    }
    boolean enemy(Player a,Player b){return !a.equals(b)&&active(b)&&(game.teamOf(a)==null||game.teamOf(a)!=game.teamOf(b));}
    public boolean fire(Player shooter){
        if(!active(shooter))return false;
        Weapon w=weapons.getOrDefault(shooter.getUniqueId(),Weapon.RAILGUN);
        if(!ready(shooter,"fire",w.cooldown))return false;
        protectedUntil.remove(shooter.getUniqueId());shooter.setCooldown(w.item,w.cooldown);
        shooter.playSound(shooter.getLocation(),"minecraft:entity.firework_rocket.blast",.6f,1.7f);
        Location eye=shooter.getEyeLocation();Vector forward=eye.getDirection();Vector right=new Vector(-forward.getZ(),0,forward.getX());
        if(right.lengthSquared()>0)right.normalize();
        Map<Player,Integer> hits=new HashMap<>();
        for(int i=0;i<(w==Weapon.RAILGUN?1:3);i++){
            Vector direction=forward.clone().add(right.clone().multiply((i-1)*w.spread)).normalize();
            var wall=game.world().rayTraceBlocks(eye,direction,w.range,FluidCollisionMode.NEVER,true);
            double range=wall==null?w.range:wall.getHitPosition().distance(eye.toVector());
            var hit=game.world().rayTraceEntities(eye,direction,range,.08,e->e instanceof Player p&&enemy(shooter,p));
            Location end=hit==null?eye.clone().add(direction.clone().multiply(range)):hit.getHitPosition().toLocation(game.world());
            double distance=eye.distance(end);
            for(double n=0;n<distance;n+=.8)game.world().spawnParticle(Particle.END_ROD,eye.clone().add(direction.clone().multiply(n)),1,0,0,0,0);
            if(hit!=null&&hit.getHitEntity() instanceof Player p&&!protectedSpawn(p))hits.merge(p,w.damage,Integer::sum);
        }
        hits.forEach((victim,damage)->hit(shooter,victim,damage));return true;
    }
    void hit(Player shooter,Player victim,int damage){
        if(!active(shooter)||!enemy(shooter,victim)||protectedSpawn(victim))return;
        int left=Math.max(0,health.getOrDefault(victim.getUniqueId(),100)-damage);health.put(victim.getUniqueId(),left);
        shooter.sendActionBar(Text.mm("<green>HIT! "+left+" HP remaining"));shooter.playSound(shooter.getLocation(),"minecraft:entity.arrow.hit_player",1,1.4f);
        victim.sendActionBar(Text.mm("<red>HIT! "+left+" HP"));victim.playHurtAnimation(shooter.getLocation().getYaw());
        if(left==0)game.handleDeath(victim,shooter);
    }
    @Override public void onDeath(Player victim,Player killer){
        if(killer!=null&&enemy(killer,victim)){
            game.addScore(killer,1);game.stats().addKill(killer,game.type().id());
            game.broadcast("<yellow>"+killer.getName()+" <gray>tagged <yellow>"+victim.getName());
            if(sideScore(killer)>=target()){game.end(side(killer),"quake-target");return;}
        }
        game.respawn(victim,2);
    }
    @Override public void onRespawn(Player p){equip(p);}
    @Override public Location respawnLocation(Player p){
        if(game.mode().isTeams())return game.spawnFor(p);
        List<Location> points=new ArrayList<>();
        for(int i=0;i<game.arena().spawnCount();i++)points.add(game.arena().spawn(i,game.world()));
        Collections.shuffle(points);
        return points.stream().max(Comparator.comparingDouble(location->game.alivePlayers().stream()
                .filter(other->enemy(p,other)).mapToDouble(other->other.getLocation().distanceSquared(location))
                .min().orElse(0))).orElseGet(()->game.spawnFor(p));
    }
    @Override public void onSecond(int elapsed){
        for(Player p:game.players())if(!protectedSpawn(p)
                && p.getOpenInventory().getTopInventory().getHolder() instanceof QuakeGame.Menu menu && menu.source==this)p.closeInventory();
    }
    private List<Player> side(Player p){var team=game.teamOf(p);return team==null?List.of(p):game.players().stream().filter(team::contains).toList();}
    private int sideScore(Player p){return side(p).stream().mapToInt(game::score).sum();}
    @Override public void onTimeUp(){
        int best=game.players().stream().mapToInt(this::sideScore).max().orElse(0);
        List<Player> leaders=game.players().stream().filter(p->sideScore(p)==best).toList();
        boolean unique=game.mode().isTeams()?leaders.stream().map(game::teamOf).distinct().count()==1:leaders.size()==1;
        game.end(best>0&&unique?leaders:List.of(),"quake-time");
    }
    @Override public Location botDestination(Player bot){return goals.get(bot.getUniqueId());}
    @Override public void botTick(Player bot,long tick,BotDifficulty difficulty){
        if(!active(bot))return;
        Player target=game.alivePlayers().stream().filter(p->enemy(bot,p)&&bot.hasLineOfSight(p))
                .filter(p->p.getLocation().distanceSquared(bot.getLocation())<=32*32)
                .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(bot.getLocation()))).orElse(null);
        if(target==null){goals.put(bot.getUniqueId(),game.plugin().bots().patrolDestination(game,bot));botReady.remove(bot.getUniqueId());return;}
        goals.put(bot.getUniqueId(),target.getLocation());
        difficulty=game.plugin().bots().difficultyFor(game,target);
        long next=botReady.computeIfAbsent(bot.getUniqueId(),id->tick+20);
        if(tick<next)return;
        botReady.put(bot.getUniqueId(),tick+Math.max(8,difficulty.projectileDelayTicks()));
        Location aim=bot.getEyeLocation();aim.setDirection(target.getEyeLocation().toVector().subtract(aim.toVector()));
        double spread=(100-difficulty.accuracyPercent())*.4;var random=ThreadLocalRandom.current();
        bot.setRotation(aim.getYaw()+(float)(random.nextDouble()*2-1)*(float)spread,aim.getPitch());fire(bot);
        if(tick%40==0)ability(bot,ThreadLocalRandom.current().nextInt(4)==0);
    }
    @Override public List<String> sidebar(Player p){return List.of("<gray>HP: <white>"+health.getOrDefault(p.getUniqueId(),100),
            "<gray>Side kills: <yellow>"+sideScore(p)+"/"+target(),"<gray>Weapon: <aqua>"+weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN).label,
            "<gray>Dash / Jump: slots 2 / 3");}
    @Override public void onEnd(List<Player> winners){onCleanup();}
    @Override public void onCleanup(){
        for(Player p:game.everyone()){
            if(p.getOpenInventory().getTopInventory().getHolder() instanceof QuakeGame.Menu menu && menu.source==this)p.closeInventory();
            for(Material m:List.of(Material.IRON_HOE,Material.GOLDEN_HOE,Material.FEATHER,Material.RABBIT_FOOT))p.setCooldown(m,0);
        }
        weapons.clear();health.clear();cooldowns.clear();protectedUntil.clear();goals.clear();botReady.clear();
    }
}
