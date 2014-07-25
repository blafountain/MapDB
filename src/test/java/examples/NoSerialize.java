package examples;

import org.mapdb.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 * Created by blafountain on 7/24/2014.
 */
public class NoSerialize {

    public static void main(String[] args) throws IOException {

        //Configure and open database using builder pattern.
        //All options are available with code auto-completion.
        File dbFile = File.createTempFile("mapdb","db");
        DB db = DBMaker.newFileDB(dbFile)
                .cacheDisable()
                .closeOnJvmShutdown()
                .make();

        BTreeMap<String,byte[]> a = db.createTreeMap("collectionName")
                .valueSerializer(new NoSerializeUtil.NoSerialzieSerializer())
                .makeOrGet();

        a.put("asdf", new byte[2048]);

        byte [] dd = a.get("asdf");


        a.put("asdf", dd);


        db.close();

    }
}
