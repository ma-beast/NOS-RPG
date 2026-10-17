package catacombs;

import java.io.*;
/** Minimal shared state contract for NecronomicOS Menu 0.4. */
public final class SaveData implements Serializable {
    private static final long serialVersionUID=1L;
    public int hero=1;
    public boolean intro=false;
    public int level=1;
    public boolean fromExit=false;
    public byte[] world=null; // Opaque game snapshot; menu preserves it unchanged.
    private static File file(){return new File("save.dat");}
    public static SaveData load() throws IOException,ClassNotFoundException {
        File f=file();if(!f.isFile())return null;
        ObjectInputStream in=new ObjectInputStream(new FileInputStream(f));
        try {Object obj=in.readObject();return (SaveData)obj;}finally{in.close();}
    }
    public void write() throws IOException {
        File f=file();File tmp=new File(f.getPath()+".tmp");
        ObjectOutputStream out=new ObjectOutputStream(new FileOutputStream(tmp));
        try{out.writeObject(this);}finally{out.close();}
        if(f.exists()&&!f.delete())throw new IOException("Cannot replace save.dat");
        if(!tmp.renameTo(f))throw new IOException("Cannot rename save.dat.tmp");
    }
}
