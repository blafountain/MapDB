package org.mapdb;

import org.junit.After;

import java.io.File;
import java.io.IOException;

/**
 * provides temporarily test files which are deleted after JVM exits.
 */
abstract public class TestFile {

    protected final File index = UtilsTest.tempDbFile();
    protected final File data = new File(index.getPath()+ StoreDirect.DATA_FILE_EXT);
    protected final File log = new File(index.getPath()+ StoreWAL.TRANS_LOG_FILE_EXT);

    protected Volume.Factory fac = Volume.fileFactory(index,0,false, 0L, CC.VOLUME_SLICE_SHIFT, 0, data, log);


    @After public void after() throws IOException {
        if(index!=null)
            index.delete();
        if(data!=null)
            data.delete();
        if(log!=null)
            log.delete();
    }
}
