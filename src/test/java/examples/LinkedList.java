package examples;

import org.mapdb.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by blafountain on 7/25/2014.
 */
public class LinkedList {

    public static void main(String[] args) throws IOException {
        //Configure and open database using builder pattern.
        //All options are available with code auto-completion.
        File dbFile = File.createTempFile("mapdb","db");
        //File dbFile = new File("d:/test.db");
        DB db = DBMaker.newFileDB(dbFile)
        //DB db = DBMaker.newMemoryDB()
                //.cacheDisable()
                //.transactionDisable()
                .closeOnJvmShutdown()
                .deleteFilesAfterClose()
                .make();

        Documents.DocumentMap m = db.getDocumentMap("test");

        Documents.Document d = m.create("asdf");

        d.put("test", 1234);

        // linked list
        List<String> list = db.getList("test");

        list.add("1asdfasfdasdfa");
        list.add("2asdfasfdasdfa");
        list.add("3asdfasfdasdfa");
        list.add("4asdfasfdasdfa");

        // use java's foreach
        System.out.println("java foreach");
        for(String i : list) {
            System.out.println(i);
        }

        /*
        System.out.println("iterate forward");
        ListIterator<String> iterator = list.listIterator();
        while(iterator.hasNext()) {
            String e = iterator.next();

            System.out.println(e);
        }

        System.out.println("iterate backwards");
        while(iterator.hasPrevious()) {
            String e = iterator.previous();

            System.out.println(e);
        }

        // test list
        long start = System.currentTimeMillis();
        int count = 10000;
        List<String> list2 = db.getList("test2");
        for(int i = 0;i < count;i++) {
            list2.add("asdfasdfasfd");
        }
        float len = (System.currentTimeMillis() - start) / 1000.0f;
        System.out.println("time - " + len);
        System.out.println("     - " + ((float)count / len));

        start = System.currentTimeMillis();

        */
        // test btree
        int count = 50;
        BTreeMap<Integer, Integer> s = db.getTreeMap("asdf");

        for(int i = 0;i < count;i++) {
               s.put(i, i);
        }
        s.printTreeStructure();
        //len = (System.currentTimeMillis() - start) / 1000.0f;
        //System.out.println("time - " + len);
        //System.out.println("     - " + (count / len));

        db.close();
    }
}
