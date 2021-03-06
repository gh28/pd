package pd.fenc;

import static pd.fenc.Util.checkByte;

import java.util.ArrayList;
import java.util.Arrays;

public class InstallmentByteBuffer implements IWriter {

    /**
     * not a java.io.Reader<br/>
     */
    public class Reader implements IReader {

        public static final int SEEK_SET = 0;
        public static final int SEEK_CUR = 1;
        public static final int SEEK_END = 2;

        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos >= 0 && pos < size();
        }

        public void moveBack() {
            seek(size - 1);
        }

        @Override
        public int next() {
            if (hasNext()) {
                return InstallmentByteBuffer.this.get(pos++) & 0xFF;
            }
            return -1;
        }

        public int peek() {
            if (hasNext()) {
                return InstallmentByteBuffer.this.get(pos) & 0xFF;
            }
            return -1;
        }

        @Override
        public int position() {
            return pos;
        }

        public void seek(int pos) {
            if (pos >= 0 && pos < size) {
                this.pos = pos;
                return;
            }
            throw new IndexOutOfBoundsException();
        }

        public void seek(int whence, int offset) {
            switch (whence) {
                case SEEK_SET:
                    whence = 0;
                    break;
                case SEEK_CUR:
                    whence = pos;
                    break;
                case SEEK_END:
                    whence = size;
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

    private static final int INSTALLMENT_BITS = 10;

    private static final int INSTALLMENT_BYTES = 1 << INSTALLMENT_BITS;
    private static final int INSTALLMENT_MASK = INSTALLMENT_BYTES - 1;

    private ArrayList<byte[]> savings = new ArrayList<>();

    private int size = 0;

    public InstallmentByteBuffer() {
        this(INSTALLMENT_BYTES);
    }

    public InstallmentByteBuffer(int initialCapacity) {
        setupCapacity(initialCapacity);
    }

    public int capacity() {
        return savings.size() << INSTALLMENT_BITS;
    }

    /**
     * @return a copy of valid in bounds byte array
     */
    public byte[] copyBytes() {
        byte[] bytes = new byte[size];
        int i = 0;
        while (i + INSTALLMENT_BYTES <= size) {
            System.arraycopy(savings.get(i >> INSTALLMENT_BITS),
                    0, bytes, i, INSTALLMENT_BYTES);
            i += INSTALLMENT_BYTES;
        }
        System.arraycopy(savings.get(i >> INSTALLMENT_BITS),
                0, bytes, i, size - i);
        return bytes;
    }

    private int get(int pos) {
        return savings.get(pos >> INSTALLMENT_BITS)[pos & INSTALLMENT_MASK];
    }

    @Override
    public int position() {
        return size;
    }

    public InstallmentByteBuffer push(byte[] a) {
        return push(a, 0, a.length);
    }

    public InstallmentByteBuffer push(byte[] a, int i, int j) {
        int n = j - i;
        setupCapacity(size + n);

        if ((size & INSTALLMENT_MASK) != 0) {
            int left = INSTALLMENT_BYTES - (size & INSTALLMENT_MASK);
            if (left > n) {
                left = n;
            }
            System.arraycopy(a, i, savings.get(size >> INSTALLMENT_BITS),
                    size & INSTALLMENT_MASK, left);
            i += left;
            size += left;
        }

        while (i + INSTALLMENT_BYTES < j) {
            System.arraycopy(a, i, savings.get(size >> INSTALLMENT_BITS),
                    0, INSTALLMENT_BYTES);
            i += INSTALLMENT_BYTES;
            size += INSTALLMENT_BYTES;
        }

        if (i < j) {
            System.arraycopy(a, i, savings.get(size >> INSTALLMENT_BITS),
                    0, j - i);
            size += j - i;
        }

        return this;
    }

    @Override
    public InstallmentByteBuffer push(int ch) {
        checkByte(ch);
        setupCapacity(size + 1);
        put(size++, (byte) ch);
        return this;
    }

    private void put(int pos, byte b) {
        savings.get(pos >> INSTALLMENT_BITS)[pos & INSTALLMENT_MASK] = b;
    }

    public Reader reader() {
        return new Reader();
    }

    private void setupCapacity(int newCapacity) {
        if (savings == null) {
            savings = new ArrayList<>();
        }
        if (newCapacity > capacity()) {
            int n = newCapacity >> INSTALLMENT_BITS;
            if ((newCapacity & INSTALLMENT_MASK) != 0) {
                ++n;
            }
            for (int i = savings.size() + 1; i <= n; ++i) {
                savings.add(new byte[INSTALLMENT_BYTES]);
            }
        }
    }

    public int size() {
        return size;
    }

    @Override
    public String toString() {
        return new String(copyBytes());
    }

    /**
     * For writing, erase every slot's content and reset size.<br/>
     */
    public void wipe() {
        for (byte[] a : savings) {
            Arrays.fill(a, (byte) 0);
        }
        size = 0;
    }
}
