package examples;

import org.mapdb.Bind;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.HTreeMap;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Simple way to create  bidirectional map (can find key for given value) using Binding.
 */
public class Bidi_Map {

    public static void main(String[] args) {
        //primary map
        HTreeMap<String,Long> map = DBMaker.newTempHashMap();

        // inverse mapping for primary map
        NavigableSet<Fun.Tuple2<Long, String>> inverseMapping = new TreeSet<Fun.Tuple2<Long, String>>();
        //NOTE: you may also use Set provided by MapDB to make it persistent

        // bind inverse mapping to primary map, so it is auto-updated
        Bind.mapInverse(map, inverseMapping);


        map.put("value1", 10L);
        map.put("value2", 10L);
        map.put("value3", 1112L);
        map.put("val4", 111L);

        map.remove("value2");

        //now find all keys for given value
        for(String key: Fun.filter(inverseMapping, 10L)){
            System.out.println("Key for 'value' is: "+key);
        }

    }
}
