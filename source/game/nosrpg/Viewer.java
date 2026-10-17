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
    // Level 30: independent 100-turn ritual, no pursuit.
    private static final int[] RITUAL_X={4,5,5,5,4,3,3,3};
    private static final int[] RITUAL_Y={2,2,3,4,4,4,3,2};
    private int ritualSteps=0,ritualIndex=0;
    private boolean ritualFinished=false;
    // 0: normal; 1: approach pit; 2: three staff swings; 4: final lightning; 3: step into pit.
    private int ritualFinalStage=0;
    private long ritualFinalStarted=0L;
    private int ritualStaffSwings=0;
    private int ritualFinalX=4,ritualFinalY=2;
    private final javax.swing.Timer ritualFinalTimer=new javax.swing.Timer(60,new ActionListener(){
        public void actionPerformed(ActionEvent e){advanceRitualFinal();}
    });
    private void startRitualFinal(){
        ritualFinalStage=1;ritualStaffSwings=0;ritualFinalStarted=System.currentTimeMillis();
        ritualFinalX=RITUAL_X[ritualIndex];ritualFinalY=RITUAL_Y[ritualIndex];
        ritualFinalTimer.start();repaint();
    }
    private void advanceRitualFinal(){
        long now=System.currentTimeMillis();
        if(ritualFinalStage==1&&now-ritualFinalStarted>=500){
            if(ritualIndex!=0){
                ritualIndex=(ritualIndex+RITUAL_X.length-1)%RITUAL_X.length;
                ritualFinalX=RITUAL_X[ritualIndex];ritualFinalY=RITUAL_Y[ritualIndex];
                ritualFacing=ritualIndex==0?3:(ritualIndex==1||ritualIndex==2)?0:(ritualIndex==3||ritualIndex==4)?1:2;
            }else{ritualFinalStage=2;ritualFacing=3;ritualStaffSwings=0;}
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
            ritualFinalStage=3;ritualFacing=3;ritualFinalStarted=now;
        }else if(ritualFinalStage==3&&now-ritualFinalStarted>=650){
            ritualFinished=true;ritualFinalStage=0;ritualFinalTimer.stop();
        }
        repaint();
    }
    // The final staff strike is lethal to skeletons, unlike normal stunning bolts.
    private void ritualFinalBlast(){
        final int x=ritualFinalX,y=ritualFinalY;
        for(int yy=Math.max(0,y-3);yy<=Math.min(H-1,y+3);yy++)
            for(int xx=Math.max(0,x-3);xx<=Math.min(W-1,x+3);xx++){
                shocks()[yy][xx]='!';
                for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==xx&&mob.y==yy){
                    if(mob.kind=='L'||mob.kind=='R')mob.dead=true;
                    else {mob.stun=10;mob.dx=mob.dy=0;}
                }
                if(heroX==xx&&heroY==yy)heroDead=true;
            }
    }
    private boolean ritualAttacking=false;
    private int ritualFacing=3;
    private int ritualHeroStartX=-1,ritualHeroStartY=-1;
    private boolean ritualCrateMoved=false;
    private final java.awt.image.BufferedImage[][] wizardImages=new java.awt.image.BufferedImage[4][4];
    private boolean ritualRoom(){return levels.get(selected).id==30;}
    // A new visit to level 30 starts a new ritual, regardless of the hero.
    private void resetRitual(){
        ritualFinalTimer.stop();
        ritualSteps=0;ritualIndex=0;ritualFinished=false;
        ritualFinalStage=0;ritualFinalStarted=0L;ritualStaffSwings=0;
        ritualFinalX=RITUAL_X[0];ritualFinalY=RITUAL_Y[0];
        ritualFacing=3;ritualAttacking=false;
        ritualHeroStartX=-1;ritualHeroStartY=-1;
        ritualCrateMoved=false;ritualCrateX=-1;ritualCrateY=-1;
    }
    private void loadWizard(){
        String[] dirs={"right","down","left","up"},poses={"stand","step_left","step_right","action"};
        for(int d=0;d<4;d++)for(int a=0;a<4;a++)try{
            wizardImages[d][a]=javax.imageio.ImageIO.read(new File("sprites/mobs/wizard/"+dirs[d]+"_"+poses[a]+".png"));
        }catch(Exception ignored){}
    }
    private void drawWizard(Graphics2D g,int row){
        if(!ritualRoom()||ritualFinished)return;
        int x=ritualFinalStage>0?ritualFinalX:RITUAL_X[ritualIndex];
        int y=ritualFinalStage>0?ritualFinalY:RITUAL_Y[ritualIndex];
        if(ritualFinalStage==3)y=3;
        if(y!=row)return;
        int pose=(ritualFinalStage==4||ritualAttacking||(ritualFinalStage==2&&ritualStaffSwings%2==0))?3:(ritualFinalStage==1?1:0);
        java.awt.image.BufferedImage im=wizardImages[(ritualFacing+2)%4][pose];
        if(im!=null)g.drawImage(im,x*TILE,y*TILE+32-im.getHeight()-8,null);
        else {g.setColor(new Color(135,50,170));g.fillOval(x*TILE+6,y*TILE-5,20,28);}
    }
    private boolean ritualSight(int x,int y){
        int bx=RITUAL_X[ritualIndex],by=RITUAL_Y[ritualIndex];
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
        int x=RITUAL_X[ritualIndex],y=RITUAL_Y[ritualIndex];
        for(int yy=Math.max(0,y-3);yy<=Math.min(H-1,y+3);yy++)
            for(int xx=Math.max(0,x-3);xx<=Math.min(W-1,x+3);xx++){
                shocks()[yy][xx]='!';
                for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==xx&&mob.y==yy){mob.stun=10;mob.dx=mob.dy=0;}
                if(heroX==xx&&heroY==yy)heroDead=true;
            }
        // The action sprite stays visible through the mob movement animation.
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
        int next=(ritualIndex+1)%RITUAL_X.length;
        int nx=RITUAL_X[next],ny=RITUAL_Y[next];
        char c=cells()[ny][nx];
        boolean blocked=c=='#'||c=='B'||c=='b'||c=='W'||c=='T'||c=='t';
        for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==nx&&mob.y==ny)blocked=true;
        if(blocked){
            ritualBlast();
            if(c=='#'||c=='B'||c=='b'||c=='W'||c=='T'||c=='t')cells()[ny][nx]='.';
            for(Slime mob:slimes())if(!mob.dead&&!mob.escaped&&mob.x==nx&&mob.y==ny)mob.dead=true;
            return;
        }
        ritualIndex=next;
        int d=(next==1||next==2)?3:(next==3||next==4)?2:(next==5||next==6)?1:0;
        ritualFacing=d;
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
    private boolean mobPhase=false;
    private boolean spaceHeld=false, spaceUsed=false;
    // Buffered direction presses, captured with their SPACE modifier at keypress time.
    private final java.util.ArrayDeque<int[]> queuedMoves=new java.util.ArrayDeque<int[]>();
    private void queueOrMove(int dx,int dy,int direction){
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
        if(c=='1'){cells()[ogreY][ogreX]='X';ogreDead=true;return;} // corpse stays until next turn
        if(c=='O'||c=='o'){ogrePitCorpse=true;ogreDelay=5;ogreSpawnAtEntry=true;return;} // show fallen Ogre until next turn
        for(Slime mob:slimes())if(!mob.dead&&mob.kind!='M'&&mob.kind!='m'&&mob.x==ogreX&&mob.y==ogreY)mob.dead=true;
        if(heroHeight==0&&ogreX==heroX&&ogreY==heroY)heroDead=true;
    }
    private boolean onTop(char c){return c=='#'||c=='B'||c=='b';}
    private void landAt(int x,int y){
        heroHeight=0;
        if(superSlimeAt(x,y)){heroDead=true;syncSuperSlimeTimer();return;}
        char c=cells()[y][x];
        if(c=='O'||c=='o'){
            fallToBeginning();return;
        }else if(c=='1'){
            cells()[y][x]='X';heroDead=true;
        }else if(c=='E'||c=='@'){
            int target=selected+(c=='E'?1:-1);
            if(target>=0&&target<levels.size())enterLevel(target,target<selected);
        }else{
            if(ogreAt(x,y))heroDead=true;
            for(Slime m:slimes())if(!m.dead&&m.stun==0&&m.x==x&&m.y==y){heroDead=true;break;}
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
    private void startTurn(){ritualHeroStartX=heroX;ritualHeroStartY=heroY;ritualCrateMoved=false;ritualCrateX=ritualCrateY=-1;ritualAttacking=false;clearShockMarks();tickStuns();rearmTraps();clearDeadMobs();
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
            if(c!='.'&&c!='1'&&c!='x'&&c!='X'&&c!='F')break;
            if(slimeAt(x,y))break;
            tx=x;ty=y;
        }
        if(tx<0)return false;
        trash()[ty][tx]=TRASH_GLYPHS[random.nextInt(TRASH_GLYPHS.length)];
        showHeroAction();finishActionTurn();return true;
    }

    private static final class Slime implements Serializable {
        private static final long serialVersionUID=1L;
        char kind;
        int x,y,dir,tx=-1,ty=-1,dx,dy;
        boolean chasing,dead;
        int stun=0;
        boolean escaped=false;
        boolean alternateStep=false;
        Slime(int x,int y,int d,char kind){this.x=x;this.y=y;dir=d;this.kind=kind;}
    }
    private final java.util.Map<Integer,java.util.List<Slime>> slimeStates=new HashMap<Integer,java.util.List<Slime>>();
    private java.util.List<Slime> slimes(){
        Integer key=Integer.valueOf(selected);
        java.util.List<Slime> found=slimeStates.get(key);
        if(found==null){found=new ArrayList<Slime>();char[][] state=cells();
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if("SsLRMmG".indexOf(state[y][x])>=0){
                found.add(new Slime(x,y,(state[y][x]=='R'?3:state[y][x]=='L'?1:random.nextInt(4)),state[y][x]));state[y][x]='.';
            }
            slimeStates.put(key,found);
        }
        return found;
    }
    private boolean slimeAt(int x,int y){for(Slime s:slimes())if(!s.dead&&!s.escaped&&s.stun==0&&s.x==x&&s.y==y)return true;return false;}
    private boolean freeForSlime(int x,int y){
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y))return false;
        char c=cells()[y][x];return (c=='.'||c=='1'||c=='x'||c=='X'||c=='F'||c=='O'||c=='o'||c=='w')&&!slimeAt(x,y);
    }
    private boolean freeForActor(Slime actor,int x,int y){
        if(x<0||y<0||x>=W||y>=H)return false;
        if(actor.kind=='M'||actor.kind=='m')return true; // airborne: terrain and Ogre do not block flight
        if(superSlimeAt(x,y))return false;
        if(ogreAt(x,y))return false; // ground mobs treat living Ogre as an obstacle
        char c=cells()[y][x];
        if(actor.kind=='G'){
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
        if(x<0||y<0||x>=W||y>=H||superSlimeAt(x,y)||ogreAt(x,y))return false;
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

    private void planSlimes(){
        ritualTurn();
        if(heroDead){repaint();return;}
        java.util.List<Slime> list=slimes();
        for(Slime s:list){s.dx=0;s.dy=0;
            if(s.dead||s.stun>0||s.escaped)continue;
            if(s.kind=='G'){planGoblin(s);continue;}
            if(s.kind=='M'||s.kind=='m'){
                // Four independent random weights, one tile per turn.
                int[] weights={random.nextInt(100)+1,random.nextInt(100)+1,random.nextInt(100)+1,random.nextInt(100)+1};
                boolean sees=heroX>=0&&!heroDead&&(heroHeight>0||visible(s.x,s.y,heroX,heroY));
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
            if(heroX>=0&&!heroDead&&heroHeight==0&&visible(s.x,s.y,heroX,heroY)){
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
            if(!s.chasing && (s.kind=='L'||s.kind=='R')){
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
        moveTimer.stop();superSlimeTimer.stop();repaint();
    }
    private void closePause(){
        pauseScreen=0;
        if(pausedMoveTimer)moveTimer.start();
        if(pausedBossTimer)syncSuperSlimeTimer();
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
        ogreLevel=w.ogreLevel;ogreFacing=w.ogreFacing;
        ritualFinalTimer.stop();ritualFinalStage=0;ritualSteps=w.ritualSteps;ritualIndex=Math.max(0,Math.min(7,w.ritualIndex));ritualFacing=w.ritualFacing;ritualFinished=w.ritualFinished;
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
            if(ritualRoom())resetRitual();
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
                drawGround(g,state[y][x],x*TILE,y*TILE,level.tileset);
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
                        drawObject(g,c,x*TILE,y*TILE,level.tileset);
                }
                if(moving && crateX>=0){
                    int cx=crateX*TILE+moveDX*8*movePhase;
                    int cy=crateY*TILE+moveDY*8*movePhase;
                    if((cy>=y*TILE && cy<(y+1)*TILE) || (y==H-1 && cy>=H*TILE))
                        drawObject(g,'B',cx,cy,level.tileset);
                }
                for(Slime slime:actors){
                    if(slime.escaped||slime.kind=='M'||slime.kind=='m')continue;
                    int sy=slime.y*TILE+(mobPhase?slime.dy*8*movePhase:0);
                    if(sy>=y*TILE&&sy<(y+1)*TILE)
                        drawMob(g,slime.kind,slime.x*TILE+(mobPhase?slime.dx*8*movePhase:0),sy,level.tileset,slime.dir,slime.dx!=0||slime.dy!=0,slime.alternateStep);
                }
                drawWizard(g,y);
                if(ogrePresent&&ogreLevel==selected&&ogreY==y)drawOgreSprite(g,ogreX*TILE,ogreY*TILE);
                if(ogrePresent&&ogreLevel==selected&&ogreY==y&&ogreStun>0)drawBolt(g,ogreX*TILE,ogreY*TILE);
                for(Slime slime:actors)if(!slime.dead&&!slime.escaped&&slime.stun>0&&slime.y==y)drawBolt(g,slime.x*TILE,slime.y*TILE);
                for(int x=0;x<W;x++)if(shocks()[y][x]=='!')drawBolt(g,x*TILE,y*TILE);
                if(superSlimeRoom()&&y==4)drawSuperSlime(g);
                if(heroX>=0&&heroRow==y)drawCurrentHero(g,hx,hy);
                for(int x=0;x<W;x++)if(state[y][x]=='W'||state[y][x]=='w')
                    drawObject(g,state[y][x],x*TILE,y*TILE,level.tileset);
                for(int x=0;x<W;x++)if(state[y][x]=='M'||state[y][x]=='m')
                    drawMob(g,state[y][x],x*TILE,y*TILE-32,level.tileset,2,false,false);
                for(Slime slime:actors)if(!slime.escaped&&(slime.kind=='M'||slime.kind=='m')){
                    int sy=slime.y*TILE-32+(mobPhase?slime.dy*8*movePhase:0);
                    int rowY=slime.y+(mobPhase&&slime.dy>0&&movePhase>=2?1:0)-(mobPhase&&slime.dy<0&&movePhase>=2?1:0);
                    if(rowY==y)
                        drawMob(g,slime.kind,slime.x*TILE+(mobPhase?slime.dx*8*movePhase:0),sy,level.tileset,slime.dir,slime.dx!=0||slime.dy!=0,slime.alternateStep);
                }
            }
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
        if(heroType!=2||height<=0)return 0;
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
    // Pit transition only: preserve the hero and all existing world state.
    private void fallToBeginning(){
        queuedMoves.clear();
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
        queuedMoves.clear();
        moving=false;mobPhase=false;moveTimer.stop();movePhase=0;crateX=-1;crateY=-1;slimes();
        int[] at=findSpawn(levels.get(selected),fromExit?'E':'@');
        // The opening room has a fixed, agreed start: its centre (2,3).
        // Do not invent a spawn by scanning for the first available floor.
        if(at==null && selected==0 && !fromExit && cells()[3][2]=='.' && !slimeAt(2,3))
            at=new int[]{2,3};
        heroX=at==null?-1:at[0];heroY=at==null?-1:at[1];heroHeight=0;heroDead=false;
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
        if(oldSelected!=target && (levels.get(oldSelected).id==30 || levels.get(target).id==30))
            resetRitual();
        // Spawned slimes exist only during a visit to level 20.
        if(levels.get(oldSelected).id==20 && oldSelected!=target){
            slimeStates.remove(Integer.valueOf(oldSelected));
        }
        queuedMoves.clear();
        heroX=spawnX;heroY=spawnY;heroHeight=0;fromExit=backward;heroDead=false;
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
            if(mob.kind=='L'||mob.kind=='R'||mob.kind=='M'||mob.kind=='m'||mob.kind=='G')mob.dead=true;
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
            fighterDash=false;fallToBeginning();return;
        }
        if(c=='1'||c=='X'){
            if(c=='1')a[y][x]='X';
            heroDead=true;
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
        if(moving||heroX<0||heroDead)return;
        facing=direction;
        int nx=heroX+dx,ny=heroY+dy;
        if(nx<0||nx>=W||ny<0||ny>=H)return;
        char[][] state=cells();
        char c=state[ny][nx];
        crateX=-1;crateY=-1;pushAction=false;pushIntoPit=false;pushIntoFilledPit=false;doorMove=false;doorTarget=-1;
        elevatedMove=false;landingMove=false;walkingOnTop=false;
        if(heroType==3 && action){fighterRush(dx,dy);return;}
        if(heroType==2){
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
        if(action && heroType==1){
            if(c=='B'||c=='b'){ /* push below */ }
            else if(c=='T'){toggleLever(nx,ny);return;}
            else {showHeroAction();shock(nx,ny);return;}
        }
        if(action && heroType==0 && c=='T'){toggleLever(nx,ny);return;}
        if(action && heroType==0 && c!='B' && c!='b'){
            throwTrash(dx,dy);return;
        }
        if(action && heroType==2 && (c=='1'||c=='x')){
            state[ny][nx]=(c=='1'?'x':'1');
            showHeroAction();finishActionTurn();return;
        }
        if(c=='B'||c=='b'){
            // The first hero can push only through SPACE + direction.
            if(!action || (heroType!=0 && heroType!=1))return;
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
                    if(slime.dead||slime.escaped||slime.stun>0)continue;
                    if(slime.dx!=0||slime.dy!=0)slime.alternateStep=!slime.alternateStep;
                    slime.x+=slime.dx;slime.y+=slime.dy;slime.dx=0;slime.dy=0;
                    if(slime.kind=='G'&&(cells()[slime.y][slime.x]=='@'||cells()[slime.y][slime.x]=='E')){slime.escaped=true;continue;}
                    char ground=cells()[slime.y][slime.x];
                    if(slime.kind!='M'&&slime.kind!='m'&&ground!='W'&&(ground=='O'||ground=='o'||ground=='1')){
                        slime.dead=true;
                        if(ground=='1')cells()[slime.y][slime.x]='X';
                    }
                }
                for(Slime slime:slimes())if(!slime.dead&&!slime.escaped)collectTrash(slime.x,slime.y);
                boolean caught=false;
                for(Slime slime:slimes())if(!slime.dead&&!slime.escaped&&slime.stun==0&&(heroHeight==0||slime.kind=='M'||slime.kind=='m')&&slime.x==heroX&&slime.y==heroY){caught=true;break;}
                if(caught)heroDead=true;
                if(heroDead)syncSuperSlimeTimer();
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
                if(!heroDead){collectTrash(heroX,heroY);}
                movePhase=0;moving=false;moveTimer.stop();stride^=1;
                tickGrates();if(!heroDead)planSlimes();repaint();return;
            }
            collectTrash(heroX,heroY);
            if((ogreAt(heroX,heroY)&&heroHeight==0)||superSlimeAt(heroX,heroY))heroDead=true;
            char ground=cells()[heroY][heroX];
            if(ground=='O'||ground=='o'){
                fallToBeginning();return;
            }
            if(ground=='1'){
                cells()[heroY][heroX]='X';heroDead=true;
                movePhase=0;moving=false;mobPhase=false;moveTimer.stop();repaint();return;
            }
            movePhase=0;moving=false;moveTimer.stop();stride^=1;tickGrates();if(!heroDead)planSlimes();else syncSuperSlimeTimer();
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
        if(!moving&&heroX>=0&&!heroDead)finishActionTurn();
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
        if(ritualFinalStage>0)return;
        if(pauseScreen!=0){pauseKey(k);return;}
        if(k==KeyEvent.VK_ESCAPE){openPause();return;}
        if(heroDead){
            // Ignore key-release and key-repeat artefacts; one new key press advances.
            spaceHeld=false;spaceUsed=false;return;
        }
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
