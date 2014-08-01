package org.mapdb;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by blafountain on 7/28/2014.
 */
public class Documents {


    public static class Document {
        Engine engine;
        BTreeMap<String, Object> root;

        public static class MapRef {
            final long recid;

            public MapRef(long recid) {
                this.recid = recid;
            }
        }

        public static class DocumentSerializer implements Serializer<Document>, Serializable {

            @Override
            public void serialize(DataOutput out, Document value) throws IOException {
                out.writeLong(value.root.rootRecidRef);
            }

            @Override
            public Document deserialize(DataInput in, int available) throws IOException {
                return new Document(null, in.readLong());
            }

            @Override
            public int fixedSize() {
                return -1;
            }
        }

        public Document(Engine engine) {
            long rootRecId = BTreeMap.createRootRef(engine,
                    BTreeKeySerializer.BASIC,Serializer.BASIC,
                    BTreeMap.COMPARABLE_COMPARATOR,0);

            root = new BTreeMap<String, Object>(engine, rootRecId, 32, false,
                    0L, BTreeKeySerializer.STRING, engine.getSerializerPojo(),
                    BTreeMap.COMPARABLE_COMPARATOR,
                    0, false);
        }

        public Document(Engine engine, long rootRef) {
            root = new BTreeMap<String, Object>(engine, rootRef, 32, false,
                    0L, BTreeKeySerializer.STRING, engine.getSerializerPojo(),
                    BTreeMap.COMPARABLE_COMPARATOR,
                    0, false);
        }

        public void put(String key, Object value) {
            root.put(key, value);
        }

        public void createPath(String key, Object value) {
            String [] keys = key.split("\\.");
            BTreeMap<String, Object> cur = root;

            // readlock(cur)
            try {
                for(int i = 0;i < keys.length - 1;i++) {
                    if(!cur.containsKey(keys[i])) {
                        // writelock(cur)
                        try {
                            cur.put(keys[i], new Document(engine));
                        } finally {
                            // writeunlock(cur)
                        }
                    }
                }
            } finally {
                // unlock(cur)
            }
        }

    }

    public static class DocumentMap {
        BTreeMap<String, Document> documents;
        Engine engine;

        public DocumentMap(Engine engine, long rootId, boolean useLocks) {
            this.engine = engine;
            documents = new BTreeMap<String, Document>(engine, rootId,
                    32, false, 0, BTreeKeySerializer.STRING, new Document.DocumentSerializer(),
                    BTreeMap.COMPARABLE_COMPARATOR, 0, false);
        }

        public Document get(String id) {
            Document d = documents.get(id);

            return d;
        }

        public Document create(String id) {
            Document d = new Document(engine);

            documents.put(id, d);
            return d;
        }

    }
}
