package org.intermine.api.types;

import java.util.Map.Entry;

import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * A tuple of order two.
 * @author Alex Kalderimis
 * 
 * This is also an implementation of Map.Entry, with the exception that setValue
 * throws an error.
 *
 * @param <A> The first element in the pair is an A.
 * @param <B> The second element in the pair is a B.
 */
public final class Pair<A, B> implements Entry<A, B>
{
    final A a;
    final B b;
    
    /**
     * Construct a pair of two things
     * @param a Thing the first
     * @param b Thing the second
     */
    public Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public A getKey() {
        return a;
    }

    @Override
    public B getValue() {
        return b;
    }

    @Override
    public B setValue(B value) {
        throw new UnsupportedOperationException("Pairs are final");
    } 
    
    @Override
    public String toString() {
        return String.format("org.intermine.webservice.core.Pair(%s => %s)", a, b);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(13, 27).append(a).append(b).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;

        if (other instanceof Entry<?, ?>){
            Entry<?,?> otherEntry = (Entry<?,?>) other;
            return (a == null ? otherEntry.getKey() == null : a.equals(otherEntry.getKey()))
                    && (b == null ? otherEntry.getValue() == null : b.equals(otherEntry));
        }
        return false;
    }
}
