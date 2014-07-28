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

        protected static class LinkedListIterator<E> implements Iterator<E> {
            Node<E> current;
            long currentRecId;
            Node<E> prev;
            long preRecId;
            LinkedList<E> list;

            LinkedListIterator(LinkedList<E> list) {
                this.list = list;

                currentRecId = list.head.get();
                current = list.engine.get(currentRecId, list.nodeSerializer);

                next();
            }

            @Override
            public boolean hasNext() {
                return current.next != 0;
            }

            @Override
            public E next() {
                // special case
                do {
                    prev = current;
                    preRecId = currentRecId;
                    currentRecId = current.next;
                    current = list.engine.get(currentRecId, list.nodeSerializer);

                    // detect if the node was deleted and just skip past it
                    if(!prev.deleted) {
                        return prev.value;
                    }
                } while(true);
            }

            @Override
            public void remove() {
                list.remove(preRecId, prev, currentRecId, current);
            }
        }

        protected static class NodeSerializer<E> implements Serializer<Node<E>> {
            private final Serializer<E> serializer;

            public NodeSerializer(Serializer<E> serializer) {
                this.serializer = serializer;
            }

            @Override
            public void serialize(DataOutput out, Node<E> value) throws IOException {
                DataOutput2.packLong(out,value.next);

                out.writeBoolean(value.deleted);
                out.writeByte(value.value == null ? 0 : 1);
                if(value.value != null)
                    serializer.serialize(out, value.value);
            }

            @Override
            public Node<E> deserialize(DataInput in, int available) throws IOException {
                long next = DataInput2.unpackLong(in);
                boolean deleted = in.readBoolean();
                boolean hasValue = in.readByte() == 1;
                E value = null;

                if(hasValue) {
                    value = serializer.deserialize(in, -1);
                }

                return new Node<E>(next, value, deleted);
            }

            @Override
            public int fixedSize() {
                return -1;
            }

        }

        protected static final class Node<E> {
            protected static final Node<?> EMPTY = new Node(0L, null);

            final long next;
            final boolean deleted;
            final E value;

            public Node(long next, E value) {
                this.next = next;
                this.deleted = false;
                this.value = value;
            }

            public Node(long next, E value, boolean deleted) {
                this.next = next;
                this.deleted = deleted;
                this.value = value;
            }
        }

        protected final Engine engine;
        protected final Serializer<E> serializer;
        protected final Serializer<Node<E>> nodeSerializer;

        protected final Atomic.Long head;
        protected final Atomic.Long tail;

        public LinkedList(Engine engine, Serializer<E> serializer, long headRecidRef, long tailRecidRef, boolean useLocks) {
            this.engine = engine;
            this.serializer = serializer;

            this.head = new Atomic.Long(engine, headRecidRef);
            this.tail = new Atomic.Long(engine, tailRecidRef);

            nodeSerializer = new NodeSerializer<E>(serializer);
        }

        public void init() {
            long tailId = this.engine.put((Node<E>)Node.EMPTY, nodeSerializer);
            long headId = this.engine.put(new Node<E>(tailId, null), nodeSerializer);

            this.head.set(headId);
            this.tail.set(tailId);
        }

        @Override
        public boolean add(E e) {
            long newTail = engine.put((Node<E>) Node.EMPTY, nodeSerializer);

            do {
                long oldTail = tail.get();
                Node<E> tailNode = engine.get(oldTail, nodeSerializer);
                Node<E> newEntry = new Node<E>(newTail, e);

                // so here we want to make sure our node safely gets into
                //  the linked list with the pointers correct...
                if(engine.compareAndSwap(oldTail, tailNode, newEntry, nodeSerializer)) {
                    // now lets move our tail pointer, if there happens to be
                    //  another thread that has already updated the tail pointer,
                    //  then we still should be good since the node has already
                    //  correctly been inserted and we will only forward the pointer
                    //  if its still what we think it is
                    tail.compareAndSet(oldTail, newTail);
                    return true;
                }
            }while(true);
        }

        public boolean insert(long afterRecId, E value) {
            return true;
        }

        public boolean remove(long prevRecId, Node<E> prev, long recId, Node<E> remove) {
            // first we are going to logically mark the node as deleted
            //  according to http://research.microsoft.com/pubs/67089/2001-disc.pdf
            //  then we are going to do a second cas to physically remove the node
            do {
                if(engine.compareAndSwap(prev.next, remove, new Node<E>(remove.next, null, true), nodeSerializer)) {
                    break;
                }
                remove = engine.get(recId, nodeSerializer);

                // we're already deleted
                if(!remove.deleted) {
                    return true;
                }
            } while(true);

            engine.compareAndSwap(prevRecId, prev, new Node<E>(remove.next, prev.value), nodeSerializer);
            return true;
        }

        @Override
        public Iterator<E> iterator() {
            return new LinkedListIterator<E>(this);
        }

        @Override
        public ListIterator<E> listIterator() {
            // TODO: support indexs!
            return null;
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            // TODO: support indexs!
            return null;
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
