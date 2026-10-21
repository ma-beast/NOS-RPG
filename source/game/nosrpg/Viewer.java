package nosrpg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/** Read-only, data-driven map viewer. Java 5 source syntax. */
public final class Viewer extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    static final int W=9,H=7,TILE=32;
    static final class Level {
        int id, tileset;
        char[][] cells=new char[H][W];
    }
    // Garden boss: randomized contiguous growth. Visual-only, never changes map cells.
    private boolean gardenAwakened=false;
    private int gardenOriginX=-1,gardenOriginY=-1,gardenRadius=-1;
    private int gardenStep=0;
    private boolean heroSlimeForm=false;
    private int heroGardenStun=0;
    private boolean gardenDefeated=false,gardenCleansing=false;
    private int cleanseX=-1,cleanseY=-1,cleanseStep=0;
    private final boolean[][] cleanseMask=new boolean[H][W];
    private final javax.swing.Timer cleanseTimer=new javax.swing.Timer(1000,new ActionListener(){
        public void actionPerformed(ActionEvent e){
            if(!gardenCleansing||pauseScreen!=0||heroDead)return;
            cleanseStep++;
            // Organic reverse wave: sequential random neighboring cells, accelerated by 2 per tick.
            if(cleanseStep==1){if(cleanseX>=0&&cleanseX<W&&cleanseY>=0&&cleanseY<H)cleanseMask[cleanseY][cleanseX]=true;}
            else growCleansing((cleanseStep-1)*2);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cleanseMask[y][x])gardenMask[y][x]=false;
            restoreCleansedForms();
            boolean all=true;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(!cleanseMask[y][x])all=false;
            if(all){gardenCleansing=false;cleanseTimer.stop();}
            repaint();
        }
    });
    private void growCleansing(int amount){
        for(int i=0;i<amount;i++){
            java.util.ArrayList<Point> frontier=new java.util.ArrayList<Point>();
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                if(cleanseMask[y][x])continue;
                boolean adjacent=false;
                for(int dy=-1;dy<=1&&!adjacent;dy++)for(int dx=-1;dx<=1;dx++){
                    if(dx==0&&dy==0)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&xx<W&&yy>=0&&yy<H&&cleanseMask[yy][xx]){adjacent=true;break;}
                }
                if(adjacent)frontier.add(new Point(x,y));
            }
            if(frontier.isEmpty())break;
            Point next=frontier.get(gardenRandom.nextInt(frontier.size()));
            cleanseMask[next.y][next.x]=true;
        }
    }
    private void restoreCleansedForms(){
        if(heroSlimeForm&&heroX>=0&&heroY>=0&&heroX<W&&heroY<H&&!gardenMask[heroY][heroX])heroSlimeForm=false;
        for(Slime mob:slimes())if(mob.gardenSlime&&!mob.dead&&!mob.escaped&&
                mob.x>=0&&mob.x<W&&mob.y>=0&&mob.y<H&&!gardenMask[mob.y][mob.x])mob.gardenSlime=false;
    }
    private void defeatGarden(int wizardX,int wizardY){
        if(!gardenAwakened||gardenDefeated)return;
        gardenDefeated=true;gardenCleansing=true;gardenTimer.stop();
        cleanseX=wizardX;cleanseY=wizardY;cleanseStep=0;
        for(int y=0;y<H;y++)java.util.Arrays.fill(cleanseMask[y],false);
        cleanseTimer.restart();
    }
    private void gardenHitByLightning(int x,int y,int radius){
        if(!gardenAwakened||gardenDefeated)return;
        for(int yy=Math.max(0,y-radius);yy<=Math.min(H-1,y+radius);yy++)
            for(int xx=Math.max(0,x-radius);xx<=Math.min(W-1,x+radius);xx++)
                if(gardenMask[yy][xx]){defeatGarden(x,y);return;}
    }
    private final boolean[][] gardenMask=new boolean[H][W];
    private final Random gardenRandom=new Random();
    private final javax.swing.Timer gardenTimer=new javax.swing.Timer(2000,new ActionListener(){
        public void actionPerformed(ActionEvent e){
            if(!gardenAwakened||gardenDefeated||pauseScreen!=0||heroDead)return;
            gardenStep++;
            if(gardenStep==1){
                if(gardenOriginX>=0&&gardenOriginX<W&&gardenOriginY>=0&&gardenOriginY<H)
                    gardenMask[gardenOriginY][gardenOriginX]=true;
            }else growGarden((gardenStep-1)*2);
            applyGardenTransformations();
            repaint();
        }
    });
    private void growGarden(int amount){
        // Rebuild the frontier after every single addition. New growth may branch immediately.
        for(int i=0;i<amount;i++){
            java.util.ArrayList<Point> frontier=new java.util.ArrayList<Point>();
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                if(gardenMask[y][x])continue;
                boolean adjacent=false;
                for(int dy=-1;dy<=1&&!adjacent;dy++)for(int dx=-1;dx<=1;dx++){
                    if(dx==0&&dy==0)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&xx<W&&yy>=0&&yy<H&&gardenMask[yy][xx]){adjacent=true;break;}
                }
                if(adjacent)frontier.add(new Point(x,y));
            }
            if(frontier.isEmpty())break;
            Point next=frontier.get(gardenRandom.nextInt(frontier.size()));
            gardenMask[next.y][next.x]=true;
        }
    }
    private void applyGardenTransformations(){
        if(!gardenAwakened||gardenDefeated)return;
        if(heroX>=0&&heroY>=0&&heroX<W&&heroY<H&&gardenMask[heroY][heroX])heroSlimeForm=true;
        for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.kind!='V'&&mob.kind!='M'&&mob.kind!='m'&&
                mob.x>=0&&mob.x<W&&mob.y>=0&&mob.y<H&&gardenMask[mob.y][mob.x]){
            mob.gardenSlime=true;
            mob.chasing=false;mob.tx=mob.ty=-1;
        }
    }
    private void clearGardenForms(int oldLevel){
        heroSlimeForm=false;heroGardenStun=0;
        java.util.List<Slime> old=slimeStates.get(Integer.valueOf(oldLevel));
        if(old!=null)for(Slime mob:old)mob.gardenSlime=false;
    }
    private void startGardenAt(int x,int y){
        gardenOriginX=x;gardenOriginY=y;gardenRadius=0;gardenStep=0;
        for(int yy=0;yy<H;yy++)java.util.Arrays.fill(gardenMask[yy],false);
        // First garden cell appears only on the first 2-second timer tick.
        if(!gardenDefeated)gardenTimer.restart();
    }
    private int gardenTileset(int x,int y,int original){
        if(gardenAwakened&&gardenMask[y][x])return 3;
        return original;
    }
    private void beginGardenOnFortiethLevel(){
        if(levels.get(selected).id!=40||gardenAwakened||gardenDefeated)return;
        gardenAwakened=true;
        char[][] map=cells();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(map[y][x]=='O'||map[y][x]=='o'){
            startGardenAt(x,y);return;
        }
        // The centre is a fallback only if the central pit was already modified.
        startGardenAt(W/2,H/2);
    }
    // Wizard patrol offsets around the pit immediately below its map V spawn.
    private static final int[] RITUAL_OX={0,1,1,1,0,-1,-1,-1};
    private static final int[] RITUAL_OY={0,0,1,2,2,2,1,0};
    private Slime wizard(){
        for(Slime s:slimes())if(s.kind=='V'&&!s.dead&&!s.escaped)return s;
        return null;
    }
    private int ritualX(int i){Slime w=wizard();return w==null?-1:w.spawnX+RITUAL_OX[i];}
    private int ritualY(int i){Slime w=wizard();return w==null?-1:w.spawnY+RITUAL_OY[i];}
    private int ritualPreviousIndex=-1;
    private boolean ritualMoving=false;
    private int ritualStartX,ritualStartY;
    private void resetWizardVisit(int level){
        java.util.List<Slime> mobs=slimeStates.get(Integer.valueOf(level));
        if(mobs!=null)for(Slime w:mobs)if(w.kind=='V'){
            w.x=w.spawnX;w.y=w.spawnY;w.dx=w.dy=0;w.dead=false;w.escaped=false;
            w.stun=0;w.dir=3;w.alternateStep=false;
        }
    }
    private int ritualSteps=0,ritualIndex=0;
    private boolean ritualFinished=false;
    // 0: normal; 1: approach pit; 2: three staff swings; 4: final lightning; 3: step into pit.
    private int ritualFinalStage=0;
    private long ritualFinalStarted=0L;
    private int ritualStaffSwings=0;
    private int ritualFinalX=4,ritualFinalY=2;
    private int ritualFinalFromX=4,ritualFinalFromY=2;
    private final javax.swing.Timer ritualFinalTimer=new javax.swing.Timer(60,new ActionListener(){
        public void actionPerformed(ActionEvent e){advanceRitualFinal();}
    });
    private void startRitualFinal(){
        ritualFinalStage=1;ritualStaffSwings=0;ritualFinalStarted=System.currentTimeMillis();
        ritualFinalX=ritualX(ritualIndex);ritualFinalY=ritualY(ritualIndex);
        ritualFinalFromX=ritualFinalX;ritualFinalFromY=ritualFinalY;
        if(ritualIndex!=0){
            int prev=(ritualIndex+RITUAL_OX.length-1)%RITUAL_OX.length;
            ritualFacing=wizardDirection(ritualX(prev)-ritualFinalX,ritualY(prev)-ritualFinalY);
        }
        ritualFinalTimer.start();repaint();
    }
    private void advanceRitualFinal(){
        long now=System.currentTimeMillis();
        if(ritualFinalStage==1&&now-ritualFinalStarted>=500){
            if(ritualIndex!=0){
                ritualFinalFromX=ritualFinalX;ritualFinalFromY=ritualFinalY;
                ritualIndex=(ritualIndex+RITUAL_OX.length-1)%RITUAL_OX.length;
                ritualFinalX=ritualX(ritualIndex);ritualFinalY=ritualY(ritualIndex);
                ritualFacing=wizardDirection(ritualFinalX-ritualFinalFromX,ritualFinalY-ritualFinalFromY);
            }else{ritualFinalStage=2;ritualFacing=2;ritualStaffSwings=0;}
            ritualFinalStarted=now;
        }else if(ritualFinalStage==2&&now-ritualFinalStarted>=500){
            ritualStaffSwings++;
            if(ritualStaffSwings>=6){
                ritualFinalStage=4;
                ritualFinalBlast();
            }
            ritualFinalStarted=now;
        }else if(ritualFinalStage==4&&now-ritualFinalStarted>=850){
            clearShockMarks();
            ritualFinalStage=3;ritualFacing=2;ritualFinalStarted=now;
        }else if(ritualFinalStage==3&&now-ritualFinalStarted>=650){
            // Preserve the original smooth step into the pit before sinking.
            ritualFinalStage=5;ritualFinalStarted=now;
        }else if(ritualFinalStage==5&&now-ritualFinalStarted>=400){
            ritualFinished=true;ritualFinalStage=0;ritualFinalTimer.stop();
            Slime w=wizard();if(w!=null){w.escaped=true;w.dx=w.dy=0;}
        }
        repaint();
    }
    // Final ritual lightning is lethal to every mob and to the hero, including slime form.
    private void ritualFinalBlast(){
        final int x=ritualFinalX,y=ritualFinalY;
        gardenHitByLightning(x,y,3);
        for(int yy=Math.max(0,y-3);yy<=Math.min(H-1,y+3);yy++)
            for(int xx=Math.max(0,x-3);xx<=Math.min(W-1,x+3);xx++){
                shocks()[yy][xx]='!';
                for(Slime mob:slimes())if(mob.kind!='V'&&!mob.dead&&!mob.escaped&&mob.x==xx&&mob.y==yy){
                    mob.dead=true;
                }
                if(heroX==xx&&heroY==yy)heroDead=true;
            }
    }
    private boolean ritualAttacking=false;
    private int ritualFacing=3;
    private int ritualHeroStartX=-1,ritualHeroStartY=-1;
    private boolean ritualCrateMoved=false;
    private final java.awt.image.BufferedImage[][] wizardImages=new java.awt.image.BufferedImage[4][4];
    private boolean ritualRoom(){return wizard()!=null;}
    // A new visit to level 30 starts a new ritual, regardless of the hero.
    private void resetRitual(){
        ritualFinalTimer.stop();
        ritualSteps=0;ritualIndex=0;ritualFinished=false;ritualMoving=false;ritualPreviousIndex=-1;
        ritualFinalStage=0;ritualFinalStarted=0L;ritualStaffSwings=0;
        ritualFinalX=ritualX(0);ritualFinalY=ritualY(0);
        ritualFacing=3;ritualAttacking=false;
        ritualHeroStartX=-1;ritualHeroStartY=-1;
        ritualCrateMoved=false;ritualCrateX=-1;ritualCrateY=-1;
    }
    private void loadWizard(){
        String[] dirs={"up","left","down","right"},poses={"stand","step_left","step_right","action"};
        for(int d=0;d<4;d++)for(int a=0;a<4;a++)try{
            wizardImages[d][a]=javax.imageio.ImageIO.read(new File("sprites/mobs/wizard/"+dirs[(d==1)?3:(d==3)?1:d]+"_"+poses[a]+".png"));
        }catch(Exception ignored){}
    }
    private void drawWizard(Graphics2D g,int row){
        Slime w=wizard();
        if(w==null||ritualFinished)return;
        int x=ritualFinalStage>0?ritualFinalX:w.x;
        int y=ritualFinalStage>0?ritualFinalY:w.y;
        if(ritualFinalStage==5)y=ritualFinalY+1; // pit tile reached by the preceding smooth step
        if((ritualMoving&&mobPhase?y+(w.dy>0&&movePhase>=2?1:0)-(w.dy<0&&movePhase>=2?1:0):y)!=row)return;
        boolean walking=ritualMoving&&mobPhase&&movePhase>0;
        int pose=(ritualFinalStage==4||ritualAttacking||(ritualFinalStage==2&&ritualStaffSwings%2==0))?3:
            (walking?(w.alternateStep?1:2):(ritualFinalStage==1?1:0));
        java.awt.image.BufferedImage im=wizardImages[ritualFacing][pose];
        int px=x*TILE+(walking?w.dx*8*movePhase:0);
        int py=y*TILE+(walking?w.dy*8*movePhase:0);
        if(ritualFinalStage==1){
            double fraction=Math.min(1.0,(System.currentTimeMillis()-ritualFinalStarted)/500.0);
            px=(int)Math.round((ritualFinalFromX+(ritualFinalX-ritualFinalFromX)*fraction)*TILE);
            py=(int)Math.round((ritualFinalFromY+(ritualFinalY-ritualFinalFromY)*fraction)*TILE);
        }else if(ritualFinalStage==3){
            // Original 650-ms smooth step into the pit, unchanged.
            double fraction=Math.min(1.0,(System.currentTimeMillis()-ritualFinalStarted)/650.0);
            py+=(int)Math.round(TILE*fraction);
        }else if(ritualFinalStage==5){
            // After the step, sink at the actual pit location.
            int phase=Math.min(3,(int)((System.currentTimeMillis()-ritualFinalStarted)/100L));
            java.awt.image.BufferedImage fallPose=wizardImages[ritualFacing][(phase%2)+1];
            if(fallPose!=null)im=fallPose;
            Graphics2D clipped=(Graphics2D)g.create();
            try{
                clipped.clipRect(px-16,py-24,64,40);
                if(im!=null)clipped.drawImage(im,px,py+32-im.getHeight()-8+phase*10,null);
                else {clipped.setColor(new Color(135,50,170));clipped.fillOval(px+6,py-5+phase*10,20,28);}
            }finally{clipped.dispose();}
            return;
        }
        if(im!=null)g.drawImage(im,px,py+32-im.getHeight()-8,null);
        else {g.setColor(new Color(135,50,170));g.fillOval(x*TILE+6,y*TILE-5,20,28);}
    }
    private boolean ritualSight(int x,int y){
        int bx=ritualX(ritualIndex),by=ritualY(ritualIndex);
        if(x!=bx&&y!=by)return false;
        int dx=Integer.compare(x,bx),dy=Integer.compare(y,by);
        for(int px=bx+dx,py=by+dy;px!=x||py!=y;px+=dx,py+=dy){
            char c=cells()[py][px];
            if(c=='#'||c=='W'||c=='B'||c=='b'||c=='T'||c=='t')return false;
        }
        return true;
    }
    private void ritualBlast(){
        ritualAttacking=true;
        int x=ritualX(ritualIndex),y=ritualY(ritualIndex);
        gardenHitByLightning(x,y,3);
        for(int yy=Math.max(0,y-3);yy<=Math.min(H-1,y+3);yy++)
            for(int xx=Math.max(0,x-3);xx<=Math.min(W-1,x+3);xx++){
                shocks()[yy][xx]='!';
                for(Slime mob:slimes())if(mob.kind!='V'&&!mob.dead&&!mob.escaped&&mob.x==xx&&mob.y==yy){mob.stun=10;mob.dx=mob.dy=0;}
                if(heroX==xx&&heroY==yy){
                    if(heroSlimeForm){heroGardenStun=10;queuedMoves.clear();}
                    else heroDead=true;
                }
            }
        // The action sprite stays visible through the mob movement animation.
    }
    private int wizardDirection(int dx,int dy){
        if(dx>0)return 3;
        if(dx<0)return 1;
        if(dy>0)return 2;
        return 0;
    }
    private void ritualTurn(){
        if(!ritualRoom()||ritualFinished||ritualFinalStage>0||heroDead)return;
        ritualAttacking=false;
        ritualSteps++;
        if(ritualSteps>=100){startRitualFinal();return;}
        boolean moved=heroX!=ritualHeroStartX||heroY!=ritualHeroStartY;
        if((moved&&ritualSight(heroX,heroY))||ritualCrateMoved){
            if(moved&&ritualSight(heroX,heroY)){ritualBlast();return;}
            // The crate only triggers the ritual when its new position is visible.
            if(ritualSight(ritualCrateX,ritualCrateY)){ritualBlast();return;}
        }
        int next=(ritualIndex+1)%RITUAL_OX.length;
        int nx=ritualX(next),ny=ritualY(next);
        char c=cells()[ny][nx];
        boolean blocked=c=='#'||c=='B'||c=='b'||c=='W'||c=='T'||c=='t';
        for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==nx&&mob.y==ny)blocked=true;
        if(blocked){
            ritualBlast();
            if(c=='#'||c=='B'||c=='b'||c=='W'||c=='T'||c=='t')cells()[ny][nx]='.';
            for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==nx&&mob.y==ny)mob.dead=true;
            return;
        }
        Slime w=wizard();
        if(w==null)return;
        // Keep the route index at the last committed tile until movement succeeds.
        int d=wizardDirection(nx-w.x,ny-w.y);
        ritualFacing=d;
        w.dir=d;w.dx=nx-w.x;w.dy=ny-w.y;
        ritualMoving=true;
    }
    private int ritualCrateX=-1,ritualCrateY=-1;
    private final java.util.List<Level> levels;
    private int selected;
    private int heroType=0;
    private boolean deathHandled=false;
    private long deathFadeStarted=0L;
    private boolean deathFadeActive=false;
    private final javax.swing.Timer deathFadeTimer=new javax.swing.Timer(40,new ActionListener(){public void actionPerformed(ActionEvent e){repaint();if(System.currentTimeMillis()-deathFadeStarted>=2000L)((javax.swing.Timer)e.getSource()).stop();}});
    private void beginDeathFade(){
        if(deathFadeActive)return;
        deathHandled=true;deathFadeActive=true;deathFadeStarted=System.currentTimeMillis();
        moveTimer.stop();superSlimeTimer.stop();queuedMoves.clear();
        deathFadeTimer.start();repaint();
    }
    private void exitAfterDeath(){if(!deathFadeActive)return;deathFadeTimer.stop();saveDeathAndExit();}

    private final javax.swing.Timer deathTimer;
    private boolean fromExit=false;
    private int heroX=-1,heroY=-1;
    private int facing=2; // 0=up, 1=left, 2=down, 3=right
    private int stride=0; // reserved: alternate leading foot on each move
    private int moveDX,moveDY,movePhase;
    private int crateX=-1,crateY=-1;
    private final java.util.Map<Integer,char[][]> levelStates=new HashMap<Integer,char[][]>();
    private boolean moving=false;
    private boolean heroDead=false;
    // 0.5.45.6: visual-only spike death for heroes. No changes to trap rules.
    private static final class SpikeDeath {
        int x,y,frame;
        final java.awt.image.BufferedImage[] poses=new java.awt.image.BufferedImage[6];
    }
    private SpikeDeath spikeDeath=null;
    private final javax.swing.Timer spikeTimer=new javax.swing.Timer(100,new ActionListener(){
        public void actionPerformed(ActionEvent e){
            if(spikeDeath==null){spikeTimer.stop();return;}
            spikeDeath.frame++;
            if(spikeDeath.frame>=6){
                spikeTimer.stop();spikeDeath=null;heroDead=true;syncSuperSlimeTimer();
            }
            repaint();
        }
    });
    private void beginHeroSpikeDeath(int x,int y){
        if(spikeDeath!=null||heroDead)return;
        SpikeDeath f=new SpikeDeath();f.x=x;f.y=y;
        for(int i=0;i<f.poses.length;i++){
            java.awt.image.BufferedImage b=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg=b.createGraphics();
            try{
                int pose=gardenRandom.nextInt(3);
                if(heroSlimeForm){
                    java.awt.image.BufferedImage img=MOB_IMAGES[0][pose];
                    if(img!=null)bg.drawImage(img,16+(32-img.getWidth())/2,56-img.getHeight(),null);
                    else drawMob(bg,'S',16,24,3,facing,false,false);
                }else{
                    java.awt.image.BufferedImage img=null;
                    if(heroType==0)img=BUCKET_HERO_IMAGES[facing][pose];
                    else if(heroType==1)img=POLICE_HERO_IMAGES[facing][pose];
                    else if(heroType==2)img=SCIENTIST_HERO_IMAGES[facing][pose];
                    else img=FIGHTER_HERO_IMAGES[facing][pose];
                    if(img!=null)bg.drawImage(img,16,56-img.getHeight(),null);
                    else drawHero(bg,16,16,heroType);
                }
            }finally{bg.dispose();}
            f.poses[i]=b;
        }
        spikeDeath=f;
        queuedMoves.clear();moving=false;mobPhase=false;moveTimer.stop();movePhase=0;
        spikeTimer.start();repaint();
    }
    private void drawHeroSpikeDeath(Graphics2D g){
        if(spikeDeath==null)return;
        SpikeDeath f=spikeDeath;
        // Contact, quarter-turn, upside-down, three-quarter-turn, full turn, landing.
        int[] degrees={0,90,180,270,360,360};
        int lift=(f.frame==0||f.frame==5)?0:16;
        Graphics2D spin=(Graphics2D)g.create();
        try{
            spin.translate(f.x*TILE-16,f.y*TILE-40-lift);
            spin.rotate(Math.toRadians(degrees[f.frame]),32,36);
            spin.drawImage(f.poses[f.frame],0,0,null);
        }finally{spin.dispose();}
    }
    private boolean heroInSpikeAnimation(){return spikeDeath!=null;}

    // 0.5.45.7: visual-only spike falls for mobs and Ogre; game rules unchanged.
    private static final class EnemySpikeDeath {
        int x,y,frame; boolean ogre;
        final java.awt.image.BufferedImage[] poses=new java.awt.image.BufferedImage[6];
    }
    private final java.util.List<EnemySpikeDeath> enemySpikeDeaths=new java.util.ArrayList<EnemySpikeDeath>();
    private final javax.swing.Timer enemySpikeTimer=new javax.swing.Timer(100,new ActionListener(){
        public void actionPerformed(ActionEvent e){
            for(java.util.Iterator<EnemySpikeDeath> it=enemySpikeDeaths.iterator();it.hasNext();){
                EnemySpikeDeath f=it.next();if(++f.frame>=6)it.remove();
            }
            if(enemySpikeDeaths.isEmpty())enemySpikeTimer.stop();
            repaint();
        }
    });
    private void beginEnemySpikeDeath(Slime mob){
        if(mob.kind=='V')return; // Wizard is immune.
        EnemySpikeDeath f=new EnemySpikeDeath();f.x=mob.x;f.y=mob.y;
        int d=Math.max(0,Math.min(3,mob.dir));
        for(int i=0;i<6;i++){
            java.awt.image.BufferedImage b=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg=b.createGraphics();
            try{
                int pose=gardenRandom.nextInt(3);
                char c=mob.gardenSlime?'S':mob.kind;
                java.awt.image.BufferedImage img=null;
                if(c=='L'||c=='R')img=SKELETON_IMAGES[(d==1?3:d==3?1:d)][pose];
                else if(c=='P')img=SPIDER_IMAGES[d][pose];
                else if(c=='N')img=ORC_IMAGES[d][pose];
                else if(c=='G')img=GOBLIN_IMAGES[d][pose];
                else if(c=='K')img=RAT_IMAGES[d][pose];
                else if(c=='S'||c=='s')img=MOB_IMAGES[mob.gardenSlime?0:(levels.get(selected).tileset==1?1:levels.get(selected).tileset==2?2:0)][pose];
                if(img!=null)bg.drawImage(img,16+(32-img.getWidth())/2,56-img.getHeight(),null);
                else drawMob(bg,c,16,24,levels.get(selected).tileset,d,false,false);
            }finally{bg.dispose();}
            f.poses[i]=b;
        }
        enemySpikeDeaths.add(f);enemySpikeTimer.start();repaint();
    }
    private void beginOgreSpikeDeath(){
        EnemySpikeDeath f=new EnemySpikeDeath();f.x=ogreX;f.y=ogreY;f.ogre=true;
        for(int i=0;i<6;i++){
            java.awt.image.BufferedImage b=new java.awt.image.BufferedImage(96,96,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg=b.createGraphics();
            try{
                java.awt.image.BufferedImage img=OGRE_IMAGES[ogreFacing][gardenRandom.nextInt(3)];
                if(img!=null)bg.drawImage(img,(96-img.getWidth())/2,80-img.getHeight(),null);
                else drawOgre(bg,32,48);
            }finally{bg.dispose();}
            f.poses[i]=b;
        }
        enemySpikeDeaths.add(f);enemySpikeTimer.start();repaint();
    }
    private void drawEnemySpikeDeaths(Graphics2D g){
        int[] degrees={0,90,180,270,360,360};
        for(EnemySpikeDeath f:enemySpikeDeaths){
            int size=f.ogre?96:64;
            int lift=(f.frame==0||f.frame==5)?0:16;
            Graphics2D spin=(Graphics2D)g.create();
            try{
                spin.translate(f.x*TILE-(size-32)/2,f.y*TILE-(f.ogre?72:40)-lift);
                spin.rotate(Math.toRadians(degrees[f.frame]),size/2,size/2+4);
                spin.drawImage(f.poses[f.frame],0,0,null);
            }finally{spin.dispose();}
        }
    }

    private boolean mobPhase=false;
    private boolean spaceHeld=false, spaceUsed=false;
    // Buffered direction presses, captured with their SPACE modifier at keypress time.
    private final java.util.ArrayDeque<int[]> queuedMoves=new java.util.ArrayDeque<int[]>();
    private void queueOrMove(int dx,int dy,int direction){
        if(heroGardenStun>0){if(!moving)finishActionTurn();return;}
        boolean action=spaceHeld;
        if(action)spaceUsed=true;
        if(moving){
            if(queuedMoves.size()<8)queuedMoves.addLast(new int[]{dx,dy,direction,action?1:0});
        }else beginMove(dx,dy,direction,action);
    }
    private void playQueuedMove(){
        if(heroDead){queuedMoves.clear();return;}
        if(moving||queuedMoves.isEmpty())return;
        int[] cmd=queuedMoves.removeFirst();
        beginMove(cmd[0],cmd[1],cmd[2],cmd[3]!=0);
        // Invalid queued moves are discarded; keep draining without recursion.
        if(!moving&&!queuedMoves.isEmpty())SwingUtilities.invokeLater(new Runnable(){public void run(){playQueuedMove();}});
    }
    private void afterAnimation(){
        if(!heroDead&&!queuedMoves.isEmpty())SwingUtilities.invokeLater(new Runnable(){public void run(){playQueuedMove();}});
        else if(heroDead)queuedMoves.clear();
    }
    private boolean pushAction=false;
    private boolean pushIntoPit=false;
    private boolean pushIntoFilledPit=false;
    private final java.util.Map<Integer,boolean[][]> lidUnderCrate=new HashMap<Integer,boolean[][]>();
    private boolean[][] lids(){Integer key=Integer.valueOf(selected);boolean[][] a=lidUnderCrate.get(key);
        if(a==null){a=new boolean[H][W];lidUnderCrate.put(key,a);}return a;}
    // A crate can stand on an open grate; remember the grate beneath it.
    private final java.util.Map<Integer,boolean[][]> grateUnderCrate=new HashMap<Integer,boolean[][]>();
    private boolean[][] crateGrates(){Integer key=Integer.valueOf(selected);boolean[][] a=grateUnderCrate.get(key);
        if(a==null){a=new boolean[H][W];grateUnderCrate.put(key,a);}return a;}
    private boolean doorMove=false;
    // Scientist elevation: 0=floor, 1=standing on a crate or wall.
    private int heroHeight=0, moveHeight=0;
    private boolean elevatedMove=false, landingMove=false, walkingOnTop=false;
    private boolean fighterDash=false;
    private int dashEndX,dashEndY;
    private final java.util.Set<Integer> brokenCircuits=new HashSet<Integer>();
    // Single persistent pursuer: introduced by the tenth map, not a level-specific movement rule.
    // Level-20 stationary 3x3 slime boss; spawns four ordinary slimes every
    // three seconds of REAL time while the player remains in its room.
    // Its body occupies x=3..5, y=2..4. No new map symbols are required.
    private final javax.swing.Timer superSlimeTimer;
    private boolean superSlimeRoom(){return levels.get(selected).id==20;}
    private boolean superSlimeAt(int x,int y){return superSlimeRoom()&&x>=3&&x<=5&&y>=2&&y<=4;}
    private void syncSuperSlimeTimer(){
        if(superSlimeRoom()&&!heroDead){
            if(!superSlimeTimer.isRunning()){superSlimeTimer.restart();}
        }else superSlimeTimer.stop();
    }
    private void spawnSuperSlimes(){
        if(!superSlimeRoom()||heroDead)return;
        java.util.List<int[]> spots=new ArrayList<int[]>();
        // Immediate perimeter of the 3x3 body. The four spawns are spread
        // over available cells; occupied cells never receive a second mob.
        for(int y=1;y<=5;y++)for(int x=2;x<=6;x++){
            if(superSlimeAt(x,y)||!freeForSlime(x,y)||ogreAt(x,y))continue;
            if(x==heroX&&y==heroY)continue;
            spots.add(new int[]{x,y});
        }
        Collections.shuffle(spots,random);
        for(int i=0;i<4&&i<spots.size();i++){
            int[] at=spots.get(i);
            slimes().add(new Slime(at[0],at[1],random.nextInt(4),'S'));
        }
        repaint();
    }
    // External PNG animation: 4 frames per mob, no changes to movement logic.
    private static final java.awt.image.BufferedImage[][] MOB_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static java.awt.image.BufferedImage SUPER_IMAGE;
    private static final java.awt.image.BufferedImage[][] OGRE_IMAGES=new java.awt.image.BufferedImage[4][4];
    private int ogreFacing=2;
    private boolean ogreAlternateStep=false;
    private boolean ogreMovedThisTurn=false;
    private static final java.awt.image.BufferedImage[][] SKELETON_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] GOBLIN_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] RAT_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] ORC_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] SPIDER_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] BUCKET_HERO_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] POLICE_HERO_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] FIGHTER_HERO_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] FIGHTER_DASH_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] SCIENTIST_HERO_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final java.awt.image.BufferedImage[][] SCIENTIST_CLIMB_IMAGES=new java.awt.image.BufferedImage[4][4];
    private static final String[] SKELETON_DIR={"up","left","down","right"};
    private static final String[] SKELETON_POSE={"stand","step_left","step_right","action"};
    static {
        String[] names={"slime_green","slime_blue","slime_purple","bat"};
        for(int kind=0;kind<4;kind++)for(int frame=0;frame<4;frame++){
            java.io.File f=new java.io.File("sprites/mobs/"+names[kind]+"_"+(frame+1)+".png");
            try {if(f.isFile())MOB_IMAGES[kind][frame]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<3;pose++){
            java.io.File f=new java.io.File("sprites/mobs/skeleton/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())SKELETON_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<3;pose++){
            java.io.File f=new java.io.File("sprites/mobs/goblin/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())GOBLIN_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/mobs/ratman/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())RAT_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/mobs/spider/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())SPIDER_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/mobs/orc/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())ORC_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/heroes/bucket/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())BUCKET_HERO_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/heroes/police/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())POLICE_HERO_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            java.io.File f=new java.io.File("sprites/heroes/scientist/walk/"+SKELETON_DIR[(d==1)?3:(d==3)?1:d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(pose<3&&f.isFile())SCIENTIST_HERO_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
            String phase=pose==0?"stand":"climbing_"+pose;
            f=new java.io.File("sprites/heroes/scientist/climb/"+SKELETON_DIR[(d==1)?3:(d==3)?1:d]+"_"+phase+".png");
            try {if(f.isFile())SCIENTIST_CLIMB_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<4;pose++){
            // Fighter sprites have correct left/right names: do not swap directions.
            java.io.File f=new java.io.File("sprites/heroes/fighter/walk/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())FIGHTER_HERO_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
            f=new java.io.File("sprites/heroes/fighter/dash/"+SKELETON_DIR[d]+"_"+(pose==0?"stand":"dash_"+pose)+".png");
            try {if(f.isFile())FIGHTER_DASH_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        for(int d=0;d<4;d++)for(int pose=0;pose<3;pose++){
            java.io.File f=new java.io.File("sprites/mobs/ogre/"+SKELETON_DIR[d]+"_"+SKELETON_POSE[pose]+".png");
            try {if(f.isFile())OGRE_IMAGES[d][pose]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
        }
        try {
            java.io.File f=new java.io.File("sprites/mobs/super_slime.png");
            if(f.isFile())SUPER_IMAGE=javax.imageio.ImageIO.read(f);
        }catch(java.io.IOException ignored){}
    }
    private void drawMob(Graphics2D g,char c,int px,int py,int tileset,int direction,boolean walking,boolean alternate){
        java.awt.image.BufferedImage img=null;
        if(c=='L'||c=='R'){
            int d=Math.max(0,Math.min(3,direction));
            int pose=(walking&&mobPhase&&movePhase>0)?(alternate?2:1):0;
            img=SKELETON_IMAGES[(d==1?3:d==3?1:d)][pose];
        }else if(c=='P'){
            int d=Math.max(0,Math.min(3,direction));
            int pose=(walking&&mobPhase&&movePhase>0)?(alternate?2:1):0;
            img=SPIDER_IMAGES[d][pose];
        }else if(c=='N'){
            int d=Math.max(0,Math.min(3,direction));
            int pose=(walking&&mobPhase&&movePhase>0)?(alternate?2:1):0;
            img=ORC_IMAGES[d][pose];
        }else if(c=='K'){
            int d=Math.max(0,Math.min(3,direction));
            int pose=(walking&&mobPhase&&movePhase>0)?(alternate?2:1):0;
            img=RAT_IMAGES[d][pose];
        }else if(c=='G'){
            int d=Math.max(0,Math.min(3,direction));
            int pose=(walking&&mobPhase&&movePhase>0)?(alternate?2:1):0;
            img=GOBLIN_IMAGES[d][pose];
        }else if(c=='S'||c=='s'||c=='M'||c=='m'){
            int kind=(c=='M'||c=='m')?3:(tileset==1?1:tileset==2?2:0);
            int frame=mobPhase?Math.min(3,Math.max(0,movePhase)):0;
            img=MOB_IMAGES[kind][frame];
        }
        if(img!=null){g.drawImage(img,px+(32-img.getWidth())/2,py+32-img.getHeight()-(c=='M'||c=='m'?0:8),null);return;}
        drawObject(g,c,px,py,tileset);
    }
    private void drawSuperSlime(Graphics2D g){
        int x=3*TILE,y=2*TILE;
        if(SUPER_IMAGE!=null){g.drawImage(SUPER_IMAGE,x,y,null);return;}
        g.setColor(new Color(35,75,36));g.fillOval(x+4,y+6,88,86);
        g.setColor(new Color(69,146,54));g.fillOval(x+8,y+8,80,75);
        g.setColor(new Color(109,190,70));g.fillOval(x+18,y+12,60,40);
        g.setColor(new Color(19,49,28));g.fillOval(x+27,y+36,12,15);g.fillOval(x+57,y+36,12,15);
        g.setColor(new Color(211,235,122));g.fillOval(x+29,y+37,5,6);g.fillOval(x+59,y+37,5,6);
        g.setColor(new Color(28,68,32));g.drawArc(x+32,y+46,32,19,185,170);
    }
    private boolean ogreAwakened=false,ogreDead=false,ogrePresent=false,ogrePitCorpse=false;
    private boolean ogreDebugDisabled=false;
    private int ogreX=-1,ogreY=-1,ogreStun=0,ogreDelay=-1,ogreLevel=-1;
    private boolean ogreAt(int x,int y){return !ogreDebugDisabled&&!ogreDead&&!ogrePitCorpse&&ogrePresent&&ogreLevel==selected&&ogreX==x&&ogreY==y;}
    private void ogreShock(int x,int y){if(ogreAt(x,y))ogreStun=5;}
    private boolean ogreWalkable(int x,int y){
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y))return false;
        char c=cells()[y][x];
        return c=='.'||c=='1'||c=='x'||c=='X'||c=='F'||c=='O'||c=='o'||c=='w';
    }
    private int[] ogreArrival(boolean atEntry){
        char door=atEntry?'@':'E';int ex=-1,ey=-1;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]==door){ex=x;ey=y;}
        if(ex<0)return null;
        for(int d=0;d<4;d++){int x=ex+SX[d],y=ey+SY[d];if(ogreWalkable(x,y))return new int[]{x,y};}
        return null;
    }
    private void scheduleOgre(boolean entry){
        if(!ogreAwakened||ogreDead)return;
        ogrePresent=false;ogreLevel=selected;ogreDelay=5;
        ogreSpawnAtEntry=entry;
    }
    private boolean ogreSpawnAtEntry=false;
    private void ogreStep(){
        ogreMovedThisTurn=false;
        if(ogreDebugDisabled)return;
        if(ogreDead||!ogreAwakened)return;
        if(ogrePitCorpse)return;
        if(ogreDelay>=0){
            if(--ogreDelay==0){
                int[] pos=ogreArrival(ogreSpawnAtEntry);
                if(pos==null){ogreDelay=1;return;}
                ogreX=pos[0];ogreY=pos[1];ogreLevel=selected;ogrePresent=true;ogreDelay=-1;
                if(heroHeight==0&&ogreX==heroX&&ogreY==heroY)heroDead=true;
            }
            return;
        }
        if(!ogrePresent||ogreLevel!=selected)return;
        if(ogreStun>0){ogreStun--;return;}
        // BFS from Ogre to reachable cells. The target can stand above solid terrain;
        // in that case select the nearest reachable position, never enter the wall.
        int[][] dist=new int[H][W];for(int[] row:dist)Arrays.fill(row,-1);
        int[] qx=new int[W*H],qy=new int[W*H];int head=0,tail=0;
        qx[tail]=ogreX;qy[tail++]=ogreY;dist[ogreY][ogreX]=0;
        while(head<tail){int x=qx[head],y=qy[head++];
            for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                if(!ogreWalkable(nx,ny)||dist[ny][nx]>=0)continue;
                dist[ny][nx]=dist[y][x]+1;qx[tail]=nx;qy[tail++]=ny;
            }
        }
        int bestX=ogreX,bestY=ogreY,best=9999,bestDist=9999;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(dist[y][x]>=0){
            int h=Math.abs(x-heroX)+Math.abs(y-heroY);
            if(h<best||(h==best&&dist[y][x]<bestDist)){
                best=h;bestDist=dist[y][x];bestX=x;bestY=y;
            }
        }
        // Trace a shortest path to the closest reachable target tile.
        if(bestDist>0&&bestDist<9999){
            int x=bestX,y=bestY;
            while(dist[y][x]>1){
                for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                    if(nx>=0&&ny>=0&&nx<W&&ny<H&&dist[ny][nx]==dist[y][x]-1){x=nx;y=ny;break;}
                }
            }
            int dx=x-ogreX,dy=y-ogreY;
            if(dx<0)ogreFacing=1;
            else if(dx>0)ogreFacing=3;
            else if(dy<0)ogreFacing=0;
            else if(dy>0)ogreFacing=2;
            ogreMovedThisTurn=true;
            ogreAlternateStep=!ogreAlternateStep;
            ogreX=x;ogreY=y;
        }
        char c=cells()[ogreY][ogreX];
        if(c=='1'){cells()[ogreY][ogreX]='X';beginOgreSpikeDeath();ogreDead=true;ogrePresent=false;return;} // visual spin; existing permanent death
        if(c=='O'||c=='o'){ogrePitCorpse=true;ogreDelay=5;ogreSpawnAtEntry=true;beginOgrePitFall();return;} // show fallen Ogre until next turn
        for(Slime mob:slimes())if(!mob.dead&&mob.kind!='M'&&mob.kind!='m'&&mob.x==ogreX&&mob.y==ogreY)mob.dead=true;
        if(heroHeight==0&&ogreX==heroX&&ogreY==heroY)heroDead=true;
    }
    private boolean onTop(char c){return c=='#'||c=='B'||c=='b';}
    private void landAt(int x,int y){
        heroHeight=0;
        if(superSlimeAt(x,y)){heroDead=true;syncSuperSlimeTimer();return;}
        char c=cells()[y][x];
        if(c=='O'||c=='o'){
            beginPitFall(true,null,x,y);return;
        }else if(c=='1'){
            cells()[y][x]='X';beginHeroSpikeDeath(x,y);
        }else if(c=='E'||c=='@'){
            int target=selected+(c=='E'?1:-1);
            if(target>=0&&target<levels.size())enterLevel(target,target<selected);
        }else{
            if(ogreAt(x,y))heroDead=true;
            for(Slime m:slimes())if(!heroSlimeForm&&!m.dead&&m.stun==0&&m.x==x&&m.y==y){heroDead=true;break;}
        }
    }
    private final java.util.Map<Integer,char[][]> shockStates=new HashMap<Integer,char[][]>();
    private char[][] shocks(){Integer k=Integer.valueOf(selected);char[][] a=shockStates.get(k);if(a==null){a=new char[H][W];shockStates.put(k,a);}return a;}
    private void clearShockMarks(){char[][] a=shocks();for(int y=0;y<H;y++)java.util.Arrays.fill(a[y],(char)0);}
    private void tickStuns(){for(Slime s:slimes())if(s.stun>0)s.stun--;}
    private void shock(int x,int y){startTurn();shocks()[y][x]='!';for(Slime s:slimes())if(!s.dead&&s.x==x&&s.y==y){s.stun=10;s.dx=s.dy=0;}ogreShock(x,y);tickGrates();if(!heroDead)planSlimes();repaint();}
    // A single seven-turn grate circuit per level; state belongs to the level, not the map file.
    private final java.util.Map<Integer,Integer> grateTimers=new HashMap<Integer,Integer>();
    private int grateTime(){Integer n=grateTimers.get(Integer.valueOf(selected));return n==null?0:n.intValue();}
    private void setGrateTime(int n){grateTimers.put(Integer.valueOf(selected),Integer.valueOf(n));}
    // One level-wide circuit. W/w always toggle symmetrically.
    // A crate on an open grate is destroyed when the grate closes.
    private void flipGrates(){
        char[][] a=cells();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            if(a[y][x]=='W')a[y][x]='w';
            else if(a[y][x]=='w'||crateGrates()[y][x]){
                a[y][x]='W';crateGrates()[y][x]=false;
                if(heroX==x&&heroY==y&&heroHeight==0)heroDead=true;
                for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==x&&mob.y==y)mob.dead=true;
                trash()[y][x]=0;
            }
        }
    }
    private void tickGrates(){
        int t=grateTime();
        if(t>0){t--;if(t==0){flipGrates();setGrateTime(0);}else setGrateTime(t);}
    }
    private void toggleLever(int x,int y){
        if(brokenCircuits.contains(Integer.valueOf(selected)))return;
        boolean active=grateTime()>0;
        flipGrates();
        finishActionTurn();
        setGrateTime(active?0:7);
    }
    private int doorTarget=-1;
    private static final int[] SX={0,-1,0,1}, SY={-1,0,1,0};
    private final Random random=new Random();
    private static final char[] TRASH_GLYPHS={'!','?',',',';',':'};
    private final java.util.Map<Integer,char[][]> trashStates=new HashMap<Integer,char[][]>();
    private char[][] trash(){
        Integer key=Integer.valueOf(selected);char[][] t=trashStates.get(key);
        if(t==null){t=new char[H][W];trashStates.put(key,t);}return t;
    }
    private static boolean isTrash(char c){return "!?,;:".indexOf(c)>=0;}
    private void collectTrash(int x,int y){if(x>=0&&y>=0&&x<W&&y<H)trash()[y][x]=0;}
    private void rearmTraps(){char[][] a=cells();for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(a[y][x]=='X')a[y][x]='1';}
    private void startTurn(){if(heroGardenStun>0)heroGardenStun--;ritualHeroStartX=heroX;ritualHeroStartY=heroY;ritualCrateMoved=false;ritualCrateX=ritualCrateY=-1;ritualAttacking=false;clearShockMarks();tickStuns();tickWeb();rearmTraps();clearDeadMobs();
        if(ogreDead||ogrePitCorpse){ogrePresent=false;ogrePitCorpse=false;}
        if(!ogreDebugDisabled&&!ogreAwakened&&levels.get(selected).id==10){
            ogreAwakened=true;ogreDelay=5;ogreLevel=selected;ogreSpawnAtEntry=false;
        }
    }
    private boolean throwTrash(int dx,int dy){
        // A throw travels up to five cells; it lands on the last clear tile before an obstacle.
        int tx=-1,ty=-1;
        for(int i=1;i<=5;i++){
            int x=heroX+dx*i,y=heroY+dy*i;
            if(x<0||y<0||x>=W||y>=H)break;
            char c=cells()[y][x];
            if(c!='.'&&c!='1'&&c!='x'&&c!='X'&&c!='F'&&c!='w')break;
            if(slimeAt(x,y))break;
            tx=x;ty=y;
        }
        if(tx<0)return false;
        trash()[ty][tx]=TRASH_GLYPHS[random.nextInt(TRASH_GLYPHS.length)];
        showHeroAction();finishActionTurn();return true;
    }

    private int heroWebTurns=0,heroWebFrame=0;
    private boolean heroWebJustReleased=false;
    private static final class WebShot {
        int x,y,dx,dy,remaining,frame;
        WebShot(int x,int y,int dx,int dy,int remaining,int frame){this.x=x;this.y=y;this.dx=dx;this.dy=dy;this.remaining=remaining;this.frame=frame;}
    }
    private final java.util.List<WebShot> webShots=new ArrayList<WebShot>();
    private final javax.swing.Timer webTimer=new javax.swing.Timer(45,new ActionListener(){public void actionPerformed(ActionEvent e){
        for(java.util.Iterator<WebShot> it=webShots.iterator();it.hasNext();){WebShot w=it.next();w.x+=w.dx;w.y+=w.dy;w.remaining--;if(w.remaining<=0)it.remove();}
        if(webShots.isEmpty())webTimer.stop();repaint();
    }});
    private void webFire(Slime spider,int x,int y,int type,Slime mob){
        int dx=Integer.compare(x,spider.x),dy=Integer.compare(y,spider.y);
        if(dx==0&&dy==0)return;
        int distance=Math.abs(x-spider.x)+Math.abs(y-spider.y);
        webShots.add(new WebShot(spider.x,spider.y,dx,dy,distance,random.nextInt(4)));
        if(!webTimer.isRunning())webTimer.start();
        if(type==1){heroWebTurns=3;heroWebFrame=random.nextInt(4);}
        else if(type==2&&mob!=null){mob.webTurns=3;mob.webFrame=random.nextInt(4);mob.dx=mob.dy=0;}
        else if(type==3){spider.webTurns=3;spider.webFrame=random.nextInt(4);}
    }
    private void tickWeb(){
        heroWebJustReleased=false;
        if(heroWebTurns>0){heroWebTurns--;heroWebFrame=random.nextInt(4);if(heroWebTurns==0)heroWebJustReleased=true;}
        for(Slime m:slimes()){
            m.webJustReleased=false;
            if(m.webTurns>0){m.webTurns--;m.webFrame=random.nextInt(4);if(m.webTurns==0)m.webJustReleased=true;}
        }
    }
    private void drawWeb(Graphics2D g,int x,int y,int frame){
        java.awt.image.BufferedImage img=SPIDER_IMAGES[frame%4][3];
        if(img!=null)g.drawImage(img,x,y-8,null);
    }
    private static final class Slime implements Serializable {
        private static final long serialVersionUID=1L;
        char kind;
        int x,y,dir,tx=-1,ty=-1,dx,dy;
        boolean chasing,dead;
        int stun=0;
        int webTurns=0,webFrame=0;
        transient boolean webJustReleased=false;
        boolean escaped=false;
        boolean alternateStep=false;
        int spawnX,spawnY;
        int ratLeverX=-1,ratLeverY=-1; // persistent guard position
        boolean ratFleeing=false; // one-way mode until next level entry
        boolean gardenSlime=false;
        Slime(int x,int y,int d,char kind){this.x=x;this.y=y;spawnX=x;spawnY=y;dir=d;this.kind=kind;}
    }
    private final java.util.Map<Integer,java.util.List<Slime>> slimeStates=new HashMap<Integer,java.util.List<Slime>>();
    private java.util.List<Slime> slimes(){
        Integer key=Integer.valueOf(selected);
        java.util.List<Slime> found=slimeStates.get(key);
        if(found==null){found=new ArrayList<Slime>();char[][] state=cells();
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if("SsLRMmGKNVP".indexOf(state[y][x])>=0){
                found.add(new Slime(x,y,(state[y][x]=='R'?3:state[y][x]=='L'?1:state[y][x]=='N'?0:state[y][x]=='V'?3:random.nextInt(4)),state[y][x]));state[y][x]='.';
            }
            slimeStates.put(key,found);
        }
        return found;
    }
    private boolean slimeAt(int x,int y){for(Slime s:slimes())if(!s.dead&&!s.escaped&&s.stun==0&&s.x==x&&s.y==y)return true;return false;}
    private boolean freeForSlime(int x,int y){
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y)||heroSlimeObstacle(x,y))return false;
        char c=cells()[y][x];return (c=='.'||c=='1'||c=='x'||c=='X'||c=='F'||c=='O'||c=='o'||c=='w')&&!slimeAt(x,y);
    }
    private boolean heroSlimeObstacle(int x,int y){
        return heroSlimeForm&&!heroDead&&heroHeight==0&&heroX==x&&heroY==y;
    }
    private boolean freeForActor(Slime actor,int x,int y){
        if(x<0||y<0||x>=W||y>=H)return false;
        if(actor.kind=='M'||actor.kind=='m')return true; // airborne: terrain and Ogre do not block flight
        if(superSlimeAt(x,y)||heroSlimeObstacle(x,y))return false;
        if(ogreAt(x,y))return false; // ground mobs treat living Ogre as an obstacle
        char c=cells()[y][x];
        if(actor.kind=='N'){
            if(c!='.'&&c!='x'&&c!='X'&&c!='F'&&c!='w'&&c!='@'&&c!='E')return false;
        }else if(actor.kind=='G'||actor.kind=='K'){
            if(c!='.'&&c!='x'&&c!='X'&&c!='F'&&c!='w'&&c!='@'&&c!='E')return false;
        }else if(actor.kind=='L'||actor.kind=='R'){
            if(c!='.'&&c!='1'&&c!='x'&&c!='X'&&c!='F'&&c!='w')return false;
        }else if(c!='.'&&c!='1'&&c!='x'&&c!='X'&&c!='F'&&c!='O'&&c!='o'&&c!='w')return false;
        for(Slime other:slimes())if(other!=actor&&!other.dead&&!other.escaped&&other.stun==0&&other.x==x&&other.y==y)return false;
        return true;
    }
    private int[][] actorDistances(Slime actor,int tx,int ty){
        int[][] dist=new int[H][W];for(int y=0;y<H;y++)java.util.Arrays.fill(dist[y],-1);
        if(tx<0||tx>=W||ty<0||ty>=H)return dist;
        int[] qx=new int[W*H],qy=new int[W*H];int head=0,tail=0;
        dist[ty][tx]=0;qx[tail]=tx;qy[tail++]=ty;
        while(head<tail){int x=qx[head],y=qy[head++];
            for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                if(nx<0||ny<0||nx>=W||ny>=H||dist[ny][nx]>=0)continue;
                if(!freeForActor(actor,nx,ny)&&!(nx==actor.x&&ny==actor.y))continue;
                dist[ny][nx]=dist[y][x]+1;qx[tail]=nx;qy[tail++]=ny;
            }
        }
        return dist;
    }
    private boolean visible(int ax,int ay,int bx,int by){
        if(ax!=bx&&ay!=by)return false;
        int dx=Integer.compare(bx,ax),dy=Integer.compare(by,ay);
        int x=ax+dx,y=ay+dy;
        while(x!=bx||y!=by){if(x<0||y<0||x>=W||y>=H)return false;
            char c=cells()[y][x];if(c!='.'&&c!='1'&&c!='x'&&c!='X'&&c!='F'&&c!='O'&&c!='o'&&c!='w'&&c!='W')return false;
            x+=dx;y+=dy;
        }
        return true;
    }
    private boolean grateBetween(int ax,int ay,int bx,int by){
        if(ax!=bx&&ay!=by)return false;
        int dx=Integer.compare(bx,ax),dy=Integer.compare(by,ay);
        int x=ax+dx,y=ay+dy;
        while(x!=bx||y!=by){
            if(x<0||y<0||x>=W||y>=H)return false;
            if(cells()[y][x]=='W')return true;
            x+=dx;y+=dy;
        }
        return false;
    }

    // Goblin: hero has absolute priority. Routes avoid pits and armed traps.
    // No look-ahead for the seven-turn gate countdown.
    // Escape pathfinding treats the hero and four adjacent tiles as danger.
    // The goblin's current tile is permitted as the BFS starting point,
    // so it can escape even when already inside that danger zone.
    private boolean goblinEscapeRoute=false;
    private int goblinEscapeX=-1,goblinEscapeY=-1;
    private boolean goblinDanger(int x,int y){
        return heroX>=0&&!heroDead&&heroHeight==0&&
            Math.abs(x-heroX)+Math.abs(y-heroY)<=1;
    }
    private boolean goblinSafe(int x,int y,boolean assumeOpen){
        if(goblinEscapeRoute&&goblinDanger(x,y)&&!(x==goblinEscapeX&&y==goblinEscapeY))return false;
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y)||ogreAt(x,y)||heroSlimeObstacle(x,y))return false;
        char c=cells()[y][x];
        if(c=='W')return assumeOpen;
        return c=='.'||c=='x'||c=='X'||c=='F'||c=='w'||c=='@'||c=='E';
    }
    private int[][] goblinDistances(int tx,int ty,boolean assumeOpen){
        int[][] dist=new int[H][W];for(int y=0;y<H;y++)Arrays.fill(dist[y],999);
        if(!goblinSafe(tx,ty,assumeOpen))return dist;
        int[] qx=new int[W*H],qy=new int[W*H];int head=0,tail=0;
        qx[tail]=tx;qy[tail++]=ty;dist[ty][tx]=0;
        while(head<tail){int x=qx[head],y=qy[head++];
            for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                if(!goblinSafe(nx,ny,assumeOpen)||dist[ny][nx]!=999)continue;
                dist[ny][nx]=dist[y][x]+1;qx[tail]=nx;qy[tail++]=ny;
            }
        }
        return dist;
    }
    private void goblinOpenLever(){
        if(brokenCircuits.contains(Integer.valueOf(selected))||grateTime()>0)return;
        char[][] a=cells();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(a[y][x]=='W')a[y][x]='w';
        setGrateTime(7); // Same timer as hero; never closes it deliberately.
    }
    private void planGoblin(Slime s){
        boolean sees=heroX>=0&&!heroDead&&heroHeight==0&&visible(s.x,s.y,heroX,heroY);
        if(sees){s.chasing=false;s.tx=s.ty=-1;}
        else{
            int best=999,tx=-1,ty=-1;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(isTrash(trash()[y][x])&&visible(s.x,s.y,x,y)){
                int d=Math.abs(x-s.x)+Math.abs(y-s.y);
                if(d<best){best=d;tx=x;ty=y;}
            }
            if(tx>=0){s.tx=tx;s.ty=ty;s.chasing=true;}
            if(s.chasing&&s.tx==s.x&&s.ty==s.y)s.chasing=false;
        }
        // After seeing the hero, flee toward the nearest reachable exit.
        // Keep fleeing until safe: seeing hero again always takes precedence.
        if(sees||(!s.chasing&&s.tx==-2)){
            s.tx=-2;s.ty=-2;s.chasing=false;
            goblinEscapeRoute=true;goblinEscapeX=s.x;goblinEscapeY=s.y;
            int best=999,ex=-1,ey=-1;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]=='@'||cells()[y][x]=='E'){
                int[][] ds=goblinDistances(x,y,false);
                if(ds[s.y][s.x]<best){best=ds[s.y][s.x];ex=x;ey=y;}
            }
            if(ex<0){
                // A gate may be the only route to an exit.
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]=='@'||cells()[y][x]=='E'){
                    int[][] ds=goblinDistances(x,y,true);
                    if(ds[s.y][s.x]<best){best=ds[s.y][s.x];ex=x;ey=y;}
                }
            }
            if(ex>=0){
                goblinRoute(s,ex,ey);
                goblinEscapeRoute=false;
                // If another ground mob blocks the selected step, try retreating.
                if(s.dx!=0||s.dy!=0)return;
            }else goblinEscapeRoute=false;
            // No safe route to either door: retreat into a safe neighboring
            // tile instead of charging through the hero toward an exit.
            int chosen=-1,far=-1;
            for(int d=0;d<4;d++){
                int nx=s.x+SX[d],ny=s.y+SY[d];
                if(goblinDanger(nx,ny)||!goblinSafe(nx,ny,false))continue;
                boolean occupied=false;
                for(Slime other:slimes())if(other!=s&&!other.dead&&!other.escaped&&other.x==nx&&other.y==ny&&other.kind!='M'&&other.kind!='m')occupied=true;
                if(occupied)continue;
                int distance=Math.abs(heroX-nx)+Math.abs(heroY-ny);
                if(distance>far){far=distance;chosen=d;}
            }
            if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
            else if(Math.abs(heroX-s.x)+Math.abs(heroY-s.y)==1&&heroHeight==0)heroDead=true;
            return;
        }
        if(s.chasing){goblinRoute(s,s.tx,s.ty);return;}
        // Otherwise stays alert by its exit instead of chasing the hero.
    }
    private void goblinRoute(Slime s,int tx,int ty){
        int[][] now=goblinDistances(tx,ty,false);
        int[][] opened=goblinDistances(tx,ty,true);
        int chosen=-1,best=now[s.y][s.x];
        // If opening a gate gives a shorter route, go to the nearest lever.
        boolean useLever=opened[s.y][s.x]<999&&(now[s.y][s.x]==999||opened[s.y][s.x]+2<now[s.y][s.x]);
        if(useLever&&grateTime()==0&&!brokenCircuits.contains(Integer.valueOf(selected))){
            int leverX=-1,leverY=-1,leverDist=999;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]=='T'){
                for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                    if(!goblinSafe(nx,ny,false))continue;
                    int[][] dd=goblinDistances(nx,ny,false);
                    if(dd[s.y][s.x]<leverDist){leverDist=dd[s.y][s.x];leverX=nx;leverY=ny;}
                }
            }
            if(leverX>=0&&leverDist<999){
                if(s.x==leverX&&s.y==leverY){goblinOpenLever();return;}
                now=goblinDistances(leverX,leverY,false);best=now[s.y][s.x];
            }
        }
        for(int d=0;d<4;d++){int nx=s.x+SX[d],ny=s.y+SY[d];
            if(!goblinSafe(nx,ny,false))continue;
            boolean occupied=false;
            for(Slime other:slimes())if(other!=s&&!other.dead&&!other.escaped&&other.x==nx&&other.y==ny&&other.kind!='M'&&other.kind!='m')occupied=true;
            if(occupied)continue;
            if(now[ny][nx]<best){best=now[ny][nx];chosen=d;}
        }
        if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
        else if(s.tx==-2&&heroHeight==0&&Math.abs(heroX-s.x)+Math.abs(heroY-s.y)==1)heroDead=true;
    }

    // Ratman: no trash targeting. Operates the ordinary global lever circuit.
    // It can only set a trap while directly seeing the hero along a gated corridor.
    private boolean ratCorridor(Slime s){
        if(heroX<0||heroDead||heroHeight!=0||!visible(s.x,s.y,heroX,heroY))return false;
        if(s.x!=heroX&&s.y!=heroY)return false;
        int dx=Integer.compare(heroX,s.x),dy=Integer.compare(heroY,s.y);
        int x=s.x+dx,y=s.y+dy;
        while(x!=heroX||y!=heroY){
            if(cells()[y][x]=='W'||cells()[y][x]=='w')return true;
            x+=dx;y+=dy;
        }
        return cells()[heroY][heroX]=='w';
    }
    private void planRat(Slime s){
        if(heroX<0||heroDead||heroHeight!=0)return;
        boolean onGate=cells()[heroY][heroX]=='w';
        // Direct unobstructed encounter takes priority over guarding a lever.
        // Only a grate-free sight line triggers retreat; otherwise the accepted
        // lever/last-guard-position logic remains unchanged.
        if(!s.ratFleeing&&visible(s.x,s.y,heroX,heroY)&&!ratGrateOnSightLine(s.x,s.y,heroX,heroY))
            s.ratFleeing=true;
        if(s.ratFleeing){ratRetreat(s);return;}
        int targetX=-1,targetY=-1,best=999;
        // Find a reachable lever position with a real sight line through a grate.
        // Prefer the current guard position when equally good: avoid oscillation.
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]=='T'){
            for(int d=0;d<4;d++){
                int nx=x+SX[d],ny=y+SY[d];
                if(nx<0||ny<0||nx>=W||ny>=H)continue;
                if(!freeForActor(s,nx,ny)&&!(nx==s.x&&ny==s.y))continue;
                if(!visible(nx,ny,heroX,heroY))continue;
                if(nx!=heroX&&ny!=heroY)continue;
                int dx=Integer.compare(heroX,nx),dy=Integer.compare(heroY,ny);
                boolean gate=false;
                for(int xx=nx+dx,yy=ny+dy;xx!=heroX||yy!=heroY;xx+=dx,yy+=dy)
                    if(cells()[yy][xx]=='W'||cells()[yy][xx]=='w')gate=true;
                if(cells()[heroY][heroX]=='w')gate=true;
                if(!gate)continue;
                int[][] distances=actorDistances(s,nx,ny);
                int dist=distances[s.y][s.x];
                if(dist<0)continue;
                if(dist<best||(dist==best&&nx==s.ratLeverX&&ny==s.ratLeverY)){
                    best=dist;targetX=nx;targetY=ny;
                }
            }
        }
        if(targetX>=0){
            s.ratLeverX=targetX;s.ratLeverY=targetY;
        }else if(s.ratLeverX>=0){
            // Hero is out of all lever sight lines: guard the last lever.
            // Never fall back to the goblin's exit/retreat behavior here.
            targetX=s.ratLeverX;targetY=s.ratLeverY;
        }
        if(targetX>=0){
            if(s.x==targetX&&s.y==targetY){
                // A trap can only be sprung when the hero actually occupies a grate.
                if(onGate&&grateTime()>0&&visible(s.x,s.y,heroX,heroY)){
                    flipGrates();setGrateTime(0);return;
                }
                if(best<999&&grateTime()==0&&!brokenCircuits.contains(Integer.valueOf(selected)))
                    goblinOpenLever();
                return;
            }
            int[][] ds=actorDistances(s,targetX,targetY);
            int chosen=-1,dist=ds[s.y][s.x];
            if(dist>=0)for(int d=0;d<4;d++){
                int nx=s.x+SX[d],ny=s.y+SY[d];
                if(!freeForActor(s,nx,ny))continue;
                if(ds[ny][nx]>=0&&ds[ny][nx]<dist){dist=ds[ny][nx];chosen=d;}
            }
            if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
            // If blocked, wait rather than switch into an unrelated escape mode.
            return;
        }
        // No lever acquired yet: the same local retreat, without exit routing.
        ratRetreat(s);
    }

    private boolean ratGrateOnSightLine(int ax,int ay,int bx,int by){
        if(ax!=bx&&ay!=by)return false;
        int dx=Integer.compare(bx,ax),dy=Integer.compare(by,ay);
        int x=ax+dx,y=ay+dy;
        while(x!=bx||y!=by){
            if(x<0||y<0||x>=W||y>=H)return false;
            char c=cells()[y][x];
            if(c=='W'||c=='w')return true;
            x+=dx;y+=dy;
        }
        return cells()[by][bx]=='w';
    }
    private void ratRetreat(Slime s){
        // One-way escape mode: nearest reachable door, never return to levers.
        goblinEscapeRoute=true;goblinEscapeX=s.x;goblinEscapeY=s.y;
        int ex=-1,ey=-1,best=999;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)
            if(cells()[y][x]=='@'||cells()[y][x]=='E'){
                int[][] ds=goblinDistances(x,y,false);
                if(ds[s.y][s.x]<best){best=ds[s.y][s.x];ex=x;ey=y;}
            }
        if(ex>=0){
            int[][] ds=goblinDistances(ex,ey,false);
            int chosen=-1,dist=ds[s.y][s.x];
            for(int d=0;d<4;d++){
                int nx=s.x+SX[d],ny=s.y+SY[d];
                if(!goblinSafe(nx,ny,false)||!freeForActor(s,nx,ny))continue;
                if(ds[ny][nx]<dist){dist=ds[ny][nx];chosen=d;}
            }
            if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
        }
        goblinEscapeRoute=false;
        if(s.dx!=0||s.dy!=0)return;
        // If a route is temporarily blocked, retreat as far as possible.
        int chosen=-1,far=-1;
        for(int d=0;d<4;d++){
            int nx=s.x+SX[d],ny=s.y+SY[d];
            if(goblinDanger(nx,ny)||!goblinSafe(nx,ny,false)||!freeForActor(s,nx,ny))continue;
            int distance=Math.abs(heroX-nx)+Math.abs(heroY-ny);
            if(distance>far){far=distance;chosen=d;}
        }
        if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
        else if(Math.abs(heroX-s.x)+Math.abs(heroY-s.y)==1)heroDead=true;
    }

    // Stage 1: stationary clockwise lookout; one sighting alerts all living orcs.
    private final java.util.Set<Integer> orcAlerts=new java.util.HashSet<Integer>();
    private boolean orcSeesHero(Slime s){
        if(heroX<0||heroDead||heroHeight!=0||heroSlimeForm||s.gardenSlime)return false;
        if(s.dir==0&&heroX!=s.x || s.dir==1&&heroY!=s.y ||
           s.dir==2&&heroX!=s.x || s.dir==3&&heroY!=s.y)return false;
        if(s.dir==0&&heroY>=s.y || s.dir==1&&heroX>=s.x ||
           s.dir==2&&heroY<=s.y || s.dir==3&&heroX<=s.x)return false;
        return visible(s.x,s.y,heroX,heroY);
    }
    // Orc lever routing: uses the existing global seven-turn grate circuit.
    private boolean orcTile(Slime s,int x,int y,boolean open){
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y)||ogreAt(x,y)||heroSlimeObstacle(x,y))return false;
        char c=cells()[y][x];
        if(c=='W'){if(!open)return false;}
        else if(c!='.'&&c!='x'&&c!='X'&&c!='F'&&c!='w'&&c!='@'&&c!='E')return false;
        for(Slime o:slimes())if(o!=s&&!o.dead&&!o.escaped&&o.stun==0&&o.x==x&&o.y==y)return false;
        return true;
    }
    private int[][] orcDistances(Slime s,int tx,int ty,boolean open){
        int[][] dist=new int[H][W];for(int y=0;y<H;y++)Arrays.fill(dist[y],999);
        if(tx<0||ty<0||tx>=W||ty>=H)return dist;
        int[] qx=new int[W*H],qy=new int[W*H];int head=0,tail=0;
        dist[ty][tx]=0;qx[tail]=tx;qy[tail++]=ty;
        while(head<tail){int x=qx[head],y=qy[head++];
            for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                if(nx<0||ny<0||nx>=W||ny>=H||dist[ny][nx]!=999)continue;
                if(!orcTile(s,nx,ny,open)&&!(nx==s.x&&ny==s.y))continue;
                dist[ny][nx]=dist[y][x]+1;qx[tail]=nx;qy[tail++]=ny;
            }
        }
        return dist;
    }
    private void planOrc(Slime s){
        if(!orcAlerts.contains(Integer.valueOf(selected)))return;
        int[][] route=orcDistances(s,heroX,heroY,false);
        int[][] opened=orcDistances(s,heroX,heroY,true);
        int best=route[s.y][s.x];
        boolean leverUseful=opened[s.y][s.x]<999&&(best==999||opened[s.y][s.x]+2<best);
        if(leverUseful&&grateTime()==0&&!brokenCircuits.contains(Integer.valueOf(selected))){
            int lx=-1,ly=-1,ld=999;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(cells()[y][x]=='T'){
                for(int d=0;d<4;d++){int nx=x+SX[d],ny=y+SY[d];
                    if(!orcTile(s,nx,ny,false))continue;
                    int[][] dd=orcDistances(s,nx,ny,false);
                    if(dd[s.y][s.x]<ld){ld=dd[s.y][s.x];lx=nx;ly=ny;}
                }
            }
            if(lx>=0&&ld<999){
                if(s.x==lx&&s.y==ly){goblinOpenLever();return;}
                route=orcDistances(s,lx,ly,false);best=route[s.y][s.x];
            }
        }
        int choice=-1;
        for(int d=0;d<4;d++){int nx=s.x+SX[d],ny=s.y+SY[d];
            if(!freeForActor(s,nx,ny))continue;
            if(route[ny][nx]<best){best=route[ny][nx];choice=d;}
        }
        if(choice>=0){s.dir=choice;s.dx=SX[choice];s.dy=SY[choice];}
    }
    private void resetOrcsOnExit(int level){
        java.util.List<Slime> mobs=slimeStates.get(Integer.valueOf(level));
        if(mobs!=null)for(Slime s:mobs)if(s.kind=='N'&&!s.dead){
            s.x=s.spawnX;s.y=s.spawnY;s.dir=0;s.dx=s.dy=0;
        }
        orcAlerts.remove(Integer.valueOf(level));
    }
    private void planSlimes(){
        ritualTurn();
        if(heroDead){repaint();return;}
        java.util.List<Slime> list=slimes();
        // Sight check before rotation.
        if(!orcAlerts.contains(Integer.valueOf(selected)))
            for(Slime s:list)if(s.kind=='N'&&!s.gardenSlime&&!s.dead&&!s.escaped&&s.stun==0&&orcSeesHero(s)){
                orcAlerts.add(Integer.valueOf(selected));break;
            }
        // Idle guards rotate together. Recheck sight before any orc plans its step.
        if(!orcAlerts.contains(Integer.valueOf(selected))){
            for(Slime s:list)if(s.kind=='N'&&!s.gardenSlime&&!s.dead&&!s.escaped&&s.stun==0)s.dir=(s.dir+3)%4;
            for(Slime s:list)if(s.kind=='N'&&!s.gardenSlime&&!s.dead&&!s.escaped&&s.stun==0&&orcSeesHero(s)){
                orcAlerts.add(Integer.valueOf(selected));break;
            }
        }
        for(Slime s:list){
            if(s.kind=='V')continue; // Keep the wizard's planned movement for conflict resolution.
            s.dx=0;s.dy=0;
            if(s.kind=='P'){
                if(s.dead||s.stun>0||s.escaped)continue;
                // Stateless selection each turn: prefer visible webbed prey, whoever fired.
                int best=999,tx=-1,ty=-1,type=0;Slime target=null;
                boolean webbed=false;
                if(heroX>=0&&!heroDead&&heroHeight==0&&visible(s.x,s.y,heroX,heroY)){
                    best=Math.abs(heroX-s.x)+Math.abs(heroY-s.y);
                    tx=heroX;ty=heroY;type=1;webbed=heroWebTurns>0;
                }
                for(Slime p:list)if(p!=s&&p.kind!='P'&&!p.dead&&!p.escaped&&visible(s.x,s.y,p.x,p.y)){
                    int d=Math.abs(p.x-s.x)+Math.abs(p.y-s.y);
                    boolean w=p.webTurns>0;
                    if(type==0||(w&&!webbed)||(w==webbed&&d<best)){
                        best=d;tx=p.x;ty=p.y;type=2;target=p;webbed=w;
                    }
                }
                char[][] t=trash();
                for(int yy=0;yy<H;yy++)for(int xx=0;xx<W;xx++)if(isTrash(t[yy][xx])&&visible(s.x,s.y,xx,yy)){
                    int d=Math.abs(xx-s.x)+Math.abs(yy-s.y);
                    // Existing web marker on the firing spider represents a webbed trash tile.
                    boolean w=false;
                    for(Slime p:list)if(p.kind=='P'&&p.webTurns>0&&p.tx==xx&&p.ty==yy){w=true;break;}
                    if(type==0||(w&&!webbed)||(w==webbed&&d<best)){
                        best=d;tx=xx;ty=yy;type=3;target=null;webbed=w;
                    }
                }
                if(type==0)continue;
                s.tx=tx;s.ty=ty;
                if(webbed){
                    int distance=best,choice=-1;
                    for(int d=0;d<4;d++){
                        int nx=s.x+SX[d],ny=s.y+SY[d];
                        // A spider may step onto its webbed prey; other occupied cells remain blocked.
                        if(!(nx==tx&&ny==ty&&webbed&&type!=0)&&!freeForActor(s,nx,ny))continue;
                        int nd=Math.abs(nx-tx)+Math.abs(ny-ty);
                        if(nd<distance){distance=nd;choice=d;}
                    }
                    if(choice>=0){s.dir=choice;s.dx=SX[choice];s.dy=SY[choice];}
                }else{
                    // One free action after release, then a new shot if prey is visible.
                    boolean justReleased=type==1?heroWebJustReleased:type==2?target.webJustReleased:false;
                    if(justReleased)continue;
                    s.dir=tx<s.x?1:tx>s.x?3:ty<s.y?0:2;
                    webFire(s,tx,ty,type,target);
                }
                continue;
            }
            if(s.dead||s.stun>0||s.escaped||s.webTurns>0)continue;
            if(s.kind=='N'&&!s.gardenSlime){planOrc(s);continue;}
            if(s.kind=='G'&&!s.gardenSlime){planGoblin(s);continue;}
            if(s.kind=='K'&&!s.gardenSlime){planRat(s);continue;}
            if(!s.gardenSlime&&(s.kind=='M'||s.kind=='m')){
                // Four independent random weights, one tile per turn.
                int[] weights={random.nextInt(100)+1,random.nextInt(100)+1,random.nextInt(100)+1,random.nextInt(100)+1};
                boolean sees=heroX>=0&&!heroDead&&!heroSlimeForm&&(heroHeight>0||visible(s.x,s.y,heroX,heroY));
                if(sees){
                    if(heroX<s.x)weights[1]*=2;
                    if(heroX>s.x)weights[3]*=2;
                    if(heroY<s.y)weights[0]*=2;
                    if(heroY>s.y)weights[2]*=2;
                }
                int choice=-1,best=-1;
                for(int d=0;d<4;d++){
                    int nx=s.x+SX[d],ny=s.y+SY[d];
                    if(nx<0||nx>=W||ny<0||ny>=H)continue;
                    if(weights[d]>best){best=weights[d];choice=d;}
                }
                if(choice>=0){s.dir=choice;s.dx=SX[choice];s.dy=SY[choice];}
                continue;
            }
            if(heroX>=0&&!heroDead&&!heroSlimeForm&&heroHeight==0&&visible(s.x,s.y,heroX,heroY)){
                s.chasing=true;s.tx=heroX;s.ty=heroY;
            }
            else {
                // Trash is a secondary target. The hero always has priority.
                int best=999,tx=-1,ty=-1;
                char[][] t=trash();
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(isTrash(t[y][x])&&visible(s.x,s.y,x,y)){
                    int distance=Math.abs(x-s.x)+Math.abs(y-s.y);
                    if(distance<best){best=distance;tx=x;ty=y;}
                }
                if(tx>=0){s.chasing=true;s.tx=tx;s.ty=ty;}
            }
            if(s.chasing){
                if(s.x==s.tx&&s.y==s.ty)s.chasing=false;
                else{
                    int best=999,chosen=-1;
                    // While the target is visible behind a closed grate, wait at the grate.
                    // No BFS detour around it for either skeletons or slimes.
                    boolean directSight=visible(s.x,s.y,s.tx,s.ty);
                    int[][] distances=(s.kind=='L'||s.kind=='R')&&!directSight?actorDistances(s,s.tx,s.ty):null;
                    for(int d=0;d<4;d++){
                        int nx=s.x+SX[d],ny=s.y+SY[d];
                        if(!freeForActor(s,nx,ny))continue;
                        int dist=distances==null?Math.abs(nx-s.tx)+Math.abs(ny-s.ty):distances[ny][nx];
                        if(dist<0)continue;
                        if(directSight && dist>=Math.abs(s.x-s.tx)+Math.abs(s.y-s.ty))continue;
                        if(dist<best){best=dist;chosen=d;}
                    }
                    if(chosen>=0){s.dir=chosen;s.dx=SX[chosen];s.dy=SY[chosen];}
                    // If blocked, retain the remembered target and wait for a new sighting.
                }
            }
            if(!s.chasing && !s.gardenSlime && (s.kind=='L'||s.kind=='R')){
                // Hand-on-wall maze rule: preferred side, straight, opposite side, back.
                // Direction indices are clockwise reversed: up,left,down,right.
                int hand=s.kind=='L'?1:-1;
                int[] order={(s.dir+hand+4)%4,s.dir,(s.dir-hand+4)%4,(s.dir+2)%4};
                for(int d:order)if(freeForActor(s,s.x+SX[d],s.y+SY[d])){
                    s.dir=d;s.dx=SX[d];s.dy=SY[d];break;
                }
            }else if(!s.chasing){
                int nx=s.x+SX[s.dir],ny=s.y+SY[s.dir];
                if(!freeForActor(s,nx,ny)){
                    int[] choices=new int[4];int count=0;
                    for(int d=0;d<4;d++)if(freeForActor(s,s.x+SX[d],s.y+SY[d]))choices[count++]=d;
                    if(count>0)s.dir=choices[random.nextInt(count)];
                }
                if(freeForActor(s,s.x+SX[s.dir],s.y+SY[s.dir])){s.dx=SX[s.dir];s.dy=SY[s.dir];}
            }
        }
        // Resolve competing destinations before the simultaneous animation.
        for(int i=0;i<list.size();i++){
            Slime a=list.get(i);if(a.dx==0&&a.dy==0)continue;
            int ax=a.x+a.dx,ay=a.y+a.dy;
            for(int j=0;j<i;j++){Slime b=list.get(j);
                if(a.kind=='M'||a.kind=='m'||b.kind=='M'||b.kind=='m')continue;
                // Spider stepping onto immobilized prey is a kill, not a competing move.
                // Still prevent two spiders (or other actors) from sharing a destination.
                if(a.kind=='P'&&b.kind!='P'&&!b.dead&&!b.escaped&&b.webTurns>0
                        &&b.dx==0&&b.dy==0&&ax==b.x&&ay==b.y)continue;
                if(!b.dead&&ax==b.x+b.dx&&ay==b.y+b.dy){a.dx=0;a.dy=0;break;}
            }
        }
        ogreStep();
        // Ogre moves after the ground mobs have chosen their steps. Revalidate
        // their destinations against its NEW cell, for every ground mob kind.
        // A flying bat ignores this obstacle entirely.
        if(!ogreDebugDisabled&&ogrePresent&&!ogreDead&&!ogrePitCorpse&&ogreLevel==selected){
            for(Slime mob:list){
                if(mob.dead||mob.escaped||mob.kind=='M'||mob.kind=='m')continue;
                if(mob.x+mob.dx==ogreX&&mob.y+mob.dy==ogreY){mob.dx=0;mob.dy=0;}
            }
        }
        if(heroDead){moving=false;mobPhase=false;moveTimer.stop();queuedMoves.clear();syncSuperSlimeTimer();repaint();return;}
        mobPhase=true;movePhase=0;moving=true;moveTimer.start();
    }

    private final javax.swing.Timer moveTimer;
    // ESC overlay: same icon positions and confirmation layout as Menu 0.4.
    private int pauseScreen=0; // 0 game, 1 icons, 2 settings, 3 surrender confirmation
    private int pauseChoice=0, surrenderChoice=0;
    private boolean pausedMoveTimer=false, pausedBossTimer=false;
    private final Rectangle[] pauseButtons=new Rectangle[4];
    private final Rectangle[] surrenderButtons={new Rectangle(103,110,45,42),new Rectangle(173,110,45,42)};
    private void openPause(){
        if(pauseScreen!=0||heroDead)return;
        pauseScreen=1;pauseChoice=0;spaceHeld=false;spaceUsed=false;
        pausedMoveTimer=moveTimer.isRunning();pausedBossTimer=superSlimeTimer.isRunning();
        moveTimer.stop();superSlimeTimer.stop();gardenTimer.stop();cleanseTimer.stop();repaint();
    }
    private void closePause(){
        pauseScreen=0;
        if(pausedMoveTimer)moveTimer.start();
        if(pausedBossTimer)syncSuperSlimeTimer();
        if(gardenAwakened&&!gardenDefeated&&!gardenTimer.isRunning())gardenTimer.start();
        if(gardenCleansing&&!cleanseTimer.isRunning())cleanseTimer.start();
        pausedMoveTimer=false;pausedBossTimer=false;repaint();
    }
    private void pauseActivate(){
        if(pauseScreen==1){
            if(pauseChoice==0)closePause();
            else if(pauseChoice==1)pauseScreen=2;
            else if(pauseChoice==2){pauseScreen=3;surrenderChoice=0;}
            else {saveProgress();System.exit(0);}
        }else if(pauseScreen==3){
            if(surrenderChoice==1)saveDeathAndExit();
            else pauseScreen=1;
        }else pauseScreen=1;
        repaint();
    }
    private void pauseKey(int k){
        if(k==KeyEvent.VK_ESCAPE){if(pauseScreen==1)closePause();else {pauseScreen=1;repaint();}return;}
        if(pauseScreen==1 && (k==KeyEvent.VK_LEFT||k==KeyEvent.VK_UP))pauseChoice=(pauseChoice+3)%4;
        else if(pauseScreen==1 && (k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_DOWN))pauseChoice=(pauseChoice+1)%4;
        else if(pauseScreen==3 && (k==KeyEvent.VK_LEFT||k==KeyEvent.VK_RIGHT||k==KeyEvent.VK_UP||k==KeyEvent.VK_DOWN))surrenderChoice=1-surrenderChoice;
        if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE)pauseActivate();
        repaint();
    }
    private void drawPauseIcon(Graphics2D g,Rectangle r,int type,boolean active){
        if(active){g.setColor(new Color(226,216,184,105));g.setStroke(new BasicStroke(1.4f));g.drawRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,8,8);}
        g.setColor(active?new Color(255,237,180):new Color(230,225,216));
        g.setStroke(new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        int x=r.x+r.width/2,y=r.y+r.height/2;
        switch(type){
            case 0: g.fillPolygon(new int[]{x-8,x-8,x+11},new int[]{y-12,y+12,y},3);break;
            case 1:
                for(int i=0;i<8;i++){double a=i*Math.PI/4;g.drawLine(x+(int)(Math.cos(a)*9),y+(int)(Math.sin(a)*9),x+(int)(Math.cos(a)*13),y+(int)(Math.sin(a)*13));}
                g.fillOval(x-11,y-11,22,22);g.setColor(new Color(15,14,23));g.fillOval(x-4,y-4,8,8);break;
            case 2: g.drawLine(x-10,y-10,x+10,y+10);g.drawLine(x+10,y-10,x-10,y+10);break;
            case 3:
                g.drawRect(x-12,y-14,23,29);g.drawLine(x-5,y-11,x+6,y-15);g.drawLine(x+6,y-15,x+6,y+11);
                g.drawLine(x-5,y+11,x+6,y+11);g.drawLine(x-5,y-11,x-5,y+11);g.fillOval(x+1,y-1,3,3);break;
            case 4: g.drawLine(x+12,y,x-11,y);g.drawLine(x-11,y,x-1,y-10);g.drawLine(x-11,y,x-1,y+10);break;
            case 5:
                g.fillOval(x-11,y-12,22,21);g.fillRect(x-7,y+4,14,9);g.setColor(new Color(15,14,23));
                g.fillOval(x-7,y-4,6,7);g.fillOval(x+2,y-4,6,7);
                g.fillPolygon(new int[]{x,x-3,x+3},new int[]{y+3,y+7,y+7},3);
                g.drawLine(x-3,y+8,x-3,y+12);g.drawLine(x+3,y+8,x+3,y+12);break;
        }
    }
    private void drawControls(Graphics2D g){
        g.setColor(new Color(0,0,0,220));g.fillRect(0,0,320,240);
        // Exact pictorial scheme: four movement keys with four external arrows.
        drawKey(g,145,20,30,27,0);
        drawKey(g,111,49,30,27,2);drawKey(g,145,49,30,27,1);drawKey(g,179,49,30,27,3);
        drawArrow(g,78,62,2);drawArrow(g,242,62,3);
        drawArrow(g,160,10,0);drawArrow(g,160,94,1);
        // Space + four action keys, each paired with a fist.
        drawSpace(g,20,143,92,28);
        g.setColor(new Color(230,225,216));g.setStroke(new BasicStroke(3f));
        g.drawLine(120,157,134,157);g.drawLine(127,150,127,164);
        drawKey(g,187,122,27,25,0);
        drawKey(g,157,151,27,25,2);drawKey(g,187,151,27,25,1);drawKey(g,217,151,27,25,3);
        drawFist(g,151,126);drawFist(g,250,126);
        drawFist(g,201,105);drawFist(g,201,193);
        drawPauseIcon(g,new Rectangle(136,202,48,34),4,true);
    }
    private void drawSpace(Graphics2D g,int x,int y,int w,int h){
        g.setColor(new Color(226,216,184));g.setStroke(new BasicStroke(2f));g.drawRoundRect(x,y,w,h,5,5);
        g.drawLine(x+15,y+h-8,x+w-15,y+h-8);
    }
    private void drawKey(Graphics2D g,int x,int y,int w,int h,int direction){
        g.setColor(new Color(226,216,184));g.setStroke(new BasicStroke(2f));g.drawRoundRect(x,y,w,h,5,5);
        drawArrow(g,x+w/2,y+h/2,direction);
    }
    private void drawArrow(Graphics2D g,int x,int y,int direction){
        Graphics2D a=(Graphics2D)g.create();a.translate(x,y);a.rotate((direction==0?0:direction==1?Math.PI:direction==2?-Math.PI/2:Math.PI/2));
        a.setColor(new Color(230,225,216));a.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        a.drawLine(0,7,0,-7);a.drawLine(-5,-2,0,-7);a.drawLine(5,-2,0,-7);a.dispose();
    }
    private void drawFist(Graphics2D g,int x,int y){
        g.setColor(new Color(230,225,216));g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x-11,y-9,22,18,5,5);
        for(int i=0;i<3;i++)g.drawLine(x-6+i*5,y-9,x-6+i*5,y-2);
        g.drawLine(x-11,y+1,x-16,y-2);g.drawLine(x-16,y-2,x-14,y-8);
    }
    private void drawPause(Graphics2D g){
        g.setColor(new Color(0,0,0,175));g.fillRect(0,0,320,240);
        if(pauseScreen==1){
            for(int i=0;i<4;i++){
                Rectangle r=pauseButtons[i];Point center=new Point(r.x+r.width/2,r.y+r.height/2);
                g.setPaint(new RadialGradientPaint(center,47f,new float[]{0f,.38f,1f},new Color[]{new Color(0,0,0,205),new Color(0,0,0,143),new Color(0,0,0,0)}));
                g.fillOval(center.x-47,center.y-47,94,94);
            }
            for(int i=0;i<4;i++)drawPauseIcon(g,pauseButtons[i],i,pauseChoice==i);
        }else if(pauseScreen==3){
            g.setColor(new Color(0,0,0,210));g.fillRoundRect(76,83,169,90,9,9);
            drawPauseIcon(g,surrenderButtons[0],4,surrenderChoice==0);
            drawPauseIcon(g,surrenderButtons[1],5,surrenderChoice==1);
        }else{
            drawControls(g);
        }
    }
    private Point mouseTile(MouseEvent e){
        double sx=getWidth()/320.0,sy=getHeight()/240.0;
        return new Point((int)(e.getX()/sx),(int)(e.getY()/sy));
    }
    public void mouseClicked(MouseEvent e){
        if(deathFadeActive){exitAfterDeath();return;}
        if(ritualFinalStage>0)return;
        if(pauseScreen==0)return;
        Point p=mouseTile(e);
        if(pauseScreen==1){for(int i=0;i<4;i++)if(pauseButtons[i].contains(p)){pauseChoice=i;pauseActivate();return;}}
        else if(pauseScreen==3){for(int i=0;i<2;i++)if(surrenderButtons[i].contains(p)){surrenderChoice=i;pauseActivate();return;}}
        else {pauseScreen=1;repaint();}
    }
    public void mouseMoved(MouseEvent e){
        if(pauseScreen==0)return;
        Point p=mouseTile(e);
        if(pauseScreen==1){for(int i=0;i<4;i++)if(pauseButtons[i].contains(p)){if(pauseChoice!=i){pauseChoice=i;repaint();}return;}}
        else if(pauseScreen==3){for(int i=0;i<2;i++)if(surrenderButtons[i].contains(p)){if(surrenderChoice!=i){surrenderChoice=i;repaint();}return;}}
    }
    public void mouseDragged(MouseEvent e){}
    public void mousePressed(MouseEvent e){} public void mouseReleased(MouseEvent e){}
    public void mouseEntered(MouseEvent e){} public void mouseExited(MouseEvent e){}
    private final BufferedImageHolder buffer=new BufferedImageHolder();
    static final class BufferedImageHolder {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(320,240,java.awt.image.BufferedImage.TYPE_INT_RGB);
    }
    Viewer(java.util.List<Level> data) {
        levels=data;setPreferredSize(new Dimension(960,720));setFocusable(true);addKeyListener(this);addMouseListener(this);addMouseMotionListener(this);
        for(int i=0;i<4;i++)pauseButtons[i]=new Rectangle(31+i*69,190,50,42);
        moveTimer=new javax.swing.Timer(50,new ActionListener(){public void actionPerformed(ActionEvent e){advanceMove();}});
        superSlimeTimer=new javax.swing.Timer(3000,new ActionListener(){public void actionPerformed(ActionEvent e){spawnSuperSlimes();}});
        deathTimer=new javax.swing.Timer(100,new ActionListener(){public void actionPerformed(ActionEvent e){
            if(heroDead&&!deathFadeActive){deathTimer.stop();beginDeathFade();}
        }});
        deathTimer.start();
        loadWizard();
        restoreSave();
        resetHero();
        if(levels.get(selected).id==40)beginGardenOnFortiethLevel();
        // A normal exit resumes at the exact saved tile and elevation.
        if(restoredWorld!=null && restoredWorld.heroX>=0){
            heroX=restoredWorld.heroX;heroY=restoredWorld.heroY;
            heroHeight=restoredWorld.heroHeight;facing=restoredWorld.facing;stride=restoredWorld.stride;
            restoredWorld=null;
            repaint();
        }
    }
    // Full world snapshot stored inside the existing menu-compatible save.dat.
    private static final class World implements Serializable {
        private static final long serialVersionUID=1L;
        java.util.Map<Integer,char[][]> levels, trash, shocks;
        java.util.Map<Integer,boolean[][]> lids, crateGrates;
        java.util.Map<Integer,Integer> grateTimers;
        java.util.Map<Integer,java.util.List<Slime>> mobs;
        java.util.Set<Integer> broken;
        boolean ogreAwakened,ogreDead,ogrePresent,ogrePitCorpse,ogreDebugDisabled,ogreSpawnAtEntry,ogreAlternateStep;
        int ogreX,ogreY,ogreStun,ogreDelay,ogreLevel,ogreFacing;
        int heroX,heroY,heroHeight,facing,stride,selected;
        int ritualSteps,ritualIndex,ritualFacing;boolean ritualFinished;
        boolean gardenAwakened;int gardenOriginX,gardenOriginY,gardenRadius,gardenStep;boolean[][] gardenMask;boolean heroSlimeForm;
        boolean gardenDefeated,gardenCleansing;int cleanseX,cleanseY,cleanseStep,heroGardenStun;boolean[][] cleanseMask;
    }
    private byte[] worldBytes() throws IOException {
        World w=new World();
        w.levels=new HashMap<Integer,char[][]>(levelStates);
        w.trash=new HashMap<Integer,char[][]>(trashStates);
        w.shocks=new HashMap<Integer,char[][]>(shockStates);
        w.lids=new HashMap<Integer,boolean[][]>(lidUnderCrate);
        w.crateGrates=new HashMap<Integer,boolean[][]>(grateUnderCrate);
        w.grateTimers=new HashMap<Integer,Integer>(grateTimers);
        w.mobs=new HashMap<Integer,java.util.List<Slime>>(slimeStates);
        w.broken=new HashSet<Integer>(brokenCircuits);
        w.ogreAwakened=ogreAwakened;w.ogreDead=ogreDead;w.ogrePresent=ogrePresent;
        w.ogrePitCorpse=ogrePitCorpse;w.ogreDebugDisabled=ogreDebugDisabled;
        w.ogreSpawnAtEntry=ogreSpawnAtEntry;w.ogreAlternateStep=ogreAlternateStep;
        w.ogreX=ogreX;w.ogreY=ogreY;w.ogreStun=ogreStun;w.ogreDelay=ogreDelay;
        w.ogreLevel=ogreLevel;w.ogreFacing=ogreFacing;
        w.ritualSteps=ritualSteps;w.ritualIndex=ritualIndex;w.ritualFacing=ritualFacing;w.ritualFinished=ritualFinished;
        w.gardenAwakened=gardenAwakened;w.gardenOriginX=gardenOriginX;w.gardenOriginY=gardenOriginY;w.gardenRadius=gardenRadius;
        w.gardenStep=gardenStep;w.heroSlimeForm=heroSlimeForm;w.gardenMask=new boolean[H][W];
        w.gardenDefeated=gardenDefeated;w.gardenCleansing=gardenCleansing;w.cleanseX=cleanseX;w.cleanseY=cleanseY;w.cleanseStep=cleanseStep;w.heroGardenStun=heroGardenStun;w.cleanseMask=new boolean[H][W];
        for(int yy=0;yy<H;yy++)System.arraycopy(cleanseMask[yy],0,w.cleanseMask[yy],0,W);
        for(int yy=0;yy<H;yy++)System.arraycopy(gardenMask[yy],0,w.gardenMask[yy],0,W);
        w.heroX=heroX;w.heroY=heroY;w.heroHeight=heroHeight;
        w.facing=facing;w.stride=stride;w.selected=selected;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ObjectOutputStream stream=new ObjectOutputStream(bytes);
        try{stream.writeObject(w);}finally{stream.close();}
        return bytes.toByteArray();
    }
    private World readWorld(byte[] bytes) throws IOException,ClassNotFoundException {
        if(bytes==null||bytes.length==0)return null;
        ObjectInputStream stream=new ObjectInputStream(new ByteArrayInputStream(bytes));
        try{return (World)stream.readObject();}finally{stream.close();}
    }
    private World restoredWorld;
    private void applyWorld(World w){
        if(w==null)return;
        if(w.levels!=null)levelStates.putAll(w.levels);
        if(w.trash!=null)trashStates.putAll(w.trash);
        if(w.shocks!=null)shockStates.putAll(w.shocks);
        if(w.lids!=null)lidUnderCrate.putAll(w.lids);
        if(w.crateGrates!=null)grateUnderCrate.putAll(w.crateGrates);
        if(w.grateTimers!=null)grateTimers.putAll(w.grateTimers);
        if(w.mobs!=null)slimeStates.putAll(w.mobs);
        if(w.broken!=null)brokenCircuits.addAll(w.broken);
        ogreAwakened=w.ogreAwakened;ogreDead=w.ogreDead;ogrePresent=w.ogrePresent;
        ogrePitCorpse=w.ogrePitCorpse;ogreDebugDisabled=w.ogreDebugDisabled;
        ogreSpawnAtEntry=w.ogreSpawnAtEntry;ogreAlternateStep=w.ogreAlternateStep;
        ogreX=w.ogreX;ogreY=w.ogreY;ogreStun=w.ogreStun;ogreDelay=w.ogreDelay;
        gardenAwakened=w.gardenAwakened;heroSlimeForm=w.heroSlimeForm;
        gardenDefeated=w.gardenDefeated;gardenCleansing=w.gardenCleansing;heroGardenStun=w.heroGardenStun;
        cleanseX=w.cleanseX;cleanseY=w.cleanseY;cleanseStep=w.cleanseStep;
        for(int yy=0;yy<H;yy++){java.util.Arrays.fill(cleanseMask[yy],false);if(w.cleanseMask!=null&&yy<w.cleanseMask.length&&w.cleanseMask[yy]!=null)System.arraycopy(w.cleanseMask[yy],0,cleanseMask[yy],0,Math.min(W,w.cleanseMask[yy].length));}
        if(gardenAwakened){
            gardenOriginX=w.gardenOriginX;gardenOriginY=w.gardenOriginY;gardenRadius=w.gardenRadius;gardenStep=w.gardenStep;
            for(int yy=0;yy<H;yy++)java.util.Arrays.fill(gardenMask[yy],false);
            if(w.gardenMask!=null){
                for(int yy=0;yy<H&&yy<w.gardenMask.length;yy++)
                    if(w.gardenMask[yy]!=null)System.arraycopy(w.gardenMask[yy],0,gardenMask[yy],0,Math.min(W,w.gardenMask[yy].length));
            }else{
                // Loading a save from 0.5.37.10: preserve the previously visible garden.
                for(int yy=0;yy<H;yy++)for(int xx=0;xx<W;xx++)
                    if(Math.max(Math.abs(xx-gardenOriginX),Math.abs(yy-gardenOriginY))<=gardenRadius)gardenMask[yy][xx]=true;
            }
            if(!gardenDefeated)gardenTimer.start();
            else if(gardenCleansing)cleanseTimer.start();
        }
        ogreLevel=w.ogreLevel;ogreFacing=w.ogreFacing;
        ritualFinalTimer.stop();ritualFinalStage=0;ritualSteps=w.ritualSteps;ritualIndex=Math.max(0,Math.min(7,w.ritualIndex));ritualFacing=w.ritualFacing;ritualFinished=w.ritualFinished;ritualMoving=false;
    }
    private void restoreSave(){
        try{
            catacombs.SaveData data=catacombs.SaveData.load();
            if(data!=null){
                // Menu 0.4 clears intro after showing concrete, but leaves hero=5.
                // On the next launch, begin a fresh cycle with the man.
                if(data.hero==5 && !data.intro){
                    data.hero=1;
                    data.level=1;
                    data.fromExit=false;
                    data.intro=false;
                    data.world=null; // A completed cycle begins a clean world.
                    data.write();
                }
                heroType=Math.max(0,Math.min(3,data.hero-1));
                selected=Math.max(0,Math.min(levels.size()-1,data.level-1));
                fromExit=data.fromExit;
                World world=readWorld(data.world);
                if(world!=null){applyWorld(world);if(!data.intro)restoredWorld=world;}
            }
        }catch(Exception ex){System.err.println("Save load: "+ex);}
    }
    private void saveProgress(){
        if(deathHandled||heroDead)return;
        try{
            catacombs.SaveData data=catacombs.SaveData.load();
            if(data==null)data=new catacombs.SaveData();
            data.hero=heroType+1;data.intro=false;data.level=selected+1;data.fromExit=fromExit;data.world=worldBytes();data.write();
        }catch(Exception ex){System.err.println("Save write: "+ex);}
    }
    private void saveDeathAndExit(){
        try{
            catacombs.SaveData data=new catacombs.SaveData();
            data.hero=heroType+2;data.intro=true;data.level=1;data.fromExit=false;
            // Death on level 30 must not carry the previous hero's ritual into the next life.
            if(ritualRoom()){resetWizardVisit(selected);resetRitual();}
            data.world=worldBytes();
            // The next hero must spawn at the start, not at the death location.
            World deathWorld=readWorld(data.world);deathWorld.heroX=-1;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            ObjectOutputStream stream=new ObjectOutputStream(bytes);
            stream.writeObject(deathWorld);stream.close();data.world=bytes.toByteArray();
            data.write();
        }catch(Exception ex){System.err.println("Death save: "+ex);}
        System.exit(0);
    }
    static java.util.List<Level> load(File file) throws IOException {
        java.util.List<Level> result=new ArrayList<Level>();
        BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
        Level current=null;int row=-1,line=0;String text;
        try {
            while((text=reader.readLine())!=null){
                line++;text=text.trim();if(text.length()==0||(row<0&&text.startsWith("#")))continue;
                if(text.startsWith("[LEVEL ")&&text.endsWith("]")){
                    if(current!=null&&row!=H)throw new IOException("Level "+current.id+": expected 7 rows");
                    current=new Level();try{current.id=Integer.parseInt(text.substring(7,text.length()-1).trim());}catch(NumberFormatException e){throw new IOException("Bad level number at line "+line);}
                    if(current.id!=result.size()+1)throw new IOException("Level numbers must be sequential, expected "+(result.size()+1));
                    result.add(current);row=-1;
                } else if(text.startsWith("TILESET=")){
                    if(current==null||row>=0)throw new IOException("Unexpected TILESET at line "+line);
                    try{current.tileset=Integer.parseInt(text.substring(8).trim());}catch(NumberFormatException e){throw new IOException("Bad TILESET at line "+line);}
                    if(current.tileset<0)throw new IOException("Negative TILESET at line "+line);
                } else if(text.equals("MAP:")){
                    if(current==null||row>=0)throw new IOException("Unexpected MAP at line "+line);row=0;
                } else {
                    if(current==null||row<0||row>=H||text.length()!=W)throw new IOException("Invalid map row at line "+line+" (expected 9 cells, 7 rows)");
                    for(int x=0;x<W;x++)current.cells[row][x]=text.charAt(x);
                    row++;
                }
            }
            if(current!=null&&row!=H)throw new IOException("Level "+current.id+": expected 7 rows");
            if(result.isEmpty())throw new IOException("No levels found in "+file);
        } finally {reader.close();}
        return result;
    }
    private char[][] cells(){
        Integer key=Integer.valueOf(selected);char[][] state=levelStates.get(key);
        if(state==null){state=new char[H][W];char[][] base=levels.get(selected).cells;
            for(int y=0;y<H;y++)System.arraycopy(base[y],0,state[y],0,W);
            levelStates.put(key,state);}
        return state;
    }
    protected void paintComponent(Graphics outer){
        super.paintComponent(outer);
        Graphics2D g=buffer.image.createGraphics();
        try{
            g.setColor(new Color(13,17,23));g.fillRect(0,0,320,240);
            g.translate(16,8);
            Level level=levels.get(selected);
            char[][] state=cells();
            java.util.List<Slime> actors=slimes();
            // Ground pass: entire floor, so moving actor cannot erase adjacent floor.
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)
                drawGround(g,state[y][x],x*TILE,y*TILE,gardenTileset(x,y,level.tileset));
            // Flat trash lies on the floor and does not replace the underlying tile.
            char[][] litter=trash();
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(isTrash(litter[y][x]))
                drawTrash(g,litter[y][x],x*TILE,y*TILE);
            // Row-by-row layers: terrain and walls, then mobs, hero, grates, bats.
            // Height changes the hero's visual Y, not the row in which it is layered.
            // Hero sprite is anchored 8 pixels above the tile bottom.
            // Scientist absolute lift from tile bottom: 8 px floor, 28 px crate, 31 px wall.
            // This is rendering only: logical heroHeight and collisions are unchanged.
            int surfaceLift=scientistSurfaceLift(heroX,heroY,heroHeight);
            int hx=heroX*TILE,hy=heroY*TILE-surfaceLift;
            if(moving && !mobPhase){
                hx+=moveDX*(fighterDash?Math.abs(dashEndX-heroX)+Math.abs(dashEndY-heroY):1)*8*movePhase;
                hy+=moveDY*(fighterDash?Math.abs(dashEndX-heroX)+Math.abs(dashEndY-heroY):1)*8*movePhase;
                if(elevatedMove){
                    int targetLift=scientistSurfaceLift(heroX+moveDX,heroY+moveDY,moveHeight);
                    hy-=(targetLift-surfaceLift)*movePhase/4;
                }
                if(landingMove)hy+=surfaceLift*movePhase/4;
            }
            int heroRow=heroY;
            if(moving&&!mobPhase&&moveDY>0&&movePhase>=2)heroRow++;
            if(moving&&!mobPhase&&moveDY<0&&movePhase>=2)heroRow--;
            for(int y=0;y<H;y++){
                for(int x=0;x<W;x++){
                    if(moving && x==crateX && y==crateY)continue;
                    char c=state[y][x];
                    if(c!='W'&&c!='w'&&c!='M'&&c!='m')
                        drawObject(g,c,x*TILE,y*TILE,gardenTileset(x,y,level.tileset));
                }
                if(moving && crateX>=0){
                    int cx=crateX*TILE+moveDX*8*movePhase;
                    int cy=crateY*TILE+moveDY*8*movePhase;
                    if((cy>=y*TILE && cy<(y+1)*TILE) || (y==H-1 && cy>=H*TILE))
                        drawObject(g,'B',cx,cy,gardenTileset(crateX,crateY,level.tileset));
                }
                for(Slime slime:actors){
                    if(slime.escaped||slime.dead||((slime.kind=='M'||slime.kind=='m')&&!slime.gardenSlime)||slime.kind=='V')continue;
                    int sy=slime.y*TILE+(mobPhase?slime.dy*8*movePhase:0);
                    if(sy>=y*TILE&&sy<(y+1)*TILE)
                        drawMob(g,slime.gardenSlime?'S':slime.kind,slime.x*TILE+(mobPhase?slime.dx*8*movePhase:0),sy,level.tileset,slime.dir,slime.dx!=0||slime.dy!=0,slime.alternateStep);
                }
                drawWizard(g,y);
                if(ogrePresent&&!ogrePitCorpse&&ogreLevel==selected&&ogreY==y)drawOgreSprite(g,ogreX*TILE,ogreY*TILE);
                if(ogrePresent&&!ogrePitCorpse&&ogreLevel==selected&&ogreY==y&&ogreStun>0)drawBolt(g,ogreX*TILE,ogreY*TILE);
                for(Slime slime:actors)if(!slime.dead&&!slime.escaped&&slime.stun>0&&slime.y==y)drawBolt(g,slime.x*TILE,slime.y*TILE);
                for(int x=0;x<W;x++)if(shocks()[y][x]=='!')drawBolt(g,x*TILE,y*TILE);
                if(superSlimeRoom()&&y==4)drawSuperSlime(g);
                if(heroX>=0&&heroRow==y&&!heroInPitAnimation()&&!heroInSpikeAnimation()){
                    if(heroSlimeForm){drawMob(g,'S',hx,hy,3,facing,moving&&!mobPhase,stride!=0);if(heroGardenStun>0)drawBolt(g,hx,hy);}
                    else drawCurrentHero(g,hx,hy);
                }
                if(heroWebTurns>0&&heroY==y)drawWeb(g,hx,hy,heroWebFrame);
                for(Slime prey:actors)if(prey.kind!='P'&&prey.kind!='M'&&prey.kind!='m'&&prey.webTurns>0&&!prey.dead&&!prey.escaped&&prey.y==y)drawWeb(g,prey.x*TILE,prey.y*TILE,prey.webFrame);
                for(Slime spider:actors)if(spider.kind=='P'&&spider.webTurns>0&&spider.y==y)drawWeb(g,spider.tx*TILE,spider.ty*TILE,spider.webFrame);
                for(WebShot shot:webShots)if(shot.y==y)drawWeb(g,shot.x*TILE,shot.y*TILE,shot.frame);
                for(int x=0;x<W;x++)if(state[y][x]=='W'||state[y][x]=='w')
                    drawObject(g,state[y][x],x*TILE,y*TILE,gardenTileset(x,y,level.tileset));
                for(int x=0;x<W;x++)if(state[y][x]=='M'||state[y][x]=='m')
                    drawMob(g,state[y][x],x*TILE,y*TILE-32,level.tileset,2,false,false);
                for(Slime slime:actors)if(!slime.escaped&&!slime.gardenSlime&&(slime.kind=='M'||slime.kind=='m')){
                    int sy=slime.y*TILE-32+(mobPhase?slime.dy*8*movePhase:0);
                    int rowY=slime.y+(mobPhase&&slime.dy>0&&movePhase>=2?1:0)-(mobPhase&&slime.dy<0&&movePhase>=2?1:0);
                    if(rowY==y){
                        int sx=slime.x*TILE+(mobPhase?slime.dx*8*movePhase:0);
                        drawMob(g,slime.gardenSlime?'S':slime.kind,sx,sy,level.tileset,slime.dir,slime.dx!=0||slime.dy!=0,slime.alternateStep);
                        if(slime.webTurns>0&&!slime.dead)drawWeb(g,sx,sy,slime.webFrame);
                    }
                }
            }
            drawPitFalls(g);
            drawHeroSpikeDeath(g);
            drawEnemySpikeDeaths(g);
            g.translate(-16,-8);
            if(pauseScreen!=0)drawPause(g);
            if(deathFadeActive){
                float progress=Math.min(1f,(System.currentTimeMillis()-deathFadeStarted)/2000f);
                g.setColor(new Color(0,0,0,Math.min(225,(int)(225*progress))));
                g.fillRect(0,0,320,240);
                Graphics2D skull=(Graphics2D)g.create();
                skull.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,Math.max(0.01f,progress)));
                skull.translate(160,120);
                skull.scale(3.0,3.0);
                drawPauseIcon(skull,new Rectangle(-25,-25,50,50),5,false);
                skull.dispose();
            }
        }finally{g.dispose();}
        Graphics2D screen=(Graphics2D)outer.create();
        try{screen.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            screen.drawImage(buffer.image,0,0,getWidth(),getHeight(),null);
        }finally{screen.dispose();}
    }
    // Temporary built-in shapes copied in style, dimensions and palette from 0.2.21.1.
    // Later replace only this renderer / sprite provider, not the map format.
    // Ground never projects above the tile.  The wall is an object, not ground.

    // Optional external PNG architecture, selected by TILESET=0..3.
    // Images are loaded once; absence falls back to the legacy vector renderer.
    static final String[] ARCH_LOC={"01_catacombs","02_basement","03_castle","04_garden"};
    static final String[] ARCH_TYPES={"wall","floor","trap","trap_disabled","trap_triggered","crate","pit","pit_crate","gate_closed","gate_open","door","lever_left","lever_right","lever_broken"};
    static final java.awt.image.BufferedImage[][] ARCH=new java.awt.image.BufferedImage[4][14];
    static {
        for(int set=0;set<4;set++)for(int i=0;i<14;i++){
            java.io.File dir=new java.io.File("sprites/architecture");
            java.io.File[] candidates=dir.listFiles();
            if(candidates==null)continue;
            String prefix=ARCH_LOC[set]+"_"+String.format(java.util.Locale.ROOT,"%02d",i+1)+"_"+ARCH_TYPES[i]+"_";
            for(java.io.File f:candidates)if(f.getName().startsWith(prefix)&&f.getName().endsWith(".png")){
                try{ARCH[set][i]=javax.imageio.ImageIO.read(f);}catch(java.io.IOException ignored){}
                break;
            }
        }
    }
    static boolean architecture(Graphics2D g,int tileset,int kind,int px,int py){
        if(tileset<0||tileset>=ARCH.length||kind<0||kind>=14)return false;
        java.awt.image.BufferedImage img=ARCH[tileset][kind];
        if(img==null)return false;
        g.drawImage(img,px,py+32-img.getHeight(),null);
        return true;
    }
    static void drawGround(Graphics2D g,char c,int px,int py,int tileset){
        // Flat terrain: a floor under every object, except special ground overlays.
        if(architecture(g,tileset,1,px,py)){
            int kind=c=='F'?7:c=='X'?4:c=='1'?2:c=='x'?3:(c=='O'||c=='o')?6:-1;
            if(kind>=0)architecture(g,tileset,kind,px,py);
            return;
        }
        g.setColor(new Color(101,92,78));g.fillRect(px,py,32,32);
        g.setColor(new Color(119,109,90));g.drawRect(px,py,31,31);
        if(c=='F'){
            // Crate lid covering a pit: flat wooden planks, not a trap tile.
            g.setColor(new Color(137,91,44));g.fillRect(px+2,py+2,28,28);
            g.setColor(new Color(216,165,93));g.drawRect(px+2,py+2,27,27);
            g.drawLine(px+3,py+11,px+29,py+11);
            g.drawLine(px+3,py+21,px+29,py+21);
            g.setColor(new Color(95,62,34));g.fillRect(px+7,py+6,2,2);g.fillRect(px+24,py+24,2,2);
        }
        if(c=='X'){
            // Spike trap (distinct from the crate lid).
            g.setColor(new Color(68,69,74));g.fillRect(px+3,py+3,26,26);
            g.setColor(new Color(195,198,202));
            for(int yy=7;yy<=23;yy+=8)for(int xx=7;xx<=23;xx+=8){
                int[] xs={px+xx-3,px+xx,px+xx+3};
                int[] ys={py+yy+3,py+yy-4,py+yy+3};
                g.fillPolygon(xs,ys,3);
            }
        }
        if(c=='1'){
            // Armed: almost indistinguishable from floor, only two altered pixels.
            g.setColor(new Color(75,68,61));g.fillRect(px+9,py+11,2,2);g.fillRect(px+23,py+22,2,2);
        }
        if(c=='x'){
            // Scientist-disabled: single dark centre pixel.
            g.setColor(new Color(38,35,34));g.fillRect(px+16,py+16,2,2);
        }
        if(c=='O'||c=='o'){
            g.setColor(new Color(12,13,17));g.fillRect(px+3,py+3,26,26);
            g.setColor(new Color(39,37,42));g.drawRect(px+3,py+3,25,25);
        }
    }
    private void drawOgreSprite(Graphics2D g,int px,int py){
        int pose=(ogreMovedThisTurn&&mobPhase&&movePhase>0)?(ogreAlternateStep?1:2):0;
        java.awt.image.BufferedImage img=OGRE_IMAGES[ogreFacing][pose];
        if(img!=null){g.drawImage(img,px,py+32-img.getHeight()-8,null);return;}
        drawOgre(g,px,py);
    }
    static void drawOgre(Graphics2D g,int px,int py){
        int top=py-8;
        g.setColor(new Color(91,71,48));g.fillRect(px+3,top+7,26,32);
        g.setColor(new Color(133,111,76));g.fillOval(px+5,top,22,22);
        g.setColor(new Color(34,29,24));g.fillRect(px+10,top+11,4,4);g.fillRect(px+20,top+11,4,4);
        g.setColor(new Color(206,192,156));g.fillRect(px+14,top+19,4,5);
        g.setColor(new Color(65,50,36));g.fillRect(px+1,top+25,6,14);g.fillRect(px+25,top+25,6,14);
    }
    static void drawBolt(Graphics2D g,int px,int py){
        g.setColor(new Color(255,241,85));
        int[] xx={px+18,px+12,px+18,px+14,px+23,px+19};
        int[] yy={py-6,py+10,py+10,py+27,py+7,py+7};
        g.fillPolygon(xx,yy,6);
        g.setColor(new Color(255,255,220));g.drawLine(px+18,py-5,px+13,py+10);
    }
    static void drawTrash(Graphics2D g,char kind,int px,int py){
        // Five temporary punctuation-based pixel designs; replaceable by PNG sprites.
        int variant="!?,;:".indexOf(kind);
        Color[] palette={new Color(183,153,95),new Color(137,158,145),new Color(172,116,92),
            new Color(155,137,181),new Color(118,158,171)};
        if(variant<0)return;
        g.setColor(palette[variant]);
        int cx=px+16,cy=py+17;
        switch(variant){
        case 0:g.fillRect(cx-4,cy-3,8,6);g.fillRect(cx-2,cy+3,5,2);break;
        case 1:g.fillOval(cx-5,cy-4,10,8);g.setColor(new Color(70,72,65));g.fillRect(cx,cy-2,3,2);break;
        case 2:g.fillPolygon(new int[]{cx-5,cx+4,cx+2,cx-4},new int[]{cy+2,cy-4,cy+5,cy+4},4);break;
        case 3:g.fillRect(cx-5,cy-2,4,7);g.fillRect(cx+1,cy-4,5,6);break;
        case 4:g.fillOval(cx-5,cy-3,7,7);g.fillRect(cx+2,cy-5,4,8);break;
        }
    }
    static boolean knownSymbol(char c){
        return "#.D@EeTtWw1XxFOoBbSsLRMmG".indexOf(c)>=0;
    }
    // All raised sprites use a 32x40 bounding box, bottom anchored to the tile.
    // This is deliberately temporary vector artwork, not a permanent sprite format.
    void drawObject(Graphics2D g,char c,int px,int py,int tileset){
        int kind=c=='#'?0:(c=='D'||c=='@'||c=='E'||c=='e')?10:(c=='B'||c=='b')?5:c=='W'?8:c=='w'?9:c=='T'?(grateTime()>0?12:11):c=='t'?13:-1;
        if(kind>=0&&architecture(g,tileset,kind,px,py))return;
        int top=py-8;
        if(c=='#'){
            g.setColor(new Color(66,67,73));g.fillRect(px,top,32,40);
            g.setColor(new Color(91,91,98));g.drawRect(px,top,31,39);
            g.setColor(new Color(52,52,59));g.drawLine(px+2,top+20,px+29,top+20);
        } else if(c=='D'||c=='@'||c=='E'||c=='e'){
            g.setColor(new Color(90,45,23));g.fillRect(px+5,py+3,22,28);
            g.setColor(new Color(201,163,91));g.drawRect(px+5,py+3,21,27);g.fillRect(px+22,py+17,2,2);
        } else if(c=='G'){
            g.setColor(new Color(45,113,53));g.fillOval(px+5,top+2,22,22);
            g.setColor(new Color(73,130,57));g.fillRect(px+8,top+21,16,17);
            g.setColor(Color.YELLOW);g.fillRect(px+11,top+12,3,3);g.fillRect(px+19,top+12,3,3);
        } else if(c=='t'){
            drawGround(g,'F',px,py,tileset);
        } else if(c=='T'){

            g.setColor(new Color(65,60,52));g.fillRect(px+8,top+10,16,28);
            g.setColor(new Color(174,163,139));g.drawRect(px+8,top+10,15,27);
            g.setColor(new Color(212,189,111));g.drawLine(px+16,top+30,grateTime()>0?px+23:px+10,top+7);
            g.fillOval(px+7,top+4,7,7);
            if(c=='t'){g.setColor(new Color(230,91,59));g.drawLine(px+6,top+4,px+25,top+34);g.drawLine(px+25,top+4,px+6,top+34);}
        } else if(c=='W'||c=='w'){
            if(c=='w'){
                // Raised open grille: bars retracted into the upper frame, floor unobstructed.
                g.setColor(new Color(75,81,86));g.fillRect(px+1,top+1,30,7);
                g.setColor(new Color(170,182,191));g.fillRect(px+2,top+2,28,3);
                g.setColor(new Color(108,121,132));g.fillRect(px+2,top+7,28,2);
                return;
            }
            g.setColor(new Color(42,46,51));g.fillRect(px+1,top+1,30,38);
            g.setColor(new Color(161,175,184));
            for(int bar=5;bar<30;bar+=7)g.fillRect(px+bar,top+2,3,36);
            g.setColor(new Color(110,119,125));
            g.fillRect(px+2,top+10,28,3);g.fillRect(px+2,top+30,28,3);
        } else if(c=='B'||c=='b'){
            g.setColor(new Color(137,91,44));g.fillRect(px+2,top+2,28,36);
            g.setColor(new Color(216,165,93));g.drawRect(px+2,top+2,27,35);
            g.drawLine(px+4,top+4,px+28,top+36);
            g.drawLine(px+28,top+4,px+4,top+36);
        } else if(c=='S'||c=='s'){
            g.setColor(new Color(75,153,82));g.fillOval(px+4,top+7,24,31);
            g.setColor(new Color(29,69,32));g.drawOval(px+4,top+7,23,30);
            g.setColor(Color.WHITE);g.fillRect(px+11,top+15,3,4);g.fillRect(px+20,top+15,3,4);
        } else if(c=='L'||c=='R'){
            g.setColor(new Color(221,215,185));g.fillOval(px+7,top+4,18,24);
            g.setColor(new Color(45,42,39));g.fillRect(px+11,top+13,4,4);g.fillRect(px+19,top+13,4,4);
            g.setColor(new Color(225,220,190));g.fillRect(px+10,top+28,12,10);
            g.setColor(Color.BLACK);g.setFont(new Font("Dialog",Font.BOLD,11));
            g.drawString(c=='L'?"Л":"Р",px+12,top+36);
        } else if(c=='M'||c=='m'){
            g.setColor(new Color(45,38,53));g.fillOval(px+2,top+5,28,23);
            g.setColor(new Color(112,85,121));g.fillOval(px+10,top+11,12,27);
            g.setColor(new Color(220,70,67));g.fillRect(px+13,top+16,2,2);g.fillRect(px+18,top+16,2,2);
        } else if(!knownSymbol(c)){
            g.setColor(new Color(170,18,30));g.fillRect(px+3,py+3,26,26);
            g.setColor(Color.WHITE);g.setFont(new Font("Monospaced",Font.BOLD,22));
            g.drawString("?",px+10,py+25);
        }
    }
    // Purely visual spawn: find @ or E in map, prefer adjacent walkable floor.
    // No coordinates or door roles are hardcoded per level.
    static int[] findSpawn(Level level,char door){
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(level.cells[y][x]==door){
            int[][] offsets={{1,0},{-1,0},{0,1},{0,-1}};
            for(int[] d:offsets){int nx=x+d[0],ny=y+d[1];
                if(nx>=0&&nx<W&&ny>=0&&ny<H && level.cells[ny][nx]=='.')return new int[]{nx,ny};
            }
            return new int[]{x,y}; // Show hero on door if no adjacent floor exists.
        }
        return null;
    }
    private long heroActionUntil=0;
    private void showHeroAction(){
        heroActionUntil=System.currentTimeMillis()+220;
        javax.swing.Timer timer=new javax.swing.Timer(230,new ActionListener(){public void actionPerformed(ActionEvent e){repaint();}});
        timer.setRepeats(false);timer.start();repaint();
    }
    private int scientistSurfaceLift(int x,int y,int height){
        if((heroType!=2&&!heroSlimeForm)||height<=0)return 0;
        if(x<0||x>=W||y<0||y>=H)return 20;
        char surface=cells()[y][x];
        return surface=='#'?23:20;
    }
    private void drawCurrentHero(Graphics2D g,int px,int py){
        if(heroType>=0&&heroType<=3){
            int pose=0;
            java.awt.image.BufferedImage img=null;
            if(heroType==3){
                if(fighterDash&&moving&&!mobPhase){
                    img=FIGHTER_DASH_IMAGES[facing][Math.min(3,Math.max(0,movePhase))];
                }else{
                    if(moving&&!mobPhase&&movePhase>0)pose=(stride==0?1:2);
                    else if(System.currentTimeMillis()<heroActionUntil)pose=3;
                    img=FIGHTER_HERO_IMAGES[facing][pose];
                }
            }else if(heroType==2){
                if(moving&&!mobPhase){
                    if(elevatedMove&&!walkingOnTop){
                        int phase=Math.min(3,Math.max(0,movePhase));
                        img=SCIENTIST_CLIMB_IMAGES[facing][phase];
                    }else if(landingMove){
                        // Descent: stand -> step -> stand -> climbing1 -> stand.
                        if(movePhase==1)img=SCIENTIST_HERO_IMAGES[facing][stride==0?1:2];
                        else if(movePhase==3)img=SCIENTIST_CLIMB_IMAGES[facing][1];
                        else img=SCIENTIST_HERO_IMAGES[facing][0];
                    }else{
                        pose=(movePhase>0)?(stride==0?1:2):0;
                    }
                }else if(System.currentTimeMillis()<heroActionUntil){
                    img=SCIENTIST_CLIMB_IMAGES[facing][1];
                }
                if(img==null)img=SCIENTIST_HERO_IMAGES[facing][pose];
            }else{
                if(moving&&!mobPhase&&movePhase>0)
                    pose=pushAction?3:(stride==0?1:2);
                else if(System.currentTimeMillis()<heroActionUntil)pose=3;
                img=(heroType==0?BUCKET_HERO_IMAGES:POLICE_HERO_IMAGES)[facing][pose];
            }
            if(img!=null){g.drawImage(img,px,py+32-img.getHeight()-8,null);return;}
        }
        drawHero(g,px,py-8,heroType);
    }
    static void drawHero(Graphics2D g,int px,int py,int type){
        int top=py-8;
        Color[] coats={new Color(120,103,78),new Color(65,89,145),new Color(112,126,106),new Color(55,62,72)};
        g.setColor(new Color(205,167,131));g.fillOval(px+11,top+4,10,11);
        g.setColor(coats[type]);g.fillRect(px+8,top+16,16,15);
        g.fillRect(px+9,top+30,5,9);g.fillRect(px+18,top+30,5,9);
        g.setColor(new Color(25,25,28));g.drawRect(px+8,top+16,15,14);
        g.setColor(Color.WHITE);g.setFont(new Font("Monospaced",Font.BOLD,9));
        g.drawString(Integer.toString(type+1),px+13,top+27);
    }
    // 0.5.45.0: pit visuals only. Four 100-ms frames, with no change to pit rules.
    private static final class PitFall {
        int x,y,frame,kind,direction,tileset,heroType;
        boolean hero,slime,ogre;
        final java.awt.image.BufferedImage[] poses=new java.awt.image.BufferedImage[4];
    }
    private final java.util.List<PitFall> pitFalls=new java.util.ArrayList<PitFall>();
    private final javax.swing.Timer pitTimer=new javax.swing.Timer(100,new ActionListener(){
        public void actionPerformed(ActionEvent e){
            boolean heroFinished=false;
            for(java.util.Iterator<PitFall> it=pitFalls.iterator();it.hasNext();){
                PitFall f=it.next();f.frame++;
                if(f.frame>=4){if(f.hero)heroFinished=true;it.remove();}
            }
            if(pitFalls.isEmpty())pitTimer.stop();
            if(heroFinished)fallToBeginning();
            repaint();
        }
    });
    private void beginPitFall(boolean hero,Slime mob,int x,int y){
        PitFall f=new PitFall();f.hero=hero;f.x=x;f.y=y;
        f.direction=hero?facing:mob.dir;
        f.kind=hero?'S':mob.kind;f.slime=hero?heroSlimeForm:mob.gardenSlime;
        f.heroType=heroType;f.tileset=levels.get(selected).tileset;
        for(int i=0;i<4;i++){
            java.awt.image.BufferedImage b=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg=b.createGraphics();
            try{
                int pose=gardenRandom.nextInt(3);
                if(hero&&!f.slime){
                    java.awt.image.BufferedImage img=null;
                    if(heroType==0)img=BUCKET_HERO_IMAGES[f.direction][pose];
                    else if(heroType==1)img=POLICE_HERO_IMAGES[f.direction][pose];
                    else if(heroType==2)img=SCIENTIST_HERO_IMAGES[f.direction][pose];
                    else img=FIGHTER_HERO_IMAGES[f.direction][pose];
                    if(img!=null)bg.drawImage(img,16,56-img.getHeight(),null);
                    else drawHero(bg,16,16,heroType);
                }else{
                    char c=f.slime?'S':(char)f.kind;
                    java.awt.image.BufferedImage img=null;
                    int d=Math.max(0,Math.min(3,f.direction));
                    if(c=='L'||c=='R')img=SKELETON_IMAGES[(d==1?3:d==3?1:d)][pose];
                    else if(c=='N')img=ORC_IMAGES[d][pose];
                    else if(c=='G')img=GOBLIN_IMAGES[d][pose];
                else if(c=='K')img=RAT_IMAGES[d][pose];
                    else if(c=='S'||c=='s')img=MOB_IMAGES[f.slime?0:(f.tileset==1?1:f.tileset==2?2:0)][pose];
                    if(img!=null)bg.drawImage(img,16+(32-img.getWidth())/2,56-img.getHeight(),null);
                    else drawMob(bg,c,16,24,f.tileset,d,false,false);
                }
            }finally{bg.dispose();}
            f.poses[i]=b;
        }
        pitFalls.add(f);pitTimer.start();
        if(hero){queuedMoves.clear();moving=false;mobPhase=false;moveTimer.stop();movePhase=0;}
        repaint();
    }
    // Ogre retains its own pit handler and delayed respawn rules.
    private void beginOgrePitFall(){
        for(PitFall old:pitFalls)if(old.ogre)return;
        PitFall f=new PitFall();f.ogre=true;f.x=ogreX;f.y=ogreY;
        for(int i=0;i<4;i++){
            java.awt.image.BufferedImage b=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg=b.createGraphics();
            try{
                java.awt.image.BufferedImage img=OGRE_IMAGES[ogreFacing][gardenRandom.nextInt(3)];
                if(img!=null)bg.drawImage(img,16,56-img.getHeight(),null);
                else drawOgre(bg,16,24);
            }finally{bg.dispose();}
            f.poses[i]=b;
        }
        pitFalls.add(f);pitTimer.start();repaint();
    }
    private void drawPitFalls(Graphics2D g){
        for(PitFall f:pitFalls){
            // Fixed pit mouth: move the full sprite DOWN by 10px each frame.
            // Clipping stays fixed, so its lower quarters disappear below the rim.
            int sink=f.frame*10;
            Graphics2D clipped=(Graphics2D)g.create();
            try{
                clipped.clipRect(f.x*TILE-16,f.y*TILE-24,64,40);
                clipped.drawImage(f.poses[f.frame],f.x*TILE-16,f.y*TILE-40+sink,null);
            }finally{clipped.dispose();}
        }
    }
    private boolean isFallingMob(Slime mob){for(PitFall f:pitFalls)if(!f.hero&&f.x==mob.x&&f.y==mob.y)return true;return false;}
    private boolean heroInPitAnimation(){for(PitFall f:pitFalls)if(f.hero)return true;return false;}
    // Pit transition only: preserve the hero and all existing world state.
    private void fallToBeginning(){
        queuedMoves.clear();
        pitFalls.clear();pitTimer.stop();
        moving=false;mobPhase=false;moveTimer.stop();movePhase=0;
        fighterDash=false;landingMove=false;elevatedMove=false;
        crateX=crateY=-1;pushAction=false;pushIntoPit=false;pushIntoFilledPit=false;
        doorMove=false;doorTarget=-1;
        selected=0;fromExit=false;heroHeight=0;heroDead=false;
        int[] at=findSpawn(levels.get(0),'@');
        if(at==null && cells()[3][2]=='.' && !slimeAt(2,3))at=new int[]{2,3};
        heroX=at==null?-1:at[0];heroY=at==null?-1:at[1];
        syncSuperSlimeTimer();saveProgress();repaint();
    }
    private void resetHero(){
        heroGardenStun=0;
        queuedMoves.clear();
        moving=false;mobPhase=false;moveTimer.stop();movePhase=0;crateX=-1;crateY=-1;slimes();
        int[] at=findSpawn(levels.get(selected),fromExit?'E':'@');
        // The opening room has a fixed, agreed start: its centre (2,3).
        // Do not invent a spawn by scanning for the first available floor.
        if(at==null && selected==0 && !fromExit && cells()[3][2]=='.' && !slimeAt(2,3))
            at=new int[]{2,3};
        heroX=at==null?-1:at[0];heroY=at==null?-1:at[1];heroHeight=0;heroDead=false;
        if(levels.get(selected).id==40)beginGardenOnFortiethLevel();
        syncSuperSlimeTimer();repaint();
    }
    // Entry is read from the destination map. Never assign a door by level number.
    // Only a floor cell, crate, or slime can be cleared for entry; solid terrain cannot.
    private boolean entryCandidate(int x,int y){
        if(x<0||y<0||x>=W||y>=H)return false;
        char c=cells()[y][x];
        return c=='.'||c=='1'||c=='x'||c=='X'||c=='F'||c=='w'||c=='B'||c=='b'||((c=='S'||c=='s'));
    }
    private boolean relocateSlime(Slime target,int reservedX,int reservedY){
        // Uniform random coordinate sampling with rejection, as agreed.
        // Precheck prevents an endless loop on a full map.
        boolean available=false;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)
            if(!(x==reservedX&&y==reservedY)&&freeForSlime(x,y)&&!(x==heroX&&y==heroY))available=true;
        if(!available)return false;
        while(true){
            int x=random.nextInt(W),y=random.nextInt(H);
            if((x==reservedX&&y==reservedY)||(x==heroX&&y==heroY)||!freeForSlime(x,y))continue;
            target.x=x;target.y=y;target.dx=target.dy=0;target.chasing=false;target.tx=target.ty=-1;
            return true;
        }
    }
    private boolean enterLevel(int target,boolean backward){
        if(target<0||target>=levels.size())return false;
        char door=backward?'E':'@';
        Level destination=levels.get(target);
        int doorX=-1,doorY=-1;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(destination.cells[y][x]==door){doorX=x;doorY=y;break;}
        if(doorX<0)return false;
        int oldSelected=selected,oldX=heroX,oldY=heroY;
        selected=target;
        slimes(); // Convert map slime symbols to independent actors.
        // Ratman always starts fresh on each new entry, even after death or escape.
        for(Slime rat:slimes())if(rat.kind=='K'){
            rat.x=rat.spawnX;rat.y=rat.spawnY;rat.dead=false;rat.escaped=false;
            rat.stun=0;rat.dx=rat.dy=0;rat.ratFleeing=false;
            rat.ratLeverX=rat.ratLeverY=-1;rat.tx=rat.ty=-1;
        }
        for(Slime gob:slimes())if(gob.kind=='G'&&gob.escaped){
            for(int yy=0;yy<H;yy++)for(int xx=0;xx<W;xx++)if(levels.get(selected).cells[yy][xx]=='G'){
                gob.x=xx;gob.y=yy;gob.escaped=false;gob.dead=false;gob.stun=0;gob.dx=gob.dy=0;
                gob.tx=gob.ty=-1;gob.chasing=false;
            }
        }
        int[][] offsets={{0,-1},{1,0},{0,1},{-1,0}};
        int spawnX=-1,spawnY=-1;
        for(int[] d:offsets){
            int x=doorX+d[0],y=doorY+d[1];
            if(!entryCandidate(x,y))continue;
            // No room to relocate a mob? Try another adjacent cell.
            Slime occupant=null;
            for(Slime slime:slimes())if(slime.x==x&&slime.y==y){occupant=slime;break;}
            if(occupant!=null&&!relocateSlime(occupant,x,y))continue;
            char c=cells()[y][x];
            if(c=='B'||c=='b')cells()[y][x]='.';
            if(slimeAt(x,y))continue; // Verify after relocation.
            spawnX=x;spawnY=y;break;
        }
        if(spawnX<0){selected=oldSelected;heroX=oldX;heroY=oldY;return false;}
        // Reset the ritual only after a successful level transition.
        // This also clears a completed ritual when the player leaves level 30.
        if(oldSelected!=target){
            resetWizardVisit(oldSelected);
            resetWizardVisit(target);
            resetRitual();
        }
        if(oldSelected!=target){resetOrcsOnExit(oldSelected);clearGardenForms(oldSelected);}
        // Spawned slimes exist only during a visit to level 20.
        if(levels.get(oldSelected).id==20 && oldSelected!=target){
            slimeStates.remove(Integer.valueOf(oldSelected));
        }
        queuedMoves.clear();
        heroX=spawnX;heroY=spawnY;heroHeight=0;fromExit=backward;heroDead=false;
        if(levels.get(selected).id==40){gardenDefeated=false;gardenAwakened=false;beginGardenOnFortiethLevel();}
        else if(gardenAwakened&&!gardenDefeated)startGardenAt(heroX,heroY);
        else if(gardenDefeated){gardenTimer.stop();cleanseTimer.stop();gardenCleansing=false;for(int y=0;y<H;y++)java.util.Arrays.fill(gardenMask[y],false);}
        applyGardenTransformations();

        if(!ogreDebugDisabled&&ogreAwakened&&!ogreDead)scheduleOgre(!backward);
        moving=false;mobPhase=false;moveTimer.stop();movePhase=0;crateX=crateY=-1;
        syncSuperSlimeTimer();saveProgress();repaint();return true;
    }
    // Fighter dash: up to two tiles. A pit or trap on the FIRST tile is jumped
    // over; the final tile is a normal landing. Destructible objects stop the dash.
    private void fighterRush(int dx,int dy){
        char[][] a=cells();
        int endX=heroX,endY=heroY;
        for(int step=1;step<=2;step++){
            int x=heroX+dx*step,y=heroY+dy*step;
            if(x<0||y<0||x>=W||y>=H)break;
            char c=a[y][x];
            if(c=='#'||c=='W')break;
            if(ogreAt(x,y)||superSlimeAt(x,y)){endX=x;endY=y;break;}
            if(c=='B'||c=='b'||c=='T'||c=='t'){
                endX=x;endY=y;break;
            }
            Slime occupant=null;
            for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==x&&mob.y==y){occupant=mob;break;}
            if(occupant!=null){endX=x;endY=y;break;}
            if(c=='.'||c=='1'||c=='x'||c=='X'||c=='F'||c=='O'||c=='o'||c=='w'||c=='E'||c=='@'){
                if(step==1&&(c=='O'||c=='o'||c=='1'||c=='X')){
                    // A hazardous first cell can be crossed only if a second cell exists.
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=W||yy>=H||a[yy][xx]=='#'||a[yy][xx]=='W')break;
                }
                endX=x;endY=y;
                if(c=='E'||c=='@')break;
            }else break;
        }
        if(endX==heroX&&endY==heroY)return;
        dashEndX=endX;dashEndY=endY;fighterDash=true;
        moveDX=dx;moveDY=dy;movePhase=0;
        startTurn();moving=true;moveTimer.start();
    }
    private void resolveFighterDash(){
        int x=dashEndX,y=dashEndY;
        char[][] a=cells();char c=a[y][x];
        if(ogreAt(x,y)||superSlimeAt(x,y))heroDead=true;
        for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==x&&mob.y==y){
            if(mob.kind=='N'){
                // Fighter cannot kill an orc directly: stun it and try a one-tile push.
                mob.stun=1;mob.dx=mob.dy=0;
                int nx=x+moveDX,ny=y+moveDY;
                boolean canPush=nx>=0&&ny>=0&&nx<W&&ny<H;
                if(canPush){
                    char landing=a[ny][nx];
                    if(landing=='#'||landing=='W'||landing=='B'||landing=='b'||landing=='T'||landing=='t'||
                       landing=='G'||ogreAt(nx,ny)||superSlimeAt(nx,ny))canPush=false;
                    for(Slime other:slimes())if(other!=mob&&!other.dead&&!other.escaped&&other.x==nx&&other.y==ny)canPush=false;
                    if(canPush){
                        mob.x=nx;mob.y=ny;
                        if(landing=='O'||landing=='o'||landing=='1'||landing=='X'){
                            mob.dead=true;
                            if(landing=='O'||landing=='o')beginPitFall(false,mob,nx,ny);
                            if(landing=='1'){a[ny][nx]='X';beginEnemySpikeDeath(mob);}
                        }
                    }
                }
                // The fighter stops immediately before the stunned or pushed orc.
                dashEndX=x-moveDX;dashEndY=y-moveDY;
                x=dashEndX;y=dashEndY;c=a[y][x];
            }else if(mob.kind=='L'||mob.kind=='R'||mob.kind=='M'||mob.kind=='m'||mob.kind=='G'||mob.kind=='K'||mob.kind=='P')mob.dead=true;
            else {heroDead=true;}
        }
        if(c=='B'||c=='b')a[y][x]='.';
        if(c=='T'){
            a[y][x]='t';brokenCircuits.add(Integer.valueOf(selected));
            // Breaking the lever performs exactly one toggle, then locks the circuit.
            flipGrates();
            setGrateTime(0);
        }
        heroX=x;heroY=y;
        if(c=='O'||c=='o'){
            fighterDash=false;beginPitFall(true,null,x,y);return;
        }
        if(c=='1'||c=='X'){
            if(c=='1')a[y][x]='X';
            fighterDash=false;beginHeroSpikeDeath(x,y);return;
        }
        collectTrash(x,y);
        fighterDash=false;moving=false;movePhase=0;moveTimer.stop();stride^=1;
        if(!heroDead&&(c=='E'||c=='@')){
            int target=selected+(c=='E'?1:-1);
            if(target>=0&&target<levels.size()){enterLevel(target,target<selected);repaint();return;}
        }
        tickGrates();if(!heroDead)planSlimes();else syncSuperSlimeTimer();repaint();
    }
    private void beginMove(int dx,int dy,int direction,boolean action){
        if(moving||heroX<0||heroDead||heroInPitAnimation()||heroInSpikeAnimation())return;
        facing=direction;
        int nx=heroX+dx,ny=heroY+dy;
        if(nx<0||nx>=W||ny<0||ny>=H)return;
        char[][] state=cells();
        char c=state[ny][nx];
        crateX=-1;crateY=-1;pushAction=false;pushIntoPit=false;pushIntoFilledPit=false;doorMove=false;doorTarget=-1;
        elevatedMove=false;landingMove=false;walkingOnTop=false;
        if(heroType==3 && action && !heroSlimeForm){fighterRush(dx,dy);return;}
        if(heroType==2||heroSlimeForm){
            if(heroHeight>0){
                if(action){
                    // On a wall or crate, SPACE+direction always means jump down.
                    // A wall/crate itself is not a landing tile.
                    if(onTop(c))return;
                    landingMove=true;moveDX=dx;moveDY=dy;movePhase=0;
                    startTurn();moving=true;moveTimer.start();return;
                }
                if(onTop(c)){
                    // Move on top of adjacent crates and walls: ordinary walking animation.
                    walkingOnTop=true;elevatedMove=true;moveHeight=1;moveDX=dx;moveDY=dy;movePhase=0;
                    startTurn();moving=true;moveTimer.start();return;
                }
                // Ordinary arrow from an elevated tile doesn't silently drop.
                return;
            }
            if(action&&onTop(c)){
                // Scientist may climb onto a crate from the floor, never directly onto a wall.
                if(c=='#')return;
                elevatedMove=true;moveHeight=1;moveDX=dx;moveDY=dy;movePhase=0;
                startTurn();moving=true;moveTimer.start();return;
            }
            if(action&&c=='T'){showHeroAction();toggleLever(nx,ny);return;}
        }
        if(!action && (c=='E'||c=='@')){
            int target=selected+(c=='E'?1:-1);
            if(target<0||target>=levels.size())return;
            // Do not animate a transition if destination has no valid entry.
            doorMove=true;doorTarget=target;
            moveDX=dx;moveDY=dy;movePhase=0;startTurn();moving=true;moveTimer.start();return;
        }
        if(action && heroType==1 && !heroSlimeForm){
            if(c=='B'||c=='b'){ /* push below */ }
            else if(c=='T'){toggleLever(nx,ny);return;}
            else {showHeroAction();shock(nx,ny);return;}
        }
        if(action && heroType==0 && !heroSlimeForm && c=='T'){toggleLever(nx,ny);return;}
        if(action && heroType==0 && !heroSlimeForm && c!='B' && c!='b'){
            throwTrash(dx,dy);return;
        }
        if(action && heroType==2 && !heroSlimeForm && (c=='1'||c=='x')){
            state[ny][nx]=(c=='1'?'x':'1');
            showHeroAction();finishActionTurn();return;
        }
        if(c=='B'||c=='b'){
            // The first hero can push only through SPACE + direction.
            if(!action || heroSlimeForm || (heroType!=0 && heroType!=1))return;
            int bx=nx+dx,by=ny+dy;
            if(bx<0||bx>=W||by<0||by>=H)return;
            if((state[by][bx]!='.' && state[by][bx]!='F' && state[by][bx]!='1' && state[by][bx]!='O' && state[by][bx]!='o' && state[by][bx]!='w')||slimeAt(bx,by))return;
            pushIntoPit=(state[by][bx]=='O'||state[by][bx]=='o');
            pushIntoFilledPit=(state[by][bx]=='F');
            crateX=nx;crateY=ny;pushAction=true;
        }else if((c!='.' && c!='1' && c!='x' && c!='X' && c!='F' && c!='O' && c!='o' && c!='w' && c!='T' && c!='t')||slimeAt(nx,ny))return;
        // An action directed at an empty cell is not a normal walk.
        if(action&&!pushAction)return;
        moveDX=dx;moveDY=dy;movePhase=0;startTurn();moving=true;moveTimer.start();
    }
    private void advanceMove(){
        if(!moving)return;
        movePhase++;
        if(movePhase>=4){
            if(fighterDash){resolveFighterDash();return;}
            if(mobPhase){
                for(Slime slime:slimes()){
                    if(slime.dead||slime.escaped||slime.stun>0||slime.webTurns>0)continue;
                    if(slime.kind=='V'){
                        if(ritualMoving){
                            // Only advance the route after an actual one-tile move.
                            int next=(ritualIndex+1)%RITUAL_OX.length;
                            int nx=ritualX(next),ny=ritualY(next);
                            boolean valid=slime.x+slime.dx==nx&&slime.y+slime.dy==ny
                                &&Math.abs(slime.dx)+Math.abs(slime.dy)==1;
                            if(valid){
                                // Another mob may have arrived at the destination during the animation.
                                for(Slime other:slimes())if(other!=slime&&!other.dead&&!other.escaped
                                    &&other.x==nx&&other.y==ny){valid=false;break;}
                            }
                            if(valid){slime.alternateStep=!slime.alternateStep;slime.x=nx;slime.y=ny;ritualIndex=next;}
                            slime.dx=slime.dy=0;ritualMoving=false;
                        }
                        continue;
                    }
                    if(slime.dx!=0||slime.dy!=0)slime.alternateStep=!slime.alternateStep;
                    slime.x+=slime.dx;slime.y+=slime.dy;slime.dx=0;slime.dy=0;
                    // Standard step-on collision: spider occupies the prey tile, then prey dies.
                    if(slime.kind=='P')for(Slime prey:slimes())if(prey!=slime&&prey.kind!='P'&&!prey.dead&&!prey.escaped&&prey.x==slime.x&&prey.y==slime.y){prey.dead=true;prey.webTurns=0;}
                    if((slime.kind=='G'||slime.kind=='K')&&(cells()[slime.y][slime.x]=='@'||cells()[slime.y][slime.x]=='E')){slime.escaped=true;continue;}
                    char ground=cells()[slime.y][slime.x];
                    if(slime.kind!='M'&&slime.kind!='m'&&ground!='W'&&(ground=='1'||(slime.kind!='P'&&(ground=='O'||ground=='o')))){
                        slime.dead=true;
                        if(ground=='O'||ground=='o')beginPitFall(false,slime,slime.x,slime.y);
                        if(ground=='1'){cells()[slime.y][slime.x]='X';beginEnemySpikeDeath(slime);}
                    }
                }
                for(Slime slime:slimes())if(!slime.dead&&!slime.escaped&&slime.kind!='V')collectTrash(slime.x,slime.y);
                boolean caught=false;
                for(Slime slime:slimes())if(!slime.dead&&!slime.escaped&&slime.stun==0&&(heroHeight==0||slime.kind=='M'||slime.kind=='m')&&slime.x==heroX&&slime.y==heroY){caught=true;break;}
                if(caught&&!heroSlimeForm)heroDead=true;
                if(heroDead)syncSuperSlimeTimer();
                applyGardenTransformations();
                mobPhase=false;movePhase=0;moving=false;moveTimer.stop();
                repaint();afterAnimation();return;
            }
            if(doorMove){
                int target=doorTarget;doorMove=false;doorTarget=-1;
                moving=false;moveTimer.stop();movePhase=0;
                enterLevel(target,target<selected);repaint();return;
            }
            if(crateX>=0){
                char[][] state=cells();
                char crate=state[crateY][crateX];
                state[crateY][crateX]=crateGrates()[crateY][crateX]?'w':(lids()[crateY][crateX]?'F':'.');
                crateGrates()[crateY][crateX]=false;
                lids()[crateY][crateX]=false;
                int toX=crateX+moveDX,toY=crateY+moveDY;
                ritualCrateMoved=true;ritualCrateX=toX;ritualCrateY=toY;
                char destination=state[toY][toX];
                if(destination=='1')state[toY][toX]='X'; // trap fires and destroys crate
                else {state[toY][toX]=pushIntoPit?'F':crate;
                    if(pushIntoFilledPit)lids()[toY][toX]=true;
                    if(destination=='w')crateGrates()[toY][toX]=true;}
                crateX=-1;crateY=-1;pushIntoPit=false;pushIntoFilledPit=false;
            }
            heroX+=moveDX;heroY+=moveDY;
            if(elevatedMove){
                heroHeight=moveHeight;elevatedMove=false;
                movePhase=0;moving=false;moveTimer.stop();stride^=1;
                tickGrates();if(!heroDead)planSlimes();repaint();return;
            }
            if(landingMove){
                landingMove=false;landAt(heroX,heroY);
                if(heroInPitAnimation()||heroInSpikeAnimation())return;
                if(!heroDead){collectTrash(heroX,heroY);}
                movePhase=0;moving=false;moveTimer.stop();stride^=1;
                tickGrates();if(!heroDead)planSlimes();repaint();return;
            }
            collectTrash(heroX,heroY);
            if((ogreAt(heroX,heroY)&&heroHeight==0)||superSlimeAt(heroX,heroY))heroDead=true;
            char ground=cells()[heroY][heroX];
            if(ground=='O'||ground=='o'){
                beginPitFall(true,null,heroX,heroY);return;
            }
            if(ground=='1'){
                cells()[heroY][heroX]='X';beginHeroSpikeDeath(heroX,heroY);return;
            }
            movePhase=0;moving=false;moveTimer.stop();stride^=1;applyGardenTransformations();tickGrates();if(!heroDead)planSlimes();else syncSuperSlimeTimer();
        }
        repaint();
    }
    private void clearDeadMobs(){
        java.util.Iterator<Slime> it=slimes().iterator();
        while(it.hasNext())if(it.next().dead)it.remove();
    }
    private void finishActionTurn(){
        startTurn();tickGrates();if(!heroDead)planSlimes();repaint();
    }
    private void waitTurn(){
        if(!moving&&heroX>=0&&!heroDead&&!heroInPitAnimation()&&!heroInSpikeAnimation())finishActionTurn();
    }
    private void nextHero(){
        queuedMoves.clear();
        // Death freezes the scene until a new key press; next hero starts in the opening room.
        rearmTraps();heroType=(heroType+1)%4;
        selected=0;fromExit=false;resetHero();
    }
    public void keyPressed(KeyEvent e){int k=e.getKeyCode();
        if(k==KeyEvent.VK_W)k=KeyEvent.VK_UP;
        else if(k==KeyEvent.VK_A)k=KeyEvent.VK_LEFT;
        else if(k==KeyEvent.VK_S)k=KeyEvent.VK_DOWN;
        else if(k==KeyEvent.VK_D)k=KeyEvent.VK_RIGHT;
        if(deathFadeActive){exitAfterDeath();return;}
        if(ritualFinalStage>0||heroInSpikeAnimation())return;
        if(pauseScreen!=0){pauseKey(k);return;}
        if(k==KeyEvent.VK_ESCAPE){openPause();return;}
        if(heroDead){
            // Ignore key-release and key-repeat artefacts; one new key press advances.
            spaceHeld=false;spaceUsed=false;return;
        }
        if(heroWebTurns>0&&k!=KeyEvent.VK_SPACE){if(!moving&&(k==KeyEvent.VK_UP||k==KeyEvent.VK_DOWN||k==KeyEvent.VK_LEFT||k==KeyEvent.VK_RIGHT)){finishActionTurn();}return;}
        if(k==KeyEvent.VK_SPACE){
            if(!spaceHeld){spaceHeld=true;spaceUsed=false;}
            return;
        }
        if(moving && k!=KeyEvent.VK_UP && k!=KeyEvent.VK_DOWN && k!=KeyEvent.VK_LEFT && k!=KeyEvent.VK_RIGHT)return;
        if(e.isControlDown() && e.isShiftDown() && k>=KeyEvent.VK_1&&k<=KeyEvent.VK_4){heroType=k-KeyEvent.VK_1;repaint();}
        else if(e.isControlDown() && e.isShiftDown() && k==KeyEvent.VK_HOME){fromExit=false;resetHero();}
        else if(e.isControlDown() && e.isShiftDown() && k==KeyEvent.VK_END){fromExit=true;resetHero();}
        else if(e.isControlDown() && e.isShiftDown() && k==KeyEvent.VK_PAGE_UP){enterLevel(selected+1,false);}
        else if(e.isControlDown() && e.isShiftDown() && k==KeyEvent.VK_PAGE_DOWN){enterLevel(selected-1,true);}
        else if(e.isControlDown() && e.isShiftDown() && k==KeyEvent.VK_BACK_SPACE){
            ogreDebugDisabled=!ogreDebugDisabled;
            if(ogreDebugDisabled){ogrePresent=false;ogrePitCorpse=false;ogreDelay=-1;ogreStun=0;}
            else if(ogreAwakened&&!ogreDead)scheduleOgre(true);
            repaint();
        }
        else if(k==KeyEvent.VK_UP){queueOrMove(0,-1,0);}
        else if(k==KeyEvent.VK_LEFT){queueOrMove(-1,0,1);}
        else if(k==KeyEvent.VK_DOWN){queueOrMove(0,1,2);}
        else if(k==KeyEvent.VK_RIGHT){queueOrMove(1,0,3);}
    }
    public void keyTyped(KeyEvent e){}
    public void keyReleased(KeyEvent e){
        if(pauseScreen!=0)return;
        if(e.getKeyCode()==KeyEvent.VK_SPACE && spaceHeld){
            boolean shouldWait=!spaceUsed;
            spaceHeld=false;spaceUsed=false;
            if(shouldWait)waitTurn();
        }
    }
    public static void main(String[] args){
        try{
            File file=new File("nosrpg.map");
            if(!file.isFile())throw new IOException("nosrpg.map not found next to launch directory: "+file.getAbsolutePath());
            final Viewer panel=new Viewer(load(file));
            SwingUtilities.invokeLater(new Runnable(){public void run(){
                JFrame frame=new JFrame("NOS-RPG 0.4.31.4 - Super Slime");frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){panel.saveProgress();}});
                frame.add(panel);frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);panel.requestFocusInWindow();
            }});
        }catch(Exception ex){System.err.println(ex.getMessage());JOptionPane.showMessageDialog(null,ex.getMessage(),"Map load error",JOptionPane.ERROR_MESSAGE);System.exit(1);}
    }
}
