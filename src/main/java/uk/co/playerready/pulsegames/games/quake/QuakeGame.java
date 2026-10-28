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
import uk.co.playerready.pulsegames.core.util.Cuboid;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Pack-independent arena shooter: authoritative rays, shared human/bot actions. */
public final class QuakeGame extends MiniGame implements BotMovement {
    public enum Weapon {
        RAILGUN(Material.IRON_HOE, "Railgun", 32, 100, 24, 0),
        SCATTERGUN(Material.GOLDEN_HOE, "Scattergun", 12, 40, 9, .16),
        BLASTER(Material.DIAMOND_HOE, "Pulse blaster", 24, 34, 7, 0);
        final Material item; final String label; final int range, damage, cooldown; final double spread;
        Weapon(Material item,String label,int range,int damage,int cooldown,double spread) {
            this.item=item;this.label=label;this.range=range;this.damage=damage;this.cooldown=cooldown;this.spread=spread;
        }
    }
    public enum Ability {
        DASH(Material.FEATHER,"Dash",120), JUMP(Material.RABBIT_FOOT,"Jump",160),
        SHIELD(Material.SHIELD,"Shield · 50% damage for 2s",240), SPRINT(Material.SUGAR,"Sprint · 3s",200);
        final Material item; final String label; final int cooldown;
        Ability(Material item,String label,int cooldown){this.item=item;this.label=label;this.cooldown=cooldown;}
    }
    private final Map<UUID,List<Ability>> abilities=new HashMap<>();
    private final Map<UUID,Long> shields=new HashMap<>(), sprints=new HashMap<>();
    private final Map<UUID,Float> originalSpeed=new HashMap<>();
    List<Ability> abilities(Player p){return abilities.getOrDefault(p.getUniqueId(),List.of(Ability.DASH,Ability.JUMP));}
    boolean selectAbilities(Player p,Ability first,Ability second){
        if(first==null||second==null||first==second)return false;
        abilities.put(p.getUniqueId(),List.of(first,second));return true;
    }
    private final Map<UUID,Weapon> weapons=new HashMap<>();
    private final Map<UUID,Integer> health=new HashMap<>();
    private final Map<String,Long> cooldowns=new HashMap<>();
    private final Map<UUID,Long> protectedUntil=new HashMap<>(), botReady=new HashMap<>();
    private final Map<UUID,Location> goals=new HashMap<>();
    private final Map<UUID,UUID> botTargets=new HashMap<>();
    private final LongSupplier clock;
    private final class Menu implements InventoryHolder {
        final QuakeGame source=QuakeGame.this; final UUID owner; Inventory inventory;
        Menu(UUID owner){this.owner=owner;}
        public Inventory getInventory(){return inventory;}
    }
    public QuakeGame(GameInstance game){this(game,System::nanoTime);}
    QuakeGame(GameInstance game,LongSupplier clock){super(game);this.clock=clock;}
    @Override public int startFreezeSeconds(){return 15;}
    @Override public boolean environmentalDamage(){return false;}
    @Override public boolean respawnable(){return true;}
    @Override public int respawnDelaySeconds(){return 2;}
    @Override public int timeLimitSeconds(){return Math.max(60,Math.min(900,game.arena().settings().getInt("time-limit",300)));}
    @Override public int killTarget(){return target();}
    int target(){return Math.max(1,Math.min(100,game.arena().settings().getInt("target-kills",20)));}
    private boolean active(Player p){return game.state()==GameState.RUNNING && !game.frozen() && game.isActive(p) && p.getWorld()==game.world();}
    @Override public void onStart(){
        for(Player p:game.alivePlayers()){
            weapons.put(p.getUniqueId(),game.isBot(p)?Weapon.values()[ThreadLocalRandom.current().nextInt(Weapon.values().length)]:Weapon.RAILGUN);
            if(game.isBot(p)){
                List<Ability> choices=new ArrayList<>(List.of(Ability.values()));Collections.shuffle(choices);
                selectAbilities(p,choices.get(0),choices.get(1));
            }
            p.teleport(respawnLocation(p));
            equip(p);
            protectedUntil.put(p.getUniqueId(),clock.getAsLong()+(startFreezeSeconds()+2)*1_000_000_000L);
            if(!game.isBot(p)) game.runLater(1,()->{if(game.state()==GameState.RUNNING && game.isActive(p))open(p);});
        }
        game.broadcast("<aqua>Quake! Choose a weapon and two abilities. First side to "+target()+" kills wins. Right-click your weapon or ability items.");
    }
    private static ItemStack item(Material material,String name){
        ItemStack item=new ItemStack(material);item.editMeta(m->m.displayName(Text.mm(name)));return item;
    }
    private void equip(Player p){
        clearEffects(p);p.getInventory().clear();health.put(p.getUniqueId(),100);
        protectedUntil.put(p.getUniqueId(),clock.getAsLong()+2_000_000_000L);
        Weapon weapon=weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN);
        p.getInventory().setItem(0,item(weapon.item,"<aqua>"+weapon.label+" · right-click to fire"));
        equipAbilities(p);
        p.getInventory().setItem(4,item(Material.COMPASS,"<yellow>Choose loadout · during spawn protection"));
        p.getInventory().setHeldItemSlot(0);
    }
    private boolean protectedSpawn(Player p){return clock.getAsLong()<protectedUntil.getOrDefault(p.getUniqueId(),0L);}
    private void equipAbilities(Player p){
        for(int i=0;i<2;i++){Ability a=abilities(p).get(i);p.getInventory().setItem(i+1,item(a.item,"<green>"+a.label+" · "+a.cooldown/20+"s cooldown"));}
    }
    private void open(Player p){
        if(game.state()!=GameState.RUNNING||!game.isActive(p)||!protectedSpawn(p))return;
        Menu menu=new Menu(p.getUniqueId());menu.inventory=Bukkit.createInventory(menu,36,Text.mm("<aqua>Quake · weapon + two abilities"));
        for(int i=0;i<Weapon.values().length;i++){
            Weapon w=Weapon.values()[i];menu.inventory.setItem(2+i*2,item(w.item,
                    (weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN)==w?"<green>Selected: ":"<aqua>")+w.label+" · "+w.damage+" damage · "+w.cooldown/20.0+"s"));
        }
        for(int i=0;i<Ability.values().length;i++){
            Ability a=Ability.values()[i];int selected=abilities(p).indexOf(a);
            for(int target=0;target<2;target++)menu.inventory.setItem(10+target*9+i*2,item(a.item,(selected==target?"<green>Selected: ":"<yellow>Slot "+(target+1)+": ")+a.label));
        }
        menu.inventory.setItem(31,item(Material.LIME_DYE,"<green>Ready · keep this loadout"));p.openInventory(menu.inventory);
    }
    @Override public void onInventoryClick(Player p,InventoryClickEvent e){
        e.setCancelled(true);
        if(!(e.getView().getTopInventory().getHolder() instanceof QuakeGame.Menu menu)||menu.source!=this||!menu.owner.equals(p.getUniqueId())
                ||game.state()!=GameState.RUNNING||!game.isActive(p)||!protectedSpawn(p))return;
        int slot=e.getRawSlot();
        if(slot==31){p.closeInventory();return;}
        for(int i=0;i<Weapon.values().length;i++)if(slot==2+i*2){
            Weapon chosen=Weapon.values()[i];weapons.put(p.getUniqueId(),chosen);
            p.getInventory().setItem(0,item(chosen.item,"<aqua>"+chosen.label+" · right-click to fire"));open(p);return;
        }
        for(int i=0;i<Ability.values().length;i++)for(int target=0;target<2;target++)if(slot==10+target*9+i*2){
            Ability chosen=Ability.values()[i];List<Ability> current=new ArrayList<>(abilities(p));
            int existing=current.indexOf(chosen);
            if(existing>=0)Collections.swap(current,target,existing);else current.set(target,chosen);
            selectAbilities(p,current.get(0),current.get(1));equipAbilities(p);open(p);return;
        }
    }
    @Override public void onInventoryDrag(Player p,InventoryDragEvent e){e.setCancelled(true);}
    @Override public void onInteract(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||e.getItem()==null)return;
        e.setCancelled(true);Player p=e.getPlayer();Material type=e.getItem().getType();
        if(type==Material.COMPASS)open(p);
        else if(abilities(p).stream().anyMatch(a->a.item==type))ability(p,abilities(p).stream().filter(a->a.item==type).findFirst().orElseThrow());
        else if(type==weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN).item)fire(p);
    }
    private boolean ready(Player p,String action,int ticks){
        String key=p.getUniqueId()+":"+action;long now=clock.getAsLong();
        if(now<cooldowns.getOrDefault(key,0L))return false;
        cooldowns.put(key,now+ticks*50_000_000L);return true;
    }
    boolean ability(Player p,boolean jump){return ability(p,jump?Ability.JUMP:Ability.DASH);}
    boolean ability(Player p,Ability ability){
        if(!active(p)||!abilities(p).contains(ability)||!ready(p,ability.name(),ability.cooldown))return false;
        protectedUntil.remove(p.getUniqueId()); // Acting locks the loadout, preventing four-ability hot swapping.
        long now=clock.getAsLong();
        if(ability==Ability.SHIELD)shields.put(p.getUniqueId(),now+2_000_000_000L);
        else if(ability==Ability.SPRINT){
            originalSpeed.putIfAbsent(p.getUniqueId(),p.getWalkSpeed());p.setWalkSpeed(Math.min(.4f,p.getWalkSpeed()*1.5f));
            long expires=now+3_000_000_000L;sprints.put(p.getUniqueId(),expires);
            game.runLater(60,()->{if(Objects.equals(sprints.get(p.getUniqueId()),expires)){
                Float speed=originalSpeed.remove(p.getUniqueId());if(speed!=null)p.setWalkSpeed(speed);sprints.remove(p.getUniqueId());
            }});
        }else{
            boolean jump=ability==Ability.JUMP;Vector v=p.getLocation().getDirection().setY(0);
            if(v.lengthSquared()>0)v.normalize().multiply(jump?.4:1.3);
            v.setY(jump?.85:.18);p.setVelocity(v);
        }
        p.setCooldown(ability.item,ability.cooldown);p.sendActionBar(Text.mm("<aqua>"+ability.label+" activated"));return true;
    }
    private void clearEffects(Player p){
        Float speed=originalSpeed.remove(p.getUniqueId());if(speed!=null)p.setWalkSpeed(speed);
        sprints.remove(p.getUniqueId());shields.remove(p.getUniqueId());
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
        for(int i=0;i<(w==Weapon.SCATTERGUN?3:1);i++){
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
        if(clock.getAsLong()<shields.getOrDefault(victim.getUniqueId(),0L))damage=(damage+1)/2;
        int left=Math.max(0,health.getOrDefault(victim.getUniqueId(),100)-damage);health.put(victim.getUniqueId(),left);
        shooter.sendActionBar(Text.mm("<green>HIT! "+left+" HP remaining"));shooter.playSound(shooter.getLocation(),"minecraft:entity.arrow.hit_player",1,1.4f);
        victim.sendActionBar(Text.mm("<red>HIT! "+left+" HP"));victim.playHurtAnimation(shooter.getLocation().getYaw());
        if(left==0)game.handleDeath(victim,shooter);
    }
    @Override public void onDeath(Player victim,Player killer){
        clearEffects(victim);
        if(killer!=null&&enemy(killer,victim)){
            game.addScore(killer,1);game.stats().addKill(killer,game.type().id());
            game.broadcast("<yellow>"+killer.getName()+" <gray>tagged <yellow>"+victim.getName());
            if(sideScore(killer)>=target()){game.end(side(killer),"quake-target");return;}
        }
        game.respawn(victim,2);
    }
    @Override public void onQuit(Player p){clearEffects(p);}
    @Override public void onEliminated(Player p){clearEffects(p);}
    @Override public void onRespawn(Player p){equip(p);}
    private Cuboid spawnArea() {
        Cuboid configured = game.arena().region("spawn-area");
        if (configured != null) return configured;
        // Existing maps use the envelope of their reviewed anchors; never guess outer map boundaries.
        if (game.arena().spawnCount() == 0) return null;
        Location first = game.arena().spawn(0, game.world());
        int minX=first.getBlockX(), maxX=minX, minY=first.getBlockY(), maxY=minY, minZ=first.getBlockZ(), maxZ=minZ;
        for (int i=1;i<game.arena().spawnCount();i++) {
            Location point=game.arena().spawn(i,game.world());
            minX=Math.min(minX,point.getBlockX());maxX=Math.max(maxX,point.getBlockX());
            minY=Math.min(minY,point.getBlockY());maxY=Math.max(maxY,point.getBlockY());
            minZ=Math.min(minZ,point.getBlockZ());maxZ=Math.max(maxZ,point.getBlockZ());
        }
        try { return new Cuboid(minX,minY,minZ,maxX,maxY+1,maxZ); }
        catch (IllegalArgumentException tooLarge) { return null; }
    }
    @Override public Location respawnLocation(Player p){
        List<Player> others=game.alivePlayers().stream().filter(other -> !other.equals(p)
                && game.isActive(other) && other.getWorld()==game.world()).toList();
        List<Location> opponents=others.stream().filter(other -> game.teamOf(p)==null || game.teamOf(p)!=game.teamOf(other))
                .map(Player::getLocation).toList();
        Cuboid area=spawnArea();
        if(area!=null) {
            Location random=QuakeSpawns.choose(game.world(),area,others.stream().map(Player::getLocation).toList(),
                    opponents,ThreadLocalRandom.current());
            if(random!=null)return random;
        }
        // Keep reviewed anchors as a fallback when no safe sampled floor is available.
        List<Location> points=new ArrayList<>();
        for(int i=0;i<game.arena().spawnCount();i++)points.add(game.arena().spawn(i,game.world()));
        Collections.shuffle(points);
        return points.stream().max(Comparator.comparingDouble(location->opponents.stream()
                .mapToDouble(location::distanceSquared).min().orElse(0))).orElseGet(()->game.spawnFor(p));
    }
    Location combatDestination(Player bot, Player target, long tick) {
        Location origin=bot.getLocation();
        Vector towards=target.getLocation().toVector().subtract(origin.toVector()).setY(0);
        double distance=towards.length();
        if(distance<.01)towards=new Vector(1,0,0);else towards.normalize();
        Weapon weapon=weapons.getOrDefault(bot.getUniqueId(),Weapon.RAILGUN);
        double ideal=weapon==Weapon.RAILGUN?14:weapon==Weapon.BLASTER?11:7;
        boolean retreat=health.getOrDefault(bot.getUniqueId(),100)<=40 || distance<ideal-2;
        double forward=retreat?-4:distance>ideal+3?4:0;
        double side=((tick/40+bot.getUniqueId().hashCode())&1)==0?3:-3;
        Vector lateral=new Vector(-towards.getZ(),0,towards.getX());
        Cuboid area=spawnArea();
        for(double strafe:new double[]{side,-side,0}) {
            Vector offset=towards.clone().multiply(forward).add(lateral.clone().multiply(strafe));
            // Do not request a retreat across a wall or a drop. Citizens still owns pathfinding.
            Location destination=origin.clone().add(offset);
            boolean clear=true;
            int steps=Math.max(1,(int)Math.ceil(offset.length()*2));
            for(int i=1;i<=steps;i++) {
                Location step=origin.clone().add(offset.clone().multiply((double)i/steps));
                if ((area!=null && !area.contains(step)) || !QuakeSpawns.safe(game.world(),step.getBlockX(),step.getBlockY(),step.getBlockZ())) {clear=false;break;}
            }
            if(clear && offset.lengthSquared()>0)return destination;
        }
        // If boxed in, hold cover instead of charging the opponent while trying to retreat.
        return retreat?origin:target.getLocation();
    }
    @Override public void onSecond(int elapsed){
        for(Player p:game.everyone())if(clock.getAsLong()>=sprints.getOrDefault(p.getUniqueId(),Long.MAX_VALUE)){
            Float speed=originalSpeed.remove(p.getUniqueId());if(speed!=null)p.setWalkSpeed(speed);sprints.remove(p.getUniqueId());
        }
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
                .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(bot.getLocation())
                        * (p.getUniqueId().equals(botTargets.get(bot.getUniqueId()))?.6:1))).orElse(null);
        if(target==null){goals.put(bot.getUniqueId(),game.plugin().bots().patrolDestination(game,bot));botReady.remove(bot.getUniqueId());botTargets.remove(bot.getUniqueId());return;}
        goals.put(bot.getUniqueId(),combatDestination(bot,target,tick));
        difficulty=game.plugin().bots().difficultyFor(game,target);
        if(!target.getUniqueId().equals(botTargets.put(bot.getUniqueId(),target.getUniqueId())))
            botReady.put(bot.getUniqueId(),tick+Math.max(8,difficulty.projectileDelayTicks()));
        long next=botReady.getOrDefault(bot.getUniqueId(),tick+20);
        if(tick<next)return;
        botReady.put(bot.getUniqueId(),tick+Math.max(8,difficulty.projectileDelayTicks()));
        Location aim=bot.getEyeLocation();aim.setDirection(target.getEyeLocation().toVector().subtract(aim.toVector()));
        double spread=(100-difficulty.accuracyPercent())*.4;var random=ThreadLocalRandom.current();
        bot.setRotation(aim.getYaw()+(float)(random.nextDouble()*2-1)*(float)spread,aim.getPitch());fire(bot);
        if(health.getOrDefault(bot.getUniqueId(),100)<=60)ability(bot,Ability.SHIELD);
        if(random.nextInt(3)==0 && goals.get(bot.getUniqueId()).distanceSquared(bot.getLocation())>1) {
            // Jump may accompany evasion; forward dash while aiming at an enemy would undo retreat.
            if(health.getOrDefault(bot.getUniqueId(),100)>40 && bot.getLocation().distanceSquared(target.getLocation())>18*18) ability(bot,false);
            else if(ThreadLocalRandom.current().nextInt(4)==0) ability(bot,true);
            if(ThreadLocalRandom.current().nextInt(3)==0)ability(bot,Ability.SPRINT);
        }
    }
    @Override public List<String> sidebar(Player p){return List.of("<gray>HP: <white>"+health.getOrDefault(p.getUniqueId(),100),
            "<gray>Side kills: <yellow>"+sideScore(p)+"/"+target(),"<gray>Weapon: <aqua>"+weapons.getOrDefault(p.getUniqueId(),Weapon.RAILGUN).label,
            "<gray>Abilities: <white>"+abilities(p).get(0).name()+" / "+abilities(p).get(1).name());}
    @Override public void onEnd(List<Player> winners){onCleanup();}
    @Override public void onCleanup(){
        for(Player p:game.everyone()){
            if(p.getOpenInventory().getTopInventory().getHolder() instanceof QuakeGame.Menu menu && menu.source==this)p.closeInventory();
            clearEffects(p);
            for(Weapon w:Weapon.values())p.setCooldown(w.item,0);
            for(Ability a:Ability.values())p.setCooldown(a.item,0);
        }
        abilities.clear();shields.clear();sprints.clear();originalSpeed.clear();weapons.clear();health.clear();cooldowns.clear();protectedUntil.clear();goals.clear();botReady.clear();botTargets.clear();
    }
    @Override
    public String phaseCountdown() { int seconds = game.startCountdownSeconds(); return seconds > 0 ? "Choose loadout: " + seconds + "s" : null; }

}
