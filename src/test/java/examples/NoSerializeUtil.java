package examples;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by blafountain on 7/24/2014.
 */
public interface NoSerializeUtil {
    public static class NoSerialzieSerializer implements Serializer<byte[]>,Serializable {

        @Override
        public void serialize(DataOutput out, byte[] value) throws IOException {
            DataOutput2.packInt(out, value.length);
            out.write(value);
        }

        @Override
        public byte[] deserialize(DataInput in, int available) throws IOException {
            int size = DataInput2.unpackInt(in);
            byte[] ret = new byte[size];
            in.readFully(ret);
            return ret;
        }

        @Override
        public int fixedSize() {
            return -1;
        }
    } ;
}
