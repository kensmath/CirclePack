package circlePack;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Specialized 'ClassLoader' so user can load self-written 
 * 'PackExtender's.
 * @author Chris Brumgard
 *
 */
public class PackExtenderLoader extends ClassLoader
{

    @Override
    // TODO: changed 'protected' to 'public'. OK???
    protected Class<?> findClass(String path) throws ClassNotFoundException
    {
        try
        {
            byte[] clsData = this.loadClassData(path);
            
            return defineClass(null, clsData, 0, clsData.length);
            
        }catch (IOException e)
        {
            throw new ClassNotFoundException(e.toString());
        }
    }
    
    private byte[] loadClassData(String path) throws IOException 
    {
        RandomAccessFile raf = new RandomAccessFile(path, "r");
        
        byte[] contents = new byte[(int)raf.length()];
        
        raf.readFully(contents);
        
        raf.close();
        return contents;
    }
}
