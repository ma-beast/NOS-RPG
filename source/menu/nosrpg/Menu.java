
package nosrpg;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

public final class Menu extends JPanel implements KeyListener, MouseListener {
    private static final int W=320,H=240;
    private BufferedImage cover;
    private int selection=0;
    private int screen=0; // 0 main, 1 delete confirmation, 2 settings placeholder, 3 error
    private int confirm=0; // 0 return, 1 confirm
    private int confirmMode=1; // 1 delete, 5 exit
    private final JFrame window;
    private String errorText="GAME NOT FOUND";
    private boolean gameRunning=false;
    private javax.swing.Timer introTimer;
    private BufferedImage[] introFrames;
    private int introIndex;
    private int introStage; // 0 fade-in, 1 normal frames, 2 fade-out
    private long introStageStart;
    private int concreteNextIndex;
    private static final long FADE_NANOS=2000000000L;
    private static final long CONCRETE_FRAME_NANOS=1500000000L;
    private static final long CONCRETE_TRANSITION_NANOS=500000000L;
    private static final long CONCRETE_END_NANOS=5000000000L;
    private int introHero;
    private boolean introNeedsFlag;
    private static final String[] INTRO_NAMES={"", "man", "officer", "scientist", "fighter", "concrete", "box"};
    private static final int[] INTRO_DELAYS={0,500,500,1000,1000,1000,500};
    private final Rectangle[] buttons=new Rectangle[4];
    private final Rectangle[] confirmButtons={new Rectangle(103,110,45,42),new Rectangle(173,110,45,42)};
    private final Rectangle[] siteButtons={new Rectangle(103,65,30,28),new Rectangle(145,65,30,28),new Rectangle(187,65,30,28)};
    private static final String[] SITES={"https://ma-beast.itch.io","https://samlib.ru/z/zwerew_m_a/","https://github.com/ma-beast"};
    public Menu(JFrame window) {
        this.window=window;
        setPreferredSize(new Dimension(640,480));
        setFocusable(true);
        addKeyListener(this); addMouseListener(this);
        for(int i=0;i<4;i++)buttons[i]=new Rectangle(31+i*69,190,50,42);
        try {cover=ImageIO.read(Menu.class.getResource("/menu.png"));} catch(Exception e) {throw new IllegalStateException("menu.png missing",e);}
    }
    protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        Graphics2D g=(Graphics2D)original.create();
        g.setColor(Color.BLACK);g.fillRect(0,0,getWidth(),getHeight());
        double s=Math.min(getWidth()/(double)W,getHeight()/(double)H);
        int ox=(int)((getWidth()-W*s)/2),oy=(int)((getHeight()-H*s)/2);
        g.translate(ox,oy);g.scale(s,s);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(cover,0,0,W,H,null);
        if(screen==0){
            // Four local radial shadows; no horizontal opaque strip.
            for(int i=0;i<4;i++){
                Rectangle r=buttons[i];
                Point center=new Point(r.x+r.width/2,r.y+r.height/2);
                g.setPaint(new RadialGradientPaint(center,47f,
                    new float[]{0f,0.38f,1f},
                    new Color[]{new Color(0,0,0,205),new Color(0,0,0,143),new Color(0,0,0,0)}));
                g.fillOval(center.x-47,center.y-47,94,94);
            }
            for(int i=0;i<4;i++)drawButton(g,buttons[i],i,selection==i);
        }else if(screen==4){
            if(introFrames!=null && introIndex<introFrames.length){
                g.drawImage(introFrames[introIndex],0,0,W,H,null);
                long elapsed=System.nanoTime()-introStageStart;
                float progress=Math.min(1f,elapsed/(float)FADE_NANOS);
                float black=introStage==0?1f-progress:(introStage==2?progress:0f);
                if(introHero==5){
                    if(introStage==3){
                        float transition=Math.min(1f,elapsed/(float)CONCRETE_TRANSITION_NANOS);
                        black=transition<0.5f?transition*2f:(1f-transition)*2f;
                        if(transition>=0.5f)g.drawImage(introFrames[concreteNextIndex],0,0,W,H,null);
                    }else if(introStage==2){
                        black=Math.min(1f,elapsed/(float)CONCRETE_END_NANOS);
                    }
                }
                if(black>0){
                    g.setColor(new Color(0,0,0,Math.min(255,Math.max(0,Math.round(black*255)))));
                    g.fillRect(0,0,W,H);
                }
            }
        }else if(screen==1){
            g.setColor(new Color(0,0,0,210));if(confirmMode==5)g.fillRoundRect(76,55,169,118,9,9);else g.fillRoundRect(76,83,169,90,9,9);
            drawButton(g,confirmButtons[0],4,confirm==0);
            drawButton(g,confirmButtons[1],confirmMode==5?3:5,confirm==1);
            if(confirmMode==5)for(int i=0;i<3;i++)drawSite(g,siteButtons[i],i);
        }else if(screen==2){
            drawControls(g);
        }else{
            g.setColor(new Color(0,0,0,220));g.fillRect(0,0,W,H);
            g.setColor(new Color(215,207,174));
            g.setFont(new Font(Font.MONOSPACED,Font.BOLD,12));
            String message=screen==2?"SETTINGS - NOT IMPLEMENTED":errorText;
            FontMetrics fm=g.getFontMetrics();
            g.drawString(message,(W-fm.stringWidth(message))/2,115);
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));
            g.drawString("ESC - BACK",126,134);
        }
        g.dispose();
    }
    private void drawButton(Graphics2D g,Rectangle r,int type,boolean active){
        if(active){
            g.setColor(new Color(226,216,184,105));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,8,8);
        }
        g.setColor(active?new Color(255,237,180):new Color(230,225,216));
        g.setStroke(new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        int x=r.x+r.width/2,y=r.y+r.height/2;
        switch(type){
            case 0: // play
                Polygon tri=new Polygon(new int[]{x-8,x-8,x+11},new int[]{y-12,y+12,y},3);g.fillPolygon(tri);break;
            case 1: // gear
                for(int i=0;i<8;i++){double a=i*Math.PI/4;int px=x+(int)(Math.cos(a)*13),py=y+(int)(Math.sin(a)*13);
                    g.drawLine(x+(int)(Math.cos(a)*9),y+(int)(Math.sin(a)*9),px,py);}
                g.fillOval(x-11,y-11,22,22);g.setColor(new Color(15,14,23));g.fillOval(x-4,y-4,8,8);break;
            case 2: // cross
                g.drawLine(x-10,y-10,x+10,y+10);g.drawLine(x+10,y-10,x-10,y+10);break;
            case 3: // half-open door
                g.drawRect(x-12,y-14,23,29);g.drawLine(x-5,y-11,x+6,y-15);g.drawLine(x+6,y-15,x+6,y+11);
                g.drawLine(x-5,y+11,x+6,y+11);g.drawLine(x-5,y-11,x-5,y+11);g.fillOval(x+1,y-1,3,3);break;
            case 4: // return arrow
                g.drawLine(x+12,y,x-11,y);g.drawLine(x-11,y,x-1,y-10);g.drawLine(x-11,y,x-1,y+10);break;
            case 5: // skull
                g.fillOval(x-11,y-12,22,21);g.fillRect(x-7,y+4,14,9);
                g.setColor(new Color(15,14,23));
                g.fillOval(x-7,y-4,6,7);g.fillOval(x+2,y-4,6,7);
                g.fillPolygon(new int[]{x,x-3,x+3},new int[]{y+3,y+7,y+7},3);
                g.drawLine(x-3,y+8,x-3,y+12);g.drawLine(x+3,y+8,x+3,y+12);break;
        }
    }
    private void drawControls(Graphics2D g){
        g.setColor(new Color(0,0,0,220));g.fillRect(0,0,W,H);
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
        drawButton(g,new Rectangle(136,202,48,34),4,true);
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
    private final BufferedImage[] siteIcons=loadSiteIcons();
    private static BufferedImage[] loadSiteIcons(){
        BufferedImage[] icons=new BufferedImage[3];
        String[] paths={"/assets/site_itch.png","/assets/site_samlib.png","/assets/site_github.png"};
        for(int i=0;i<icons.length;i++){
            try{icons[i]=ImageIO.read(Menu.class.getResource(paths[i]));}
            catch(Exception ex){throw new IllegalStateException("Site icon missing: "+paths[i],ex);}
        }
        return icons;
    }
    private void drawSite(Graphics2D g,Rectangle r,int site){
        g.setColor(new Color(0,0,0,210));g.fillRoundRect(r.x,r.y,r.width,r.height,5,5);
        g.drawImage(siteIcons[site],r.x+2,r.y+1,r.width-4,r.height-2,null);
    }
    private void openSite(int index){
        try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(new URI(SITES[index]));}
        catch(Exception ex){ex.printStackTrace();}
    }
    private File gameFolder() throws Exception {
        File ownJar=new File(Menu.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return ownJar.isDirectory()?ownJar:ownJar.getParentFile();
    }
    // Load the game's OWN classes from Catacombs.jar, so the menu never embeds a
    // particular version of the game's serialized Game class.
    private int[] readSaveState() throws Exception {
        File gameJar=new File(gameFolder(),"Catacombs.jar");
        if(!gameJar.isFile())throw new java.io.FileNotFoundException("Catacombs.jar");
        File saveFile=new File(gameFolder(),"save.dat");
        if(!saveFile.isFile())return new int[]{1,1,0}; // first launch
        try(URLClassLoader loader=new URLClassLoader(new URL[]{gameJar.toURI().toURL()},Menu.class.getClassLoader())){
            Class<?> cls=Class.forName("catacombs.SaveData",true,loader);
            Object data=cls.getMethod("load").invoke(null);
            if(data==null)throw new java.io.IOException("save.dat disappeared");
            int hero=cls.getField("hero").getInt(data);
            boolean intro=cls.getField("intro").getBoolean(data);
            if(hero<1||hero>6)throw new java.io.IOException("Unsupported hero number");
            return new int[]{hero,intro?1:0,1};
        }
    }
    private void clearIntroFlag() throws Exception {
        File gameJar=new File(gameFolder(),"Catacombs.jar");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{gameJar.toURI().toURL()},Menu.class.getClassLoader())){
            Class<?> cls=Class.forName("catacombs.SaveData",true,loader);
            Object data=cls.getMethod("load").invoke(null);
            if(data==null)throw new java.io.IOException("Missing save after animation");
            cls.getField("intro").setBoolean(data,false);
            cls.getMethod("write").invoke(data);
        }
    }
    private void playOrResume(){
        if(gameRunning)return;
        try{
            int[] state=readSaveState();
            if(state[2]==1 && state[0]==5 && state[1]==0){startIntro(1,false);return;}
            if(state[1]==0){launchGame();return;}
            startIntro(state[0],state[2]==1);
        }catch(Exception ex){showError("SAVE / INTRO ERROR",ex);}
    }
    private void startIntro(int hero,boolean needsFlag) throws Exception {
        if(introTimer!=null){introTimer.stop();introTimer=null;}
        introHero=hero;
        introNeedsFlag=needsFlag;
        loadIntro(hero);
        introIndex=0;
        concreteNextIndex=0;
        introStage=0;
        introStageStart=System.nanoTime();
        screen=4;
        introTimer=new javax.swing.Timer(40,new ActionListener(){public void actionPerformed(ActionEvent e){advanceIntro();}});
        introTimer.start();
        repaint();
    }
    private void advanceIntro(){
        if(screen!=4)return;
        long now=System.nanoTime();
        long elapsed=now-introStageStart;
        if(introStage==0){
            if(elapsed>=FADE_NANOS){
                introStage=1;introStageStart=now;introIndex=0;
            }
        }else if(introStage==1){
            long delay=introHero==5?CONCRETE_FRAME_NANOS:INTRO_DELAYS[introHero]*1000000L;
            if(elapsed>=delay){
                introStageStart=now;
                if(introIndex+1<introFrames.length){
                    if(introHero==5){
                        concreteNextIndex=introIndex+1;
                        introStage=3;
                    }else introIndex++;
                }else introStage=2;
            }
        }else if(introStage==3){
            if(elapsed>=CONCRETE_TRANSITION_NANOS){
                introIndex=concreteNextIndex;
                introStage=1;
                introStageStart=now;
            }
        }else if(elapsed>=(introHero==5?CONCRETE_END_NANOS:FADE_NANOS)){
            finishIntro();return;
        }
        repaint();
    }
    private void loadIntro(int hero) throws Exception {
        String folder="/intros/"+INTRO_NAMES[hero]+"/";
        java.util.ArrayList<BufferedImage> frames=new java.util.ArrayList<BufferedImage>();
        for(int i=1;;i++){
            String path=folder+String.format(java.util.Locale.ROOT,"%03d.jpg",i);
            URL resource=Menu.class.getResource(path);
            if(resource==null)break;
            BufferedImage frame=ImageIO.read(resource);
            if(frame==null)throw new java.io.IOException("Unreadable image: "+path);
            frames.add(frame);
        }
        if(frames.isEmpty())throw new java.io.IOException("No animation frames for "+INTRO_NAMES[hero]);
        introFrames=frames.toArray(new BufferedImage[frames.size()]);
    }
    private void resetAfterConcrete() throws Exception {
        File gameJar=new File(gameFolder(),"Catacombs.jar");
        try(URLClassLoader loader=new URLClassLoader(new URL[]{gameJar.toURI().toURL()},Menu.class.getClassLoader())){
            Class<?> cls=Class.forName("catacombs.SaveData",true,loader);
            Object data=cls.getMethod("load").invoke(null);
            if(data==null)throw new java.io.IOException("Missing save after concrete");
            cls.getField("intro").setBoolean(data,false);
            cls.getMethod("write").invoke(data);
        }
    }
    private void finishIntro(){
        if(screen!=4)return;
        if(introTimer!=null){introTimer.stop();introTimer=null;}
        screen=0;introFrames=null;
        try{
            if(introHero==5)resetAfterConcrete();
            else if(introNeedsFlag)clearIntroFlag();
            if(introHero!=5)launchGame(); // Beton: the end, return to main menu
            else repaint();
        }catch(Exception ex){showError("SAVE WRITE ERROR",ex);}
    }
    private void showError(String msg,Exception ex){
        errorText=msg;screen=3;ex.printStackTrace();repaint();
    }
    private void launchGame(){
        if(gameRunning)return;
        try {
            File folder=gameFolder();
            File game=new File(folder,"Catacombs.jar");
            if(!game.isFile()){errorText="CATACOMBS.JAR NOT FOUND";screen=3;return;}
            String javaBin=System.getProperty("java.home")+File.separator+"bin"+File.separator+
                (System.getProperty("os.name").toLowerCase().contains("win")?"javaw.exe":"java");
            ProcessBuilder builder=new ProcessBuilder(javaBin,"-jar",game.getAbsolutePath());
            builder.directory(folder);
            final Process process=builder.start();
            gameRunning=true;
            window.setState(Frame.ICONIFIED);
            Thread watcher=new Thread(new Runnable(){public void run(){
                try{process.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();}
                SwingUtilities.invokeLater(new Runnable(){public void run(){
                    gameRunning=false;
                    window.setState(Frame.NORMAL);
                    window.toFront();
                    requestFocusInWindow();
                    try{
                        int[] state=readSaveState();
                        if(state[2]==1 && state[0]==5 && state[1]==1)
                            startIntro(5,true); // automatic defeat cutscene, no Play click
                    }catch(Exception ex){showError("SAVE / INTRO ERROR",ex);}
                    repaint();
                }});
            }},"Catacombs-return");
            watcher.setDaemon(true);
            watcher.start();
        }catch(Exception e){
            errorText="GAME LAUNCH ERROR";
            screen=3;
            e.printStackTrace();
        }
    }
    private void activate(){
        if(screen==0){
            if(selection==0)playOrResume();
            else if(selection==1)screen=2;
            else if(selection==2){screen=1;confirm=0;confirmMode=1;}
            else {screen=1;confirm=0;confirmMode=5;}
        }else if(screen==1){
            if(confirm==1 && confirmMode==5){window.dispose();return;}
            if(confirm==1 && confirmMode==1){
                try{
                    File save=new File(gameFolder(),"save.dat");
                    if(save.isFile() && !save.delete())
                        throw new java.io.IOException("Cannot delete save.dat");
                }catch(Exception ex){showError("DELETE SAVE ERROR",ex);return;}
            }
            screen=0;
        }else screen=0;
        repaint();
    }
    public void keyPressed(KeyEvent e){
        int key=e.getKeyCode();
        if(screen==4){finishIntro();return;}
        if(key==KeyEvent.VK_ESCAPE){screen=0;repaint();return;}
        if(screen==0){
            if(key==KeyEvent.VK_LEFT||key==KeyEvent.VK_UP)selection=(selection+3)%4;
            if(key==KeyEvent.VK_RIGHT||key==KeyEvent.VK_DOWN)selection=(selection+1)%4;
        }else if(screen==1 && (key==KeyEvent.VK_LEFT||key==KeyEvent.VK_RIGHT||key==KeyEvent.VK_UP||key==KeyEvent.VK_DOWN))confirm=1-confirm;
        if(key==KeyEvent.VK_ENTER||key==KeyEvent.VK_SPACE)activate();
        repaint();
    }
    public void mouseClicked(MouseEvent e){
        double s=Math.min(getWidth()/(double)W,getHeight()/(double)H);
        int ox=(int)((getWidth()-W*s)/2),oy=(int)((getHeight()-H*s)/2);
        int x=(int)((e.getX()-ox)/s),y=(int)((e.getY()-oy)/s);
        if(screen==4){finishIntro();return;}
        if(screen==0){for(int i=0;i<4;i++)if(buttons[i].contains(x,y)){selection=i;activate();return;}}
        else if(screen==1){
            if(confirmMode==5)for(int i=0;i<3;i++)if(siteButtons[i].contains(x,y)){openSite(i);return;}
            for(int i=0;i<2;i++)if(confirmButtons[i].contains(x,y)){confirm=i;activate();return;}
        }
        else {screen=0;repaint();}
    }
    public void keyReleased(KeyEvent e){} public void keyTyped(KeyEvent e){}
    public void mousePressed(MouseEvent e){} public void mouseReleased(MouseEvent e){}
    public void mouseEntered(MouseEvent e){} public void mouseExited(MouseEvent e){}
    public static void main(String[] args){
        SwingUtilities.invokeLater(new Runnable(){public void run(){
            JFrame f=new JFrame("NecronomicOS NOS-RPG — Menu prototype");
            Menu m=new Menu(f);f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(m);f.pack();f.setLocationRelativeTo(null);f.setVisible(true);m.requestFocusInWindow();
        }});
    }
}
