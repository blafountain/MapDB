package org.mapdb;

import javax.naming.OperationNotSupportedException;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by blafountain on 7/25/2014.
 */
public class Lists {

    public static class LinkedList<E> implements List<E> {

        protected static class LinkedListIterator<E> implements ListIterator<E>, Iterator<E> {
            Node<E> current;
            long currentRef;

            long tail;
            long head;
            LinkedList<E> list;

            LinkedListIterator(LinkedList<E> list) {
                this.list = list;

                this.head = list.head.get();
                this.tail = list.tail.get();

                currentRef = head;
                current = list.engine.get(head, list.nodeSerializer);
            }

            @Override
            public boolean hasNext() {
                return current.next != 0;
            }

            @Override
            public E next() {
                // special case
                if(currentRef == head) {
                    Node<E> ret = list.engine.get(current.next, list.nodeSerializer);

                    currentRef = ret.next;
                    current = list.engine.get(ret.next, list.nodeSerializer);
                    return ret.value;
                } else {
                    Node<E> ret = current;

                    currentRef = current.next;
                    current = list.engine.get(current.next, list.nodeSerializer);
                    return ret.value;
                }
            }

            @Override
            public boolean hasPrevious() {
                return current.prev != 0;
            }

            @Override
            public E previous() {
                if(currentRef == tail) {
                    Node<E> ret = list.engine.get(current.prev, list.nodeSerializer);

                    currentRef = ret.prev;
                    current = list.engine.get(ret.prev, list.nodeSerializer);
                    return ret.value;
                } else {
                    Node<E> ret = current;

                    currentRef = current.prev;
                    current = list.engine.get(current.prev, list.nodeSerializer);
                    return ret.value;
                }
            }

            @Override
            public int nextIndex() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int previousIndex() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(E e) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(E e) {
                throw new UnsupportedOperationException();
            }
        }

        protected static class NodeSerializer<E> implements Serializer<Node<E>> {
            private final Serializer<E> serializer;

            public NodeSerializer(Serializer<E> serializer) {
                this.serializer = serializer;
            }

            @Override
            public void serialize(DataOutput out, Node<E> value) throws IOException {
                DataOutput2.packLong(out,value.prev);
                DataOutput2.packLong(out,value.next);

                out.writeByte(value.value == null ? 0 : 1);
                if(value.value != null)
                    serializer.serialize(out, value.value);
            }

            @Override
            public Node<E> deserialize(DataInput in, int available) throws IOException {
                long prev = DataInput2.unpackLong(in);
                long next = DataInput2.unpackLong(in);
                boolean hasValue = in.readByte() == 1;
                E value = null;

                if(hasValue) {
                    value = serializer.deserialize(in, -1);
                }

                return new Node<E>(prev, next, value);
            }

            @Override
            public int fixedSize() {
                return -1;
            }

        }

        protected static final class Node<E> {
            protected static final Node<?> EMPTY = new Node(0L, 0L, null);

            final long prev;
            final long next;
            final E value;

            public Node(long prev, long next, E value) {
                this.prev = prev;
                this.next = next;
                this.value = value;
            }

        }

        protected final Engine engine;
        protected final Serializer<E> serializer;
        protected final Serializer<Node<E>> nodeSerializer;

        protected final Atomic.Long head;
        protected final Atomic.Long tail;

        // global lock
        protected final Lock lock = new ReentrantLock(CC.FAIR_LOCKS);
        // per rec lock
        protected final LongConcurrentHashMap<Thread> nodeLocks = new LongConcurrentHashMap<Thread>();

        public LinkedList(Engine engine, Serializer<E> serializer, long headRecidRef, long tailRecidRef, boolean useLocks) {
            this.engine = engine;
            this.serializer = serializer;

            this.head = new Atomic.Long(engine, headRecidRef);
            this.tail = new Atomic.Long(engine, tailRecidRef);

            nodeSerializer = new NodeSerializer<E>(serializer);
        }

        public void init() {
            long headId = this.engine.put((Node<E>)Node.EMPTY, nodeSerializer);
            long tailId = this.engine.put(new Node<E>(headId, 0, null), nodeSerializer);

            this.engine.update(headId, new Node<E>(0, tailId, null), nodeSerializer);
            this.head.set(headId);
            this.tail.set(tailId);
        }

        @Override
        public boolean add(E e) {
            // create a new tail and point the prev node to the existing tail
            long currentTailId = tail.get();

            Node<E> newTailNode = new Node<E>(currentTailId, 0, null);
            long newTailId = engine.put(newTailNode, nodeSerializer);

            while(!tail.compareAndSet(currentTailId, newTailId)) {
                currentTailId = tail.get();

                newTailNode = new Node<E>(currentTailId, 0, null);
                engine.update(newTailId, newTailNode, nodeSerializer);
            }

            // update the 'old' tail with our new values
            lock(nodeLocks, currentTailId);
            try {
                Node<E> current = engine.get(currentTailId, nodeSerializer);

                engine.update(currentTailId, new Node<E>(current.prev, newTailId, e), nodeSerializer);
            } finally {
                unlock(nodeLocks, currentTailId);
            }

            return true;
        }

        @Override
        public Iterator<E> iterator() {
            return new LinkedListIterator<E>(this);
        }

        @Override
        public ListIterator<E> listIterator() {
            return new LinkedListIterator<E>(this);
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            // TODO: support indexs!
            return null;
        }

        // locking
        protected static void unlock(LongConcurrentHashMap<Thread> locks,final long recid) {
            final Thread t = locks.remove(recid);
            assert(t==Thread.currentThread()):("unlocked wrong thread");
        }

        protected static void lock(LongConcurrentHashMap<Thread> locks, long recid){
            //feel free to rewrite, if you know better (more efficient) way

            final Thread currentThread = Thread.currentThread();
            //check node is not already locked by this thread
            assert(locks.get(recid)!= currentThread):("node already locked by current thread: "+recid);

            while(locks.putIfAbsent(recid, currentThread) != null){
                LockSupport.parkNanos(10);
            }
        }

        /// TODO....

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return false;
        }

        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return null;
        }

        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            return false;
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return false;
        }

        @Override
        public void clear() {

        }

        @Override
        public E get(int index) {
            return null;
        }

        @Override
        public E set(int index, E element) {
            return null;
        }

        @Override
        public void add(int index, E element) {

        }

        @Override
        public E remove(int index) {
            return null;
        }

        @Override
        public int indexOf(Object o) {
            return 0;
        }

        @Override
        public int lastIndexOf(Object o) {
            return 0;
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            return null;
        }
    }
}
