import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Standalone 9x7 Catacombs map editor. Java 7 compatible. */
public final class CatacombsLevelEditor extends JFrame {
    private static final int W=9,H=7,S=62;
    private static final char[] SYMBOLS={'.','#','B','O','1','S','L','R','M','@','E','W','w','T','t','G','V','K','N','P','D','Q','Y','A','X','Z'};
    private static final String[] NAMES={"Пол","Стена","Ящик","Яма","Шипы","Слизень","Скелет L","Скелет R","Мышь","Вход","Выход","Решётка закрыта","Решётка открыта","Рычаг","Сломанный рычаг","Гоблин","Колдун","Крысолюд","Орк","Паук","Драконид","Тёмный скелет","Призрак","Эльф","Допельгангер","Сирена"};
    private final char[][] map=new char[H][W];
    private char selected='#';
    private File currentFile;
    private boolean dirty=false;
    private final java.util.List<Level> levels=new java.util.ArrayList<Level>();
    private java.util.List<String> originalLines=new java.util.ArrayList<String>();
    private int active=-1;
    private boolean loading=false;
    private final JComboBox<String> levelBox=new JComboBox<String>();
    private final JComboBox<String> tilesetBox=new JComboBox<String>(new String[]{"0 — Катакомбы","1 — Подвал","2 — Сад","3 — Замок"});
    private static final class Level {
        int number, tileset, start, mapStart, end;
        char[][] cells=new char[H][W];
        boolean changed, added;
    }

    private final JLabel status=new JLabel("Выбрана стена (#). ЛКМ — поставить, ПКМ — стереть.");
    private final JButton[] palette=new JButton[SYMBOLS.length];
    private final JPanel board=new JPanel(new GridLayout(H,W,1,1));
    private final Cell[][] cells=new Cell[H][W];

    private CatacombsLevelEditor(){
        super("NOS-RPG map editor 1.4");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(confirmDiscard())dispose();}});
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)map[y][x]='.';
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JPanel top=new JPanel(new BorderLayout(4,6));
        JPanel palettePanel=new JPanel(new GridLayout(5,6,4,4));
        for(int i=0;i<SYMBOLS.length;i++){
            final char sym=SYMBOLS[i];
            JButton b=new JButton(NAMES[i]+"  ["+sym+"]");
            b.setMargin(new Insets(6,5,6,5));b.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
            b.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){choose(sym);}});palette[i]=b;palettePanel.add(b);
        }
        top.add(palettePanel,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        JButton open=new JButton("Открыть...");open.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){openMap();}});actions.add(open);
        JButton save=new JButton("Сохранить");save.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){saveMap(false);}});actions.add(save);
        JButton saveAs=new JButton("Сохранить как...");saveAs.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){saveMap(true);}});actions.add(saveAs);
        JButton clear=new JButton("Очистить");clear.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){if(JOptionPane.showConfirmDialog(CatacombsLevelEditor.this,"Очистить всё поле?","Подтверждение",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){for(int y=0;y<H;y++)for(int x=0;x<W;x++)map[y][x]='.';if(active>=0)levels.get(active).changed=true;dirty=true;refresh();}}});actions.add(clear);
        JPanel nav=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
        nav.add(new JLabel("Уровень:"));nav.add(levelBox);
        levelBox.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){if(!loading)selectLevel(levelBox.getSelectedIndex());}});
        JButton add=new JButton("Добавить уровень");add.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){addLevel();}});nav.add(add);
        nav.add(new JLabel("Тайлсет:"));nav.add(tilesetBox);
        tilesetBox.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){if(!loading&&active>=0){Level l=levels.get(active);int t=tilesetBox.getSelectedIndex();if(l.tileset!=t){l.tileset=t;l.changed=true;dirty=true;updateTitle();}}}});
        JPanel lower=new JPanel(new BorderLayout());lower.add(nav,BorderLayout.NORTH);lower.add(actions,BorderLayout.SOUTH);
        top.add(lower,BorderLayout.SOUTH);
        root.add(top,BorderLayout.NORTH);
        board.setBackground(new Color(60,65,74));
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            Cell c=new Cell(x,y);cells[y][x]=c;board.add(c);
        }
        JPanel boardWrap=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));boardWrap.add(board);
        root.add(boardWrap,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,4));
        bottom.add(status,BorderLayout.NORTH);
        bottom.add(new JLabel("9×7 · nosrpg.map · @ вход, E выход"),BorderLayout.SOUTH);
        root.add(bottom,BorderLayout.SOUTH);
        setContentPane(root);choose(selected);pack();setResizable(false);setLocationRelativeTo(null);
    }
    private void choose(char c){selected=c;for(int i=0;i<SYMBOLS.length;i++)palette[i].setBackground(SYMBOLS[i]==c?new Color(170,215,255):UIManager.getColor("Button.background"));status.setText("Выбран: "+name(c)+" ["+c+"]  ·  ЛКМ поставить / провести, ПКМ стереть");}
    private String name(char c){for(int i=0;i<SYMBOLS.length;i++)if(SYMBOLS[i]==c)return NAMES[i];return "Объект";}
    private void put(int x,int y,char c){
        if(map[y][x]==c)return;
        // Keep start and exit unique when placed via palette.
        if(c=='@'||c=='E')for(int yy=0;yy<H;yy++)for(int xx=0;xx<W;xx++)if(map[yy][xx]==c){map[yy][xx]='.';cells[yy][xx].repaint();}
        map[y][x]=c;if(active>=0)levels.get(active).changed=true;dirty=true;cells[y][x].repaint();updateTitle();
    }
    private void updateTitle(){setTitle("NOS-RPG map editor 1.4 — "+(currentFile==null?"новая карта":currentFile.getName())+(dirty?" *":""));}
    private void refresh(){for(int y=0;y<H;y++)for(int x=0;x<W;x++)cells[y][x].repaint();updateTitle();}
    private boolean confirmDiscard(){return !dirty||JOptionPane.showConfirmDialog(this,"Есть несохранённые изменения. Продолжить без сохранения?","Внимание",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private static File applicationDirectory(){
        try {
            File location=new File(CatacombsLevelEditor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.isDirectory()?location:location.getParentFile();
        } catch(Exception ex){return new File(System.getProperty("user.dir", "."));}
    }
    private JFileChooser chooser(){JFileChooser fc=new JFileChooser(applicationDirectory());fc.setFileFilter(new FileNameExtensionFilter("Карты NOS-RPG (*.map)","map"));if(currentFile!=null)fc.setSelectedFile(currentFile);return fc;}
    private void storeCurrent(){
        if(active<0)return;
        Level l=levels.get(active);
        for(int y=0;y<H;y++)System.arraycopy(map[y],0,l.cells[y],0,W);
    }
    private void selectLevel(int idx){
        if(idx<0||idx>=levels.size())return;
        storeCurrent();active=idx;Level l=levels.get(idx);loading=true;
        for(int y=0;y<H;y++)System.arraycopy(l.cells[y],0,map[y],0,W);
        tilesetBox.setSelectedIndex(l.tileset);levelBox.setSelectedIndex(idx);
        loading=false;refresh();status.setText("Уровень "+l.number+" / "+levels.size());
    }
    private void rebuildLevelBox(){
        loading=true;levelBox.removeAllItems();
        for(Level l:levels)levelBox.addItem(""+l.number);
        loading=false;
    }
    private void addLevel(){
        if(currentFile==null){JOptionPane.showMessageDialog(this,"Сначала откройте nosrpg.map.");return;}
        storeCurrent();Level l=new Level();l.number=levels.isEmpty()?1:levels.get(levels.size()-1).number+1;
        l.tileset=0;l.added=true;l.changed=true;
        for(int y=0;y<H;y++)java.util.Arrays.fill(l.cells[y],'.');
        levels.add(l);dirty=true;rebuildLevelBox();selectLevel(levels.size()-1);
    }
    private static boolean levelHeader(String line){return line.trim().matches("\\[LEVEL\\s+[0-9]+\\]");}
    private void openMap(){
        if(!confirmDiscard())return;
        JFileChooser fc=chooser();if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File file=fc.getSelectedFile();
        try{
            java.util.List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
            java.util.List<Level> parsed=new java.util.ArrayList<Level>();
            for(int i=0;i<lines.size();i++){
                if(!levelHeader(lines.get(i)))continue;
                Level l=new Level();l.start=i;l.number=Integer.parseInt(lines.get(i).trim().replaceAll("[^0-9]",""));
                int j=i+1;l.tileset=0;
                while(j<lines.size()&&!lines.get(j).trim().equals("MAP:")&&!levelHeader(lines.get(j))){
                    String t=lines.get(j).trim();
                    if(t.startsWith("TILESET="))l.tileset=Integer.parseInt(t.substring(8).trim());
                    j++;
                }
                if(j>=lines.size()||!lines.get(j).trim().equals("MAP:"))throw new IOException("Нет MAP: на уровне "+l.number);
                if(l.tileset<0||l.tileset>3)throw new IOException("TILESET вне диапазона 0..3 на уровне "+l.number);
                l.mapStart=j+1;
                if(l.mapStart+H>lines.size())throw new IOException("Недостаточно строк на уровне "+l.number);
                for(int y=0;y<H;y++){
                    String row=lines.get(l.mapStart+y);
                    if(row.length()!=W)throw new IOException("Уровень "+l.number+", строка "+(y+1)+": требуется 9 символов");
                    for(int x=0;x<W;x++)l.cells[y][x]=row.charAt(x);
                }
                parsed.add(l);i=l.mapStart+H-1;
            }
            if(parsed.isEmpty())throw new IOException("В файле нет разделов [LEVEL N]");
            for(int k=0;k<parsed.size();k++)parsed.get(k).end=(k+1<parsed.size()?parsed.get(k+1).start:lines.size());
            levels.clear();levels.addAll(parsed);originalLines=new java.util.ArrayList<String>(lines);
            currentFile=file;active=-1;dirty=false;rebuildLevelBox();selectLevel(0);
            status.setText("Открыто "+levels.size()+" уровней: "+file.getAbsolutePath());
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Ошибка открытия",JOptionPane.ERROR_MESSAGE);}
    }
    private void saveMap(boolean as){
        if(currentFile==null){JOptionPane.showMessageDialog(this,"Сначала откройте nosrpg.map.");return;}
        storeCurrent();File file=currentFile;
        if(as){JFileChooser fc=chooser();if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            file=fc.getSelectedFile();if(!file.getName().toLowerCase().endsWith(".map"))file=new File(file.getParentFile(),file.getName()+".map");
            if(file.exists()&&JOptionPane.showConfirmDialog(this,"Заменить файл?","Подтверждение",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        }
        try{
            java.util.List<String> output=new java.util.ArrayList<String>(originalLines);
            // Work from the last original level backwards, preserving every untouched section byte-for-byte in text.
            for(int k=levels.size()-1;k>=0;k--){
                Level l=levels.get(k);if(!l.changed||l.added)continue;
                int tileLine=-1;for(int j=l.start+1;j<l.mapStart-1;j++)if(output.get(j).trim().startsWith("TILESET=")){tileLine=j;break;}
                if(tileLine>=0)output.set(tileLine,"TILESET="+l.tileset);
                for(int y=0;y<H;y++)output.set(l.mapStart+y,new String(l.cells[y]));
            }
            for(Level l:levels)if(l.added){
                output.add("");output.add("[LEVEL "+l.number+"]");output.add("TILESET="+l.tileset);output.add("MAP:");
                for(int y=0;y<H;y++)output.add(new String(l.cells[y]));
            }
            StringBuilder sb=new StringBuilder();for(String line:output)sb.append(line).append('\n');
            // Write a temporary file first, then replace the target.
            File temp=new File(file.getAbsolutePath()+".tmp");Files.write(temp.toPath(),sb.toString().getBytes(StandardCharsets.UTF_8));
            try{java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(IOException ex){temp.delete();throw ex;}
            currentFile=file;dirty=false;
            // Re-read positions from the saved file without changing the visible level selection.
            int wanted=active;originalLines=output;
            int cursor=0;for(Level l:levels){
                while(cursor<output.size()&&!output.get(cursor).equals("[LEVEL "+l.number+"]"))cursor++;
                l.start=cursor;int j=cursor+1;while(j<output.size()&&!output.get(j).equals("MAP:"))j++;
                l.mapStart=j+1;l.added=false;l.changed=false;cursor=l.mapStart+H;
            }
            active=wanted;refresh();status.setText("Сохранено: "+file.getAbsolutePath());
        }catch(IOException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Ошибка сохранения",JOptionPane.ERROR_MESSAGE);}
    }
    private final class Cell extends JComponent{
        final int x,y;
        Cell(int x,int y){this.x=x;this.y=y;setPreferredSize(new Dimension(S,S));
            MouseAdapter mouse=new MouseAdapter(){public void mousePressed(MouseEvent e){paintAt(e);}public void mouseEntered(MouseEvent e){if((e.getModifiersEx()&MouseEvent.BUTTON1_DOWN_MASK)!=0)put(x,y,selected);else if((e.getModifiersEx()&MouseEvent.BUTTON3_DOWN_MASK)!=0)put(x,y,'.');}};
            addMouseListener(mouse);
        }
        private void paintAt(MouseEvent e){if(SwingUtilities.isRightMouseButton(e))put(x,y,'.');else if(SwingUtilities.isLeftMouseButton(e))put(x,y,selected);}
        protected void paintComponent(Graphics g){
            super.paintComponent(g);Graphics2D z=(Graphics2D)g.create();char c=map[y][x];
            Color bg=new Color(228,226,211),fg=new Color(30,36,42);
            if(c=='#'){bg=new Color(78,84,92);fg=Color.WHITE;}
            if(c=='B'||c=='b'){bg=new Color(171,117,66);fg=Color.WHITE;}
            if(c=='O'||c=='o'){bg=new Color(35,36,46);fg=new Color(225,225,225);}
            if(c=='1'){bg=new Color(225,196,168);fg=new Color(130,45,40);}
            if(c=='S'||c=='s'){bg=new Color(158,207,150);fg=new Color(22,85,35);}
            if(c=='L'||c=='R'){bg=new Color(211,204,224);fg=new Color(71,42,100);}
            if(c=='M'||c=='m'){bg=new Color(177,176,199);fg=new Color(42,34,79);}
            if(c=='@'){bg=new Color(146,204,241);fg=new Color(15,67,121);}
            if(c=='W'||c=='w'){bg=new Color(125,137,148);fg=Color.WHITE;}
            if(c=='G'||c=='V'||c=='K'||c=='N'){bg=new Color(170,110,140);fg=Color.WHITE;}
            if(c=='T'){bg=new Color(190,170,111);fg=new Color(64,50,32);}
            if(c=='E'){bg=new Color(250,216,127);fg=new Color(105,66,7);}
            z.setColor(bg);z.fillRect(0,0,getWidth(),getHeight());
            z.setColor(new Color(0,0,0,45));z.drawRect(0,0,getWidth()-1,getHeight()-1);
            if(c!='.'){
                z.setColor(fg);z.setFont(new Font(Font.MONOSPACED,Font.BOLD,32));
                FontMetrics fm=z.getFontMetrics();String s=String.valueOf(c);
                z.drawString(s,(getWidth()-fm.stringWidth(s))/2,(getHeight()-fm.getHeight())/2+fm.getAscent());
            }
            z.dispose();
        }
    }
    public static void main(String[] args){SwingUtilities.invokeLater(new Runnable(){public void run(){new CatacombsLevelEditor().setVisible(true);}});}
}
