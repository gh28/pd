package libcliff.io;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * some thing of smart array & queue & installment savings
 */
public class InstallmentByteBuffer {

    /**
     * not a java.io.Reader<br/>
     */
    public class Reader implements Nextable {

        private int next = 0;

        @Override
        public boolean hasNext() {
            return next >= 0 && next < size();
        }

        @Override
        public int next() {
            return get(next++) & 0xFF;
        }

        /**
         * For reading, get the cursor's offset.
         */
        public int offset() {
            return next;
        }

        @Override
        public int peek() {
            return get(next) & 0xFF;
        }

        public void putBack() {
            seek(used - 1);
        }

        /**
         * Rewind for reading.
         */
        public void rewind() {
            seek(0);
        }

        public void seek(int pos) {
            if (pos >= 0 && pos < used) {
                next = pos;
                return;
            }
            throw new IndexOutOfBoundsException();
        }

        public void seek(int offset, int whence) {
            switch (whence) {
                case SEEK_SET:
                    whence = 0;
                    break;
                case SEEK_CUR:
                    whence = next;
                    break;
                case SEEK_END:
                    whence = used;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            seek(whence + offset);
        }

        public int size() {
            return InstallmentByteBuffer.this.size();
        }
    }

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    private static final int INSTALLMENT_BITS = 10;
    private static final int INSTALLMENT_BYTES = 1 << INSTALLMENT_BITS;
    private static final int INSTALLMENT_MASK = INSTALLMENT_BYTES - 1;

    private ArrayList<byte[]> savings = new ArrayList<>();
    private int used = 0;

    public InstallmentByteBuffer() {
        this(INSTALLMENT_BYTES);
    }

    public InstallmentByteBuffer(int capacity) {
        setupCapacity(capacity);
    }

    public InstallmentByteBuffer append(byte[] a) {
        return append(a, 0, a.length);
    }

    public InstallmentByteBuffer append(byte[] a, int i, int j) {
        int n = j - i;
        setupCapacity(used + n);

        if ((used & INSTALLMENT_MASK) != 0) {
            int left = INSTALLMENT_BYTES - (used & INSTALLMENT_MASK);
            if (left > n) {
                left = n;
            }
            System.arraycopy(a, i, savings.get(used >> INSTALLMENT_BITS),
                    used & INSTALLMENT_MASK, left);
            i += left;
            used += left;
        }

        while (i + INSTALLMENT_BYTES < j) {
            System.arraycopy(a, i, savings.get(used >> INSTALLMENT_BITS),
                    0, INSTALLMENT_BYTES);
            i += INSTALLMENT_BYTES;
            used += INSTALLMENT_BYTES;
        }

        if (i < j) {
            System.arraycopy(a, i, savings.get(used >> INSTALLMENT_BITS),
                    0, j - i);
            used += j - i;
        }

        return this;
    }

    public InstallmentByteBuffer append(int b) {
        setupCapacity(used + 1);
        put(used++, (byte) (b & 0xFF));
        return this;
    }

    public InstallmentByteBuffer append(String s) {
        return append(s.getBytes());
    }

    public int capacity() {
        return savings.size() << INSTALLMENT_BITS;
    }

    private int get(int pos) {
        return savings.get(pos >> INSTALLMENT_BITS)[pos & INSTALLMENT_MASK];
    }

    /**
     * @return a copy of valid in bounds byte array
     */
    public byte[] getBytes() {
        byte[] array = new byte[used];
        int i = 0;
        while (i + INSTALLMENT_BYTES <= used) {
            System.arraycopy(savings.get(i >> INSTALLMENT_BITS),
                    0, array, i, INSTALLMENT_BYTES);
            i += INSTALLMENT_BYTES;
        }
        System.arraycopy(savings.get(i >> INSTALLMENT_BITS),
                0, array, i, used - i);
        return array;
    }

    public boolean isEmpty() {
        return used == 0;
    }

    private void put(int pos, byte b) {
        savings.get(pos >> INSTALLMENT_BITS)[pos & INSTALLMENT_MASK] = b;
    }

    public Reader reader() {
        return new Reader();
    }

    public void rewind() {
        seek(0);
    }

    public boolean seek(int pos) {
        if (pos >= 0 && pos < used) {
            used = pos;
            return true;
        }
        throw new IndexOutOfBoundsException();
    }

    private void setupCapacity(int newLength) {
        if (savings == null) {
            savings = new ArrayList<>();
        }
        if (newLength > capacity()) {
            int n = newLength >> INSTALLMENT_BITS;
            if ((newLength & INSTALLMENT_MASK) != 0) {
                ++n;
            }
            for (int i = savings.size() + 1; i <= n; ++i) {
                savings.add(new byte[INSTALLMENT_BYTES]);
            }
        }
    }

    public int size() {
        return used;
    }

    @Override
    public String toString() {
        try {
            return new String(getBytes(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error("Not support utf8");
        }
    }

    /**
     * For writing, erase every slot's content and reset size.<br/>
     */
    public void wipe() {
        for (byte[] a : savings) {
            Arrays.fill(a, (byte) 0);
        }
        used = 0;
    }
}
