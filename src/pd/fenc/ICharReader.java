package pd.fenc;

import java.util.PrimitiveIterator.OfInt;

public interface ICharReader {

    public static ICharReader wrap(CharSequence cs) {
        // pain: two or more intermediate objects
        // gain: existing facilities and avoid seldom O(n) on chatAt(i)
        return wrap(cs.codePoints().iterator());
    }

    private static ICharReader wrap(OfInt it) {

        return new ICharReader() {

            private int pos = 0;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public int next() {
                int value = it.nextInt();
                pos++;
                return value;
            }

            @Override
            public int position() {
                return pos;
            }
        };
    }

    public boolean hasNext();

    /**
     * provide an unicode character
     */
    public int next();

    public int position();
}
